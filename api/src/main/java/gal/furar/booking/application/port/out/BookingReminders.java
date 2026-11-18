// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Os recordatorios do concello actual (ADR-0035): que reservas tocan e que correos non os queren.
 * Sempre dentro dunha transacción (ADR-0006).
 */
public interface BookingReminders {

  /**
   * Marca como recordadas ata {@code limit} reservas ás que lles toca agora (confirmadas, sen
   * anonimizar, con correo, sen recordatorio, o momento xa chegou, o prazo para cancelar de balde
   * non pasou e feitas como moito ese momento) e devolve cales. Colle as filas con {@code for
   * update skip locked}: dúas instancias á vez non marcan a mesma, e unha cancelación que ten a
   * fila gaña.
   */
  List<Marked> markDue(Instant now, Duration lead, int limit);

  /** Unha reserva marcada e a clave do seu correo. */
  record Marked(UUID bookingId, @Nullable String emailKey) {}

  /** Os correos de {@code emailKeys} que non queren recordatorios neste concello. */
  Set<String> optedOutAmong(Collection<String> emailKeys);

  /** Se ese correo non quere recordatorios neste concello. */
  boolean optedOut(String emailKey);

  /** Ese correo non quere recordatorios neste concello (repetir non cambia nada). */
  void optOut(String emailKey, Instant now);

  /** Ese correo volve querer recordatorios neste concello. */
  void optIn(String emailKey);

  /** A clave do correo da reserva, aínda que estea cancelada; baleiro se non existe ou non ten. */
  Optional<String> holderEmailKey(UUID bookingId);
}
