// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.notification.domain.JoinRequest;
import gal.furar.shared.domain.Language;
import java.time.Instant;

/** Escribe o correo dunha solicitude de alta para quen opera o servizo (ADR-0042). */
public interface JoinRequestMailComposer {

  /**
   * @param language o idioma no que se encheu o formulario: o do correo
   * @param recipient a quen vai ({@code FURAR_JOIN_REQUEST_EMAIL})
   * @param receivedAt cando chegou
   */
  OutgoingMail compose(
      JoinRequest request, Language language, String recipient, Instant receivedAt);
}
