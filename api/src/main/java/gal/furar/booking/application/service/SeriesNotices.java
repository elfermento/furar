// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.SeriesNotice;
import gal.furar.booking.SeriesRequestNotice;
import gal.furar.booking.application.port.in.SeriesRequestView;
import gal.furar.booking.application.port.in.SeriesView;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.SeriesRequests;
import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * As tempadas e as solicitudes tal como as contan os correos á entidade (ADR-0039, punto 12): o
 * correo e o idioma da entidade (o primeiro do concello), os nomes de agora e as datas.
 */
@Component
class SeriesNotices {

  private final SeriesStore store;
  private final SeriesRequests requests;
  private final Organizations organizations;
  private final FacilityQueries facilities;
  private final SeasonSupport support;

  SeriesNotices(
      SeriesStore store,
      SeriesRequests requests,
      Organizations organizations,
      FacilityQueries facilities,
      SeasonSupport support) {
    this.store = store;
    this.requests = requests;
    this.organizations = organizations;
    this.facilities = facilities;
    this.support = support;
  }

  Optional<SeriesNotice> series(UUID seriesId) {
    Optional<Series> found = store.find(seriesId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    Series s = found.get();
    Optional<Organization> organization =
        organizations.find(s.organizationId()).filter(o -> o.email() != null);
    if (organization.isEmpty()) {
      return Optional.empty();
    }
    SeriesView view = support.views(List.of(s)).getFirst();
    // Non se crearon: as datas da tempada desde o día en que se creou que non teñen sesión.
    LocalDate created = s.createdAt().atZone(SeriesSchedule.ZONE).toLocalDate();
    Set<LocalDate> withSession = new HashSet<>(store.sessionDates(seriesId));
    List<LocalDate> skipped =
        s.schedule().dates().stream()
            .filter(d -> !d.isBefore(created) && !withSession.contains(d))
            .toList();
    String address =
        facilities
            .find(s.schedule().facilityId())
            .map(FacilitySummary::address)
            .map(FacilitySummary.Address::oneLine)
            .orElse(null);
    return Optional.of(
        new SeriesNotice(
            s.id(),
            organization.get().name(),
            Objects.requireNonNull(organization.get().email()),
            support.language().code(),
            view.facilityName(),
            address,
            view.useName(),
            view.partName(),
            s.schedule().isoWeekdays(),
            s.schedule().startMinute(),
            s.schedule().endMinute(),
            s.schedule().firstDate(),
            s.schedule().lastDate(),
            view.sessions(),
            skipped,
            s.requestId() != null,
            s.endedFrom(),
            view.cancelled()));
  }

  Optional<SeriesRequestNotice> request(UUID requestId) {
    Optional<SeriesRequest> found =
        requests.find(requestId).filter(r -> r.status() == SeriesRequest.Status.REJECTED);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    SeriesRequest r = found.get();
    Optional<Organization> organization =
        organizations.find(r.organizationId()).filter(o -> o.email() != null);
    if (organization.isEmpty()) {
      return Optional.empty();
    }
    SeriesRequestView view = support.requestViews(List.of(r)).getFirst();
    return Optional.of(
        new SeriesRequestNotice(
            r.id(),
            organization.get().name(),
            Objects.requireNonNull(organization.get().email()),
            support.language().code(),
            view.facilityName(),
            view.useName(),
            view.partName(),
            r.schedule().isoWeekdays(),
            r.schedule().startMinute(),
            r.schedule().endMinute(),
            r.schedule().firstDate(),
            r.schedule().lastDate(),
            Objects.requireNonNull(r.rejectionMessage())));
  }
}
