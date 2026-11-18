// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.SeriesRequestView;
import gal.furar.booking.application.port.in.SeriesView;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * O común das entidades e as tempadas (ADR-0039): o uso que vale para unha entidade, os nomes de
 * agora, o idioma dos seus correos e o rexistro de actividade.
 */
@Component
class SeasonSupport {

  private final FacilityQueries facilities;
  private final MunicipalityQueries municipalities;
  private final Organizations organizations;
  private final SeriesStore series;
  private final CurrentActor currentActor;
  private final AuditLog audit;

  SeasonSupport(
      FacilityQueries facilities,
      MunicipalityQueries municipalities,
      Organizations organizations,
      SeriesStore series,
      CurrentActor currentActor,
      AuditLog audit) {
    this.facilities = facilities;
    this.municipalities = municipalities;
    this.organizations = organizations;
    this.series = series;
    this.currentActor = currentActor;
    this.audit = audit;
  }

  String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }

  /**
   * O uso dunha tempada ou dunha solicitude: a instalación que ve a veciñanza, o uso vivo desa
   * instalación, enteiro ou por partes (nunca por prazas) e, por partes, unha parte viva.
   *
   * @throws DomainException {@code booking.facility-not-bookable}, {@code
   *     booking.use-not-available}, {@code booking.organization-seats-use} ou {@code
   *     booking.slot-invalid} (a parte)
   */
  UseSummary use(SeriesSchedule schedule) {
    return use(schedule, BookingError.ORGANIZATION_SEATS_USE);
  }

  /**
   * Coma {@link #use(SeriesSchedule)}, co erro dun uso por prazas de quen chama (unha entidade ou
   * unha actividade do concello, ADR-0041).
   */
  UseSummary use(SeriesSchedule schedule, BookingError seatsUse) {
    FacilitySummary facility =
        facilities
            .findPublic(schedule.facilityId())
            .orElseThrow(() -> new DomainException(BookingError.FACILITY_NOT_BOOKABLE));
    if ("WITHDRAWN".equals(facility.status()) || "DRAFT".equals(facility.status())) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    UseSummary use =
        facilities
            .use(schedule.useId())
            .filter(u -> u.facilityId().equals(schedule.facilityId()))
            .orElseThrow(() -> new DomainException(BookingError.USE_NOT_AVAILABLE));
    if (use.archived()) {
      throw new DomainException(BookingError.USE_NOT_AVAILABLE);
    }
    RentalMode mode = RentalMode.valueOf(use.rentalMode());
    if (mode == RentalMode.SEATS) {
      throw new DomainException(seatsUse);
    }
    boolean partOk =
        mode == RentalMode.PARTS
            ? schedule.partId() != null
                && use.parts().stream().anyMatch(p -> p.id().equals(schedule.partId()))
            : schedule.partId() == null;
    if (!partOk) {
      throw new DomainException(BookingError.SLOT_INVALID);
    }
    return use;
  }

  BookableUse bookable(UseSummary use) {
    return Uses.bookable(use);
  }

  /** O primeiro idioma do concello: o dos correos da entidade. */
  Language language() {
    return municipalities
        .mailContext(TenantContext.require().id())
        .map(MunicipalityMailContext::defaultLanguage)
        .flatMap(Language::fromCode)
        .orElse(Language.GL);
  }

  /** Cos nomes de agora e o reconto das sesións. */
  List<SeriesView> views(List<Series> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> orgs =
        organizations.names(list.stream().map(Series::organizationId).distinct().toList());
    Names names = names(list.stream().map(Series::schedule).toList());
    Map<UUID, SeriesStore.Counts> counts = series.counts(list.stream().map(Series::id).toList());
    return list.stream()
        .map(
            s -> {
              SeriesStore.Counts c = counts.getOrDefault(s.id(), new SeriesStore.Counts(0, 0));
              return new SeriesView(
                  s,
                  orgs.getOrDefault(s.organizationId(), ""),
                  names.facility(s.schedule()),
                  names.use(s.schedule()),
                  names.part(s.schedule()),
                  c.confirmed(),
                  c.cancelled());
            })
        .toList();
  }

  List<SeriesRequestView> requestViews(List<SeriesRequest> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> orgs =
        organizations.names(list.stream().map(SeriesRequest::organizationId).distinct().toList());
    Names names = names(list.stream().map(SeriesRequest::schedule).toList());
    return list.stream()
        .map(
            r ->
                new SeriesRequestView(
                    r,
                    orgs.getOrDefault(r.organizationId(), ""),
                    names.facility(r.schedule()),
                    names.use(r.schedule()),
                    names.part(r.schedule())))
        .toList();
  }

  Map<UUID, String> useNames(Collection<UUID> ids) {
    return facilities.usesById(ids).stream()
        .collect(Collectors.toMap(UseSummary::id, UseSummary::name));
  }

  private Names names(List<SeriesSchedule> schedules) {
    Map<UUID, FacilitySummary> byFacility =
        facilities
            .summary(schedules.stream().map(SeriesSchedule::facilityId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    Map<UUID, UseSummary> byUse =
        facilities
            .usesById(schedules.stream().map(SeriesSchedule::useId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(UseSummary::id, Function.identity()));
    return new Names(byFacility, byUse);
  }

  private record Names(Map<UUID, FacilitySummary> facilities, Map<UUID, UseSummary> uses) {

    String facility(SeriesSchedule s) {
      FacilitySummary f = facilities.get(s.facilityId());
      return f == null ? "" : f.name();
    }

    String use(SeriesSchedule s) {
      UseSummary u = uses.get(s.useId());
      return u == null ? "" : u.name();
    }

    @Nullable String part(SeriesSchedule s) {
      UseSummary u = uses.get(s.useId());
      if (u == null || s.partId() == null) {
        return null;
      }
      return u.parts().stream()
          .filter(p -> p.id().equals(s.partId()))
          .map(UseSummary.Part::name)
          .findFirst()
          .orElse(null);
    }
  }

  /** O traballo, rexistrado con éxito ou co código do fallo. */
  <T> T audited(String action, Map<String, Object> details, Supplier<T> work) {
    TenantRef tenant = TenantContext.require();
    try {
      T result = work.get();
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return result;
    } catch (RuntimeException e) {
      Map<String, Object> failure = new LinkedHashMap<>(details);
      failure.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(action, Outcome.FAILURE, tenant.id(), tenant.slug(), failure));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }

  static String text(@Nullable Object value) {
    return Objects.toString(value, "");
  }
}
