// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import gal.furar.opendata.application.port.in.OccupancySink;
import gal.furar.opendata.application.port.in.OpenDataPublisher;
import gal.furar.opendata.domain.OccupancyRange;
import gal.furar.opendata.domain.QuarterRow;
import gal.furar.shared.web.CsvCells;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * A ocupación en CSV ou JSON, escrita mentres se le (ADR-0034, puntos 16 e 20): a cabeceira (ou o
 * envoltorio) ao empezar e cada anaco despois de pechar a súa transacción. Sen fila nos tramos
 * pechados ou bloqueados; as tres columnas do uso, baleiras nos libres.
 */
final class OccupancyWriter implements OccupancySink {

  static final List<String> COLUMNS =
      List.of(
          "instalacion",
          "uso_id",
          "uso_nome",
          "modo_aluguer",
          "data",
          "inicio",
          "fin",
          "ocupacion");

  /** Instante con segundos e desfase: {@code 2026-10-15T10:30:00+02:00}. */
  static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

  private final Supplier<Writer> open;
  private final boolean csv;
  private final boolean all;
  private final JsonMapper json;
  private final Map<String, Object> envelope;
  private Writer out;
  private boolean first = true;

  /**
   * @param open abre a saída (as cabeceiras HTTP xa van postas)
   * @param envelope o envoltorio do JSON, sen {@code datos}
   */
  OccupancyWriter(
      Supplier<Writer> open,
      boolean csv,
      boolean all,
      JsonMapper json,
      Map<String, Object> envelope) {
    this.open = open;
    this.csv = csv;
    this.all = all;
    this.json = json;
    this.envelope = envelope;
  }

  @Override
  public void begin(List<OpenDataPublisher> publishers, @Nullable LocalDate updatedOn) {
    out = open.get();
    if (csv) {
      List<String> header = new ArrayList<>();
      if (all) {
        header.add("concello");
      }
      header.addAll(COLUMNS);
      StringBuilder head = new StringBuilder(CsvCells.BOM);
      OpenDataWriter.line(head, header);
      write(head.toString());
    } else {
      String opening = json.writeValueAsString(envelope);
      // O envoltorio sen pechar, para engadir «datos» fila a fila.
      write(opening.substring(0, opening.length() - 1) + ",\"datos\":[");
    }
  }

  @Override
  public void write(OpenDataPublisher publisher, List<QuarterRow> rows, Map<UUID, UseLabel> uses) {
    StringBuilder chunk = new StringBuilder();
    for (QuarterRow r : rows) {
      UseLabel use = r.useId() == null ? null : uses.get(r.useId());
      if (csv) {
        List<String> cells = new ArrayList<>();
        if (all) {
          cells.add(CsvCells.text(publisher.slug()));
        }
        cells.add(r.facilityId().toString());
        cells.add(r.useId() == null ? "" : r.useId().toString());
        cells.add(use == null ? "" : CsvCells.text(use.name()));
        cells.add(use == null ? "" : OpenDataWriter.mode(use.rentalMode()));
        cells.add(r.day().toString());
        cells.add(INSTANT.format(r.start()));
        cells.add(INSTANT.format(r.end()));
        cells.add(Integer.toString(r.value()));
        OpenDataWriter.line(chunk, cells);
      } else {
        chunk.append(first ? "" : ",").append('{');
        if (all) {
          chunk
              .append("\"concello\":")
              .append(json.writeValueAsString(publisher.slug()))
              .append(',');
        }
        chunk.append("\"instalacion\":\"").append(r.facilityId()).append("\",");
        chunk
            .append("\"uso_id\":")
            .append(r.useId() == null ? "null" : "\"" + r.useId() + "\"")
            .append(',');
        chunk
            .append("\"uso_nome\":")
            .append(use == null ? "null" : json.writeValueAsString(use.name()))
            .append(',');
        chunk
            .append("\"modo_aluguer\":")
            .append(use == null ? "null" : "\"" + OpenDataWriter.mode(use.rentalMode()) + "\"")
            .append(',');
        chunk.append("\"data\":\"").append(r.day()).append("\",");
        chunk.append("\"inicio\":\"").append(INSTANT.format(r.start())).append("\",");
        chunk.append("\"fin\":\"").append(INSTANT.format(r.end())).append("\",");
        chunk.append("\"ocupacion\":").append(r.value()).append('}');
        first = false;
      }
    }
    write(chunk.toString());
  }

  /** Pecha o JSON (ou nada no CSV) e baleira a saída. */
  void finish() {
    if (out == null) {
      return;
    }
    if (!csv) {
      write("]}");
    }
    try {
      out.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void write(String text) {
    try {
      out.write(text);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** O nome do ficheiro: {@code <slug>-ocupacion-<desde>-<ata>.<formato>}. */
  static String fileName(String prefix, OccupancyRange range, String format) {
    return prefix + "-ocupacion-" + range.from() + "-" + range.to() + "." + format;
  }
}
