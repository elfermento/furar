// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchPlayer;
import gal.furar.booking.domain.OpenMatchPlayerStatus;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** As partidas abertas no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class OpenMatchPersistenceAdapter extends TenantScopedPersistenceAdapter implements OpenMatches {

  private final OpenMatchSql matches;
  private final OpenMatchPlayerSql players;

  OpenMatchPersistenceAdapter(OpenMatchSql matches, OpenMatchPlayerSql players) {
    this.matches = matches;
    this.players = players;
  }

  @Override
  public void insert(OpenMatch match) {
    matches.insert(tenant().id(), match);
  }

  @Override
  public OpenMatch update(OpenMatch before, OpenMatch after) {
    UUID municipality = tenant().id();
    if (matches.update(municipality, before.version(), after) == 0) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return matches.find(municipality, after.id()).orElseThrow();
  }

  @Override
  public Optional<OpenMatch> find(UUID id) {
    return matches.find(tenant().id(), id);
  }

  @Override
  public Optional<OpenMatch> findLocked(UUID id) {
    return matches.findLocked(tenant().id(), id);
  }

  @Override
  public Optional<OpenMatch> findByBookingLocked(UUID bookingId) {
    return matches.findByBookingLocked(tenant().id(), bookingId);
  }

  @Override
  public Optional<Listed> listed(UUID id) {
    return matches.listed(tenant().id(), id);
  }

  @Override
  public Map<UUID, Listed> ofBookings(Collection<UUID> bookingIds) {
    UUID municipality = tenant().id();
    if (bookingIds.isEmpty()) {
      return Map.of();
    }
    return matches.ofBookings(municipality, bookingIds).stream()
        .collect(Collectors.toMap(l -> l.match().bookingId(), Function.identity()));
  }

  @Override
  public PageResult<Listed> visible(Instant now, @Nullable UUID facilityId, int page, int size) {
    UUID municipality = tenant().id();
    List<Listed> items = matches.visible(municipality, now, facilityId, size, (long) page * size);
    long total =
        page == 0 && items.size() < size
            ? items.size()
            : matches.countVisible(municipality, now, facilityId);
    return new PageResult<>(items, page, size, total);
  }

  @Override
  public List<Listed> ofAccount(String subject, Instant now) {
    return matches.ofAccount(tenant().id(), subject, now);
  }

  @Override
  public List<UUID> deadlineDue(Instant now, Instant from, int hours, int limit) {
    return matches.deadlineDue(tenant().id(), now, from, hours, limit);
  }

  @Override
  public int purgeOfBookings(Collection<UUID> bookings) {
    return matches.purgeOfBookings(tenant().id(), bookings);
  }

  @Override
  public boolean insert(OpenMatchPlayer player) {
    return players.insert(tenant().id(), player);
  }

  @Override
  public Optional<OpenMatchPlayer> player(UUID matchId, String subject) {
    return players.ofAccount(tenant().id(), matchId, subject);
  }

  @Override
  public Optional<OpenMatchPlayer> playerById(UUID matchId, UUID playerId) {
    return players.byId(tenant().id(), matchId, playerId);
  }

  @Override
  public List<OpenMatchPlayer> players(UUID matchId) {
    return players.ofMatch(tenant().id(), matchId);
  }

  @Override
  public boolean decide(UUID matchId, UUID playerId, OpenMatchPlayerStatus status, Instant at) {
    return players.decide(tenant().id(), matchId, playerId, status, at) > 0;
  }

  @Override
  public boolean leave(UUID matchId, String subject) {
    return players.leave(tenant().id(), matchId, subject) > 0;
  }

  @Override
  public int deletePending(UUID matchId) {
    return players.deletePending(tenant().id(), matchId);
  }
}
