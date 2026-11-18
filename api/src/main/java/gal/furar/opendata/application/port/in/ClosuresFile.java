// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** O conxunto «Peches»: un anaco por concello (un só nun concello). */
public record ClosuresFile(List<Part> parts, @Nullable LocalDate updatedOn) {

  public ClosuresFile {
    parts = List.copyOf(parts);
  }

  /** Os peches dun concello, por data e instalación (os festivos primeiro). */
  public record Part(OpenDataPublisher publisher, List<Row> rows) {

    public Part {
      rows = List.copyOf(rows);
    }
  }

  /**
   * Un día.
   *
   * @param holiday festivo do catálogo (sen instalación) ou peche dunha instalación
   * @param reasonGl o nome do festivo en galego, ou o motivo do peche (nun só idioma)
   * @param reasonEs o nome do festivo en castelán, ou o mesmo motivo
   */
  public record Row(
      LocalDate date,
      boolean holiday,
      @Nullable UUID facilityId,
      @Nullable String reasonGl,
      @Nullable String reasonEs) {}
}
