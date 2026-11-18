// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.notification.domain.JoinRequestRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite das solicitudes de alta por enderezo IP (ADR-0042, revisión do 2026-11-17), en memoria e
 * sen terceiros: como moito {@code perDay} por IP en cada día, na hora de Galicia (de medianoite a
 * medianoite; o aviso di «Volve tentalo mañá»). Ademais do límite xeral de peticións ({@code
 * RateLimitFilter}), que é por minuto e moito máis xeneroso. As contas bórranse ao cambiar de día;
 * con máis de {@value #MAX_CLIENTS} enderezos distintos nun día, os novos pasan sen contar (non se
 * esgota a memoria). Cada réplica conta as súas. Non se garda nin se rexistra a IP.
 */
class JoinRequestRateLimit {

  /** Enderezos distintos que se contan nun día, como moito. */
  static final int MAX_CLIENTS = 10_000;

  /** O día da hora de Galicia. */
  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private final Clock clock;
  private final int perDay;
  private final ConcurrentHashMap<String, AtomicInteger> clients = new ConcurrentHashMap<>();
  private volatile LocalDate window = LocalDate.MIN;

  /**
   * @param perDay solicitudes por IP e día; 0 ou menos, sen límite
   */
  JoinRequestRateLimit(Clock clock, int perDay) {
    this.clock = clock;
    this.perDay = perDay;
  }

  /**
   * Conta un intento desde ese enderezo.
   *
   * @throws JoinRequestRateLimitedException se pasa do límite, co que falta ata a medianoite
   */
  void check(String client) {
    if (perDay <= 0) {
      return;
    }
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ZONE);
    if (!today.equals(window)) {
      synchronized (this) {
        if (!today.equals(window)) {
          clients.clear();
          window = today;
        }
      }
    }
    AtomicInteger count = clients.get(client);
    if (count == null) {
      if (clients.size() >= MAX_CLIENTS) {
        return;
      }
      count = clients.computeIfAbsent(client, k -> new AtomicInteger());
    }
    if (count.incrementAndGet() > perDay) {
      Instant midnight = today.plusDays(1).atStartOfDay(ZONE).toInstant();
      throw new JoinRequestRateLimitedException(Duration.between(now, midnight));
    }
  }
}
