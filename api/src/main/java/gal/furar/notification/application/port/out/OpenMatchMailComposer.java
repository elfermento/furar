// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.booking.OpenMatchNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.shared.domain.Language;

/** Escribe os correos das partidas abertas (asunto, HTML e texto) co concello (ADR-0040). */
public interface OpenMatchMailComposer {

  /** O correo {@code notice.kind()} a esa persoa, no seu idioma. */
  OutgoingMail compose(
      OpenMatchNotice notice,
      OpenMatchNotice.Recipient recipient,
      Language language,
      MunicipalityMailContext municipality);
}
