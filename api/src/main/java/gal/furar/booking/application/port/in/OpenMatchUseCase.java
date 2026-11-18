// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.MatchLevel;
import gal.furar.booking.domain.OpenMatchStatus;
import gal.furar.shared.domain.DomainException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * «Buscar xente para xogar» para a persoa con sesión e correo verificado (ADR-0040, puntos 4 e 5):
 * quen ten a reserva ábrea, edítaa, deixa de buscar e acepta ou rexeita as solicitudes; quen quere
 * xogar únese, pídeo ou sae. Outra persoa ou outra reserva → 404.
 */
public interface OpenMatchUseCase {

  /**
   * O que escribe quen organiza no diálogo.
   *
   * @param level nulo = «Sen indicar»
   */
  record OpenMatchCommand(
      int knownCount,
      int minimum,
      int maximum,
      @Nullable MatchLevel level,
      @Nullable String title,
      @Nullable String description,
      boolean approval) {}

  /**
   * A partida gardada.
   *
   * @param created se se acaba de abrir (201) ou se editou (200)
   */
  record Saved(MyOpenMatch match, boolean created) {}

  /**
   * Abre ou edita a partida da reserva, baixo o bloqueo da fila da reserva e despois o da partida.
   *
   * @param expectedVersion a versión da partida ({@code If-Match}); obrigatoria ao editar
   * @throws DomainException {@code booking.matches-off}, {@code booking.not-found}, {@code
   *     booking.match-not-allowed}, {@code booking.match-seats-use}, {@code
   *     booking.match-name-required}, {@code booking.match-too-late}, {@code
   *     booking.match-counts-invalid} ou {@code booking.match-maximum-below-count}
   */
  Saved save(BookingId bookingId, @Nullable Long expectedVersion, OpenMatchCommand command);

  /**
   * «Deixar de buscar» ({@code CLOSED}: bórranse as solicitudes pendentes) ou volver buscar ({@code
   * OPEN}, coas comprobacións de abrir).
   */
  MyOpenMatch changeStatus(BookingId bookingId, OpenMatchStatus status);

  /**
   * Quen entra.
   *
   * @param created se se acaba de unir ou pedir (201) ou xa estaba (200)
   */
  record Joined(MyOpenMatch match, boolean created) {}

  /**
   * Unirse (sen aprobación) ou pedilo (con ela), baixo o bloqueo da fila da partida: dúas persoas á
   * vez pola última praza, unha entra e a outra recibe 409 {@code booking.match-full}.
   */
  Joined join(UUID matchId);

  /** Saír ou retirar a solicitude, ata que empeza a partida. Sen fila, non fai nada. */
  void leave(UUID matchId);

  /** Quen organiza acepta unha solicitude pendente, se queda sitio. */
  MyOpenMatch accept(UUID matchId, UUID playerId);

  /** Quen organiza rexeita unha solicitude pendente. */
  MyOpenMatch reject(UUID matchId, UUID playerId);

  /**
   * A partida para a persoa: quen está dentro véa en calquera estado; o resto, só mentres se ve en
   * público.
   */
  MyOpenMatch get(UUID matchId);

  /** As partidas que organiza ou nas que está ou pediu entrar, aínda sen rematar. */
  List<MyOpenMatch> mine();
}
