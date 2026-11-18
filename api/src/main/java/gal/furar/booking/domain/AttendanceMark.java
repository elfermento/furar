// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A marca de asistencia dunha reserva co seu último cambio (ADR-0032, punto 2): tamén un «Desfacer»
 * deixa quen e cando, e por iso a tarefa das faltas só marca as nunca tocadas.
 *
 * @param at cando cambiou a marca por última vez; nulo se nunca se tocou
 * @param by quen a cambiou: o {@code sub} do persoal ou a tarefa das faltas; nulo se nunca se tocou
 */
public record AttendanceMark(Attendance attendance, @Nullable Instant at, @Nullable String by) {

  /** Sen marca e nunca tocada: toda reserva nova. */
  public static final AttendanceMark NEVER = new AttendanceMark(Attendance.NONE, null, null);

  /** As mesmas garantías ca a táboa {@code booking}. */
  public AttendanceMark {
    Objects.requireNonNull(attendance, "attendance");
    if ((at == null) != (by == null) || (attendance != Attendance.NONE && at == null)) {
      throw new IllegalStateException("Attendance " + attendance + " without who and when");
    }
  }

  public static AttendanceMark of(Attendance attendance, Instant at, String by) {
    return new AttendanceMark(
        attendance, Objects.requireNonNull(at, "at"), Objects.requireNonNull(by, "by"));
  }

  /** Validada ou con falta. */
  public boolean marked() {
    return attendance != Attendance.NONE;
  }
}
