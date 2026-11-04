// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * O único escritor de erros RFC 9457 (ADR-0008). Úsano o {@code @RestControllerAdvice}, os
 * manexadores de Spring Security, o filtro de concello e o {@code ErrorController}.
 */
@Component
public class ProblemWriter {

  public static final String TYPE_BASE = "https://furar.gal/problemas/";
  public static final String TRACE_ATTRIBUTE = "furar.traceId";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final MessageSource messages;
  private final JsonMapper jsonMapper;

  public ProblemWriter(MessageSource messages, JsonMapper jsonMapper) {
    this.messages = messages;
    this.jsonMapper = jsonMapper;
  }

  /** Constrúe o problema traducido ao idioma da petición. */
  public ProblemDetail problem(
      ErrorCode code,
      Map<String, Object> params,
      List<FieldViolation> violations,
      HttpServletRequest request) {
    Locale locale = Locales.locale(Locales.language(request));
    ProblemDetail problem = ProblemDetail.forStatus(code.status());
    problem.setType(URI.create(TYPE_BASE + code.code()));
    problem.setTitle(text("problem." + code.code() + ".title", params, locale));
    problem.setDetail(text("problem." + code.code() + ".detail", params, locale));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code.code());
    problem.setProperty("params", params == null ? Map.of() : new LinkedHashMap<>(params));
    if (violations != null && !violations.isEmpty()) {
      problem.setProperty(
          "errors",
          violations.stream()
              .map(
                  v -> {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("field", v.field());
                    error.put("code", v.code());
                    error.put("params", v.params());
                    return error;
                  })
              .toList());
    }
    problem.setProperty("traceId", traceId(request));
    return problem;
  }

  public ResponseEntity<ProblemDetail> response(
      ErrorCode code,
      Map<String, Object> params,
      List<FieldViolation> violations,
      HttpServletRequest request) {
    return ResponseEntity.status(code.status())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.CONTENT_LANGUAGE, Locales.language(request).code())
        .body(problem(code, params, violations, request));
  }

  /** Escribe o problema directamente na resposta (filtros, fóra de Spring MVC). */
  public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
      throws IOException {
    write(request, response, code, Map.of());
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      ErrorCode code,
      Map<String, Object> params)
      throws IOException {
    ProblemDetail problem = problem(code, params, List.of(), request);
    response.setStatus(code.status());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader(HttpHeaders.CONTENT_LANGUAGE, Locales.language(request).code());
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }

  /** Identificador do erro para correlacionar coa traza do servidor. */
  public static String traceId(HttpServletRequest request) {
    Object existing = request.getAttribute(TRACE_ATTRIBUTE);
    if (existing instanceof String s) {
      return s;
    }
    byte[] bytes = new byte[8];
    RANDOM.nextBytes(bytes);
    String id = HexFormat.of().formatHex(bytes);
    request.setAttribute(TRACE_ATTRIBUTE, id);
    return id;
  }

  private String text(String key, Map<String, Object> params, Locale locale) {
    String template;
    try {
      template = messages.getMessage(key, null, locale);
    } catch (NoSuchMessageException e) {
      template = messages.getMessage("problem.common.internal.title", null, locale);
    }
    if (params != null) {
      for (Map.Entry<String, Object> p : params.entrySet()) {
        template = template.replace("{" + p.getKey() + "}", String.valueOf(p.getValue()));
      }
    }
    return template;
  }

  static HttpStatus statusOf(ErrorCode code) {
    return HttpStatus.valueOf(code.status());
  }
}
