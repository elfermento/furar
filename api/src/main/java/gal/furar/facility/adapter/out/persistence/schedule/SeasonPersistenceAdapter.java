// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.facility.domain.schedule.WeekHours;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA das tempadas, sempre dentro dun concello (A7b). A semana substitúese enteira en
 * cada gardado: Hibernate executa os INSERT antes ca os DELETE nun mesmo flush (§1.2.15 do plan),
 * así que os tramos vellos bórranse e fanse flush antes de inserir os novos; se non, un tramo novo
 * que pisa un vello faría saltar a exclusión.
 */
@Component
class SeasonPersistenceAdapter extends TenantScopedPersistenceAdapter implements SeasonRepository {

  /** Exclusión das tempadas solapadas dunha instalación (o servizo xa o impide). */
  static final String SEASON_OVERLAP_EXCLUSION = "season_overlap_ex";

  /** Exclusión dos tramos solapados dun día (o dominio xa o impide). */
  static final String HOURS_OVERLAP_EXCLUSION = "season_hours_overlap_ex";

  private final SeasonJpaRepository seasons;
  private final SeasonHoursJpaRepository hours;

  SeasonPersistenceAdapter(SeasonJpaRepository seasons, SeasonHoursJpaRepository hours) {
    this.seasons = seasons;
    this.hours = hours;
  }

  @Override
  public List<Season> findByFacility(FacilityId facilityId) {
    tenant();
    return toDomain(seasons.findByFacilityIdOrderByStartDateAsc(facilityId.value()));
  }

  @Override
  public List<Season> findByFacilities(Collection<FacilityId> facilityIds) {
    tenant();
    if (facilityIds.isEmpty()) {
      return List.of();
    }
    return toDomain(
        seasons.findByFacilityIdInOrderByStartDateAsc(
            facilityIds.stream().map(FacilityId::value).toList()));
  }

  @Override
  public Optional<Season> findById(FacilityId facilityId, SeasonId seasonId) {
    tenant();
    return seasons
        .findByIdAndFacilityId(seasonId.value(), facilityId.value())
        .map(e -> toDomain(List.of(e)).getFirst());
  }

  @Override
  public Season insert(Season season) {
    tenant();
    return translated(
        () -> {
          SeasonEntity entity =
              new SeasonEntity(
                  season.id().value(), season.facilityId().value(), season.createdAt());
          apply(entity, season);
          seasons.saveAndFlush(entity);
          insertHours(season);
          hours.flush();
          return reload(season);
        });
  }

  @Override
  public Season update(Season season) {
    tenant();
    SeasonEntity entity = entity(season);
    return translated(
        () -> {
          // Primeiro os DELETE da semana vella, cun flush propio: se non, irían despois dos INSERT.
          hours.deleteAll(hours.findBySeasonIdIn(List.of(season.id().value())));
          hours.flush();
          insertHours(season);
          apply(entity, season);
          seasons.saveAndFlush(entity);
          return reload(season);
        });
  }

  @Override
  public void delete(Season season) {
    tenant();
    SeasonEntity entity = entity(season);
    hours.deleteAll(hours.findBySeasonIdIn(List.of(season.id().value())));
    hours.flush();
    seasons.delete(entity);
    seasons.flush();
  }

  private SeasonEntity entity(Season season) {
    SeasonEntity entity =
        seasons
            .findByIdAndFacilityId(season.id().value(), season.facilityId().value())
            .orElseThrow(() -> new DomainException(ScheduleError.SEASON_NOT_FOUND));
    if (!Objects.equals(entity.version(), season.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return entity;
  }

  private void insertHours(Season season) {
    for (Map.Entry<ScheduleDay, List<TimeRange>> day : season.week().days().entrySet()) {
      for (TimeRange range : day.getValue()) {
        hours.save(
            new SeasonHoursEntity(
                Ids.newV7(),
                season.id().value(),
                day.getKey().number(),
                range.startMinute(),
                range.endMinute()));
      }
    }
  }

  private Season reload(Season season) {
    return findById(season.facilityId(), season.id())
        .orElseThrow(() -> new DomainException(ScheduleError.SEASON_NOT_FOUND));
  }

  private static void apply(SeasonEntity entity, Season season) {
    entity.apply(season.name(), season.startDate(), season.endDate(), season.updatedAt());
  }

  /** Tempadas coa súa semana: dúas consultas para toda a lista. */
  private List<Season> toDomain(List<SeasonEntity> entities) {
    if (entities.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = entities.stream().map(SeasonEntity::getId).toList();
    Map<UUID, Map<ScheduleDay, List<TimeRange>>> bySeason = new HashMap<>();
    for (SeasonHoursEntity h : hours.findBySeasonIdIn(ids)) {
      bySeason
          .computeIfAbsent(h.seasonId(), k -> new EnumMap<>(ScheduleDay.class))
          .computeIfAbsent(ScheduleDay.of(h.day()), k -> new ArrayList<>())
          .add(new TimeRange(h.startMinute(), h.endMinute()));
    }
    return entities.stream()
        .map(
            e ->
                Season.rehydrate(
                    new SeasonId(e.getId()),
                    new FacilityId(e.facilityId()),
                    e.name(),
                    e.startDate(),
                    e.endDate(),
                    WeekHours.rehydrate(bySeason.getOrDefault(e.getId(), Map.of())),
                    e.version() == null ? 0 : e.version(),
                    e.createdAt(),
                    e.updatedAt()))
        .toList();
  }

  /** As restricións da base tradúcense ao código do dominio; o resto sae como está. */
  private static Season translated(Supplier<Season> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException e) {
      String message = String.valueOf(e.getMostSpecificCause().getMessage());
      if (message.contains(SEASON_OVERLAP_EXCLUSION)) {
        throw new DomainException(ScheduleError.SEASON_OVERLAP);
      }
      if (message.contains(HOURS_OVERLAP_EXCLUSION)) {
        throw new DomainException(ScheduleError.INTERVAL_OVERLAP);
      }
      throw e;
    }
  }
}
