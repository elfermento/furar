// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.BookingSeriesRequestRejected;
import gal.furar.booking.application.port.in.CreateSeriesCommand;
import gal.furar.booking.application.port.in.ManageSeriesRequestsUseCase;
import gal.furar.booking.application.port.in.SeriesCreated;
import gal.furar.booking.application.port.in.SeriesRequestView;
import gal.furar.booking.application.port.out.SeriesRequests;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As solicitudes de tempada (ADR-0039, punto 11). Aprobar crea a tempada co camiño de {@link
 * SeriesService}: a solicitude bloquéase despois da instalación e da entidade, nunca antes; dúas
 * aprobacións á vez dan unha serie e un 409.
 */
@Service
public class SeriesRequestService implements ManageSeriesRequestsUseCase {

  static final String REJECT = "booking.series-request-reject";

  private final SeriesRequests requests;
  private final SeriesService series;
  private final SeasonSupport support;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public SeriesRequestService(
      SeriesRequests requests,
      SeriesService series,
      SeasonSupport support,
      ApplicationEventPublisher events,
      Clock clock) {
    this.requests = requests;
    this.series = series;
    this.support = support;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public List<SeriesRequestView> list(SeriesRequest.Status status) {
    return support.requestViews(requests.list(status));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public int pendingCount() {
    return requests.countPending();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesRequestView get(UUID id) {
    return support.requestViews(List.of(found(id))).getFirst();
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesCreated approve(UUID id, CreateSeriesCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("requestId", id.toString());
    details.put("organizationId", command.organizationId().toString());
    details.put("facilityId", command.schedule().facilityId().toString());
    SeriesRequest read = found(id);
    if (!read.organizationId().equals(command.organizationId())) {
      throw new DomainException(BookingError.SERIES_REQUEST_NOT_FOUND);
    }
    String actor = support.subject();
    SeriesService.Approval approval =
        new SeriesService.Approval() {
          private SeriesRequest locked;

          @Override
          public UUID requestId() {
            return id;
          }

          @Override
          public void lock() {
            locked =
                requests
                    .findForUpdate(id)
                    .orElseThrow(() -> new DomainException(BookingError.SERIES_REQUEST_NOT_FOUND));
            locked.requirePending();
          }

          @Override
          public void approved(UUID seriesId, Instant now) {
            requests.update(locked, locked.approve(seriesId, actor, now));
          }
        };
    return support.audited(
        SeriesService.CREATE, details, () -> series.created(command, approval, details));
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ORGANIZATIONS)
  public SeriesRequestView reject(UUID id, @Nullable String message) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("requestId", id.toString());
    return support.audited(
        REJECT,
        details,
        () -> {
          SeriesRequest before =
              requests
                  .findForUpdate(id)
                  .orElseThrow(() -> new DomainException(BookingError.SERIES_REQUEST_NOT_FOUND));
          details.put("organizationId", before.organizationId().toString());
          SeriesRequest after = before.reject(message, support.subject(), clock.instant());
          requests.update(before, after);
          events.publishEvent(new BookingSeriesRequestRejected(TenantContext.require().id(), id));
          return support.requestViews(List.of(requests.find(id).orElseThrow())).getFirst();
        });
  }

  private SeriesRequest found(UUID id) {
    return requests
        .find(id)
        .orElseThrow(() -> new DomainException(BookingError.SERIES_REQUEST_NOT_FOUND));
  }
}
