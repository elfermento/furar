// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import gal.furar.booking.OpenMatchNotice;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Os correos das partidas abertas (ADR-0040, punto 12). Non é un caso de uso de ninguén: chámao a
 * escoita dos eventos de {@code booking}, despois do commit e no concello do evento. Pídelle a
 * {@code booking} a quen escribir co estado de agora e escribe a cada persoa por separado, no seu
 * idioma: un envío que falla non corta os demais; se a algún lle queda un intento, ao rematar lanza
 * {@link MailNotSentException} para que o rexistro de publicacións volva entregar o evento.
 */
public interface SendOpenMatchMail {

  /**
   * @param playerId a solicitude, en {@code REQUESTED}, {@code ACCEPTED} e {@code REJECTED}
   * @param eventId a clave do correo: a solicitude en {@code REQUESTED}, a publicación nos demais
   */
  void send(OpenMatchNotice.Kind kind, UUID matchId, @Nullable UUID playerId, UUID eventId);
}
