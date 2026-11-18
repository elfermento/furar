// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.EventDecisionMail;
import gal.furar.notification.EventNoticeMail;
import gal.furar.notification.EventSessionMail;
import gal.furar.notification.EventWaitlistMail;

/** Escribe os correos dos eventos (asunto, HTML e texto) co concello (ADR-0038). */
public interface EventMailComposer {

  /** «Tes praza» ou «Non puidemos darche praza», no idioma da persoa. */
  OutgoingMail decision(EventDecisionMail mail, MunicipalityMailContext municipality);

  /** «O evento cambiou» ou «Cancelamos o evento», no idioma de cada persoa. */
  OutgoingMail notice(
      EventNoticeMail mail,
      EventNoticeMail.Recipient recipient,
      MunicipalityMailContext municipality);

  /** «Hai unha praza libre» (ADR-0041), no idioma da persoa. */
  OutgoingMail waitlist(EventWaitlistMail mail, MunicipalityMailContext municipality);

  /** «Cancelamos unha sesión» ou «A actividade remata antes», no idioma de cada persoa. */
  OutgoingMail session(
      EventSessionMail mail,
      EventNoticeMail.Recipient recipient,
      MunicipalityMailContext municipality);
}
