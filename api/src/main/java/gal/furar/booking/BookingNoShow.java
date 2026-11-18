// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Evento público: unha reserva con correo quedou como falta, pola tarefa ou polo persoal (ADR-0032,
 * punto 10). Publícase dentro da transacción e os escoitadores (o correo) reciben despois do
 * commit; len o resto con {@link BookingQueries}. Leva sempre o concello (ADR-0006).
 *
 * @param eventId a fila da historia desta falta: distingue o seu correo doutra falta da mesma
 *     reserva (desfeita e marcada de novo)
 * @param faults cantas faltas leva o correo na xanela do concello; 0 coa penalización apagada (o
 *     correo sae sen a frase do bloqueo)
 * @param threshold faltas que bloquean
 * @param windowDays en cantos días
 * @param blockDays días sen reservar por internet
 * @param blockedUntil o último día bloqueado (hora de Galicia), se a persoa quedou ou xa estaba
 *     bloqueada; nulo se non
 */
public record BookingNoShow(
    UUID municipalityId,
    UUID bookingId,
    UUID eventId,
    int faults,
    int threshold,
    int windowDays,
    int blockDays,
    @Nullable LocalDate blockedUntil)
    implements TenantAware {

  public BookingNoShow {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(eventId, "eventId");
  }
}
