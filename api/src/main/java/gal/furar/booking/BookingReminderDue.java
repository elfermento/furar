// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: tocou o recordatorio dunha reserva (ADR-0035). Publícao a tarefa na mesma
 * transacción que deixa a reserva marcada (non se repite); o escoitador (o correo) recibe despois
 * do commit e le o resto con {@link BookingQueries#reminder}. Sen datos de persoa. Leva sempre o
 * concello (ADR-0006).
 */
public record BookingReminderDue(UUID municipalityId, UUID bookingId) implements TenantAware {

  public BookingReminderDue {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(bookingId, "bookingId");
  }
}
