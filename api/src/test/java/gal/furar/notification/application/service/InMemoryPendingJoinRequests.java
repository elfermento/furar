// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.notification.application.port.out.PendingJoinRequests;
import gal.furar.notification.domain.PendingJoinRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** As solicitudes pendentes en memoria, para os tests sen base de datos. */
class InMemoryPendingJoinRequests implements PendingJoinRequests {

  final Map<UUID, PendingJoinRequest> rows = new LinkedHashMap<>();
  final Map<UUID, Instant> next = new HashMap<>();
  boolean broken;

  @Override
  public void save(PendingJoinRequest request, Instant at) {
    if (broken) {
      throw new IllegalStateException("database down");
    }
    rows.put(request.id(), request);
    next.put(request.id(), at);
  }

  @Override
  public List<PendingJoinRequest> due(Instant now, int limit) {
    return rows.values().stream()
        .filter(r -> !next.get(r.id()).isAfter(now))
        .sorted(Comparator.comparing(PendingJoinRequest::receivedAt))
        .limit(limit)
        .toList();
  }

  @Override
  public boolean claim(UUID id, Instant now, Instant at) {
    PendingJoinRequest row = rows.get(id);
    if (row == null || next.get(id).isAfter(now)) {
      return false;
    }
    rows.put(
        id,
        new PendingJoinRequest(
            id, row.request(), row.language(), row.receivedAt(), row.attempts() + 1));
    next.put(id, at);
    return true;
  }

  @Override
  public void delete(UUID id) {
    rows.remove(id);
    next.remove(id);
  }

  @Override
  public int purgeReceivedBefore(Instant cutoff) {
    List<UUID> old =
        rows.values().stream()
            .filter(r -> r.receivedAt().isBefore(cutoff))
            .map(PendingJoinRequest::id)
            .toList();
    old.forEach(this::delete);
    return old.size();
  }
}
