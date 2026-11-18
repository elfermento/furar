// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.out;

import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As sesións das actividades con horario e a asistencia (ADR-0041), en SQL. Sempre no concello
 * actual e dentro dunha transacción.
 */
public interface SessionRepository {

  /** As sesións novas dunha actividade, nunha sentenza. */
  void insertAll(List<EventSession> sessions);

  /** As dunha actividade, por data. */
  List<EventSession> sessionsOf(EventId event);

  Optional<EventSession> find(EventId event, UUID sessionId);

  /**
   * As sesións sen cancelar destas actividades que aínda non remataron, por inicio: as próximas
   * (quen chama colle as primeiras de cada unha).
   */
  List<EventSession> upcoming(Collection<EventId> events, Instant now);

  /** A sesión de cada unha destas reservas: reserva → sesión. */
  Map<UUID, UUID> byBookings(Collection<UUID> bookingIds);

  /** A sesión que ocupa a instalación con esa reserva. */
  Optional<EventSession> findByBooking(UUID bookingId);

  /** Marca canceladas esas sesións (as que aínda non o estaban). */
  void cancel(Collection<UUID> sessionIds, Instant now);

  /** As marcas dunha sesión: inscrición → presente ({@code true}) ou ausente. */
  Map<UUID, Boolean> marks(UUID sessionId);

  /**
   * Garda as marcas dunha sesión (un valor nulo quita a marca), cando e quen as gardou (o nome,
   * copiado) e, a primeira vez, cando se pasou lista.
   */
  void mark(
      UUID sessionId, Map<UUID, @Nullable Boolean> marks, Instant now, @Nullable String byName);

  /** Cando e quen gardou a lista dunha sesión por última vez, se xa se gardou. */
  Optional<Saved> saved(UUID sessionId);

  /**
   * A última vez que se gardou a lista.
   *
   * @param byName o nome de quen a gardou; nulo se non se sabe
   */
  record Saved(Instant at, @Nullable String byName) {}

  /** Presentes e ausentes de cada sesión dunha actividade. */
  Map<UUID, Tally> tallies(EventId event);

  /** Por inscrición: sesións con lista pasada (sen cancelar), presenzas e faltas. */
  Map<UUID, Summary> summary(EventId event);

  /**
   * As sesións sen cancelar dese día en actividades sen cancelar do concello, por hora ({@code
   * registered} vai a cero: cóntao quen chama).
   */
  List<Today> on(LocalDate date);

  /** Unha sesión do día co seu evento. */
  record Today(
      EventId eventId,
      String nameGl,
      String nameEs,
      @Nullable UUID facilityId,
      @Nullable String place,
      EventSession session,
      int registered) {}

  /** Presentes e ausentes. */
  record Tally(int present, int absent) {

    public static final Tally NONE = new Tally(0, 0);
  }

  /**
   * O resumo dunha persoa.
   *
   * @param sessions sesións da actividade con lista pasada
   */
  record Summary(int sessions, int present, int absent) {}
}
