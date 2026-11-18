// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Evento do concello (ADR-0038): un evento deportivo puntual ao que a veciñanza se apunta ou, desde
 * ADR-0041, unha actividade con horario (unha sesión por día, coa inscrición a toda a actividade).
 * Inmutable: as invariantes van no construtor e cada cambio é un método con nome de negocio que
 * devolve unha copia (ADR-0005, A13). Cancelado, xa non cambia nin admite inscricións. Unha
 * actividade non cambia de horario, datas nin lugar: rematase e créase outra.
 */
public final class Event {

  private final EventId id;
  private final EventDetails details;
  private final @Nullable Instant cancelledAt;
  private final @Nullable LocalDate endedFrom;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Event(
      EventId id,
      EventDetails details,
      @Nullable Instant cancelledAt,
      @Nullable LocalDate endedFrom,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.details = Objects.requireNonNull(details, "details");
    this.cancelledAt = cancelledAt;
    this.endedFrom = endedFrom;
    if (endedFrom != null && !details.scheduled()) {
      throw new IllegalStateException("Only an activity ends early");
    }
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Event create(EventId id, EventDetails details, Instant now) {
    return new Event(id, details, null, null, null, now, now);
  }

  /** Fila dun ficheiro {@code furar-export} (ADR-0018): conserva id e datas; aínda sen gardar. */
  public static Event restore(
      EventId id,
      EventDetails details,
      @Nullable Instant cancelledAt,
      @Nullable LocalDate endedFrom,
      Instant createdAt,
      Instant updatedAt) {
    return new Event(id, details, cancelledAt, endedFrom, null, createdAt, updatedAt);
  }

  public static Event rehydrate(
      EventId id,
      EventDetails details,
      @Nullable Instant cancelledAt,
      @Nullable LocalDate endedFrom,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Event(id, details, cancelledAt, endedFrom, version, createdAt, updatedAt);
  }

  /**
   * Edición: mesma identidade e versión (o adaptador compróbaa con {@code If-Match}). Nin cancelado
   * nin xa empezado (unha actividade, nin rematada). O tipo non cambia; nunha actividade tampouco o
   * horario, as datas nin o lugar ({@code event.schedule-not-editable}).
   */
  public Event update(EventDetails changed, Instant now) {
    checkChangeable(now);
    if (changed.kind() != details.kind()) {
      throw new DomainException(EventError.KIND_NOT_EDITABLE);
    }
    EventDetails next = changed;
    if (details.scheduled()) {
      if (!Objects.equals(changed.schedule(), details.schedule())
          || !changed.place().equals(details.place())) {
        throw new DomainException(EventError.SCHEDULE_NOT_EDITABLE);
      }
      next = changed.withTimesOf(details);
    }
    return new Event(id, next, null, endedFrom, version, createdAt, now);
  }

  /**
   * Cancela o evento: deixa de verse na portada e avísase a quen estaba apuntado. Unha actividade
   * cancélase mentres non remate (as sesións xa empezadas quedan).
   */
  public Event cancel(Instant now) {
    checkChangeable(now);
    return new Event(id, details, now, endedFrom, version, createdAt, now);
  }

  /**
   * Remata antes unha actividade (ADR-0041): desde {@code from} xa non hai sesións, e o fin pasa a
   * ser {@code end} (o da última sesión que queda, ou o inicio se non queda ningunha). Repetilo cun
   * día posterior non cambia nada.
   *
   * @throws DomainException {@code event.not-scheduled}, {@code event.cancelled}, {@code
   *     event.ended} ou {@code event.end-invalid} ({@code from} antes de hoxe ou despois da última
   *     data)
   */
  public Event endFrom(LocalDate from, LocalDate today, Instant end, Instant now) {
    EventSchedule schedule = details.schedule();
    if (schedule == null) {
      throw new DomainException(EventError.NOT_SCHEDULED);
    }
    checkChangeable(now);
    if (from.isBefore(today) || from.isAfter(schedule.lastDate())) {
      throw new DomainException(EventError.END_INVALID);
    }
    if (endedFrom != null && !from.isBefore(endedFrom)) {
      return this;
    }
    Instant ends = end.isBefore(details.startsAt()) ? details.startsAt() : end;
    return new Event(id, details.withEndsAt(ends), null, from, version, createdAt, now);
  }

  private void checkChangeable(Instant now) {
    if (cancelled()) {
      throw new DomainException(EventError.CANCELLED);
    }
    if (details.scheduled() && ended(now)) {
      throw new DomainException(EventError.ENDED);
    }
    if (!details.scheduled() && started(now)) {
      throw new DomainException(EventError.STARTED);
    }
  }

  /**
   * Se admite inscricións agora: publicado, sen cancelar e antes de «Apuntarse ata» (ou de que
   * empece).
   */
  public boolean open(Instant now) {
    return details.published() && !cancelled() && now.isBefore(details.closesAt());
  }

  /** Comproba que admite inscricións (ou que se pode desapuntar): {@code event.closed}. */
  public void checkOpen(Instant now) {
    if (cancelled()) {
      throw new DomainException(EventError.CANCELLED);
    }
    if (!open(now)) {
      throw new DomainException(EventError.CLOSED);
    }
  }

  public boolean started(Instant now) {
    return !now.isBefore(details.startsAt());
  }

  /** Xa pasou: un evento dun día cando empeza; unha actividade cando remata a última sesión. */
  public boolean ended(Instant now) {
    return !now.isBefore(details.endsAt());
  }

  /**
   * Se a persoa pode desapuntarse ou saír da lista de espera: sen cancelar e, nun evento dun día,
   * antes de que empece; nunha actividade, antes de que remate.
   */
  public boolean leavable(Instant now) {
    return !cancelled() && (details.scheduled() ? !ended(now) : !started(now));
  }

  /**
   * Se ten lista de espera automática (ADR-0041): con prazas e sen «Con aprobación» (con
   * aprobación, ao encherse, «completo» coma sempre).
   */
  public boolean waitlist() {
    return details.capacity() != null && !details.approval();
  }

  /** Desde que día xa non hai sesións, se a actividade rematou antes. */
  public @Nullable LocalDate endedFrom() {
    return endedFrom;
  }

  public boolean cancelled() {
    return cancelledAt != null;
  }

  /** Se a veciñanza o ve (portada e ficha): publicado e sen cancelar. */
  public boolean visible() {
    return details.published() && !cancelled();
  }

  /** Se ten prezo: as inscricións levan estado de pago. */
  public boolean paid() {
    return details.priceCents() != null;
  }

  public EventId id() {
    return id;
  }

  public EventDetails details() {
    return details;
  }

  public @Nullable Instant cancelledAt() {
    return cancelledAt;
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
