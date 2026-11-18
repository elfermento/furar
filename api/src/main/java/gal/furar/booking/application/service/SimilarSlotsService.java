// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.SimilarSlotsUseCase;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.ConfirmedSpan;
import gal.furar.booking.application.port.out.SlotRecommender;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.FreeSlot;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.OpenInterval;
import gal.furar.booking.domain.SlotCandidate;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.booking.domain.SlotGrid.SlotState;
import gal.furar.booking.domain.SlotTarget;
import gal.furar.booking.domain.TimeWindow;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Ocos parecidos» (ADR-0033, punto 9): xunta os candidatos (ocos libres que a persoa pode reservar
 * nesta instalación e nas que teñen o mesmo uso) e o {@link SlotRecommender} escolle. Só le.
 *
 * <p><b>O mesmo uso noutra instalación:</b> un uso vivo co mesmo nome (sen distinguir maiúsculas
 * nin tiles) e a mesma duración de franxa, nunha instalación visible, activa e con reserva por
 * internet.
 *
 * <p><b>Custo:</b> lecturas fixas, sexan cantas sexan as instalacións: os usos por lote, os tramos
 * abertos do día por lote, a ocupación do día nunha consulta, os tramos e a ocupación de sete días
 * despois só desta instalación e, con sesión, as reservas da persoa nunha consulta. Sen caché: só
 * se pide cando a persoa escolle unha hora ocupada.
 */
@Service
public class SimilarSlotsService implements SimilarSlotsUseCase {

  /** Unha fila por regra. */
  static final int MAX = 4;

  private static final int WEEK_DAYS = 7;
  private static final String ACTIVE = "ACTIVE";

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final SlotRecommender recommender;
  private final NoShowPenalty penalty;
  private final CurrentActor currentActor;
  private final StaffAccounts staffAccounts;
  private final Clock clock;

  public SimilarSlotsService(
      FacilityQueries facilities,
      BookingRepository bookings,
      SlotRecommender recommender,
      NoShowPenalty penalty,
      CurrentActor currentActor,
      ObjectProvider<StaffAccounts> staffAccounts,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.recommender = recommender;
    this.penalty = penalty;
    this.currentActor = currentActor;
    // Un só StaffAccounts (ADR-0005): sen ningún, NoStaffAccounts.
    this.staffAccounts = staffAccounts.getIfAvailable(NoStaffAccounts::new);
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public List<Item> forAnyone(UUID facilityId, UUID useId, Instant start, int slots) {
    return similar(facilityId, useId, start, slots, null, clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<Item> forMe(UUID facilityId, UUID useId, Instant start, int slots) {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    String email = actor.profile().email();
    if (email == null) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    Instant now = clock.instant();
    String emailKey = EmailKey.of(email);
    // Quen non pode reservar por internet non recibe ocos que non podería coller (PC6).
    boolean cannotBook =
        staffAccounts.isStaffAccount(actor.subject()) || penalty.blocked(emailKey, now).isPresent();
    List<Item> items = similar(facilityId, useId, start, slots, emailKey, now);
    return cannotBook ? List.of() : items;
  }

  private List<Item> similar(
      UUID facilityId,
      UUID useId,
      Instant start,
      int slots,
      @Nullable String emailKey,
      Instant now) {
    FacilitySummary facility =
        facilities
            .findPublic(facilityId)
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    UseSummary summary =
        facilities
            .use(useId)
            .filter(u -> u.facilityId().equals(facilityId) && !u.archived())
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    BookableUse use = Uses.bookable(summary);
    SlotTarget target = SlotTarget.of(facilityId, useId, start, summary.slotMinutes());
    LocalDate day = target.date();
    LocalDate nextWeek = day.plusDays(WEEK_DAYS);

    // Os usos do concello, nunha lectura: os desta instalación e os «mesmos» das outras.
    Map<UUID, List<UseSummary>> usesByFacility =
        facilities.allUses().stream()
            .filter(u -> !u.archived())
            .collect(Collectors.groupingBy(UseSummary::facilityId));
    String name = key(summary.name());
    List<UseSummary> twins =
        usesByFacility.values().stream()
            .flatMap(List::stream)
            .filter(u -> !u.facilityId().equals(facilityId))
            .filter(u -> u.slotMinutes() == summary.slotMinutes() && key(u.name()).equals(name))
            .toList();
    List<UUID> twinFacilities =
        facilities.visibleIds(twins.stream().map(UseSummary::facilityId).distinct().toList());
    List<UUID> ids = new ArrayList<>(twinFacilities);
    ids.add(facilityId);
    Map<UUID, FacilitySummary> summaries =
        twinFacilities.isEmpty()
            ? Map.of()
            : facilities.summary(twinFacilities).stream()
                .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));

    Map<UUID, List<OpeningInterval>> opening =
        facilities.openingIntervalsByFacility(null, day, day);
    Map<UUID, List<Occupied>> occupancy =
        bookings.activeOccupancy(ids, dayStart(day), dayStart(day.plusDays(1)));
    SlotGrid here =
        grid(
            facilityId,
            day,
            usesByFacility,
            opening.get(facilityId),
            occupancy.get(facilityId),
            now);
    if (here.stateAt(use, start) == SlotState.OFF_GRID) {
      throw new DomainException(BookingError.SLOT_INVALID);
    }
    // Unha hora pasada ou libre non ten ocos parecidos.
    if (!start.isAfter(now) || here.stateAt(use, start) == SlotState.FREE) {
      return List.of();
    }

    List<Candidate> candidates = new ArrayList<>();
    if (bookable(facility)) {
      here.freeSlots(use).forEach(s -> candidates.add(new Candidate(facility, use, s)));
      SlotGrid later =
          grid(
              facilityId,
              nextWeek,
              usesByFacility,
              facilities.openingIntervals(facilityId, nextWeek, nextWeek),
              bookings
                  .activeOccupancy(
                      List.of(facilityId), dayStart(nextWeek), dayStart(nextWeek.plusDays(1)))
                  .get(facilityId),
              now);
      later.freeSlots(use).forEach(s -> candidates.add(new Candidate(facility, use, s)));
    }
    for (UseSummary twin : twins) {
      FacilitySummary other = summaries.get(twin.facilityId());
      if (other == null || !bookable(other)) {
        continue;
      }
      BookableUse twinUse = Uses.bookable(twin);
      grid(other.id(), day, usesByFacility, opening.get(other.id()), occupancy.get(other.id()), now)
          .freeSlots(twinUse)
          .forEach(s -> candidates.add(new Candidate(other, twinUse, s)));
    }
    List<Candidate> allowed =
        candidates.stream().filter(c -> c.slot().maxSlots() >= slots).toList();
    if (emailKey != null) {
      allowed = allowedTo(emailKey, allowed, slots);
    }
    Map<UUID, FacilitySummary> shown = new java.util.HashMap<>(summaries);
    shown.put(facilityId, facility);
    return recommender
        .recommend(target, allowed.stream().map(c -> c.candidate(slots)).toList(), MAX)
        .stream()
        .map(
            s -> {
              FacilitySummary f = shown.get(s.slot().facilityId());
              boolean other = !f.id().equals(facilityId);
              return new Item(s, f.name(), other ? street(f) : null);
            })
        .toList();
  }

  /**
   * As normas da persoa (ADR-0033, punto 9): fóra os ocos nos que o seu correo xa ten reserva dese
   * uso (unha praza por persoa) e os dun período no que xa chegou ao límite por persoa do uso.
   * Todas as súas reservas confirmadas, nunha consulta.
   */
  private List<Candidate> allowedTo(String emailKey, List<Candidate> candidates, int slots) {
    List<TimeWindow> windows =
        candidates.stream()
            .map(c -> c.use().rules().capWindow(c.slot().start()).orElse(c.span(slots)))
            .toList();
    if (windows.isEmpty()) {
      return candidates;
    }
    Instant from = windows.stream().map(TimeWindow::from).min(Instant::compareTo).orElseThrow();
    Instant to = windows.stream().map(TimeWindow::to).max(Instant::compareTo).orElseThrow();
    // Unha reserva que empezou antes do primeiro oco aínda pode pisalo: un día de marxe.
    List<ConfirmedSpan> mine =
        bookings.confirmedSpans(emailKey, from.minus(Duration.ofDays(1)), to);
    return candidates.stream()
        .filter(
            c -> {
              TimeWindow span = c.span(slots);
              boolean seated =
                  mine.stream()
                      .anyMatch(
                          b ->
                              b.useId().equals(c.use().id())
                                  && b.start().isBefore(span.to())
                                  && b.end().isAfter(span.from()));
              if (seated) {
                return false;
              }
              Integer cap = c.use().rules().capCount();
              return cap == null
                  || c.use()
                      .rules()
                      .capWindow(c.slot().start())
                      .map(
                          w ->
                              mine.stream()
                                      .filter(
                                          b ->
                                              b.useId().equals(c.use().id())
                                                  && w.contains(b.start()))
                                      .count()
                                  < cap)
                      .orElse(true);
            })
        .toList();
  }

  private static SlotGrid grid(
      UUID facilityId,
      LocalDate date,
      Map<UUID, List<UseSummary>> usesByFacility,
      @Nullable List<OpeningInterval> opening,
      @Nullable List<Occupied> occupancy,
      Instant now) {
    List<BookableUse> uses =
        usesByFacility.getOrDefault(facilityId, List.of()).stream().map(Uses::bookable).toList();
    return new SlotGrid(
        date,
        (opening == null ? List.<OpeningInterval>of() : opening)
            .stream()
                .map(i -> new OpenInterval(i.date(), i.start(), i.end(), i.alignFromMinute()))
                .toList(),
        occupancy == null ? List.of() : occupancy,
        SlotGrid.exclusiveUse(uses, date).orElse(null),
        now);
  }

  private static boolean bookable(FacilitySummary facility) {
    return ACTIVE.equals(facility.status()) && facility.onlineBooking();
  }

  /** «Rúa do Alto, 12»: onde está a outra instalación, sen código postal nin localidade. */
  private static @Nullable String street(FacilitySummary facility) {
    FacilitySummary.Address address = facility.address();
    if (address == null) {
      return null;
    }
    String number = address.streetNumber();
    return number == null || number.isBlank() ? address.street() : address.street() + ", " + number;
  }

  /** O nome dun uso para comparalo: sen maiúsculas, sen tiles e sen espazos de máis. */
  static String key(String name) {
    return Normalizer.normalize(name.strip(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  private static Instant dayStart(LocalDate date) {
    return date.atStartOfDay(SlotGrid.ZONE).toInstant();
  }

  /** Un oco libre dun uso dunha instalación. */
  private record Candidate(FacilitySummary facility, BookableUse use, FreeSlot slot) {

    /** O que ocuparían as franxas pedidas. */
    TimeWindow span(int slots) {
      return new TimeWindow(
          slot.start(),
          slot.start().plus(Duration.ofMinutes(use.slotMinutes()).multipliedBy(slots)));
    }

    SlotCandidate candidate(int slots) {
      TimeWindow span = span(slots);
      return new SlotCandidate(
          facility.id(),
          facility.name(),
          use.id(),
          slot.start().atZone(SlotGrid.ZONE).toLocalDate(),
          span.from(),
          span.to(),
          slot.startMinute());
    }
  }
}
