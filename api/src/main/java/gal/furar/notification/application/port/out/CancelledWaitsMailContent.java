// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.booking.CancelledWaitsNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.shared.domain.Language;
import java.util.Objects;

/**
 * Todo o que conta o correo «Lista de espera cancelada»: a instalación, as horas, o concello e o
 * idioma. Sen nome de ninguén.
 */
public record CancelledWaitsMailContent(
    Language language, CancelledWaitsNotice notice, MunicipalityMailContext municipality) {

  public CancelledWaitsMailContent {
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(notice, "notice");
    Objects.requireNonNull(municipality, "municipality");
  }
}
