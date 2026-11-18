// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.IncidentReportedMail;
import gal.furar.notification.IncidentResolvedMail;
import gal.furar.shared.domain.Language;

/** Escribe os correos das incidencias (asunto, HTML e texto) co concello (ADR-0036). */
public interface IncidentMailComposer {

  /** «Nova incidencia» para un destinatario, no idioma do concello. */
  OutgoingMail reported(
      IncidentReportedMail mail,
      String recipient,
      Language language,
      MunicipalityMailContext municipality);

  /** «Resolvemos o teu aviso», no idioma da persoa. */
  OutgoingMail resolved(IncidentResolvedMail mail, MunicipalityMailContext municipality);
}
