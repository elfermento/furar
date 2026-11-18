// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import java.util.Collection;
import java.util.UUID;

/**
 * Envía os correos das incidencias (ADR-0036): «Nova incidencia», a cada persoa da xestión activa
 * do concello, e «Resolvemos o teu aviso», á persoa que avisou. Síncrono, coma {@link
 * StaffInvitationMailer}: chámao {@code incident} despois do commit, fóra de toda transacción e no
 * concello do evento. {@code notification} pon o nome, a cor e o contacto do concello, escribe o
 * correo, envíao e garda en {@code notification_delivery} a quen, cando e o resultado (nunca o
 * contido). Cada destinatario recibe cada correo unha vez: un envío xa rexistrado non se repite. Un
 * SMTP caído non é unha excepción: queda {@code FAILED}.
 */
public interface IncidentMailer {

  /**
   * «Nova incidencia» a cada destinatario.
   *
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  void sendReported(IncidentReportedMail mail);

  /**
   * «Resolvemos o teu aviso» á persoa que avisou.
   *
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  DeliveryResult sendResolved(IncidentResolvedMail mail);

  /**
   * Esquece os envíos destas incidencias no concello actual (levan correos): cando a xestión
   * elimina a incidencia. Na transacción de quen chama, se a hai. Idempotente.
   *
   * @return cantos envíos se borraron
   */
  int forget(Collection<UUID> incidentIds);
}
