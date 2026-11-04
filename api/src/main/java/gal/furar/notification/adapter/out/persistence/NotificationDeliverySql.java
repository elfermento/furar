// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O rexistro de correos en SQL, como o resto das reservas (ADR-0025): unha fila por reserva, tipo e
 * evento, ou por invitación ao persoal (tipo {@code STAFF_INVITATION}, plan do dominio 4, §2.1).
 * Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * NotificationDeliverySqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class NotificationDeliverySql {

  /** O tipo das filas das invitacións (as das reservas levan un {@link MailKind}). */
  static final String STAFF_INVITATION = "STAFF_INVITATION";

  private final JdbcClient jdbc;

  NotificationDeliverySql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Delivery> find(UUID municipality, UUID booking, MailKind kind, String eventId) {
    return jdbc.sql(
            """
            select booking_id, kind, event_id, recipient, language, status, attempts, error,
                   sent_at
              from notification_delivery
             where booking_id = :booking and kind = :kind and event_id = :event
               and municipality_id = :tenant
            """)
        .param("booking", booking)
        .param("kind", kind.name())
        .param("event", eventId)
        .param("tenant", municipality)
        .query(NotificationDeliverySql::delivery)
        .optional();
  }

  void insert(UUID municipality, Delivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, booking_id, kind, event_id, recipient, language, status,
               attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :booking, :kind, :event, :recipient, :language, :status,
                    :attempts, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("booking", d.bookingId())
        .param("kind", d.kind().name())
        .param("event", d.eventId())
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("attempts", d.attempts())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .update();
  }

  /** Cambia o resultado dun correo xa rexistrado; devolve as filas tocadas (0 ou 1). */
  int update(UUID municipality, Delivery d, Instant now) {
    return jdbc.sql(
            """
            update notification_delivery
               set recipient = :recipient, language = :language, status = :status,
                   attempts = :attempts, error = :error, sent_at = :sentAt, updated_at = :now
             where booking_id = :booking and kind = :kind and event_id = :event
               and municipality_id = :tenant
            """)
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("attempts", d.attempts())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .param("booking", d.bookingId())
        .param("kind", d.kind().name())
        .param("event", d.eventId())
        .param("tenant", municipality)
        .update();
  }

  /** O resultado do correo dunha invitación ao persoal. */
  Optional<DeliveryStatus> invitationStatus(UUID municipality, UUID invitation) {
    return jdbc.sql(
            """
            select status
              from notification_delivery
             where invitation_id = :invitation and kind = :kind and event_id = :event
               and municipality_id = :tenant
            """)
        .param("invitation", invitation)
        .param("kind", STAFF_INVITATION)
        .param("event", InvitationMailDelivery.ONCE)
        .param("tenant", municipality)
        .query((rs, row) -> DeliveryStatus.valueOf(rs.getString("status")))
        .optional();
  }

  /** Rexistra o correo dunha invitación; só hai un por invitación (índice único parcial). */
  void insert(UUID municipality, InvitationMailDelivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, invitation_id, kind, event_id, recipient, language, status,
               attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :invitation, :kind, :event, :recipient, :language, :status,
                    1, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("invitation", d.invitationId())
        .param("kind", STAFF_INVITATION)
        .param("event", InvitationMailDelivery.ONCE)
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .update();
  }

  /** Borra os envíos destas invitacións (o seu destinatario); devolve cantos. */
  int deleteInvitations(UUID municipality, Collection<UUID> invitations) {
    return jdbc.sql(
            """
            delete from notification_delivery
             where invitation_id in (:invitations) and municipality_id = :tenant
            """)
        .param("invitations", invitations)
        .param("tenant", municipality)
        .update();
  }

  /** Conservación: borra os envíos rexistrados antes de {@code cutoff}; devolve cantos. */
  int deleteCreatedBefore(UUID municipality, Instant cutoff) {
    return jdbc.sql(
            """
            delete from notification_delivery
             where created_at < :cutoff and municipality_id = :tenant
            """)
        .param("cutoff", utc(cutoff))
        .param("tenant", municipality)
        .update();
  }

  private static Delivery delivery(ResultSet rs, int row) throws SQLException {
    OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
    return new Delivery(
        rs.getObject("booking_id", UUID.class),
        MailKind.valueOf(rs.getString("kind")),
        rs.getString("event_id"),
        rs.getString("recipient"),
        Language.fromCode(rs.getString("language")).orElseThrow(),
        DeliveryStatus.valueOf(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getString("error"),
        sentAt == null ? null : sentAt.toInstant());
  }

  private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
