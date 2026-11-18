// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * O reloxo da lista de espera (ADR-0041): a persoa avisada ten a praza libre só para ela durante
 * {@code exclusive} (unha hora) e ese prazo non corre de noite. Entre {@code quietFrom} e {@code
 * quietTo} (22:00–8:00, hora do concello) non se avisa: o aviso que tocaría de noite sae ás {@code
 * quietTo} e a súa hora conta desde entón; un aviso que cruza o {@code quietFrom} para o reloxo e
 * retómao ás {@code quietTo}. Con {@code quietFrom} igual a {@code quietTo}, sen silencio. Puro e
 * sen reloxo do sistema.
 */
public record WaitlistClock(
    Duration exclusive, LocalTime quietFrom, LocalTime quietTo, ZoneId zone) {

  public WaitlistClock {
    Objects.requireNonNull(exclusive, "exclusive");
    Objects.requireNonNull(quietFrom, "quietFrom");
    Objects.requireNonNull(quietTo, "quietTo");
    Objects.requireNonNull(zone, "zone");
    if (exclusive.isZero() || exclusive.isNegative()) {
      throw new IllegalArgumentException("The exclusive time lasts some time");
    }
  }

  /** O aviso dado en {@code now}: cando empeza a contar (e sae o correo) e ata cando é só seu. */
  public WaitlistNotice notice(Instant now) {
    Instant start = quiet(now) ? next(now, quietTo) : now;
    return new WaitlistNotice(start, until(start), null);
  }

  /** Se nese instante é de noite (non se avisa nin corre o prazo). */
  public boolean quiet(Instant at) {
    if (quietFrom.equals(quietTo)) {
      return false;
    }
    LocalTime local = at.atZone(zone).toLocalTime();
    if (quietFrom.isBefore(quietTo)) {
      return !local.isBefore(quietFrom) && local.isBefore(quietTo);
    }
    return !local.isBefore(quietFrom) || local.isBefore(quietTo);
  }

  /** Ata cando é exclusivo un aviso que empeza a contar en {@code start} (fóra do silencio). */
  Instant until(Instant start) {
    if (quietFrom.equals(quietTo)) {
      return start.plus(exclusive);
    }
    Instant at = start;
    Duration left = exclusive;
    while (true) {
      Instant pause = next(at, quietFrom);
      Instant end = at.plus(left);
      if (!end.isAfter(pause)) {
        return end;
      }
      left = left.minus(Duration.between(at, pause));
      at = next(pause, quietTo);
    }
  }

  /** A seguinte vez, despois de {@code after}, que o reloxo do concello marca {@code time}. */
  private Instant next(Instant after, LocalTime time) {
    ZonedDateTime from = after.atZone(zone);
    ZonedDateTime candidate = ZonedDateTime.ofLocal(from.toLocalDate().atTime(time), zone, null);
    if (!candidate.isAfter(from)) {
      candidate = ZonedDateTime.ofLocal(from.toLocalDate().plusDays(1).atTime(time), zone, null);
    }
    return candidate.toInstant();
  }
}
