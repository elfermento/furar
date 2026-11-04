// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.CommonError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de peticións á API por enderezo IP (decisión do mantedor do 2026-11-04; docs/ens.md). A
 * imaxe fixada de Caddy non trae un módulo de límite de peticións, así que vai aquí, sen
 * dependencias: por cada IP e minuto de reloxo, como moito {@code requests} peticións e, delas,
 * {@code writes} que escriben (as que non son GET, HEAD nin OPTIONS). Pasado, 429 {@code
 * common.rate-limited} con {@code Retry-After} ata o minuto seguinte.
 *
 * <p>A IP é a de quen pide segundo Caddy ({@code X-Forwarded-For}, {@code
 * server.forward-headers-strategy}): a API só se publica por el. Os límites son xenerosos (unha
 * oficina con moitas persoas detrás dunha IP non os nota); paran unha inundación, non unha persoa.
 * As contas viven en memoria e bórranse ao cambiar de minuto; con máis de {@value #MAX_CLIENTS}
 * enderezos distintos nun minuto, os novos pasan sen contar (non se esgota a memoria). Cada réplica
 * conta as súas. O rexistro técnico leva a IP só a primeira vez que pasa o límite nese minuto.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  /** Enderezos distintos que se contan nun minuto, como moito. */
  static final int MAX_CLIENTS = 100_000;

  private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");
  private static final long MINUTE = 60_000;
  private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

  private final ProblemWriter problems;
  private final Clock clock;
  private final int requests;
  private final int writes;
  private final ConcurrentHashMap<String, Counts> clients = new ConcurrentHashMap<>();
  private volatile long window = Long.MIN_VALUE;

  /**
   * @param requests peticións por IP e minuto; 0 ou menos, sen límite
   * @param writes peticións que escriben por IP e minuto; 0 ou menos, sen límite
   */
  public RateLimitFilter(ProblemWriter problems, Clock clock, int requests, int writes) {
    this.problems = problems;
    this.clock = clock;
    this.requests = requests;
    this.writes = writes;
  }

  private record Counts(AtomicInteger all, AtomicInteger writes) {

    Counts() {
      this(new AtomicInteger(), new AtomicInteger());
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (requests <= 0 && writes <= 0) {
      chain.doFilter(request, response);
      return;
    }
    long now = clock.millis();
    long minute = Math.floorDiv(now, MINUTE);
    if (minute != window) {
      synchronized (this) {
        if (minute != window) {
          clients.clear();
          window = minute;
        }
      }
    }
    String ip = String.valueOf(request.getRemoteAddr());
    Counts counts = clients.get(ip);
    if (counts == null) {
      if (clients.size() >= MAX_CLIENTS) {
        chain.doFilter(request, response);
        return;
      }
      counts = clients.computeIfAbsent(ip, k -> new Counts());
    }
    int all = counts.all().incrementAndGet();
    boolean write = !READS.contains(request.getMethod());
    int written = write ? counts.writes().incrementAndGet() : 0;
    boolean overAll = requests > 0 && all > requests;
    boolean overWrites = write && writes > 0 && written > writes;
    if (overAll || overWrites) {
      if (overAll ? all == requests + 1 : written == writes + 1) {
        LOG.warn("rate limit reached client={} kind={}", ip, overAll ? "requests" : "writes");
      }
      long seconds = Math.max(1, (MINUTE - Math.floorMod(now, MINUTE) + 999) / 1000);
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
      problems.write(request, response, CommonError.RATE_LIMITED);
      return;
    }
    chain.doFilter(request, response);
  }
}
