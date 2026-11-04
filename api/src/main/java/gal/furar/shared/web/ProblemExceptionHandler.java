// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.AuthError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converte toda excepción dun controlador en {@code application/problem+json} (ADR-0008). */
@RestControllerAdvice
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemExceptionHandler.class);
  private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");
  private static final Set<String> SIZE_CONSTRAINTS = Set.of("Size", "Length");
  private static final Set<String> RANGE_CONSTRAINTS =
      Set.of("Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Range");

  private final ProblemWriter problems;

  public ProblemExceptionHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException e, HttpServletRequest request) {
    return problems.response(e.errorCode(), e.params(), e.violations(), request);
  }

  /** {@code AuthorizationDeniedException} do caso de uso: 403, ou 401 se non hai sesión. */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(AccessDeniedException e, HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean anonymous =
        auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();
    ErrorCode code = anonymous ? AuthError.UNAUTHENTICATED : AuthError.FORBIDDEN;
    return problems.response(code, Map.of(), List.of(), request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> stale(
      ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
    return problems.response(CommonError.STALE_VERSION, Map.of(), List.of(), request);
  }

  /**
   * Bloqueo de fila que non chega a tempo ({@code lock_timeout}) ou interbloqueo: 503 {@code
   * common.busy}. Hibernate xa o traduce así; o bloqueo da instalación por JDBC tradúceo o seu
   * adaptador.
   */
  @ExceptionHandler(PessimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> busy(
      PessimisticLockingFailureException e, HttpServletRequest request) {
    LOG.warn(
        "Lock not acquired in time [traceId={}]: {}",
        ProblemWriter.traceId(request),
        e.getMostSpecificCause().getMessage());
    return problems.response(CommonError.BUSY, Map.of(), List.of(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
    LOG.error("Unexpected error [traceId={}]", ProblemWriter.traceId(request), e);
    return problems.response(CommonError.INTERNAL, Map.of(), List.of(), request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest webRequest) {
    HttpServletRequest request =
        ((NativeWebRequest) webRequest).getNativeRequest(HttpServletRequest.class);
    List<FieldViolation> violations = List.of();
    ErrorCode code;
    switch (ex) {
      case MethodArgumentNotValidException e -> {
        code = CommonError.VALIDATION_FAILED;
        violations = violations(e.getBindingResult().getAllErrors());
      }
      case HandlerMethodValidationException e -> {
        code = CommonError.VALIDATION_FAILED;
        violations = violations(e.getAllErrors());
      }
      case HttpMessageNotReadableException e -> {
        LOG.debug("Unreadable request body", e);
        code = CommonError.MALFORMED_JSON;
      }
      case HttpRequestMethodNotSupportedException e -> code = CommonError.METHOD_NOT_ALLOWED;
      case HttpMediaTypeNotSupportedException e -> code = CommonError.UNSUPPORTED_MEDIA_TYPE;
      case HttpMediaTypeNotAcceptableException e -> code = CommonError.NOT_ACCEPTABLE;
      case NoResourceFoundException e -> code = CommonError.NOT_FOUND;
      case NoHandlerFoundException e -> code = CommonError.NOT_FOUND;
      case MissingRequestHeaderException e when "If-Match".equalsIgnoreCase(e.getHeaderName()) ->
          code = CommonError.PRECONDITION_REQUIRED;
      default -> {
        if (statusCode.is5xxServerError()) {
          LOG.error("Unexpected error [traceId={}]", ProblemWriter.traceId(request), ex);
          code = CommonError.INTERNAL;
        } else {
          code = CommonError.INVALID_PARAMETER;
        }
      }
    }
    ResponseEntity<ProblemDetail> response = problems.response(code, Map.of(), violations, request);
    HttpHeaders merged = new HttpHeaders();
    merged.addAll(headers);
    merged.addAll(response.getHeaders());
    return new ResponseEntity<>(response.getBody(), merged, response.getStatusCode());
  }

  private static List<FieldViolation> violations(List<? extends MessageSourceResolvable> errors) {
    List<FieldViolation> result = new ArrayList<>();
    for (MessageSourceResolvable error : errors) {
      String field =
          switch (error) {
            case FieldError fe -> fe.getField();
            case ObjectError oe -> oe.getObjectName();
            default -> "request";
          };
      String constraint = error.getCodes() == null ? "" : lastSegment(error.getCodes());
      Map<String, Object> params = new LinkedHashMap<>();
      ValidationError code = codeOf(constraint);
      if (error instanceof ObjectError oe && oe.contains(ConstraintViolation.class)) {
        ConstraintDescriptor<?> descriptor =
            oe.unwrap(ConstraintViolation.class).getConstraintDescriptor();
        for (String attribute : List.of("min", "max", "value", "regexp")) {
          Object v = descriptor.getAttributes().get(attribute);
          if (v != null) {
            params.put(attribute, v);
          }
        }
      }
      result.add(FieldViolation.of(field, code, params));
    }
    return result;
  }

  private static String lastSegment(String[] codes) {
    String last = codes[codes.length - 1];
    int dot = last.lastIndexOf('.');
    return dot < 0 ? last : last.substring(dot + 1);
  }

  private static ValidationError codeOf(String constraint) {
    if (REQUIRED_CONSTRAINTS.contains(constraint)) {
      return ValidationError.REQUIRED;
    }
    if (SIZE_CONSTRAINTS.contains(constraint)) {
      return ValidationError.SIZE;
    }
    if (RANGE_CONSTRAINTS.contains(constraint)) {
      return ValidationError.RANGE;
    }
    if ("Pattern".equals(constraint) || "Email".equals(constraint)) {
      return ValidationError.PATTERN;
    }
    return ValidationError.INVALID;
  }
}
