// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.in.BookingExportQuery;
import gal.furar.booking.application.port.in.BookingExportSink;
import gal.furar.booking.application.port.in.ExportBookingsUseCase;
import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingFilter;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingRules;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Descargar CSV» (maqueta 60): as reservas do período por día e hora, en tandas de {@value #BATCH}
 * (cada tanda cos seus nomes e campos propios nunha lectura, sen N+1), nunha transacción de só
 * lectura. O rexistro de actividade vai antes da primeira fila, co período, os filtros e o número
 * de filas, nunca datos persoais: sen rexistro non hai descarga (como a exportación do concello).
 */
@Service
public class BookingExportService implements ExportBookingsUseCase {

  static final String EXPORT = "booking.export";

  /** Reservas por tanda: as que se len e se escriben de cada vez. */
  static final int BATCH = 500;

  private final BookingRepository bookings;
  private final BookingViews views;
  private final BookingCustomFields customFields;
  private final AuditLog audit;
  private final Clock clock;

  public BookingExportService(
      BookingRepository bookings,
      BookingViews views,
      BookingCustomFields customFields,
      AuditLog audit,
      Clock clock) {
    this.bookings = bookings;
    this.views = views;
    this.customFields = customFields;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.EXPORT)
  public void export(BookingExportQuery query, BookingExportSink sink) {
    TenantRef tenant = TenantContext.require();
    Map<String, Object> details = details(query);
    BookingFilter filter;
    PageResult<Booking> first;
    try {
      check(query.from(), query.to());
      filter =
          new BookingFilter(
              startOf(query.from()),
              startOf(query.to().plusDays(1)),
              query.facilityIds(),
              Set.of(),
              query.statuses(),
              null,
              null,
              false);
      first = bookings.search(filter, 0, BATCH);
    } catch (RuntimeException e) {
      audit.recordFailure(e, EXPORT, tenant.id(), tenant.slug(), details);
      throw e;
    }
    details.put("rows", first.totalItems());
    // Antes de saír a primeira fila: sen rexistro non hai descarga.
    audit.record(new AuditEvent(EXPORT, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
    Instant now = clock.instant();
    sink.begin(first.totalItems(), customFields.staffColumns());
    PageResult<Booking> page = first;
    int index = 0;
    while (!page.items().isEmpty()) {
      sink.write(views.managed(page.items(), now));
      if ((long) (index + 1) * BATCH >= first.totalItems()) {
        break;
      }
      index++;
      page = bookings.search(filter, index, BATCH);
    }
  }

  /**
   * O período: {@code from} non despois de {@code to} e como moito {@value
   * ExportBookingsUseCase#MAX_MONTHS} meses (do 1 de xaneiro ao 31 de decembro vale; ao 1 de
   * xaneiro seguinte, non).
   */
  static void check(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("name", "from"));
    }
    if (!to.isBefore(from.plusMonths(MAX_MONTHS))) {
      throw new DomainException(BookingError.EXPORT_RANGE_TOO_LONG, Map.of("months", MAX_MONTHS));
    }
  }

  private static Map<String, Object> details(BookingExportQuery query) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("from", query.from().toString());
    details.put("to", query.to().toString());
    details.put("facilities", query.facilityIds().size());
    details.put("statuses", query.statuses().stream().map(Enum::name).sorted().toList());
    return details;
  }

  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(BookingRules.ZONE).toInstant();
  }
}
