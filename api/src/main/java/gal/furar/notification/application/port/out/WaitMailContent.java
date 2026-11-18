// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.booking.WaitNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.shared.domain.Language;
import java.util.Objects;

/** Todo o que conta o correo «Quedou libre»: a hora, o concello e o idioma. Sen nome de ninguén. */
public record WaitMailContent(
    Language language, WaitNotice notice, MunicipalityMailContext municipality) {

  public WaitMailContent {
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(notice, "notice");
    Objects.requireNonNull(municipality, "municipality");
  }
}
