// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.PaymentStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Filtros da lista de xestión (plan do dominio 2, §2.2; maqueta 26). Un conxunto baleiro non
 * filtra; as datas son días en Europe/Madrid, incluídos, sobre o inicio da reserva.
 *
 * @param q nome ou correo de quen reserva (sen distinguir maiúsculas nin acentos) ou código
 */
public record BookingSearch(
    @Nullable LocalDate from,
    @Nullable LocalDate to,
    Set<UUID> facilityIds,
    Set<PaymentStatus> payments,
    Set<BookingStatus> statuses,
    @Nullable String q,
    BookingSort sort,
    int page,
    int size) {

  public BookingSearch {
    facilityIds = facilityIds == null ? Set.of() : Set.copyOf(facilityIds);
    payments = payments == null ? Set.of() : Set.copyOf(payments);
    statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    Objects.requireNonNull(sort, "sort");
  }
}
