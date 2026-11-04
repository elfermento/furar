// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Festivo nacional ou autonómico que se goza en Galicia (catálogo da plataforma, plan §1.2.12), co
 * nome en gl e es e a súa fonte no BOE e no DOG.
 */
public record PublicHoliday(LocalDate date, String nameGl, String nameEs, String source) {

  public PublicHoliday {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(nameGl, "nameGl");
    Objects.requireNonNull(nameEs, "nameEs");
    Objects.requireNonNull(source, "source");
  }
}
