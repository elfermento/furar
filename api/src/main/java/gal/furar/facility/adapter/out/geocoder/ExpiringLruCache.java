// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Caché LRU en memoria con caducidade (ADR-0023: 24 h). Os datos son públicos (rueiro do IGN) e
 * pódense volver pedir, así que non vai á base. Só garda respostas boas: un fallo do buscador non
 * se garda e a seguinte consulta volve saír.
 */
final class ExpiringLruCache<K, V> {

  private record Slot<V>(V value, Instant expires) {}

  private final int capacity;
  private final Duration ttl;
  private final Clock clock;
  private final LinkedHashMap<K, Slot<V>> entries;

  ExpiringLruCache(int capacity, Duration ttl, Clock clock) {
    this.capacity = capacity;
    this.ttl = ttl;
    this.clock = clock;
    this.entries =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, Slot<V>> eldest) {
            return size() > ExpiringLruCache.this.capacity;
          }
        };
  }

  /** Valor gardado e vixente, ou o que dea {@code load} (que se garda se non lanza). */
  V get(K key, Supplier<V> load) {
    Optional<V> cached = cached(key);
    if (cached.isPresent()) {
      return cached.get();
    }
    V loaded = load.get();
    put(key, loaded);
    return loaded;
  }

  synchronized Optional<V> cached(K key) {
    Slot<V> entry = entries.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (!clock.instant().isBefore(entry.expires())) {
      entries.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.value());
  }

  synchronized void put(K key, V value) {
    entries.put(key, new Slot<>(value, clock.instant().plus(ttl)));
  }

  synchronized int size() {
    return entries.size();
  }
}
