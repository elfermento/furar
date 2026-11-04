// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.in.GetPublicFacilityUseCase;
import gal.furar.facility.application.port.in.PublicFacilityDetail;
import gal.furar.facility.application.port.in.PublicFacilityDetail.OpeningDay;
import gal.furar.facility.application.port.in.PublicFacilityDetail.OpeningSlot;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityPaymentNotes;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.schedule.Closure;
import gal.furar.facility.domain.schedule.OpeningHours;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ficha pública da instalación (portada da veciñanza → ficha). Só se a instalación se ve na
 * portada: nin de baixa nin en borrador (ADR-0022, punto 3). Os usos que xa non se poden reservar
 * (todas as súas datas pasaron) non saen. O horario é o de hoxe e os seis días seguintes no fuso do
 * concello; os peches, os de hoxe e os sesenta días seguintes.
 */
@Service
public class PublicFacilityDetailService implements GetPublicFacilityUseCase {

  /** Días do horario: hoxe e os seis seguintes. */
  static final int WEEK = 7;

  /** Días dos próximos peches despois de hoxe (hoxe tamén conta). */
  static final int CLOSURE_DAYS = 60;

  private final FacilityRepository repository;
  private final FacilityDrafts drafts;
  private final FacilityGalleries galleries;
  private final FacilityUseRepository uses;
  private final SeasonRepository seasons;
  private final ScheduleExceptionRepository exceptions;
  private final PublicHolidayRepository holidays;
  private final BlockRepository blocks;
  private final Clock clock;

  /** Os {@link RequirementProbe} poden faltar: entón o requisito falta sempre e nada se ve. */
  @Autowired
  public PublicFacilityDetailService(
      FacilityRepository repository,
      ObjectProvider<RequirementProbe> probes,
      FacilityGalleries galleries,
      FacilityUseRepository uses,
      SeasonRepository seasons,
      ScheduleExceptionRepository exceptions,
      PublicHolidayRepository holidays,
      BlockRepository blocks,
      Clock clock) {
    this(
        repository,
        probes.orderedStream().toList(),
        galleries,
        uses,
        seasons,
        exceptions,
        holidays,
        blocks,
        clock);
  }

  public PublicFacilityDetailService(
      FacilityRepository repository,
      List<RequirementProbe> probes,
      FacilityGalleries galleries,
      FacilityUseRepository uses,
      SeasonRepository seasons,
      ScheduleExceptionRepository exceptions,
      PublicHolidayRepository holidays,
      BlockRepository blocks,
      Clock clock) {
    this.repository = repository;
    this.drafts = new FacilityDrafts(probes);
    this.galleries = galleries;
    this.uses = uses;
    this.seasons = seasons;
    this.exceptions = exceptions;
    this.holidays = holidays;
    this.blocks = blocks;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public PublicFacilityDetail getPublic(FacilityId id) {
    Facility facility =
        repository
            .findById(id)
            .filter(f -> f.status() != FacilityStatus.WITHDRAWN)
            .filter(Facility::published)
            .filter(f -> drafts.missing(f.id().value()).isEmpty())
            .orElseThrow(() -> new DomainException(FacilityError.NOT_FOUND));
    LocalDate today = LocalDate.now(clock.withZone(OpeningHours.ZONE));
    List<FacilityUse> bookable =
        uses.findActive(id).stream().filter(use -> stillBookable(use, today)).toList();
    return new PublicFacilityDetail(
        facility, galleries.of(facility).shown(), bookable, week(id, today), closures(id, today));
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public FacilityPaymentNotes paymentNotes(FacilityId id) {
    return repository
        .findById(id)
        .map(Facility::paymentNotes)
        .orElseThrow(() -> new DomainException(FacilityError.NOT_FOUND));
  }

  /** «Sempre» ou con algunha data de hoxe en diante. */
  static boolean stillBookable(FacilityUse use, LocalDate today) {
    return use.availability() == UseAvailability.ALWAYS
        || use.periods().stream().anyMatch(p -> !p.to().isBefore(today));
  }

  /** Os peches de hoxe aos sesenta días seguintes (só datas, horas e motivo). */
  private List<Closure> closures(FacilityId id, LocalDate today) {
    LocalDate last = today.plusDays(CLOSURE_DAYS);
    List<PublicHoliday> holidayList = holidays.findBetween(today, last);
    List<ScheduleException> exceptionList = exceptions.findBetween(id, today, last);
    Set<LocalDate> holidayDates =
        holidayList.stream().map(PublicHoliday::date).collect(Collectors.toUnmodifiableSet());
    OpeningHours hours = new OpeningHours(seasons.findByFacility(id), exceptionList, holidayDates);
    return Closure.between(
        hours, holidayList, exceptionList, blocks.findBetween(id, today, last), today, last);
  }

  private List<OpeningDay> week(FacilityId id, LocalDate today) {
    LocalDate last = today.plusDays(WEEK - 1L);
    Set<LocalDate> holidayDates =
        holidays.findBetween(today, last).stream()
            .map(PublicHoliday::date)
            .collect(Collectors.toUnmodifiableSet());
    OpeningHours hours =
        new OpeningHours(
            seasons.findByFacility(id), exceptions.findBetween(id, today, last), holidayDates);
    Map<LocalDate, List<OpeningSlot>> byDay = new LinkedHashMap<>();
    for (LocalDate day = today; !day.isAfter(last); day = day.plusDays(1)) {
      byDay.put(day, new ArrayList<>());
    }
    for (OpeningHours.Slot slot : hours.slots(today, last, OpeningHours.ZONE)) {
      byDay
          .get(slot.date())
          .add(
              new OpeningSlot(
                  slot.range().startMinute(), slot.range().endMinute(), slot.start(), slot.end()));
    }
    return byDay.entrySet().stream().map(e -> new OpeningDay(e.getKey(), e.getValue())).toList();
  }
}
