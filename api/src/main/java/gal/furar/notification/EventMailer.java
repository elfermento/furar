// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

/**
 * Envía os correos dos eventos do concello (ADR-0038): á persoa cando o concello acepta ou rexeita
 * a súa solicitude, e a cada persoa apuntada cando o evento cambia de data, hora ou lugar ou se
 * cancela. Síncrono, coma {@link IncidentMailer}: chámao {@code event} despois do commit, fóra de
 * toda transacción e no concello do evento. {@code notification} pon o nome, a cor e o contacto do
 * concello, escribe o correo, envíao e garda en {@code notification_delivery} a quen, cando e o
 * resultado (nunca o contido). Cada correo chega unha vez a cada persoa: un envío xa rexistrado non
 * se repite. Un SMTP caído non é unha excepción: queda {@code FAILED}.
 */
public interface EventMailer {

  /**
   * «Tes praza» ou «Non puidemos darche praza» á persoa que a pediu.
   *
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  DeliveryResult sendDecision(EventDecisionMail mail);

  /**
   * «O evento cambiou» ou «Cancelamos o evento» a cada persoa.
   *
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  void sendNotice(EventNoticeMail mail);

  /**
   * «Hai unha praza libre» á persoa da lista de espera (ADR-0041). Un correo por aviso.
   *
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  DeliveryResult sendWaitlistNotice(EventWaitlistMail mail);

  /**
   * «Cancelamos unha sesión» ou «A actividade remata antes» a cada persoa con praza (ADR-0041).
   *
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  void sendSessionNotice(EventSessionMail mail);
}
