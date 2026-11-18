// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRetention;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.application.port.out.ResidentBookings;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.account.AccountClosureStep;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * As reservas dunha conta eliminada (ADR-0043): as <b>sen pago</b> anonimízanse xa, coma se lles
 * chegase o prazo de conservación ({@link BookingRetentionService}, a mesma anonimización: os
 * campos marcados como dato persoal, a partida aberta, as persoas da reserva e a persoa). As de
 * <b>pago</b> quedan como están ata o seu prazo ({@code furar.booking.paid-retention}, 5 anos, pola
 * normativa tributaria) e anonimízanse daquela coma sempre.
 *
 * <p>En cada concello coa súa RLS, en lotes de {@value BookingRetentionService#BATCH} nunha
 * transacción curta cada un. Se a conta non reservou en ningún concello, non percorre ningún.
 * Idempotente: unha anonimizada xa non volve saír. O rexistro de actividade do concello leva a
 * cifra no evento da baixa ({@code people.account-close}), non nun evento propio.
 */
@Service
public class AccountClosureBookingService implements AccountClosureStep {

  /** Detalle do rexistro de actividade: reservas sen pago anonimizadas. */
  static final String DETAIL = "bookingsAnonymized";

  private final ResidentBookings residents;
  private final MunicipalityDirectory municipalities;
  private final BookingRetention bookings;
  private final BookingCustomFields customFields;
  private final OpenMatches openMatches;
  private final TransactionTemplate tx;
  private final Clock clock;

  public AccountClosureBookingService(
      ResidentBookings residents,
      MunicipalityDirectory municipalities,
      BookingRetention bookings,
      BookingCustomFields customFields,
      OpenMatches openMatches,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.residents = residents;
    this.municipalities = municipalities;
    this.bookings = bookings;
    this.customFields = customFields;
    this.openMatches = openMatches;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Override
  public String detail() {
    return DETAIL;
  }

  @Override
  public Map<UUID, Long> close(String subject) {
    Map<UUID, Long> anonymised = new LinkedHashMap<>();
    if (!residents.existAnywhere(subject)) {
      return anonymised;
    }
    for (TenantRef municipality : municipalities.all()) {
      long done = TenantContext.runAs(municipality, () -> all(subject));
      if (done > 0) {
        anonymised.put(municipality.id(), done);
      }
    }
    return anonymised;
  }

  /** Lote tras lote, ata que un vén incompleto. */
  private long all(String subject) {
    long done = 0;
    int last;
    do {
      last = Objects.requireNonNull(tx.execute(s -> batch(subject)), "batch");
      done += last;
    } while (last == BookingRetentionService.BATCH);
    return done;
  }

  private int batch(String subject) {
    List<BookingId> unpaid = bookings.unpaidOf(subject, BookingRetentionService.BATCH);
    if (unpaid.isEmpty()) {
      return 0;
    }
    // Coma na conservación: os campos primeiro (a reserva aínda ten a súa persoa) e a partida coa
    // reserva.
    unpaid.forEach(customFields::erasePersonalData);
    openMatches.purgeOfBookings(unpaid.stream().map(BookingId::value).toList());
    bookings.anonymise(unpaid, clock.instant());
    return unpaid.size();
  }
}
