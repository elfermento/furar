// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Os correos das tempadas á entidade (ADR-0039, punto 12), unha vez por publicación. Un fallo con
 * reintento lanza {@link MailNotSentException}.
 */
public interface SendSeriesMail {

  /** «Tempada asignada» ou, se saíu dunha solicitude, «Solicitude aprobada». */
  void created(UUID seriesId);

  /**
   * «Tempada rematada».
   *
   * @param noticeId a publicación (rematala outra vez é outro correo)
   */
  void ended(UUID seriesId, LocalDate from, int cancelled, UUID noticeId);

  /** «Solicitude de tempada non aprobada», coa mensaxe do concello. */
  void rejected(UUID requestId);
}
