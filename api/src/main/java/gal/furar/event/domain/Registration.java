// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha persoa nun evento (ADR-0038): apuntada ({@code CONFIRMED}) ou pedindo praza ({@code
 * PENDING}) ata que o concello a acepta ou a rexeita; desde ADR-0041, tamén na lista de espera
 * ({@code WAITING}) ou avisada dunha praza libre ({@code NOTIFIED}). Apúntaa a conta ({@code
 * participant}): ela mesma ou unha persoa a cargo ({@code dependent}, ADR-0031). Nun evento con
 * prezo leva o estado do pago (nulo nun evento gratis e na lista de espera).
 *
 * @param birthYear nun evento con límite de idade, o ano de nacemento da persoa a cargo, copiado da
 *     súa ficha ao apuntala; nulo na propia persoa e fóra dun evento con límite
 * @param birthDate nun evento con límite de idade, a data de nacemento que deu a propia persoa (só
 *     nesta inscrición, nunca na conta); nula nas persoas a cargo
 * @param notice o aviso da praza libre, só en {@code NOTIFIED}
 * @param coveredAt cando outra persoa cubriu a praza da que se avisara (volveu a {@code WAITING},
 *     no mesmo posto); nulo se non
 */
public record Registration(
    RegistrationId id,
    EventId eventId,
    RegistrationStatus status,
    Participant participant,
    @Nullable Dependent dependent,
    @Nullable Integer birthYear,
    @Nullable LocalDate birthDate,
    @Nullable PaymentStatus payment,
    @Nullable Instant decidedAt,
    @Nullable WaitlistNotice notice,
    @Nullable Instant coveredAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  public Registration {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(participant, "participant");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if ((status == RegistrationStatus.NOTIFIED) != (notice != null)) {
      throw new IllegalStateException("Only a notified registration has a notice");
    }
    if (coveredAt != null && status != RegistrationStatus.WAITING) {
      throw new IllegalStateException("Only a waiting registration had its seat covered");
    }
  }

  /**
   * Unha inscrición nova: confirmada ou, «Con aprobación», pendente. Con prezo, o pago comeza
   * pendente.
   */
  public static Registration open(
      Event event,
      Participant participant,
      @Nullable Dependent dependent,
      @Nullable Integer birthYear,
      @Nullable LocalDate birthDate,
      Instant now) {
    RegistrationStatus status =
        event.details().approval() ? RegistrationStatus.PENDING : RegistrationStatus.CONFIRMED;
    return new Registration(
        RegistrationId.newId(),
        event.id(),
        status,
        participant,
        dependent,
        birthYear,
        birthDate,
        event.paid() ? PaymentStatus.PENDING : null,
        null,
        null,
        null,
        now,
        now,
        0);
  }

  /** A propia persoa, sen límite de idade (ADR-0038). */
  public static Registration open(Event event, Participant participant, Instant now) {
    return open(event, participant, null, null, null, now);
  }

  /** Á lista de espera dun evento completo sen aprobación (ADR-0041): sen pago ata ter praza. */
  public static Registration waiting(
      Event event,
      Participant participant,
      @Nullable Dependent dependent,
      @Nullable Integer birthYear,
      @Nullable LocalDate birthDate,
      Instant now) {
    if (!event.waitlist()) {
      throw new DomainException(EventError.FULL);
    }
    return new Registration(
        RegistrationId.newId(),
        event.id(),
        RegistrationStatus.WAITING,
        participant,
        dependent,
        birthYear,
        birthDate,
        null,
        null,
        null,
        null,
        now,
        now,
        0);
  }

  /** Hai unha praza libre e avísase a esta persoa, que estaba esperando. */
  public Registration notified(WaitlistNotice to, Instant now) {
    if (status != RegistrationStatus.WAITING) {
      throw new IllegalStateException("Only a waiting registration is notified");
    }
    return with(RegistrationStatus.NOTIFIED, payment, decidedAt, to, null, now);
  }

  /**
   * Outra persoa confirmou a última praza libre: volve a esperar no mesmo posto e sábese que a
   * praza da que se avisara xa se cubriu.
   */
  public Registration covered(Instant now) {
    if (status != RegistrationStatus.NOTIFIED) {
      throw new IllegalStateException("Only a notified registration is covered");
    }
    return with(RegistrationStatus.WAITING, payment, decidedAt, null, now, now);
  }

  /**
   * A persoa avisada confirma a praza libre (calquera avisada, tamén pasada a súa hora, mentres a
   * praza siga libre: compróbao quen chama baixo o bloqueo do evento). Con prezo, pendente de pago.
   *
   * @throws DomainException {@code event.not-notified}
   */
  public Registration confirmNotice(Event event, Instant now) {
    if (status != RegistrationStatus.NOTIFIED) {
      throw new DomainException(EventError.NOT_NOTIFIED);
    }
    return with(
        RegistrationStatus.CONFIRMED,
        event.paid() ? PaymentStatus.PENDING : null,
        now,
        null,
        null,
        now);
  }

  /** O concello acepta a solicitude: pasa a ter praza. */
  public Registration accept(Instant now) {
    return decide(RegistrationStatus.CONFIRMED, now);
  }

  /** O concello rexeita a solicitude. */
  public Registration reject(Instant now) {
    return decide(RegistrationStatus.REJECTED, now);
  }

  private Registration decide(RegistrationStatus to, Instant now) {
    if (status != RegistrationStatus.PENDING) {
      throw new DomainException(EventError.NOT_PENDING);
    }
    return with(to, payment, now, null, null, now);
  }

  /** O persoal marca o pago (só nun evento con prezo e cunha praza confirmada). */
  public Registration pay(PaymentStatus to, Event event, Instant now) {
    if (!event.paid() || status != RegistrationStatus.CONFIRMED) {
      throw new DomainException(EventError.NO_PAYMENT);
    }
    return with(status, to, decidedAt, null, null, now);
  }

  private Registration with(
      RegistrationStatus to,
      @Nullable PaymentStatus pay,
      @Nullable Instant decided,
      @Nullable WaitlistNotice withNotice,
      @Nullable Instant covered,
      Instant now) {
    return new Registration(
        id,
        eventId,
        to,
        participant,
        dependent,
        birthYear,
        birthDate,
        pay,
        decided,
        withNotice,
        covered,
        createdAt,
        now,
        version);
  }

  /** Ocupa praza. */
  public boolean confirmed() {
    return status == RegistrationStatus.CONFIRMED;
  }

  /** O pago que se mostra: o gardado, ou pendente se o evento pasou a ter prezo despois. */
  public @Nullable PaymentStatus paymentFor(Event event) {
    if (!event.paid() || status.waitlisted()) {
      return null;
    }
    return payment == null ? PaymentStatus.PENDING : payment;
  }

  /** O nome de quen vai: a persoa a cargo ou, se non, quen se apuntou. */
  public @Nullable String attendeeName() {
    Dependent d = dependent;
    return d != null ? d.name() : participant.name();
  }
}
