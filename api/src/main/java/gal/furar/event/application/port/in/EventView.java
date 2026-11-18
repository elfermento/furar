// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventSession;
import gal.furar.facility.FacilityCover;
import gal.furar.facility.FacilitySummary;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un evento co que se ve del: cantas persoas teñen praza, cantas a piden e, desde ADR-0041, cantas
 * están na lista de espera avisadas dunha praza libre e cantas esperan sen aviso (só números, sen
 * persoas: tamén para a plataforma) e, se é nunha instalación, a instalación e a súa portada. Nunha
 * actividade con horario, o nome do uso e da parte e as próximas sesións que non remataron (sen as
 * canceladas; como moito {@link #NEXT_SESSIONS}).
 */
public record EventView(
    Event event,
    int confirmed,
    int pending,
    int notified,
    int waiting,
    @Nullable FacilitySummary facility,
    @Nullable FacilityCover cover,
    @Nullable String useName,
    @Nullable String partName,
    List<EventSession> nextSessions) {

  /** Cantas sesións próximas leva unha vista, como moito. */
  public static final int NEXT_SESSIONS = 5;

  public EventView {
    Objects.requireNonNull(event, "event");
    nextSessions = nextSessions == null ? List.of() : List.copyOf(nextSessions);
  }

  public EventView(
      Event event,
      int confirmed,
      int pending,
      int notified,
      int waiting,
      @Nullable FacilitySummary facility,
      @Nullable FacilityCover cover) {
    this(event, confirmed, pending, notified, waiting, facility, cover, null, null, List.of());
  }

  public EventView(
      Event event,
      int confirmed,
      int pending,
      @Nullable FacilitySummary facility,
      @Nullable FacilityCover cover) {
    this(event, confirmed, pending, 0, 0, facility, cover);
  }

  /**
   * Prazas libres (nulo: sen límite). Unha praza da que se avisou segue libre ata que a confirmen.
   */
  public @Nullable Integer free() {
    Integer capacity = event.details().capacity();
    return capacity == null ? null : Math.max(0, capacity - confirmed);
  }

  /** Cantas hai na lista de espera (avisadas e sen aviso). */
  public int waitlisted() {
    return notified + waiting;
  }

  /** Sen prazas libres. */
  public boolean full() {
    Integer free = free();
    return free != null && free == 0;
  }

  /**
   * Se a veciñanza pode apuntarse ou pedir praza agora sen pasar pola lista de espera: mentres haxa
   * alguén nela, unha praza libre é para a lista (ADR-0041). Con aprobación pódese pedir praza
   * aínda que estea completo: a solicitude queda pendente e aceptala segue precisando unha praza.
   */
  public boolean open(Instant now) {
    if (!event.open(now)) {
      return false;
    }
    if (event.details().approval()) {
      return true;
    }
    return !full() && (waitlisted() == 0 || !event.waitlist());
  }

  /** Se agora quen chega entra na lista de espera (ADR-0041). */
  public boolean waitlistOpen(Instant now) {
    return event.open(now) && event.waitlist() && (full() || waitlisted() > 0);
  }
}
