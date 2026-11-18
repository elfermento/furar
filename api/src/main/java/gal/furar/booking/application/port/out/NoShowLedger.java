// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.BookingId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As faltas que contan, os bloqueos por faltas e os lotes da tarefa (ADR-0032, puntos 5 e 7).
 * Sempre no concello actual e dentro dunha transacción (ADR-0006): unha falta nun concello non
 * conta noutro.
 */
public interface NoShowLedger {

  /**
   * As faltas dese correo que contan: rematadas desde {@code from} e que non causaron un bloqueo
   * sen levantar. As dun bloqueo levantado seguen contando; as dun cumprido, xa non.
   */
  List<BookingId> countingFaults(String emailKey, Instant from);

  /** Garda un bloqueo dese correo coas faltas que o causaron. */
  void block(String emailKey, Instant startsAt, Instant endsAt, List<BookingId> faults);

  /** O bloqueo vixente e sen levantar dese correo, se o hai. */
  Optional<ActiveBlock> activeBlock(String emailKey, Instant now);

  /** Os correos de {@code emailKeys} cun bloqueo vixente e sen levantar, nunha consulta. */
  Set<String> blockedAmong(Collection<String> emailKeys, Instant now);

  /**
   * Levanta os bloqueos vixentes que causou esa reserva.
   *
   * @return cantos levantou
   */
  int lift(BookingId cause, Instant now, String actorSubject);

  /**
   * Un lote da tarefa: marca como falta ata {@code limit} reservas da instalación confirmadas, sen
   * marca e nunca tocadas, que empezaron despois de {@code since} e remataron como moito en {@code
   * endedBy}, cunha fila {@code NO_SHOW} na historia de cada unha. As que ten outra transacción
   * (unha validación, unha cancelación) sáltanse e non se pisan.
   */
  List<Marked> markEnded(
      UUID facilityId, Instant since, Instant endedBy, int limit, Instant now, String actorSubject);

  /**
   * Un bloqueo vixente.
   *
   * @param faults cantas faltas o causaron
   */
  record ActiveBlock(Instant endsAt, int faults) {}

  /**
   * Unha reserva que a tarefa marcou.
   *
   * @param emailKey o correo da reserva; nulo se non o ten (nin conta nin se avisa)
   * @param eventId a fila da historia: distingue o correo desta falta
   */
  record Marked(BookingId id, @Nullable String emailKey, UUID eventId) {}
}
