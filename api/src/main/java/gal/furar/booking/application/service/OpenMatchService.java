// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.OpenMatchDecided;
import gal.furar.booking.OpenMatchHidden;
import gal.furar.booking.OpenMatchRequested;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.ManageOpenMatchUseCase;
import gal.furar.booking.application.port.in.MyOpenMatch;
import gal.furar.booking.application.port.in.MyOpenMatch.Role;
import gal.furar.booking.application.port.in.OpenMatchSummary;
import gal.furar.booking.application.port.in.OpenMatchUseCase;
import gal.furar.booking.application.port.in.OpenMatchView;
import gal.furar.booking.application.port.in.PublicOpenMatchesUseCase;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.application.port.out.OpenMatches.Listed;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.NoShowRule;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchPlayer;
import gal.furar.booking.domain.OpenMatchPlayerStatus;
import gal.furar.booking.domain.OpenMatchStatus;
import gal.furar.booking.domain.RentalMode;
import gal.furar.municipality.MunicipalityOpenMatchPolicy;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buscar xente para xogar (ADR-0040): quen ten a reserva abre, edita e pecha a partida e decide as
 * solicitudes; quen quere xogar únese, pídeo ou sae; en público vense as abertas; a xestión
 * ocúltaas. Abrir e editar toman a fila da reserva e despois a da partida; unirse, aceptar,
 * rexeitar, saír e ocultar, só a da partida: as prazas cóntanse baixo ese bloqueo. Ningún camiño
 * toma a partida e despois a reserva.
 */
@Service
public class OpenMatchService
    implements OpenMatchUseCase, PublicOpenMatchesUseCase, ManageOpenMatchUseCase {

  /** Rastro de ocultar unha partida. */
  static final String HIDE = "booking.open-match-hide";

  /** Rastro de volver mostrala. */
  static final String SHOW = "booking.open-match-show";

  private final BookingRepository bookings;
  private final OpenMatches matches;
  private final OpenMatchViews views;
  private final NoShowPenalty penalty;
  private final StaffAccounts staffAccounts;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public OpenMatchService(
      BookingRepository bookings,
      OpenMatches matches,
      OpenMatchViews views,
      NoShowPenalty penalty,
      ObjectProvider<StaffAccounts> staffAccounts,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.bookings = bookings;
    this.matches = matches;
    this.views = views;
    this.penalty = penalty;
    // Un só StaffAccounts (ADR-0005): sen ningún, NoStaffAccounts.
    this.staffAccounts = staffAccounts.getIfAvailable(NoStaffAccounts::new);
    this.currentActor = currentActor;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  // --- Quen organiza ---

  @Override
  @Transactional
  @PersonUseCase
  public Saved save(BookingId bookingId, @Nullable Long expectedVersion, OpenMatchCommand command) {
    Actor actor = actor();
    String subject = actor.subject();
    Instant now = now();
    MunicipalityOpenMatchPolicy policy = requireEnabled();
    Booking booking = lockedOwn(bookingId, subject);
    Optional<OpenMatch> existing = matches.findByBookingLocked(bookingId.value());
    checkOpenable(booking, policy, now);
    String name = OpenMatch.publicName(actor.profile().name());
    OpenMatch.Terms terms =
        new OpenMatch.Terms(
            command.knownCount(),
            command.minimum(),
            command.maximum(),
            command.level(),
            command.title(),
            command.description(),
            command.approval());
    if (existing.isPresent()) {
      OpenMatch before = existing.get();
      if (expectedVersion == null) {
        throw new DomainException(CommonError.PRECONDITION_REQUIRED);
      }
      if (before.version() != expectedVersion) {
        throw new DomainException(CommonError.STALE_VERSION);
      }
      int confirmed = listed(before.id()).confirmed();
      matches.update(before, before.edit(terms, name, confirmed, now));
      return new Saved(my(listed(before.id()), subject), false);
    }
    OpenMatch opened = OpenMatch.open(Ids.newV7(), bookingId.value(), subject, name, terms, now);
    matches.insert(opened);
    return new Saved(my(listed(opened.id()), subject), true);
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyOpenMatch changeStatus(BookingId bookingId, OpenMatchStatus status) {
    String subject = actor().subject();
    Instant now = now();
    Booking booking = lockedOwn(bookingId, subject);
    OpenMatch before =
        matches
            .findByBookingLocked(bookingId.value())
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    switch (status) {
      case CLOSED -> {
        if (before.status() == OpenMatchStatus.OPEN) {
          matches.update(before, before.close(now));
          matches.deletePending(before.id());
        } else if (before.status() == OpenMatchStatus.CANCELLED) {
          throw new DomainException(BookingError.MATCH_CLOSED);
        }
      }
      case OPEN -> {
        if (before.status() == OpenMatchStatus.CANCELLED) {
          throw new DomainException(BookingError.MATCH_CLOSED);
        }
        if (before.status() != OpenMatchStatus.OPEN) {
          MunicipalityOpenMatchPolicy policy = requireEnabled();
          checkOpenable(booking, policy, now);
          matches.update(before, before.reopen(listed(before.id()).confirmed(), now));
        }
      }
      case CANCELLED ->
          throw DomainException.validation(
              List.of(FieldViolation.of("status", ValidationError.PATTERN)));
    }
    return my(listed(before.id()), subject);
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyOpenMatch accept(UUID matchId, UUID playerId) {
    return decide(matchId, playerId, true);
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyOpenMatch reject(UUID matchId, UUID playerId) {
    return decide(matchId, playerId, false);
  }

  private MyOpenMatch decide(UUID matchId, UUID playerId, boolean accept) {
    String subject = actor().subject();
    Instant now = now();
    OpenMatch match =
        matches
            .findLocked(matchId)
            .filter(m -> m.organizerSubject().equals(subject))
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    OpenMatchPlayer player =
        matches
            .playerById(matchId, playerId)
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    if (player.status() != OpenMatchPlayerStatus.PENDING) {
      throw new DomainException(BookingError.MATCH_PLAYER_DECIDED);
    }
    Listed listed = listed(matchId);
    if (accept) {
      if (!views.policy().enabled() || !admits(listed, now)) {
        throw new DomainException(BookingError.MATCH_CLOSED);
      }
      if (match.full(listed.confirmed())) {
        throw new DomainException(BookingError.MATCH_FULL);
      }
    }
    if (!matches.decide(
        matchId,
        playerId,
        accept ? OpenMatchPlayerStatus.CONFIRMED : OpenMatchPlayerStatus.REJECTED,
        now)) {
      throw new DomainException(BookingError.MATCH_PLAYER_DECIDED);
    }
    events.publishEvent(
        new OpenMatchDecided(TenantContext.require().id(), matchId, playerId, Ids.newV7(), accept));
    return my(listed(matchId), subject);
  }

  // --- Quen quere xogar ---

  @Override
  @Transactional
  @PersonUseCase
  public Joined join(UUID matchId) {
    Actor actor = actor();
    String subject = actor.subject();
    Instant now = now();
    requireEnabled();
    OpenMatch match =
        matches
            .findLocked(matchId)
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    Listed listed = listed(matchId);
    if (match.hidden()) {
      throw new DomainException(BookingError.MATCH_NOT_FOUND);
    }
    if (!admits(listed, now)) {
      throw new DomainException(BookingError.MATCH_CLOSED);
    }
    if (match.organizerSubject().equals(subject)) {
      throw new DomainException(BookingError.MATCH_OWN);
    }
    if (staffAccounts.isStaffAccount(subject)) {
      throw new DomainException(BookingError.STAFF_ACCOUNT);
    }
    String email = actor.profile().email();
    if (email == null) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    refuseIfBlocked(EmailKey.of(email), now);
    String name = OpenMatch.publicName(actor.profile().name());
    Optional<OpenMatchPlayer> existing = matches.player(matchId, subject);
    if (existing.isPresent()) {
      if (existing.get().status() == OpenMatchPlayerStatus.REJECTED) {
        throw new DomainException(BookingError.MATCH_REJECTED);
      }
      return new Joined(my(listed, subject), false);
    }
    // As prazas cóntanse baixo o bloqueo da fila da partida: a segunda persoa pola última praza
    // agarda e recibe 409.
    if (match.full(listed.confirmed())) {
      throw new DomainException(BookingError.MATCH_FULL);
    }
    OpenMatchPlayer player =
        OpenMatchPlayer.join(
            Ids.newV7(), matchId, subject, email, name, language(actor), match.approval(), now);
    if (!matches.insert(player)) {
      return new Joined(my(listed(matchId), subject), false);
    }
    if (player.status() == OpenMatchPlayerStatus.PENDING) {
      events.publishEvent(
          new OpenMatchRequested(TenantContext.require().id(), matchId, player.id()));
    }
    return new Joined(my(listed(matchId), subject), true);
  }

  @Override
  @Transactional
  @PersonUseCase
  public void leave(UUID matchId) {
    String subject = actor().subject();
    Instant now = now();
    OpenMatch match =
        matches
            .findLocked(matchId)
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    if (match.organizerSubject().equals(subject)) {
      throw new DomainException(BookingError.MATCH_OWN);
    }
    if (matches.player(matchId, subject).isEmpty()) {
      return;
    }
    if (!listed(matchId).start().isAfter(now)) {
      throw new DomainException(BookingError.MATCH_STARTED);
    }
    // Saír despois do prazo non cancela nada: o prazo mírase unha vez.
    matches.leave(matchId, subject);
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public MyOpenMatch get(UUID matchId) {
    String subject = actor().subject();
    Listed listed =
        matches
            .listed(matchId)
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    MyOpenMatch mine = my(listed, subject);
    // Fóra, só mentres se ve en público (e coa función acesa); dentro, sempre.
    if (!mine.role().inside() && (!views.policy().enabled() || !publiclyVisible(listed, now()))) {
      throw new DomainException(BookingError.MATCH_NOT_FOUND);
    }
    return mine;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<MyOpenMatch> mine() {
    String subject = actor().subject();
    Instant now = now();
    boolean enabled = views.policy().enabled();
    return matches.ofAccount(subject, now).stream()
        .map(l -> my(l, subject))
        .filter(
            m ->
                m.role().inside()
                    || enabled && m.role() == Role.PENDING
                    || enabled && publiclyVisible(m, now))
        .toList();
  }

  // --- Público ---

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public PageResult<OpenMatchView> list(@Nullable UUID facilityId, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("max", MAX_PAGE));
    }
    if (!views.policy().enabled()) {
      return new PageResult<>(List.of(), page, size, 0);
    }
    PageResult<Listed> found = matches.visible(now(), facilityId, page, size);
    return new PageResult<>(
        views.views(found.items(), true), found.page(), found.size(), found.totalItems());
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public OpenMatchView find(UUID matchId) {
    return publicView(matchId);
  }

  // --- Xestión ---

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.OPEN_MATCHES)
  public OpenMatchSummary hide(BookingId bookingId, boolean hidden) {
    TenantRef tenant = TenantContext.require();
    String action = hidden ? HIDE : SHOW;
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", bookingId.value().toString());
    try {
      OpenMatch before =
          matches
              .findByBookingLocked(bookingId.value())
              .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
      Listed listed = listed(before.id());
      details.put("matchId", before.id().toString());
      details.put("facilityId", listed.facilityId().toString());
      if (before.hidden() == hidden) {
        return OpenMatchViews.summary(listed);
      }
      matches.update(before, before.hidden(hidden, actor().subject(), now()));
      if (hidden) {
        events.publishEvent(new OpenMatchHidden(tenant.id(), before.id(), Ids.newV7()));
      }
      // Sen título, descrición nin nomes: a partida, a reserva e a instalación (ADR-0040, p. 14).
      audit.record(
          new AuditEvent(action, AuditEvent.Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return OpenMatchViews.summary(listed(before.id()));
    } catch (RuntimeException e) {
      audit.recordFailure(e, action, tenant.id(), tenant.slug(), details);
      throw e;
    }
  }

  // --- Axudas ---

  private OpenMatchView publicView(UUID matchId) {
    if (!views.policy().enabled()) {
      throw new DomainException(BookingError.MATCH_NOT_FOUND);
    }
    Listed listed =
        matches
            .listed(matchId)
            .filter(l -> publiclyVisible(l, now()))
            .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
    return views.view(listed, true);
  }

  /**
   * Se se ve en público (sen mirar o interruptor do concello): aberta, sen ocultar, coa reserva
   * confirmada e sen empezar. Tamén completa.
   */
  private static boolean publiclyVisible(Listed l, Instant now) {
    return !l.match().hidden() && admits(l, now);
  }

  private static boolean publiclyVisible(MyOpenMatch m, Instant now) {
    OpenMatchView v = m.view();
    return !v.match().hidden()
        && v.match().status() == OpenMatchStatus.OPEN
        && v.start().isAfter(now);
  }

  /** Se aínda admite xente: aberta, coa reserva confirmada e sen empezar. */
  private static boolean admits(Listed l, Instant now) {
    return l.match().status() == OpenMatchStatus.OPEN
        && l.bookingStatus() == BookingStatus.CONFIRMED
        && l.start().isAfter(now);
  }

  /**
   * Se a reserva se pode abrir agora (ADR-0040, punto 4): confirmada, sen marca, de pista enteira
   * ou por partes, e aínda cancelable de balde e antes do prazo do concello.
   */
  static void checkOpenable(Booking booking, MunicipalityOpenMatchPolicy policy, Instant now) {
    // A reserva dunha entidade non se abre a outras persoas (ADR-0039, punto 5).
    if (booking.status() != BookingStatus.CONFIRMED
        || booking.mark().marked()
        || booking.organizationId() != null) {
      throw new DomainException(BookingError.MATCH_NOT_ALLOWED);
    }
    if (booking.slot().rentalMode() == RentalMode.SEATS) {
      throw new DomainException(BookingError.MATCH_SEATS_USE);
    }
    OpenMatch.checkInTime(booking.cancelUntil(), booking.slot().start(), policy.cancelHours(), now);
  }

  private MunicipalityOpenMatchPolicy requireEnabled() {
    MunicipalityOpenMatchPolicy policy = views.policy();
    if (!policy.enabled()) {
      throw new DomainException(BookingError.MATCHES_OFF);
    }
    return policy;
  }

  /** A reserva da persoa co bloqueo da súa fila; doutra persoa, 404. */
  private Booking lockedOwn(BookingId id, String subject) {
    bookings
        .findOfHolder(id, subject)
        .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    return bookings
        .findLocked(id)
        .filter(b -> b.holder() != null && subject.equals(b.holder().subject()))
        .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
  }

  private Listed listed(UUID matchId) {
    return matches
        .listed(matchId)
        .orElseThrow(() -> new DomainException(BookingError.MATCH_NOT_FOUND));
  }

  /** A partida para a persoa, co seu papel e os nomes que pode ver. */
  private MyOpenMatch my(Listed l, String subject) {
    OpenMatchView view = views.view(l, true);
    OpenMatch m = l.match();
    if (m.organizerSubject().equals(subject)) {
      List<OpenMatchPlayer> all = matches.players(m.id());
      return new MyOpenMatch(
          view,
          Role.ORGANIZER,
          null,
          people(all),
          all.stream()
              .filter(p -> p.status() == OpenMatchPlayerStatus.PENDING)
              .map(p -> new MyOpenMatch.Request(p.id(), p.name(), p.createdAt()))
              .toList());
    }
    Optional<OpenMatchPlayer> me = matches.player(m.id(), subject);
    Role role =
        me.map(
                p ->
                    switch (p.status()) {
                      case CONFIRMED -> Role.CONFIRMED;
                      case PENDING -> Role.PENDING;
                      case REJECTED -> Role.REJECTED;
                    })
            .orElse(Role.NONE);
    return new MyOpenMatch(
        view,
        role,
        me.map(OpenMatchPlayer::id).orElse(null),
        role == Role.CONFIRMED ? people(matches.players(m.id())) : List.of(),
        List.of());
  }

  private static List<MyOpenMatch.Person> people(List<OpenMatchPlayer> all) {
    return all.stream()
        .filter(p -> p.status() == OpenMatchPlayerStatus.CONFIRMED)
        .map(p -> new MyOpenMatch.Person(p.id(), p.name()))
        .toList();
  }

  /** Bloqueada por faltas non se une (decisión do 2026-11-12). */
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

  private static Language language(Actor actor) {
    String locale = actor.profile().locale() == null ? "" : actor.profile().locale();
    return Language.fromCode(locale.toLowerCase(Locale.ROOT).split("[-_]")[0]).orElse(Language.GL);
  }

  private Actor actor() {
    return currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
