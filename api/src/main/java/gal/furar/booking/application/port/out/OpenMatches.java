// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchPlayer;
import gal.furar.booking.domain.OpenMatchPlayerStatus;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As partidas abertas e quen se une a elas (ADR-0040), sempre no concello actual e dentro dunha
 * transacción (ADR-0006). As prazas cóntanse baixo o bloqueo da fila da partida ({@link
 * #findLocked}); os camiños que tocan a reserva toman antes a instalación e a reserva.
 */
public interface OpenMatches {

  /**
   * Unha partida coa súa reserva (instalación, uso, parte e franxa) e as persoas que se uniron.
   *
   * @param confirmed as que están dentro
   * @param pending as que o pediron e esperan
   */
  record Listed(
      OpenMatch match,
      UUID facilityId,
      UUID useId,
      @Nullable UUID partId,
      Instant start,
      Instant end,
      BookingStatus bookingStatus,
      int confirmed,
      int pending) {}

  /** Garda unha partida nova. */
  void insert(OpenMatch match);

  /**
   * Garda o cambio (versión + 1) e devolve a partida gardada.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  OpenMatch update(OpenMatch before, OpenMatch after);

  Optional<OpenMatch> find(UUID id);

  /** A partida co bloqueo da súa fila ata o fin da transacción. */
  Optional<OpenMatch> findLocked(UUID id);

  /** A partida desa reserva co bloqueo da súa fila, se a ten. */
  Optional<OpenMatch> findByBookingLocked(UUID bookingId);

  /** A partida coa súa reserva e as súas contas. */
  Optional<Listed> listed(UUID id);

  /** As partidas destas reservas, por reserva, nunha lectura. */
  Map<UUID, Listed> ofBookings(Collection<UUID> bookingIds);

  /**
   * As que se ven en público (ADR-0040, punto 9): abertas, sen ocultar, coa reserva confirmada, sen
   * empezar e con sitio, da que empeza antes; dunha instalación ou de todas.
   */
  PageResult<Listed> visible(Instant now, @Nullable UUID facilityId, int page, int size);

  /**
   * As partidas que organiza a conta ou nas que está ou pediu entrar (tamén rexeitada), que aínda
   * non remataron, da que empeza antes.
   */
  List<Listed> ofAccount(String subject, Instant now);

  /**
   * As candidatas da tarefa do prazo (ADR-0040, punto 7), sen bloqueos: abertas, sen ocultar, sen
   * comprobar, coa reserva confirmada e o prazo ({@code inicio − hours}) entre {@code from} e
   * {@code now}.
   *
   * @return os ids das partidas, como moito {@code limit}
   */
  List<UUID> deadlineDue(Instant now, Instant from, int hours, int limit);

  /**
   * Conservación (ADR-0040, punto 13, revisado o 2026-11-16): borra as partidas destas reservas
   * (coas súas persoas e solicitudes), no mesmo lote en que se anonimizan as reservas.
   *
   * @return cantas borrou
   */
  int purgeOfBookings(Collection<UUID> bookings);

  /** Garda quen se une; falso, sen escribir, se a conta xa está na partida. */
  boolean insert(OpenMatchPlayer player);

  /** A fila desa conta na partida, se a ten. */
  Optional<OpenMatchPlayer> player(UUID matchId, String subject);

  /** A fila dese id na partida, se existe. */
  Optional<OpenMatchPlayer> playerById(UUID matchId, UUID playerId);

  /** Todas as persoas da partida, por orde de chegada. */
  List<OpenMatchPlayer> players(UUID matchId);

  /** Acepta ou rexeita unha solicitude pendente; falso se xa non estaba pendente. */
  boolean decide(UUID matchId, UUID playerId, OpenMatchPlayerStatus status, Instant at);

  /** Saír ou retirar a solicitude: borra a fila desa conta; falso se non a tiña. */
  boolean leave(UUID matchId, String subject);

  /** Borra as solicitudes pendentes (ao pechar, cancelar ou empezar a partida). */
  int deletePending(UUID matchId);
}
