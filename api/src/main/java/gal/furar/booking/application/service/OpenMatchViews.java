// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.OpenMatchSummary;
import gal.furar.booking.application.port.in.OpenMatchView;
import gal.furar.booking.application.port.out.OpenMatches.Listed;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.municipality.MunicipalityOpenMatchPolicy;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * As partidas abertas cos nomes de agora da instalación, o uso e a parte e o prazo do concello:
 * unha lectura para as instalacións, outra para os usos e outra para as portadas de toda a lista,
 * nunca unha por partida.
 */
@Component
class OpenMatchViews {

  /** Sen fila do concello (non pasa), a política da alta. */
  static final MunicipalityOpenMatchPolicy OFF = new MunicipalityOpenMatchPolicy(null, 12, null);

  private final FacilityQueries facilities;
  private final MunicipalityQueries municipalities;

  OpenMatchViews(FacilityQueries facilities, MunicipalityQueries municipalities) {
    this.facilities = facilities;
    this.municipalities = municipalities;
  }

  /** A política do concello actual. */
  MunicipalityOpenMatchPolicy policy() {
    return municipalities.openMatchPolicy(TenantContext.require().id()).orElse(OFF);
  }

  OpenMatchView view(Listed listed, boolean withCover) {
    return views(List.of(listed), withCover).getFirst();
  }

  List<OpenMatchView> views(List<Listed> list, boolean withCover) {
    if (list.isEmpty()) {
      return List.of();
    }
    int hours = policy().cancelHours();
    List<UUID> facilityIds = list.stream().map(Listed::facilityId).distinct().toList();
    Map<UUID, FacilitySummary> byFacility =
        facilities.summary(facilityIds).stream()
            .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    Map<UUID, UseSummary> byUse =
        facilities.usesById(list.stream().map(Listed::useId).distinct().toList()).stream()
            .collect(Collectors.toMap(UseSummary::id, Function.identity()));
    Map<UUID, FacilityCover> covers =
        withCover
            ? facilities.covers(facilityIds).stream()
                .collect(Collectors.toMap(FacilityCover::facilityId, Function.identity()))
            : Map.of();
    return list.stream()
        .map(
            l -> {
              FacilitySummary f = byFacility.get(l.facilityId());
              UseSummary u = byUse.get(l.useId());
              FacilitySummary.Address address = f == null ? null : f.address();
              String part =
                  u == null || l.partId() == null
                      ? null
                      : u.parts().stream()
                          .filter(p -> p.id().equals(l.partId()))
                          .map(UseSummary.Part::name)
                          .findFirst()
                          .orElse(null);
              return new OpenMatchView(
                  l.match(),
                  l.facilityId(),
                  f == null ? "" : f.name(),
                  address == null ? null : address.oneLine(),
                  covers.get(l.facilityId()),
                  l.useId(),
                  u == null ? "" : u.name(),
                  part,
                  l.start(),
                  l.end(),
                  OpenMatch.deadline(l.start(), hours),
                  l.confirmed(),
                  l.pending());
            })
        .toList();
  }

  static OpenMatchSummary summary(Listed l) {
    OpenMatch m = l.match();
    return new OpenMatchSummary(
        m.id(),
        m.status(),
        m.hidden(),
        m.total(l.confirmed()),
        m.minimum(),
        m.maximum(),
        l.confirmed(),
        l.pending());
  }
}
