// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import gal.furar.opendata.adapter.in.web.dto.OpenDataBytesResponse;
import gal.furar.opendata.adapter.in.web.dto.OpenDataCustomFieldResponse;
import gal.furar.opendata.adapter.in.web.dto.OpenDataDatasetResponse;
import gal.furar.opendata.adapter.in.web.dto.OpenDataOccupancyResponse;
import gal.furar.opendata.adapter.in.web.dto.OpenDataSummaryResponse;
import gal.furar.opendata.adapter.in.web.dto.OpenDataTextResponse;
import gal.furar.opendata.application.port.in.CatalogRecord;
import gal.furar.opendata.application.port.in.ClosuresFile;
import gal.furar.opendata.application.port.in.FacilitiesFile;
import gal.furar.opendata.application.port.in.OccupancySink;
import gal.furar.opendata.application.port.in.OpenDataOverview;
import gal.furar.opendata.application.port.in.OpenDataPublisher;
import gal.furar.opendata.application.port.in.ReadOpenDataUseCase;
import gal.furar.opendata.application.port.in.Scope;
import gal.furar.opendata.domain.Dataset;
import gal.furar.opendata.domain.OccupancyRange;
import gal.furar.opendata.domain.QuarterRow;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * O común ás rutas dun concello e ás de todos (ADR-0034, puntos 10, 11, 13, 18 e 20): o idioma, as
 * cabeceiras (tipo, nome do ficheiro, caché, {@code ETag} forte e CORS aberto só nos ficheiros e na
 * ficha, tamén nos seus erros) e a resposta 304. As instalacións, os peches e a ficha xéranse en
 * memoria; a ocupación escríbese mentres se le.
 */
@Component
class OpenDataFiles {

  static final String CSV = "text/csv; charset=utf-8";
  static final String JSON = "application/json";
  static final String GEOJSON = "application/geo+json";
  static final String RDF_XML = "application/rdf+xml";

  private final ReadOpenDataUseCase openData;
  private final JsonMapper json;
  private final String publicUrl;

  OpenDataFiles(
      ReadOpenDataUseCase openData,
      JsonMapper json,
      @Value("${furar.public-url}") String publicUrl) {
    this.openData = openData;
    this.json = json;
    this.publicUrl = publicUrl;
  }

  OpenDataSummaryResponse summary(Scope scope) {
    OpenDataOverview o = openData.overview(scope);
    return new OpenDataSummaryResponse(
        List.of(
            dataset(Dataset.FACILITIES, o, List.of("csv", "json", "geojson")),
            dataset(Dataset.CLOSURES, o, List.of("csv", "json")),
            dataset(Dataset.OCCUPANCY, o, List.of("csv", "json"))),
        new OpenDataOccupancyResponse(
            o.availableFrom(),
            o.availableTo(),
            o.defaultFrom(),
            o.defaultTo(),
            o.maxMonths(),
            new OpenDataBytesResponse(o.csvBytesPerDay(), o.jsonBytesPerDay())),
        o.catalog(),
        o.municipalities(),
        o.legalEntityName(),
        o.customFields().stream()
            .map(
                f ->
                    new OpenDataCustomFieldResponse(
                        f.code(),
                        new OpenDataTextResponse(f.label().gl(), f.label().es()),
                        f.type()))
            .toList());
  }

  private static OpenDataDatasetResponse dataset(
      Dataset dataset, OpenDataOverview o, List<String> formats) {
    return new OpenDataDatasetResponse(dataset.name(), o.updatedOn().get(dataset), formats);
  }

  void facilities(
      Scope scope,
      String prefix,
      String format,
      @Nullable String lang,
      HttpServletRequest request,
      HttpServletResponse response) {
    cors(response);
    format(format, true);
    Language language = language(lang);
    FacilitiesFile file = openData.facilities(scope);
    OpenDataWriter writer = new OpenDataWriter(json, publicUrl, language, scope == Scope.ALL);
    String body =
        switch (format) {
          case "json" -> writer.facilitiesJson(file);
          case "geojson" -> writer.facilitiesGeoJson(file);
          default -> writer.facilitiesCsv(file);
        };
    send(request, response, body, type(format), prefix + "-instalacions." + format, 300);
  }

  void closures(
      Scope scope,
      String prefix,
      String format,
      @Nullable String lang,
      HttpServletRequest request,
      HttpServletResponse response) {
    cors(response);
    format(format, false);
    Language language = language(lang);
    ClosuresFile file = openData.closures(scope);
    OpenDataWriter writer = new OpenDataWriter(json, publicUrl, language, scope == Scope.ALL);
    String body = "json".equals(format) ? writer.closuresJson(file) : writer.closuresCsv(file);
    send(request, response, body, type(format), prefix + "-peches." + format, 300);
  }

  void occupancy(
      Scope scope,
      String prefix,
      String format,
      @Nullable String from,
      @Nullable String to,
      @Nullable String lang,
      HttpServletRequest request,
      HttpServletResponse response) {
    cors(response);
    format(format, false);
    Language language = language(lang);
    OccupancyRange range = openData.occupancyRange(scope, from, to);
    String fileName = OccupancyWriter.fileName(prefix, range, format);
    if (scope == Scope.MUNICIPALITY) {
      String etag = '"' + openData.occupancyTag(range) + "-" + format + "-" + language.code() + '"';
      response.setHeader(HttpHeaders.ETAG, etag);
      if (notModified(request, etag)) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
        response.setStatus(HttpStatus.NOT_MODIFIED.value());
        return;
      }
    }
    OpenDataWriter envelopeWriter =
        new OpenDataWriter(json, publicUrl, language, scope == Scope.ALL);
    Map<String, Object> envelope = new LinkedHashMap<>();
    OccupancyWriter writer =
        new OccupancyWriter(
            () -> {
              response.setContentType(type(format));
              response.setCharacterEncoding(StandardCharsets.UTF_8.name());
              response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName));
              response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
              try {
                return new BufferedWriter(
                    new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            },
            "csv".equals(format),
            scope == Scope.ALL,
            json,
            envelope);
    try {
      openData.occupancy(scope, range, new Envelope(writer, envelope, envelopeWriter, range));
    } catch (DomainException e) {
      if (e.errorCode() == CommonError.RATE_LIMITED) {
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
      }
      throw e;
    }
    writer.finish();
  }

  /** Completa o envoltorio do JSON cos concellos e a data antes de escribir. */
  private record Envelope(
      OccupancyWriter writer,
      Map<String, Object> envelope,
      OpenDataWriter base,
      OccupancyRange range)
      implements OccupancySink {

    @Override
    public void begin(List<OpenDataPublisher> publishers, @Nullable LocalDate updatedOn) {
      envelope.putAll(base.envelopeOf(Dataset.OCCUPANCY, publishers, updatedOn));
      envelope.put("desde", range.from().toString());
      envelope.put("ata", range.to().toString());
      writer.begin(publishers, updatedOn);
    }

    @Override
    public void write(
        OpenDataPublisher publisher, List<QuarterRow> rows, Map<UUID, UseLabel> uses) {
      writer.write(publisher, rows, uses);
    }
  }

  void catalog(
      Scope scope, String prefix, HttpServletRequest request, HttpServletResponse response) {
    cors(response);
    Optional<CatalogRecord> record = openData.catalog(scope);
    if (record.isEmpty()) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
    String body = new DcatCatalogWriter(publicUrl).write(record.get());
    send(request, response, body, RDF_XML, prefix + "-datos-abertos.rdf", 300);
  }

  /** {@code Access-Control-Allow-Origin: *}: só nos ficheiros e na ficha (ADR-0034). */
  static void cors(HttpServletResponse response) {
    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
  }

  /** CSV e JSON nos tres; GeoJSON só nas instalacións. Outro, 404 (punto 23). */
  static void format(String format, boolean geoJson) {
    if (!"csv".equals(format) && !"json".equals(format) && !(geoJson && "geojson".equals(format))) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
  }

  /** {@code lang=gl|es}, por defecto {@code gl}; outro valor, 400. */
  static Language language(@Nullable String lang) {
    if (lang == null || lang.isBlank()) {
      return Language.GL;
    }
    return Language.fromCode(lang.strip())
        .orElseThrow(() -> new DomainException(CommonError.INVALID_PARAMETER, Map.of()));
  }

  static String type(String format) {
    return switch (format) {
      case "json" -> JSON;
      case "geojson" -> GEOJSON;
      default -> CSV;
    };
  }

  private static void send(
      HttpServletRequest request,
      HttpServletResponse response,
      String body,
      String contentType,
      String fileName,
      int maxAge) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String etag = '"' + sha256(bytes) + '"';
    response.setHeader(HttpHeaders.ETAG, etag);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=" + maxAge);
    if (notModified(request, etag)) {
      response.setStatus(HttpStatus.NOT_MODIFIED.value());
      return;
    }
    response.setContentType(contentType);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName));
    response.setContentLength(bytes.length);
    try {
      response.getOutputStream().write(bytes);
      response.flushBuffer();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String attachment(String fileName) {
    return ContentDisposition.attachment().filename(fileName).build().toString();
  }

  /**
   * Se algún {@code If-None-Match} coincide co {@code ETag}. Sen o sufixo que pode engadir o proxy
   * ao comprimir ({@code -gzip}, {@code -zstd}) nin a marca de débil.
   */
  static boolean notModified(HttpServletRequest request, String etag) {
    String header = request.getHeader(HttpHeaders.IF_NONE_MATCH);
    if (header == null || header.isBlank()) {
      return false;
    }
    String wanted = normalize(etag);
    for (String candidate : header.split(",")) {
      String c = normalize(candidate);
      if (c.equals("*") || c.equals(wanted)) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String etag) {
    String e = etag.strip();
    if (e.startsWith("W/")) {
      e = e.substring(2);
    }
    e = e.replace("\"", "");
    for (String suffix : List.of("-gzip", "-zstd", "-br")) {
      if (e.endsWith(suffix)) {
        e = e.substring(0, e.length() - suffix.length());
      }
    }
    return e;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
