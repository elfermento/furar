// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import java.util.Collection;
import java.util.UUID;

/**
 * Envía o correo dunha invitación ao persoal (plan do dominio 4, §1.2.6 e §2.2; ADR-0027, punto 6).
 * Síncrono: chámao o caso de uso de {@code staff} despois do commit da invitación, fóra de toda
 * transacción e no concello da petición, co token en memoria dentro da ligazón. {@code
 * notification} pon o nome, a cor e o contacto do concello, escribe o correo no idioma pedido,
 * envíao e garda o resultado en {@code notification_delivery} (sen a ligazón).
 *
 * <p>Un SMTP caído non é unha excepción: devolve {@link DeliveryResult#FAILED} e quen chama gárdao
 * na invitación para ofrecer «Reenviar». Cada invitación envíase unha vez; se xa ten un envío
 * rexistrado, devolve o seu resultado sen enviar outro (un reenvío é outra invitación).
 *
 * <p>O rexistro do envío leva o correo da persoa invitada: {@link #forget} bórrao cando {@code
 * staff} xa non garda a invitación ou anonimiza a pertenza (RGPD art. 5.1.c e 5.1.e).
 */
public interface StaffInvitationMailer {

  /**
   * @throws IllegalStateException se se chama dentro dunha transacción ou sen concello
   */
  DeliveryResult send(StaffInvitationMail mail);

  /**
   * Esquece os envíos destas invitacións no concello actual: borra as súas filas de {@code
   * notification_delivery} (o correo de destino). Vai na transacción de quen chama, se a hai, para
   * que se borren xusto cando {@code staff} borra a invitación ou anonimiza a pertenza; as
   * invitacións sen envío ou doutro concello non se tocan. Idempotente.
   *
   * @return cantos envíos se borraron
   * @throws IllegalStateException sen concello
   */
  int forget(Collection<UUID> invitationIds);
}
