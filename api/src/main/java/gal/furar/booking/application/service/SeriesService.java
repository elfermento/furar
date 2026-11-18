// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.BookingSeriesCreated;
import gal.furar.booking.BookingSeriesEnded;
import gal.furar.booking.application.port.in.CreateSeriesCommand;
import gal.furar.booking.application.port.in.ManageSeriesUseCase;
import gal.furar.booking.application.port.in.SeriesCommand;
import gal.furar.booking.application.port.in.SeriesCreated;
import gal.furar.booking.application.port.in.SeriesView;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlan;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As tempadas das entidades (ADR-0039, puntos 8 e 11). Crear e rematar toman primeiro o bloqueo da
 * instalación, despois a entidade ({@code for share}) e por último a serie: a mesma orde en todos
 * os camiños (tamén ao aprobar unha solicitude e ao eliminar unha entidade), sen ciclos. As sesións
 * escríbense e cancélanse en lote.
 */
@Service
public class SeriesService implements ManageSeriesUseCase {

  static final String CREATE = "booking.series-create";
  static final String END = "booking.series-end";
  static final String PAYMENT = "booking.series-payment";

  private final FacilityQueries facilities;
  private final Organizations organizations;
  private final SeriesStore store;
  private final SeriesWriter writer;
  private final SeasonSupport support;
  private final WaitNotifier waitNotifier;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public SeriesService(
      FacilityQueries facilities,
      Organizations organizations,
      SeriesStore store,
      SeriesWriter writer,
      SeasonSupport support,
      WaitNotifier waitNotifier,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.organizations = organizations;
    this.store = store;
    this.writer = writer;
    this.support = support;
    this.waitNotifier = waitNotifier;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesPlan preview(SeriesCommand command) {
    Instant now = clock.instant();
    SeriesSchedule schedule = command.schedule();
    schedule.check(today(now));
    organizations
        .find(command.organizationId())
        .filter(o -> !o.isArchived())
        .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
    return writer.plan(schedule, support.use(schedule), now);
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesCreated create(CreateSeriesCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("organizationId", command.organizationId().toString());
    details.put("facilityId", command.schedule().facilityId().toString());
    return support.audited(CREATE, details, () -> created(command, null, details));
  }

  /**
   * Crear (tamén ao aprobar): bloqueo da instalación, a entidade {@code for share}, a clave, a
   * forma, o uso e despois, con {@code beforeWrite}, o que teña que bloquear quen chama.
   */
  SeriesCreated created(
      CreateSeriesCommand command, @Nullable Approval approval, Map<String, Object> details) {
    SeriesSchedule schedule = command.schedule();
    String actor = support.subject();
    // 1. Instalación → entidade → solicitude ou serie (ADR-0039, punto 8).
    if (!facilities.lockFacility(schedule.facilityId())) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    Organization organization =
        organizations
            .findForShare(command.organizationId())
            .filter(o -> !o.isArchived())
            .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
    if (approval != null) {
      approval.lock();
    }
    Instant now = clock.instant();
    // 2. A mesma clave e a mesma pegada: a serie xa creada.
    Optional<Series> remembered = writer.remembered(command);
    if (remembered.isPresent()) {
      details.put("seriesId", remembered.get().id().toString());
      details.put("repeated", true);
      return new SeriesCreated(support.views(List.of(remembered.get())).getFirst(), false);
    }
    schedule.check(today(now));
    UseSummary use = support.use(schedule);
    // 3. Volve calcular baixo o bloqueo e escribe.
    SeriesWriter.Written written =
        writer.create(
            organization, command, use, approval == null ? null : approval.requestId(), actor, now);
    Series series = written.series();
    if (approval != null) {
      approval.approved(series.id(), now);
    }
    details.put("seriesId", series.id().toString());
    details.put("sessions", written.plan().sessions().size());
    details.put("skipped", written.plan().skipped().size());
    if (approval != null) {
      details.put("requestId", approval.requestId().toString());
    }
    events.publishEvent(new BookingSeriesCreated(TenantContext.require().id(), series.id()));
    return new SeriesCreated(
        support.views(List.of(store.find(series.id()).orElseThrow())).getFirst(), true);
  }

  /** O que fai a aprobación dentro de {@link #created}: bloquear a solicitude e marcala. */
  interface Approval {

    UUID requestId();

    /** Bloquea a solicitude, despois da instalación e da entidade. */
    void lock();

    void approved(UUID seriesId, Instant now);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public List<SeriesView> list(boolean ended) {
    return support.views(store.list(ended, today(clock.instant())));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesView get(UUID id) {
    return support.views(List.of(found(id))).getFirst();
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesView end(UUID id, LocalDate from) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("seriesId", id.toString());
    details.put("from", from.toString());
    return support.audited(
        END,
        details,
        () -> {
          Series read = found(id);
          // Instalación → serie.
          if (!facilities.lockFacility(read.schedule().facilityId())) {
            throw new DomainException(BookingError.SERIES_NOT_FOUND);
          }
          Series before =
              store
                  .findForUpdate(id)
                  .orElseThrow(() -> new DomainException(BookingError.SERIES_NOT_FOUND));
          Instant now = clock.instant();
          Series after = before.endFrom(from, today(now), now);
          String actor = support.subject();
          // As que aínda non empezaron, desde ese día.
          Instant since = startOf(from).minusSeconds(1);
          List<Booking> sessions = store.confirmedSessions(id, since.isAfter(now) ? since : now);
          List<Booking> cancelled =
              sessions.stream().map(b -> b.cancelByMunicipality(now, actor)).toList();
          store.cancelSessions(sessions, cancelled, actor);
          store.update(before, after);
          if (!sessions.isEmpty()) {
            waitNotifier.released(
                before.schedule().facilityId(),
                sessions.stream().map(WaitNotifier::occupied).toList(),
                List.of());
          }
          details.put("cancelled", cancelled.size());
          events.publishEvent(
              new BookingSeriesEnded(
                  TenantContext.require().id(),
                  id,
                  after.endedFrom(),
                  cancelled.size(),
                  UUID.randomUUID()));
          return support.views(List.of(store.find(id).orElseThrow())).getFirst();
        });
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesView payment(UUID id, long expectedVersion, Series.PaymentStatus status) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("seriesId", id.toString());
    return support.audited(
        PAYMENT,
        details,
        () -> {
          Series before =
              store
                  .findForUpdate(id)
                  .orElseThrow(() -> new DomainException(BookingError.SERIES_NOT_FOUND));
          if (before.version() == null || before.version() != expectedVersion) {
            throw new DomainException(CommonError.STALE_VERSION);
          }
          details.put("from", before.paymentStatus().name());
          details.put("to", status.name());
          if (before.paymentStatus() != status) {
            store.update(before, before.withPayment(status, clock.instant()));
          }
          return support.views(List.of(store.find(id).orElseThrow())).getFirst();
        });
  }

  private Series found(UUID id) {
    return store.find(id).orElseThrow(() -> new DomainException(BookingError.SERIES_NOT_FOUND));
  }

  private static LocalDate today(Instant now) {
    return now.atZone(SeriesSchedule.ZONE).toLocalDate();
  }

  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(SeriesSchedule.ZONE).toInstant();
  }
}
