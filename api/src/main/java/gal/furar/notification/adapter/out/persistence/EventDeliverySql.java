// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.domain.EventMailDelivery;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.persistence.NativeSql;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Os envíos dos correos dos eventos (ADR-0038) en {@code notification_delivery}, co evento en
 * {@code municipal_event_id}. Sempre dentro dunha transacción (RLS) e co concello como parámetro
 * (A8b, {@code EventDeliverySqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class EventDeliverySql {

  private final JdbcClient jdbc;

  EventDeliverySql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  List<String> keys(UUID municipality, UUID event, EventMailDelivery.Kind kind) {
    return jdbc.sql(
            """
            select event_id
              from notification_delivery
             where municipal_event_id = :event and kind = :kind and municipality_id = :tenant
            """)
        .param("event", event)
        .param("kind", kind.name())
        .param("tenant", municipality)
        .query(String.class)
        .list();
  }

  /** Rexistra o correo; un por evento, tipo e clave (índice único parcial). */
  void insert(UUID municipality, EventMailDelivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, municipal_event_id, kind, event_id, recipient, language,
               status, attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :event, :kind, :key, :recipient, :language, :status,
                    1, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("event", d.eventId())
        .param("kind", d.kind().name())
        .param("key", d.key())
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .update();
  }

  private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
