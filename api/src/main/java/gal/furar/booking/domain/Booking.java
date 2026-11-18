// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reserva (agregado; ADR-0025). Inmutable: as invariantes van no construtor e cada transición é un
 * método con nome de negocio que devolve unha copia (ADR-0005, A13). Leva o oco reservado, o prezo
 * total e as normas aplicadas conxelados, quen reserva e dous estados separados: o da reserva e o
 * do cobro. As comprobacións que precisan a base (horario, ocupación, límite por persoa) fainas o
 * caso de uso baixo o bloqueo da instalación; aquí, o que se sabe sen ela.
 */
public final class Booking {

  private final BookingId id;
  private final BookingCode code;
  private final BookedSlot slot;
  private final int seats;
  private final BookingStatus status;
  private final PaymentStatus paymentStatus;
  private final Price price;
  private final BookingRules rules;
  private final Instant cancelUntil;
  private final @Nullable Holder holder;
  private final @Nullable String bookedBy;
  private final @Nullable Instant cancelledAt;
  private final @Nullable String cancelledBy;
  private final AttendanceMark mark;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final @Nullable UUID organizationId;
  private final @Nullable UUID seriesId;
  private final @Nullable UUID activityId;

  private Booking(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      int seats,
      BookingStatus status,
      PaymentStatus paymentStatus,
      Price price,
      BookingRules rules,
      Instant cancelUntil,
      @Nullable Holder holder,
      @Nullable String bookedBy,
      @Nullable Instant cancelledAt,
      @Nullable String cancelledBy,
      AttendanceMark mark,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt,
      @Nullable UUID organizationId,
      @Nullable UUID seriesId,
      @Nullable UUID activityId) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.slot = Objects.requireNonNull(slot, "slot");
    this.seats = seats;
    this.status = Objects.requireNonNull(status, "status");
    this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus");
    this.price = Objects.requireNonNull(price, "price");
    this.rules = Objects.requireNonNull(rules, "rules");
    this.cancelUntil = Objects.requireNonNull(cancelUntil, "cancelUntil");
    this.holder = holder;
    this.bookedBy = bookedBy;
    this.cancelledAt = cancelledAt;
    this.cancelledBy = cancelledBy;
    this.mark = Objects.requireNonNull(mark, "mark");
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.organizationId = organizationId;
    this.seriesId = seriesId;
    this.activityId = activityId;
    // As mesmas garantías ca a táboa booking: un fallo aquí é un erro de programación.
    if ((paymentStatus == PaymentStatus.NONE) != (price.kind() != PriceKind.PAID)) {
      throw new IllegalStateException("Payment " + paymentStatus + " with price " + price.kind());
    }
    if (!status.isCancelled()
        && (paymentStatus == PaymentStatus.TO_REFUND || paymentStatus == PaymentStatus.REFUNDED)) {
      throw new IllegalStateException("Only a cancelled booking is refunded");
    }
    if (status.isCancelled() != (cancelledAt != null)
        || (cancelledAt == null) != (cancelledBy == null)) {
      throw new IllegalStateException("Cancellation data does not match " + status);
    }
    if (cancelUntil.isAfter(slot.start())) {
      throw new IllegalStateException("cancelUntil after the start");
    }
    // Sen conta, sempre a fixo o persoal; a do persoal pode ter a conta do seu correo (ADR-0031).
    if (holder != null && !holder.hasAccount() && bookedBy == null) {
      throw new IllegalStateException("A booking without account is always made by the staff");
    }
    if (seats < 1
        || seats > Participants.MAX
        || (seats > 1 && slot.rentalMode() != RentalMode.SEATS)) {
      throw new IllegalStateException("Seats " + seats + " in a use by " + slot.rentalMode());
    }
    if (mark.marked() && status.isCancelled()) {
      throw new IllegalStateException("Only a confirmed booking is " + mark.attendance());
    }
    // A reserva dunha entidade (ADR-0039, punto 5): sen conta, correo nin teléfono de titular, e
    // sempre con quen a fixo; unha sesión é sempre dunha entidade e sen prezo.
    if (seriesId != null && organizationId == null) {
      throw new IllegalStateException("A session is always of an organization");
    }
    if (organizationId != null
        && (bookedBy == null
            || (holder != null
                && (holder.subject() != null
                    || holder.email() != null
                    || holder.phone() != null)))) {
      throw new IllegalStateException("An organization booking has no person as holder");
    }
    if (seriesId != null && price.kind() != PriceKind.NONE) {
      throw new IllegalStateException("A session has no price");
    }
    // A sesión dunha actividade do concello (ADR-0041): sen entidade nin persoa como titular, sen
    // prezo e sempre con quen a fixo.
    if (activityId != null
        && (organizationId != null
            || seriesId != null
            || bookedBy == null
            || price.kind() != PriceKind.NONE
            || (holder != null
                && (holder.subject() != null
                    || holder.email() != null
                    || holder.phone() != null)))) {
      throw new IllegalStateException("An activity session has no person, organization or price");
    }
  }

  /**
   * Reserva nova, confirmada: o prezo do uso por as franxas, o cobro pendente se é de pago e o
   * prazo para cancelar das normas. Comproba a antelación e a duración; o límite por persoa (que
   * precisa contar na base) compróbao antes o caso de uso con {@link BookingRules#checkCap}.
   *
   * @param unitPrice prezo do uso por franxa (nas prazas, por persoa e franxa)
   * @param rules normas do uso, que quedan conxeladas na reserva
   * @throws DomainException {@code booking.too-soon}, {@code booking.too-far} ou {@code
   *     booking.too-long}
   */
  public static Booking create(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      Instant now) {
    return create(id, code, slot, 1, unitPrice, rules, holder, null, now);
  }

  /**
   * Reserva nova con {@code seats} prazas (só nos usos por prazas; ADR-0031, punto 5): o importe é
   * o do uso por franxas e por prazas.
   */
  public static Booking create(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      int seats,
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      Instant now) {
    return create(id, code, slot, seats, unitPrice, rules, holder, null, now);
  }

  /**
   * Reserva nova que fai o persoal para unha persoa sen conta (mostrador ou teléfono): as mesmas
   * comprobacións ca {@link #create(BookingId, BookingCode, BookedSlot, Price, BookingRules,
   * Holder, Instant)}; queda quen a fixo.
   *
   * @param bookedBy {@code sub} de quen a fixo
   */
  public static Booking createFor(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      String bookedBy,
      Instant now) {
    return createFor(id, code, slot, 1, unitPrice, rules, holder, bookedBy, now);
  }

  /** Coma {@link #createFor}, con {@code seats} prazas (só nos usos por prazas). */
  public static Booking createFor(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      int seats,
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      String bookedBy,
      Instant now) {
    return create(
        id,
        code,
        slot,
        seats,
        unitPrice,
        rules,
        holder,
        Objects.requireNonNull(bookedBy, "bookedBy"),
        now);
  }

  private static Booking create(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      int seats,
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      @Nullable String bookedBy,
      Instant now) {
    Objects.requireNonNull(holder, "holder");
    rules.checkSlot(slot.start(), slot.slots(), now);
    Price total = unitPrice.forSlots(slot.slots()).forSeats(seats);
    return new Booking(
        id,
        code,
        slot,
        seats,
        BookingStatus.CONFIRMED,
        total.initialPayment(),
        total,
        rules,
        rules.cancelUntil(slot.start()),
        holder,
        bookedBy,
        null,
        null,
        AttendanceMark.NEVER,
        null,
        now,
        now,
        null,
        null,
        null);
  }

  /**
   * Reserva extra dunha entidade (ADR-0039, punto 10): as mesmas comprobacións, prezo e normas ca
   * {@link #create(BookingId, BookingCode, BookedSlot, Price, BookingRules, Holder, Instant)}; o
   * titular é a entidade (só o seu nome, {@link #organizationHolder}) e queda quen a fixo, a persoa
   * responsable.
   */
  public static Booking createForOrganization(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      UUID organizationId,
      String bookedBy,
      Instant now) {
    Objects.requireNonNull(organizationId, "organizationId");
    return createFor(id, code, slot, unitPrice, rules, holder, bookedBy, now)
        .restoredFor(organizationId, null);
  }

  /**
   * Unha sesión dunha tempada (ADR-0039, punto 5): da entidade, confirmada, sen prezo nin normas e
   * cancelable ata que empeza. Non mira a antelación nin a duración do uso: o oco xa o calculou a
   * serie baixo o bloqueo da instalación.
   *
   * @param bookedBy quen creou a tempada (o persoal)
   */
  public static Booking createSession(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      Holder holder,
      UUID organizationId,
      UUID seriesId,
      String bookedBy,
      Instant now) {
    return new Booking(
        id,
        code,
        slot,
        1,
        BookingStatus.CONFIRMED,
        PaymentStatus.NONE,
        Price.none(),
        BookingRules.none(),
        slot.start(),
        Objects.requireNonNull(holder, "holder"),
        Objects.requireNonNull(bookedBy, "bookedBy"),
        null,
        null,
        AttendanceMark.NEVER,
        null,
        now,
        now,
        Objects.requireNonNull(organizationId, "organizationId"),
        Objects.requireNonNull(seriesId, "seriesId"),
        null);
  }

  /**
   * Unha sesión dunha actividade do concello (ADR-0041): coma unha sesión da tempada, pero sen
   * entidade; o titular é só o nome da actividade e queda quen a creou (o persoal). Non mira a
   * antelación nin a duración do uso: o oco xa o calculou o planificador baixo o bloqueo.
   */
  public static Booking createActivitySession(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      Holder holder,
      UUID activityId,
      String bookedBy,
      Instant now) {
    return new Booking(
        id,
        code,
        slot,
        1,
        BookingStatus.CONFIRMED,
        PaymentStatus.NONE,
        Price.none(),
        BookingRules.none(),
        slot.start(),
        Objects.requireNonNull(holder, "holder"),
        Objects.requireNonNull(bookedBy, "bookedBy"),
        null,
        null,
        AttendanceMark.NEVER,
        null,
        now,
        now,
        null,
        null,
        Objects.requireNonNull(activityId, "activityId"));
  }

  /** A mesma reserva nova (sen gardar) con outro código: o código xa o tiña outra reserva. */
  public Booking withCode(BookingCode other) {
    if (version != null) {
      throw new IllegalStateException("Only a new booking changes its code");
    }
    return new Booking(
        id,
        Objects.requireNonNull(other, "code"),
        slot,
        seats,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        holder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        mark,
        version,
        createdAt,
        updatedAt,
        organizationId,
        seriesId,
        activityId);
  }

  /** O titular dunha reserva da entidade: só o nome, no idioma dos seus correos. */
  public static Holder organizationHolder(String name, Language language) {
    return Holder.walkIn(name, null, null, language);
  }

  /**
   * A mesma reserva como da entidade e, se é unha sesión, da súa serie: complétaa quen a le da
   * base.
   */
  public Booking restoredFor(@Nullable UUID organization, @Nullable UUID series) {
    return restoredFor(organization, series, activityId);
  }

  /** Coma {@link #restoredFor(UUID, UUID)} e, se é unha sesión dunha actividade, a actividade. */
  public Booking restoredFor(
      @Nullable UUID organization, @Nullable UUID series, @Nullable UUID activity) {
    if (organization == null && series == null && activity == null) {
      return this;
    }
    return new Booking(
        id,
        code,
        slot,
        seats,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        holder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        mark,
        version,
        createdAt,
        updatedAt,
        organization,
        series,
        activity);
  }

  /**
   * Desde a base, tal como se gardou.
   *
   * @param holder quen reservou; {@code null} se a reserva xa está anonimizada (conservación,
   *     {@code BookingRetentionService})
   */
  public static Booking rehydrate(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      BookingStatus status,
      PaymentStatus paymentStatus,
      Price price,
      BookingRules rules,
      Instant cancelUntil,
      @Nullable Holder holder,
      @Nullable Instant cancelledAt,
      @Nullable String cancelledBy,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return rehydrate(
        id,
        code,
        slot,
        1,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        holder,
        null,
        cancelledAt,
        cancelledBy,
        version,
        createdAt,
        updatedAt);
  }

  /** Desde a base, tal como se gardou, con quen a fixo se foi o persoal. */
  public static Booking rehydrate(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      BookingStatus status,
      PaymentStatus paymentStatus,
      Price price,
      BookingRules rules,
      Instant cancelUntil,
      @Nullable Holder holder,
      @Nullable String bookedBy,
      @Nullable Instant cancelledAt,
      @Nullable String cancelledBy,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return rehydrate(
        id,
        code,
        slot,
        1,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        holder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        version,
        createdAt,
        updatedAt);
  }

  /** Desde a base, tal como se gardou, coas súas prazas. */
  public static Booking rehydrate(
      BookingId id,
      BookingCode code,
      BookedSlot slot,
      int seats,
      BookingStatus status,
      PaymentStatus paymentStatus,
      Price price,
      BookingRules rules,
      Instant cancelUntil,
      @Nullable Holder holder,
      @Nullable String bookedBy,
      @Nullable Instant cancelledAt,
      @Nullable String cancelledBy,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Booking(
        id,
        code,
        slot,
        seats,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        holder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        AttendanceMark.NEVER,
        version,
        createdAt,
        updatedAt,
        null,
        null,
        null);
  }

  /** A mesma reserva coa marca de asistencia gardada (ADR-0032): complétaa quen a le da base. */
  public Booking restoredWith(AttendanceMark stored) {
    return marked(stored, updatedAt);
  }

  /**
   * A persoa cancela a súa reserva dentro do prazo. Se estaba pagada, queda «a devolver».
   *
   * @throws DomainException {@code booking.not-cancellable} se xa está cancelada; {@code
   *     booking.cancel-deadline-passed} se pasou o prazo
   */
  public Booking cancelByPerson(Instant now, String subject) {
    requireConfirmed();
    requireUnmarked();
    requireBeforeDeadline(now);
    return cancelled(BookingStatus.CANCELLED_BY_PERSON, now, subject);
  }

  /**
   * O concello cancela (detalle do xestor ou un cambio da instalación): sen prazo. Se estaba
   * pagada, queda «a devolver».
   *
   * @throws DomainException {@code booking.not-cancellable} se xa está cancelada
   */
  public Booking cancelByMunicipality(Instant now, String actorSubject) {
    requireConfirmed();
    return unmarked(now, actorSubject)
        .cancelled(BookingStatus.CANCELLED_BY_MUNICIPALITY, now, actorSubject);
  }

  /** Quen cancela a reserva dunha partida sen o mínimo (ADR-0040, punto 7): a tarefa do prazo. */
  public static final String OPEN_MATCH_ACTOR = "furar:open-match";

  /**
   * Ao prazo do concello a partida aberta non chegou ao mínimo (ADR-0040, punto 7): o concello
   * cancela a reserva sen custo, coma {@link #cancelByMunicipality}; se estaba pagada, queda «a
   * devolver». Quen cancela é a tarefa ({@value #OPEN_MATCH_ACTOR}).
   *
   * @throws DomainException {@code booking.not-cancellable} se xa está cancelada
   */
  public Booking cancelUnfilledMatch(Instant now) {
    return cancelByMunicipality(now, OPEN_MATCH_ACTOR);
  }

  /**
   * Cobro no mostrador: pendente → pagada e desfacer (só confirmadas); a devolver → devolta e
   * desfacer. Calquera outro paso, {@code booking.payment-transition-invalid}. A fila da historia é
   * {@link BookingEventKind#ofPayment}.
   */
  public Booking changePayment(PaymentStatus target, Instant now) {
    Objects.requireNonNull(target, "target");
    if (!paymentTargets().contains(target)) {
      throw new DomainException(
          BookingError.PAYMENT_TRANSITION_INVALID,
          Map.of("from", paymentStatus.name(), "to", target.name()));
    }
    return copy(
        slot, seats, status, target, price, rules, cancelUntil, cancelledAt, cancelledBy, now);
  }

  /**
   * Cambiar a outro oco da mesma instalación: mesmo id e código, prezo, normas e prazo novos. Só
   * confirmada e dentro do prazo para cancelar; se estaba pagada, o prezo novo ten que ser o mesmo.
   * A ocupación vella e o límite por persoa (sen contar esta) son do caso de uso.
   *
   * @throws DomainException {@code booking.not-cancellable}, {@code
   *     booking.cancel-deadline-passed}, {@code booking.change-other-facility}, {@code
   *     booking.change-price-paid} ou os de {@link BookingRules#checkSlot}
   */
  public Booking reschedule(BookedSlot to, Price unitPrice, BookingRules newRules, Instant now) {
    return reschedule(to, seats, unitPrice, newRules, now);
  }

  /**
   * Coma {@link #reschedule(BookedSlot, Price, BookingRules, Instant)}, coas prazas de despois do
   * cambio (ADR-0031, punto 8): así se quita ou se engade unha persoa. Unha reserva pagada non
   * cambia de número de prazas, porque cambiaría o prezo.
   */
  public Booking reschedule(
      BookedSlot to, int newSeats, Price unitPrice, BookingRules newRules, Instant now) {
    requireConfirmed();
    requireUnmarked();
    requireBeforeDeadline(now);
    return moved(to, newSeats, unitPrice, newRules, now);
  }

  /**
   * O persoal cambia a reserva a outro oco da mesma instalación (o mesmo formulario, relleno): coma
   * {@link #reschedule}, pero sen o prazo da persoa, igual ca cancelar desde a xestión. O oco novo
   * cumpre as normas do uso.
   *
   * @throws DomainException {@code booking.not-cancellable}, {@code booking.change-other-facility},
   *     {@code booking.change-price-paid} ou os de {@link BookingRules#checkSlot}
   */
  public Booking rescheduleByMunicipality(
      BookedSlot to, Price unitPrice, BookingRules newRules, Instant now) {
    return rescheduleByMunicipality(to, seats, unitPrice, newRules, now);
  }

  /** Coma {@link #rescheduleByMunicipality}, coas prazas de despois do cambio. */
  public Booking rescheduleByMunicipality(
      BookedSlot to, int newSeats, Price unitPrice, BookingRules newRules, Instant now) {
    return rescheduleByMunicipality(to, newSeats, unitPrice, newRules, now, null);
  }

  /**
   * Coma {@link #rescheduleByMunicipality}, dicindo quen a cambia: se a reserva estaba validada ou
   * tiña unha falta, o cambio borra a marca, con quen e cando (ADR-0032, punto 2).
   */
  public Booking rescheduleByMunicipality(
      BookedSlot to,
      int newSeats,
      Price unitPrice,
      BookingRules newRules,
      Instant now,
      @Nullable String actorSubject) {
    requireConfirmed();
    Booking unmarked =
        mark.marked() ? unmarked(now, Objects.requireNonNull(actorSubject, "actor")) : this;
    return unmarked.moved(to, newSeats, unitPrice, newRules, now);
  }

  /**
   * Corrixe para quen é unha reserva que fixo o persoal (nome, correo e teléfono; co correo novo
   * vai a súa conta, se a ten: ADR-0031, punto 7). A que fixo a persoa coa súa conta non se toca:
   * eses datos son os da conta.
   *
   * @throws DomainException {@code booking.holder-not-editable} se a fixo a propia persoa; {@code
   *     booking.not-cancellable} se xa está cancelada
   */
  public Booking withHolder(Holder newHolder, Instant now) {
    Objects.requireNonNull(newHolder, "newHolder");
    // A dunha entidade é da entidade (ADR-0039, punto 5): non se liga a ningunha conta.
    if (bookedBy == null || holder == null || organizationId != null) {
      throw new DomainException(BookingError.HOLDER_NOT_EDITABLE);
    }
    requireConfirmed();
    return new Booking(
        id,
        code,
        slot,
        seats,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        newHolder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        mark,
        version,
        createdAt,
        now,
        organizationId,
        seriesId,
        activityId);
  }

  private Booking moved(
      BookedSlot to, int newSeats, Price unitPrice, BookingRules newRules, Instant now) {
    if (!to.facilityId().equals(slot.facilityId())) {
      throw new DomainException(BookingError.CHANGE_OTHER_FACILITY);
    }
    newRules.checkSlot(to.start(), to.slots(), now);
    Price total = unitPrice.forSlots(to.slots()).forSeats(newSeats);
    if (paymentStatus == PaymentStatus.PAID && !total.equals(price)) {
      throw new DomainException(BookingError.CHANGE_PRICE_PAID);
    }
    PaymentStatus payment =
        paymentStatus == PaymentStatus.PAID ? paymentStatus : total.initialPayment();
    return copy(
        to,
        newSeats,
        status,
        payment,
        total,
        newRules,
        newRules.cancelUntil(to.start()),
        null,
        null,
        now);
  }

  /**
   * Comproba que a persoa aínda pode cambiar ou cancelar por internet, antes de mirar o oco novo.
   *
   * @throws DomainException {@code booking.not-cancellable} se xa está cancelada; {@code
   *     booking.cancel-deadline-passed} se pasou o prazo
   */
  public void checkChangeable(Instant now) {
    requireConfirmed();
    requireUnmarked();
    requireBeforeDeadline(now);
  }

  /**
   * Os cobros que se poden marcar agora: pendente → pagada e desfacer (só confirmadas); a devolver
   * → devolta e desfacer. Baleiro se non hai nada que cobrar.
   */
  public Set<PaymentStatus> paymentTargets() {
    return switch (paymentStatus) {
      case PENDING -> status.isCancelled() ? Set.of() : Set.of(PaymentStatus.PAID);
      case PAID -> status.isCancelled() ? Set.of() : Set.of(PaymentStatus.PENDING);
      case TO_REFUND -> Set.of(PaymentStatus.REFUNDED);
      case REFUNDED -> Set.of(PaymentStatus.TO_REFUND);
      case NONE -> Set.of();
    };
  }

  /** «Realizada»: confirmada e xa rematada (non é un estado gardado). */
  public boolean isDone(Instant now) {
    return status == BookingStatus.CONFIRMED && !slot.end().isAfter(now);
  }

  /** Se a persoa aínda pode cancelar ou cambiar por internet. */
  public boolean canCancel(Instant now) {
    return status == BookingStatus.CONFIRMED && !mark.marked() && now.isBefore(cancelUntil);
  }

  /**
   * Se {@code now} cae na marxe de validación (ADR-0032, punto 3): desde {@code margin} antes do
   * inicio ata {@code margin} despois do final, os dous extremos incluídos. É unha duración sobre
   * os instantes da franxa: non depende da zona horaria.
   */
  public boolean inValidationWindow(Instant now, Duration margin) {
    return !now.isBefore(slot.start().minus(margin)) && !now.isAfter(slot.end().plus(margin));
  }

  /**
   * Valídase: leuse o seu QR ou tecleouse o código (ADR-0032, punto 4). A marxe e o motivo de
   * saltala míraos o caso de uso. Sobre unha falta, quítaa.
   *
   * @throws DomainException {@code booking.not-validatable} se está cancelada
   */
  public Booking validate(Instant now, String actor) {
    if (status.isCancelled()) {
      throw new DomainException(BookingError.NOT_VALIDATABLE);
    }
    return marked(AttendanceMark.of(Attendance.USED, now, actor), now);
  }

  /**
   * Queda como falta (ADR-0032, punto 5): só unha confirmada, xa rematada e sen validar.
   *
   * @throws DomainException {@code booking.attendance-cancelled}, {@code booking.already-validated}
   *     ou {@code booking.no-show-too-early}
   */
  public Booking markNoShow(Instant now, String actor) {
    if (status.isCancelled()) {
      throw new DomainException(BookingError.ATTENDANCE_CANCELLED);
    }
    if (mark.attendance() == Attendance.USED) {
      throw new DomainException(BookingError.ALREADY_VALIDATED);
    }
    if (now.isBefore(slot.end())) {
      throw new DomainException(BookingError.NO_SHOW_TOO_EARLY);
    }
    return marked(AttendanceMark.of(Attendance.NO_SHOW, now, actor), now);
  }

  /**
   * Desfai a validación ou a falta, sen prazo: queda sen marca, con quen e cando a desfixo.
   *
   * @throws DomainException {@code booking.attendance-not-marked} se non hai nada que desfacer
   */
  public Booking undoAttendance(Instant now, String actor) {
    if (!mark.marked()) {
      throw new DomainException(BookingError.ATTENDANCE_NOT_MARKED);
    }
    return unmarked(now, actor);
  }

  /** Se o persoal a pode marcar como falta agora: confirmada, rematada e sen marca. */
  public boolean canMarkNoShow(Instant now) {
    return isDone(now) && !mark.marked();
  }

  private void requireConfirmed() {
    if (status.isCancelled()) {
      throw new DomainException(BookingError.NOT_CANCELLABLE);
    }
  }

  /** Validada ou con falta, xa non se cambia nin se cancela por internet (ADR-0032, punto 2). */
  private void requireUnmarked() {
    if (mark.marked()) {
      throw new DomainException(BookingError.ATTENDANCE_MARKED);
    }
  }

  private void requireBeforeDeadline(Instant now) {
    if (!now.isBefore(cancelUntil)) {
      throw new DomainException(BookingError.CANCEL_DEADLINE_PASSED);
    }
  }

  private Booking cancelled(BookingStatus newStatus, Instant now, String actor) {
    Objects.requireNonNull(actor, "actor");
    PaymentStatus payment =
        paymentStatus == PaymentStatus.PAID ? PaymentStatus.TO_REFUND : paymentStatus;
    return copy(slot, seats, newStatus, payment, price, rules, cancelUntil, now, actor, now);
  }

  private Booking copy(
      BookedSlot newSlot,
      int newSeats,
      BookingStatus newStatus,
      PaymentStatus newPayment,
      Price newPrice,
      BookingRules newRules,
      Instant newCancelUntil,
      @Nullable Instant newCancelledAt,
      @Nullable String newCancelledBy,
      Instant now) {
    return new Booking(
        id,
        code,
        newSlot,
        newSeats,
        newStatus,
        newPayment,
        newPrice,
        newRules,
        newCancelUntil,
        holder,
        bookedBy,
        newCancelledAt,
        newCancelledBy,
        mark,
        version,
        createdAt,
        now,
        organizationId,
        seriesId,
        activityId);
  }

  private Booking marked(AttendanceMark newMark, Instant now) {
    return new Booking(
        id,
        code,
        slot,
        seats,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        holder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        newMark,
        version,
        createdAt,
        now,
        organizationId,
        seriesId,
        activityId);
  }

  /** Sen marca, con quen e cando a borrou; a que non a tiña queda igual. */
  private Booking unmarked(Instant now, String actor) {
    return mark.marked() ? marked(AttendanceMark.of(Attendance.NONE, now, actor), now) : this;
  }

  public BookingId id() {
    return id;
  }

  public BookingCode code() {
    return code;
  }

  public BookedSlot slot() {
    return slot;
  }

  /** As prazas da reserva: 1 fóra dos usos por prazas; neles, unha por persoa (ADR-0031). */
  public int seats() {
    return seats;
  }

  public BookingStatus status() {
    return status;
  }

  public PaymentStatus paymentStatus() {
    return paymentStatus;
  }

  /** O total conxelado ao reservar ou cambiar. */
  public Price price() {
    return price;
  }

  /** As normas aplicadas, conxeladas. */
  public BookingRules rules() {
    return rules;
  }

  public Instant cancelUntil() {
    return cancelUntil;
  }

  /** Quen reservou; {@code null} nunha reserva anonimizada ({@link #isAnonymised}). */
  public @Nullable Holder holder() {
    return holder;
  }

  /**
   * Quen reservou, nunha reserva que aínda o garda: as que se cambian, cancelan ou avisan. Unha
   * anonimizada xa rematou hai anos.
   *
   * @throws IllegalStateException se está anonimizada
   */
  public Holder requireHolder() {
    if (holder == null) {
      throw new IllegalStateException("Anonymised booking " + id.value());
    }
    return holder;
  }

  /** {@code sub} de quen fixo a reserva no nome da persoa (o persoal); nulo se a fixo ela. */
  public @Nullable String bookedBy() {
    return bookedBy;
  }

  /** Se a conservación xa lle quitou a persoa (ADR-0017 punto 5). */
  public boolean isAnonymised() {
    return holder == null;
  }

  public @Nullable Instant cancelledAt() {
    return cancelledAt;
  }

  public @Nullable String cancelledBy() {
    return cancelledBy;
  }

  /** A marca de asistencia co seu último cambio (ADR-0032). */
  public AttendanceMark mark() {
    return mark;
  }

  public Attendance attendance() {
    return mark.attendance();
  }

  public @Nullable Long version() {
    return version;
  }

  /** A entidade titular (ADR-0039); nulo se é dunha persoa. */
  public @Nullable UUID organizationId() {
    return organizationId;
  }

  /** A tempada da que é sesión (ADR-0039); nulo fóra dunha tempada. */
  public @Nullable UUID seriesId() {
    return seriesId;
  }

  /** Se é unha sesión dunha tempada (ADR-0039). */
  public boolean isSession() {
    return seriesId != null;
  }

  /** A actividade do concello da que é sesión (ADR-0041); nulo fóra dunha actividade. */
  public @Nullable UUID activityId() {
    return activityId;
  }

  /** Se é unha sesión dunha actividade do concello (ADR-0041). */
  public boolean isActivitySession() {
    return activityId != null;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
