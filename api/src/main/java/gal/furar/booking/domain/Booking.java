// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reserva (agregado; ADR-0025). Inmutable: as invariantes van no construtor e cada transición é un
 * método con nome de negocio que devolve unha copia (ADR-0005, A13). Leva o oco reservado, o prezo
 * total e as normas aplicadas conxelados (§1.2.11), quen reserva e dous estados separados: o da
 * reserva e o do cobro (§1.2.12). As comprobacións que precisan a base (horario, ocupación, límite
 * por persoa) fainas o caso de uso baixo o bloqueo da instalación; aquí, o que se sabe sen ela.
 */
public final class Booking {

  private final BookingId id;
  private final BookingCode code;
  private final BookedSlot slot;
  private final BookingStatus status;
  private final PaymentStatus paymentStatus;
  private final Price price;
  private final BookingRules rules;
  private final Instant cancelUntil;
  private final @Nullable Holder holder;
  private final @Nullable String bookedBy;
  private final @Nullable Instant cancelledAt;
  private final @Nullable String cancelledBy;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Booking(
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
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.slot = Objects.requireNonNull(slot, "slot");
    this.status = Objects.requireNonNull(status, "status");
    this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus");
    this.price = Objects.requireNonNull(price, "price");
    this.rules = Objects.requireNonNull(rules, "rules");
    this.cancelUntil = Objects.requireNonNull(cancelUntil, "cancelUntil");
    this.holder = holder;
    this.bookedBy = bookedBy;
    this.cancelledAt = cancelledAt;
    this.cancelledBy = cancelledBy;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
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
    // Coa conta reserva a persoa; sen conta, sempre o persoal.
    if (holder != null && holder.hasAccount() != (bookedBy == null)) {
      throw new IllegalStateException("A booking without account is always made by the staff");
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
    return create(id, code, slot, unitPrice, rules, holder, null, now);
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
    return create(
        id,
        code,
        slot,
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
      Price unitPrice,
      BookingRules rules,
      Holder holder,
      @Nullable String bookedBy,
      Instant now) {
    Objects.requireNonNull(holder, "holder");
    rules.checkSlot(slot.start(), slot.slots(), now);
    Price total = unitPrice.forSlots(slot.slots());
    return new Booking(
        id,
        code,
        slot,
        BookingStatus.CONFIRMED,
        total.initialPayment(),
        total,
        rules,
        rules.cancelUntil(slot.start()),
        holder,
        bookedBy,
        null,
        null,
        null,
        now,
        now);
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
    return new Booking(
        id,
        code,
        slot,
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

  /**
   * A persoa cancela a súa reserva dentro do prazo (P1). Se estaba pagada, queda «a devolver».
   *
   * @throws DomainException {@code booking.not-cancellable} se xa está cancelada; {@code
   *     booking.cancel-deadline-passed} se pasou o prazo
   */
  public Booking cancelByPerson(Instant now, String subject) {
    requireConfirmed();
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
    return cancelled(BookingStatus.CANCELLED_BY_MUNICIPALITY, now, actorSubject);
  }

  /**
   * Cobro no mostrador (§1.2.12): pendente → pagada e desfacer (só confirmadas); a devolver →
   * devolta e desfacer. Calquera outro paso, {@code booking.payment-transition-invalid}. A fila da
   * historia é {@link BookingEventKind#ofPayment}.
   */
  public Booking changePayment(PaymentStatus target, Instant now) {
    Objects.requireNonNull(target, "target");
    if (!paymentTargets().contains(target)) {
      throw new DomainException(
          BookingError.PAYMENT_TRANSITION_INVALID,
          Map.of("from", paymentStatus.name(), "to", target.name()));
    }
    return copy(slot, status, target, price, rules, cancelUntil, cancelledAt, cancelledBy, now);
  }

  /**
   * Cambiar a outro oco da mesma instalación (§1.2.8, P2, P3): mesmo id e código, prezo, normas e
   * prazo novos. Só confirmada e dentro do prazo para cancelar (P1); se estaba pagada, o prezo novo
   * ten que ser o mesmo. A ocupación vella e o límite por persoa (sen contar esta) son do caso de
   * uso.
   *
   * @throws DomainException {@code booking.not-cancellable}, {@code
   *     booking.cancel-deadline-passed}, {@code booking.change-other-facility}, {@code
   *     booking.change-price-paid} ou os de {@link BookingRules#checkSlot}
   */
  public Booking reschedule(BookedSlot to, Price unitPrice, BookingRules newRules, Instant now) {
    requireConfirmed();
    requireBeforeDeadline(now);
    return moved(to, unitPrice, newRules, now);
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
    requireConfirmed();
    return moved(to, unitPrice, newRules, now);
  }

  /**
   * Corrixe para quen é unha reserva que fixo o persoal (nome, correo e teléfono). A dunha persoa
   * coa súa conta non se toca: eses datos son os da conta.
   *
   * @throws DomainException {@code booking.holder-not-editable} se a reserva é dunha conta; {@code
   *     booking.not-cancellable} se xa está cancelada
   */
  public Booking withHolder(Holder newHolder, Instant now) {
    Objects.requireNonNull(newHolder, "newHolder");
    if (bookedBy == null || holder == null || newHolder.hasAccount()) {
      throw new DomainException(BookingError.HOLDER_NOT_EDITABLE);
    }
    requireConfirmed();
    return new Booking(
        id,
        code,
        slot,
        status,
        paymentStatus,
        price,
        rules,
        cancelUntil,
        newHolder,
        bookedBy,
        cancelledAt,
        cancelledBy,
        version,
        createdAt,
        now);
  }

  private Booking moved(BookedSlot to, Price unitPrice, BookingRules newRules, Instant now) {
    if (!to.facilityId().equals(slot.facilityId())) {
      throw new DomainException(BookingError.CHANGE_OTHER_FACILITY);
    }
    newRules.checkSlot(to.start(), to.slots(), now);
    Price total = unitPrice.forSlots(to.slots());
    if (paymentStatus == PaymentStatus.PAID && !total.equals(price)) {
      throw new DomainException(BookingError.CHANGE_PRICE_PAID);
    }
    PaymentStatus payment =
        paymentStatus == PaymentStatus.PAID ? paymentStatus : total.initialPayment();
    return copy(
        to, status, payment, total, newRules, newRules.cancelUntil(to.start()), null, null, now);
  }

  /**
   * Comproba que a persoa aínda pode cambiar ou cancelar por internet (P1), antes de mirar o oco
   * novo.
   *
   * @throws DomainException {@code booking.not-cancellable} se xa está cancelada; {@code
   *     booking.cancel-deadline-passed} se pasou o prazo
   */
  public void checkChangeable(Instant now) {
    requireConfirmed();
    requireBeforeDeadline(now);
  }

  /**
   * Os cobros que se poden marcar agora (§1.2.12, P13): pendente → pagada e desfacer (só
   * confirmadas); a devolver → devolta e desfacer. Baleiro se non hai nada que cobrar.
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

  /** Se a persoa aínda pode cancelar ou cambiar por internet (P1). */
  public boolean canCancel(Instant now) {
    return status == BookingStatus.CONFIRMED && now.isBefore(cancelUntil);
  }

  private void requireConfirmed() {
    if (status.isCancelled()) {
      throw new DomainException(BookingError.NOT_CANCELLABLE);
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
    return copy(slot, newStatus, payment, price, rules, cancelUntil, now, actor, now);
  }

  private Booking copy(
      BookedSlot newSlot,
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
        newStatus,
        newPayment,
        newPrice,
        newRules,
        newCancelUntil,
        holder,
        bookedBy,
        newCancelledAt,
        newCancelledBy,
        version,
        createdAt,
        now);
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

  public @Nullable Long version() {
    return version;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
