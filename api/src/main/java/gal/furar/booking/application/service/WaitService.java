// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.WaitUseCase;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.BookingWaits;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.NoShowRule;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.booking.domain.SlotGrid.SlotState;
import gal.furar.booking.domain.Wait;
import gal.furar.booking.domain.WaitLimit;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A lista de espera (ADR-0033, puntos 4 e 5). Esperar toma o bloqueo da instalación: sen el, unha
 * espera creada mentres outra transacción cancela podería quedar sen aviso cunha hora xa libre; con
 * el, ou a cancelación ve a espera e avisa, ou a espera ve a hora libre e dá 409. O límite de
 * esperas por conta garánteo a base (un ordinal por espera): aquí só se proban os ordinais.
 *
 * <p>Cada caso de uso pon antes o correo verificado do token nas esperas da conta que teñan outro:
 * se a persoa cambia o correo da conta, o aviso segue o correo novo desde que volve entrar.
 */
@Service
public class WaitService implements WaitUseCase {

  private static final String ACTIVE = "ACTIVE";

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final BookingWaits waits;
  private final SlotGrids grids;
  private final NoShowPenalty penalty;
  private final CurrentActor currentActor;
  private final StaffAccounts staffAccounts;
  private final Clock clock;

  public WaitService(
      FacilityQueries facilities,
      BookingRepository bookings,
      BookingWaits waits,
      SlotGrids grids,
      NoShowPenalty penalty,
      CurrentActor currentActor,
      ObjectProvider<StaffAccounts> staffAccounts,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.waits = waits;
    this.grids = grids;
    this.penalty = penalty;
    this.currentActor = currentActor;
    // Un só StaffAccounts (ADR-0005): sen ningún, NoStaffAccounts.
    this.staffAccounts = staffAccounts.getIfAvailable(NoStaffAccounts::new);
    this.clock = clock;
  }

  @Override
  @Transactional
  @PersonUseCase
  public Joined join(UUID facilityId, UUID useId, Instant start) {
    Account account = account();
    // O bloqueo da instalación, antes de ler nada (ADR-0022, punto 4).
    if (!facilities.lockFacility(facilityId)) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
    Instant now = clock.instant();
    waits.refreshEmail(account.subject(), account.emailKey());
    if (staffAccounts.isStaffAccount(account.subject())) {
      throw new DomainException(BookingError.STAFF_ACCOUNT);
    }
    refuseIfBlocked(account.emailKey(), now);
    FacilitySummary facility =
        facilities
            .findPublic(facilityId)
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    if (!ACTIVE.equals(facility.status()) || !facility.onlineBooking()) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    UseSummary summary =
        facilities
            .use(useId)
            .filter(u -> u.facilityId().equals(facilityId))
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    BookableUse use = Uses.bookable(summary);
    LocalDate date = start.atZone(SlotGrid.ZONE).toLocalDate();
    SlotGrid grid = grids.of(facilityId, date, now);
    if (!use.availableOn(date)
        || (grid.exclusiveUseId() != null && !grid.exclusiveUseId().equals(use.id()))) {
      throw new DomainException(BookingError.USE_NOT_AVAILABLE);
    }
    SlotState state = grid.stateAt(use, start);
    if (state == SlotState.OFF_GRID) {
      throw new DomainException(BookingError.SLOT_INVALID);
    }
    use.rules().checkSlot(start, 1, now);
    if (state == SlotState.FREE) {
      throw new DomainException(BookingError.WAIT_SLOT_FREE);
    }
    Instant end = start.plus(Duration.ofMinutes(use.slotMinutes()));
    // Non se comproba o límite por persoa do uso (PC7): se ao liberarse segue cheo, reservar dálle
    // o erro de sempre.
    if (!bookings.bookedAmong(use.id(), List.of(account.emailKey()), start, end).isEmpty()) {
      throw new DomainException(BookingError.WAIT_OWN_BOOKING);
    }
    Optional<Wait> existing = waits.findOfAccount(account.subject(), use.id(), start, now);
    if (existing.isPresent()) {
      return new Joined(view(existing.get()), false);
    }
    // As xa pasadas desa conta vencen antes, para que non ocupen sitio.
    waits.expireOfAccount(account.subject(), now);
    Wait wait =
        new Wait(
            Ids.newV7(),
            facilityId,
            use.id(),
            start,
            end,
            account.subject(),
            account.emailKey(),
            account.language(),
            now.truncatedTo(ChronoUnit.MICROS));
    for (int ordinal : WaitLimit.ordinals()) {
      if (waits.insert(wait, ordinal)) {
        return new Joined(view(wait), true);
      }
      // Non entrou: ou o ordinal é doutra espera da conta, ou outra petición á vez xa gardou esta.
      existing = waits.findOfAccount(account.subject(), use.id(), start, now);
      if (existing.isPresent()) {
        return new Joined(view(existing.get()), false);
      }
    }
    throw new DomainException(BookingError.WAIT_LIMIT, Map.of("max", WaitLimit.MAX));
  }

  @Override
  @Transactional
  @PersonUseCase
  public void leave(UUID id) {
    Account account = account();
    waits.refreshEmail(account.subject(), account.emailKey());
    if (!waits.delete(id, account.subject())) {
      throw new DomainException(BookingError.WAIT_NOT_FOUND);
    }
  }

  @Override
  @Transactional
  @PersonUseCase
  public List<MyWait> mine() {
    Account account = account();
    waits.refreshEmail(account.subject(), account.emailKey());
    return views(waits.activeOfAccount(account.subject(), clock.instant()));
  }

  private MyWait view(Wait wait) {
    return views(List.of(wait)).getFirst();
  }

  /** Cos nomes de agora da instalación e do uso e a portada, por lote. */
  private List<MyWait> views(List<Wait> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    List<UUID> facilityIds = list.stream().map(Wait::facilityId).distinct().toList();
    Map<UUID, String> facilityNames =
        facilities.summary(facilityIds).stream()
            .collect(Collectors.toMap(FacilitySummary::id, FacilitySummary::name));
    Map<UUID, FacilityCover> covers =
        facilities.covers(facilityIds).stream()
            .collect(Collectors.toMap(FacilityCover::facilityId, Function.identity()));
    Map<UUID, String> useNames =
        facilities.usesById(list.stream().map(Wait::useId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(UseSummary::id, UseSummary::name));
    return list.stream()
        .map(
            w ->
                new MyWait(
                    w,
                    facilityNames.getOrDefault(w.facilityId(), ""),
                    covers.get(w.facilityId()),
                    useNames.getOrDefault(w.useId(), "")))
        .toList();
  }

  /** Bloqueada por faltas non pode esperar (PC6); as esperas que tiña quedan. */
  private void refuseIfBlocked(String emailKey, Instant now) {
    penalty
        .blocked(emailKey, now)
        .ifPresent(
            block -> {
              throw new DomainException(
                  BookingError.BLOCKED_NO_SHOW,
                  Map.of(
                      "until", NoShowRule.lastBlockedDay(block.endsAt()).toString(),
                      "faults", block.faults(),
                      "windowDays", penalty.rule().windowDays()));
            });
  }

  /**
   * A conta da sesión: o correo verificado xa o comprobou {@link PersonUseCase}; sen idioma
   * coñecido, galego.
   */
  private Account account() {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    String email = actor.profile().email();
    if (email == null) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    String locale = actor.profile().locale() == null ? "" : actor.profile().locale();
    Language language =
        Language.fromCode(locale.toLowerCase(Locale.ROOT).split("[-_]")[0]).orElse(Language.GL);
    return new Account(actor.subject(), EmailKey.of(email), language);
  }

  private record Account(String subject, String emailKey, Language language) {}
}
