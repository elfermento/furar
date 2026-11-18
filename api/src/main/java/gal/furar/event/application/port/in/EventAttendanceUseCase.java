// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSession;
import gal.furar.event.domain.Registration;
import gal.furar.facility.FacilitySummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Pasar lista nas sesións dunha actividade (ADR-0041, permiso {@code event.attendance}): Xestión e
 * Recepción. Sen penalización automática.
 */
public interface EventAttendanceUseCase {

  /**
   * As persoas con praza nesa sesión e a súa marca. Nunha sesión sen lista pasada todas veñen
   * presentes por defecto, sen gardar: ata que se garda, a sesión non conta.
   */
  SessionAttendance attendance(EventId id, UUID sessionId);

  /**
   * Garda a lista: presente ({@code true}), ausente ({@code false}) ou sen marca (nulo, só ao
   * corrixir). A primeira vez, quen non vén na petición queda presente. Desde a marxe antes do
   * inicio e despois para corrixir. Unha entrada no rexistro de actividade por petición.
   */
  SessionAttendance mark(EventId id, UUID sessionId, Map<UUID, @Nullable Boolean> marks);

  /** Por persoa con praza: sesións con lista pasada, presenzas e faltas. */
  List<AttendeeSummary> summary(EventId id);

  /** As sesións de hoxe do concello, para entrar a pasar lista sen buscar a actividade. */
  List<TodaySession> today();

  /**
   * A lista dunha sesión.
   *
   * @param taken se xa se gardou algunha vez
   * @param takenAt cando se gardou por última vez
   * @param takenByName quen a gardou por última vez (o nome do persoal, copiado ao gardar)
   */
  record SessionAttendance(
      EventSession session,
      boolean taken,
      @Nullable Instant takenAt,
      @Nullable String takenByName,
      List<AttendeeMark> attendees) {

    public SessionAttendance {
      Objects.requireNonNull(session, "session");
      attendees = List.copyOf(attendees);
    }
  }

  /** Unha persoa e a súa marca nunha sesión (nula: sen marcar). */
  record AttendeeMark(Registration registration, @Nullable Boolean present) {

    public AttendeeMark {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /** O resumo dunha persoa. */
  record AttendeeSummary(Registration registration, int sessions, int present, int absent) {

    public AttendeeSummary {
      Objects.requireNonNull(registration, "registration");
    }
  }

  /**
   * Unha sesión de hoxe co seu evento, onde é, cantas persoas teñen praza e, coa lista pasada,
   * cantas estaban e cantas faltaron.
   *
   * @param facility a instalación; nula se é noutro sitio ({@code place})
   */
  record TodaySession(
      EventId eventId,
      String nameGl,
      String nameEs,
      @Nullable FacilitySummary facility,
      @Nullable String place,
      EventSession session,
      int registered,
      int present,
      int absent) {}
}
