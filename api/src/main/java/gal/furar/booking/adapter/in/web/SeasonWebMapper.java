// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.CreateSeriesRequest;
import gal.furar.booking.adapter.in.web.dto.MySeriesRequestResponse;
import gal.furar.booking.adapter.in.web.dto.OrganizationResponse;
import gal.furar.booking.adapter.in.web.dto.OrganizationRowResponse;
import gal.furar.booking.adapter.in.web.dto.OrganizationStatusValue;
import gal.furar.booking.adapter.in.web.dto.SeriesConflictKindValue;
import gal.furar.booking.adapter.in.web.dto.SeriesConflictResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesPaymentStatusValue;
import gal.furar.booking.adapter.in.web.dto.SeriesPreviewResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesRequestCardResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesRequestStatusValue;
import gal.furar.booking.adapter.in.web.dto.SeriesResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesScheduleRequest;
import gal.furar.booking.application.port.in.CreateSeriesCommand;
import gal.furar.booking.application.port.in.OrganizationRow;
import gal.furar.booking.application.port.in.SeriesCommand;
import gal.furar.booking.application.port.in.SeriesRequestView;
import gal.furar.booking.application.port.in.SeriesView;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlan;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.booking.domain.season.SeriesSchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** As entidades, as tempadas e as solicitudes entre o dominio e o contrato (ADR-0039). */
final class SeasonWebMapper {

  private SeasonWebMapper() {}

  static OrganizationResponse organization(Organization o) {
    return new OrganizationResponse(
        o.id(),
        o.name(),
        o.contactName(),
        o.email(),
        o.phone(),
        o.managers().stream().map(Organization.Manager::email).toList(),
        o.isArchived(),
        Objects.requireNonNull(o.version(), "version"));
  }

  static OrganizationRowResponse row(OrganizationRow r) {
    Organization o = r.organization();
    return new OrganizationRowResponse(
        o.id(),
        o.name(),
        o.contactName(),
        o.email(),
        o.phone(),
        r.managers(),
        OrganizationStatusValue.valueOf(r.status().name()),
        o.isArchived());
  }

  static SeriesCommand command(SeriesScheduleRequest r) {
    return new SeriesCommand(
        r.organizationId(),
        SeriesSchedule.of(
            r.facilityId(),
            r.useId(),
            r.partId(),
            r.weekdays(),
            r.startMinute(),
            r.endMinute(),
            r.firstDate(),
            r.lastDate()));
  }

  static CreateSeriesCommand command(CreateSeriesRequest r, IdempotencyKey key) {
    SeriesCommand series =
        new SeriesCommand(
            r.organizationId(),
            SeriesSchedule.of(
                r.facilityId(),
                r.useId(),
                r.partId(),
                r.weekdays(),
                r.startMinute(),
                r.endMinute(),
                r.firstDate(),
                r.lastDate()));
    return new CreateSeriesCommand(
        series, new TreeSet<>(r.skip() == null ? List.<LocalDate>of() : r.skip()), key);
  }

  static SeriesPreviewResponse preview(SeriesPlan plan) {
    return new SeriesPreviewResponse(
        plan.sessions().size(),
        plan.sessions().isEmpty() ? null : plan.sessions().getFirst().start(),
        plan.sessions().isEmpty() ? null : plan.sessions().getLast().start(),
        plan.holidays(),
        plan.useOff(),
        plan.closed(),
        plan.conflicts().stream()
            .map(
                c ->
                    new SeriesConflictResponse(
                        c.date(),
                        c.start(),
                        c.end(),
                        SeriesConflictKindValue.valueOf(c.kind().name()),
                        c.bookingId(),
                        c.name(),
                        c.label()))
            .toList());
  }

  static SeriesResponse series(SeriesView v) {
    Series s = v.series();
    SeriesSchedule h = s.schedule();
    return new SeriesResponse(
        s.id(),
        s.organizationId(),
        v.organizationName(),
        h.facilityId(),
        v.facilityName(),
        h.useId(),
        v.useName(),
        h.partId(),
        v.partName(),
        h.isoWeekdays(),
        h.startMinute(),
        h.endMinute(),
        h.firstDate(),
        h.lastDate(),
        SeriesPaymentStatusValue.valueOf(s.paymentStatus().name()),
        v.sessions(),
        v.cancelled(),
        s.endedFrom(),
        s.requestId(),
        Objects.requireNonNull(s.version(), "version"));
  }

  static SeriesRequestCardResponse card(SeriesRequestView v) {
    SeriesRequest r = v.request();
    SeriesSchedule h = r.schedule();
    return new SeriesRequestCardResponse(
        r.id(),
        r.organizationId(),
        v.organizationName(),
        h.facilityId(),
        v.facilityName(),
        h.useId(),
        v.useName(),
        h.partId(),
        v.partName(),
        h.isoWeekdays(),
        h.startMinute(),
        h.endMinute(),
        h.firstDate(),
        h.lastDate(),
        SeriesRequestStatusValue.valueOf(r.status().name()),
        r.createdAt(),
        r.note(),
        r.requestedByEmail(),
        r.rejectionMessage(),
        r.seriesId());
  }

  static MySeriesRequestResponse mine(SeriesRequestView v) {
    SeriesRequest r = v.request();
    SeriesSchedule h = r.schedule();
    return new MySeriesRequestResponse(
        r.id(),
        h.facilityId(),
        v.facilityName(),
        h.useId(),
        v.useName(),
        h.partId(),
        v.partName(),
        h.isoWeekdays(),
        h.startMinute(),
        h.endMinute(),
        h.firstDate(),
        h.lastDate(),
        r.note(),
        SeriesRequestStatusValue.valueOf(r.status().name()),
        r.rejectionMessage(),
        r.createdAt());
  }
}
