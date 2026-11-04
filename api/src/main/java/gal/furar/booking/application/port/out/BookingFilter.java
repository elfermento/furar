// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.PaymentStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Filtro da lista de xestión xa resolto: instantes sobre o inicio da reserva e o texto xa
 * normalizado. Un conxunto baleiro non filtra.
 *
 * @param since inicio desde (incluído)
 * @param until inicio ata (excluído)
 * @param name parte do nome ou do correo de quen reserva, en minúsculas e sen acentos
 * @param code código exacto ({@code FUR-XXXX}); só se o texto pode selo
 * @param recentFirst as máis recentes primeiro; se non, por día e hora
 */
public record BookingFilter(
    @Nullable Instant since,
    @Nullable Instant until,
    Set<UUID> facilityIds,
    Set<PaymentStatus> payments,
    Set<BookingStatus> statuses,
    @Nullable String name,
    @Nullable String code,
    boolean recentFirst) {

  public BookingFilter {
    facilityIds = Set.copyOf(facilityIds);
    payments = Set.copyOf(payments);
    statuses = Set.copyOf(statuses);
  }
}
