// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.IncidentMailDelivery;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import gal.furar.notification.domain.OpenMatchDelivery;
import gal.furar.notification.domain.SeriesDelivery;
import gal.furar.notification.domain.WaitDelivery;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O rexistro de correos en SQL, como o resto das reservas (ADR-0025): unha fila por reserva, tipo e
 * evento, ou por invitación ao persoal (tipo {@code STAFF_INVITATION}). Sempre dentro dunha
 * transacción (RLS) e co concello como parámetro (A8b, {@code NotificationDeliverySqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class NotificationDeliverySql {

  /** O tipo das filas das invitacións (as das reservas levan un {@link MailKind}). */
  static final String STAFF_INVITATION = "STAFF_INVITATION";

  /** Os tipos dos correos dunha reserva (o último que saíu, no detalle do persoal). */
  static final List<String> BOOKING_KINDS =
      Arrays.stream(MailKind.values()).filter(MailKind::ofBooking).map(Enum::name).toList();

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

  /** O último correo da reserva que saíu (SENT): o tipo e cando. */
  Optional<SentDelivery> lastSent(UUID municipality, UUID booking) {
    return jdbc.sql(
            """
            select kind, sent_at
              from notification_delivery
             where booking_id = :booking and status = 'SENT' and kind in (:kinds)
               and municipality_id = :tenant
             order by sent_at desc
             limit 1
            """)
        .param("booking", booking)
        // Só os da reserva: nin a invitación nin os das partidas abertas (ADR-0040), que van a
        // quen organiza co id da súa reserva.
        .param("kinds", BOOKING_KINDS)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new SentDelivery(
                    MailKind.valueOf(rs.getString("kind")),
                    rs.getObject("sent_at", OffsetDateTime.class).toInstant()))
        .optional();
  }

  /** Un correo dunha reserva que saíu. */
  record SentDelivery(MailKind kind, Instant at) {}

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

  /** Os correos dese tipo e evento a esas esperas (ADR-0033, punto 7). */
  List<WaitDelivery> waitDeliveries(
      UUID municipality, MailKind kind, UUID release, Collection<UUID> waits) {
    if (waits.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select wait_id, event_id, recipient, language, status, attempts, error, sent_at
              from notification_delivery
             where wait_id in (:waits) and event_id = :event and kind = :kind
               and municipality_id = :tenant
            """)
        .param("waits", List.copyOf(waits))
        .param("event", release.toString())
        .param("kind", kind.name())
        .param("tenant", municipality)
        .query(
            (rs, n) -> {
              OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
              return new WaitDelivery(
                  kind,
                  rs.getObject("wait_id", UUID.class),
                  UUID.fromString(rs.getString("event_id")),
                  rs.getString("recipient"),
                  Language.fromCode(rs.getString("language")).orElseThrow(),
                  DeliveryStatus.valueOf(rs.getString("status")),
                  rs.getInt("attempts"),
                  rs.getString("error"),
                  sentAt == null ? null : sentAt.toInstant());
            })
        .list();
  }

  /** As esperas que recibiron un aviso (SENT) desde {@code since}, por calquera liberación. */
  List<UUID> waitsNoticedSince(UUID municipality, Collection<UUID> waits, Instant since) {
    if (waits.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select distinct wait_id
              from notification_delivery
             where wait_id in (:waits) and status = 'SENT' and sent_at >= :since
               and kind = :kind
               and municipality_id = :tenant
            """)
        .param("waits", List.copyOf(waits))
        .param("since", utc(since))
        .param("kind", MailKind.SLOT_RELEASED.name())
        .param("tenant", municipality)
        .query(UUID.class)
        .list();
  }

  void insert(UUID municipality, WaitDelivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, wait_id, kind, event_id, recipient, language, status,
               attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :wait, :kind, :event, :recipient, :language, :status,
                    :attempts, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("wait", d.waitId())
        .param("kind", d.kind().name())
        .param("event", d.releaseId().toString())
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("attempts", d.attempts())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .update();
  }

  /** Cambia o resultado dun aviso xa rexistrado; devolve as filas tocadas (0 ou 1). */
  int update(UUID municipality, WaitDelivery d, Instant now) {
    return jdbc.sql(
            """
            update notification_delivery
               set recipient = :recipient, language = :language, status = :status,
                   attempts = :attempts, error = :error, sent_at = :sentAt, updated_at = :now
             where wait_id = :wait and event_id = :event
               and municipality_id = :tenant
            """)
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("attempts", d.attempts())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .param("wait", d.waitId())
        .param("event", d.releaseId().toString())
        .param("tenant", municipality)
        .update();
  }

  /**
   * Conservación dos correos ás esperas (os dous tipos): os rexistrados antes de {@code cutoff}.
   */
  int deleteWaitNoticesBefore(UUID municipality, Instant cutoff) {
    return jdbc.sql(
            """
            delete from notification_delivery
             where wait_id is not null and created_at < :cutoff and municipality_id = :tenant
            """)
        .param("cutoff", utc(cutoff))
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

  /** As claves ({@code event_id}) dos correos dese tipo desa incidencia (ADR-0036). */
  List<String> incidentKeys(UUID municipality, UUID incident, IncidentMailDelivery.Kind kind) {
    return jdbc.sql(
            """
            select event_id
              from notification_delivery
             where incident_id = :incident and kind = :kind and municipality_id = :tenant
            """)
        .param("incident", incident)
        .param("kind", kind.name())
        .param("tenant", municipality)
        .query(String.class)
        .list();
  }

  /** Rexistra o correo dunha incidencia; un por incidencia, tipo e clave (índice único parcial). */
  void insert(UUID municipality, IncidentMailDelivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, incident_id, kind, event_id, recipient, language, status,
               attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :incident, :kind, :event, :recipient, :language, :status,
                    1, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("incident", d.incidentId())
        .param("kind", d.kind().name())
        .param("event", d.key())
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .update();
  }

  /** Borra os envíos destas incidencias; devolve cantos. */
  int deleteIncidents(UUID municipality, Collection<UUID> incidents) {
    return jdbc.sql(
            """
            delete from notification_delivery
             where incident_id in (:incidents) and municipality_id = :tenant
            """)
        .param("incidents", incidents)
        .param("tenant", municipality)
        .update();
  }

  /** Conservación: borra os envíos rexistrados antes de {@code cutoff}; devolve cantos. */
  /** Os correos dese tipo e publicación dunha partida aberta xa rexistrados (ADR-0040). */
  List<OpenMatchDelivery> openMatchDeliveries(UUID municipality, MailKind kind, String event) {
    return jdbc.sql(
            """
            select booking_id, open_match_player_id, event_id, recipient, language, status,
                   attempts, error, sent_at
              from notification_delivery
             where kind = :kind and event_id = :event
               and coalesce(open_match_player_id, booking_id) is not null
               and municipality_id = :tenant
            """)
        .param("kind", kind.name())
        .param("event", event)
        .param("tenant", municipality)
        .query(
            (rs, n) -> {
              OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
              return new OpenMatchDelivery(
                  kind,
                  rs.getObject("booking_id", UUID.class),
                  rs.getObject("open_match_player_id", UUID.class),
                  rs.getString("event_id"),
                  rs.getString("recipient"),
                  Language.fromCode(rs.getString("language")).orElseThrow(),
                  DeliveryStatus.valueOf(rs.getString("status")),
                  rs.getInt("attempts"),
                  rs.getString("error"),
                  sentAt == null ? null : sentAt.toInstant());
            })
        .list();
  }

  void insertOpenMatch(UUID municipality, OpenMatchDelivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, booking_id, open_match_player_id, kind, event_id, recipient,
               language, status, attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :booking, :player, :kind, :event, :recipient, :language,
                    :status, :attempts, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("booking", d.bookingId(), Types.OTHER)
        .param("player", d.playerId(), Types.OTHER)
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
  int updateOpenMatch(UUID municipality, OpenMatchDelivery d, Instant now) {
    return jdbc.sql(
            """
            update notification_delivery
               set recipient = :recipient, language = :language, status = :status,
                   attempts = :attempts, error = :error, sent_at = :sentAt, updated_at = :now
             where kind = :kind and event_id = :event
               and coalesce(open_match_player_id, booking_id) = :target
               and municipality_id = :tenant
            """)
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("attempts", d.attempts())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .param("kind", d.kind().name())
        .param("event", d.eventId())
        .param("target", d.target())
        .param("tenant", municipality)
        .update();
  }

  /** O correo dese tipo e publicación dunha tempada ou solicitude (ADR-0039). */
  Optional<SeriesDelivery> seriesDelivery(
      UUID municipality, MailKind kind, UUID target, String event) {
    return jdbc.sql(
            """
            select event_id, recipient, language, status, attempts, error, sent_at
              from notification_delivery
             where kind = :kind and event_id = :event
               and coalesce(series_id, series_request_id) = :target
               and municipality_id = :tenant
            """)
        .param("kind", kind.name())
        .param("event", event)
        .param("target", target)
        .param("tenant", municipality)
        .query(
            (rs, n) -> {
              OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
              return new SeriesDelivery(
                  kind,
                  target,
                  rs.getString("event_id"),
                  rs.getString("recipient"),
                  Language.fromCode(rs.getString("language")).orElseThrow(),
                  DeliveryStatus.valueOf(rs.getString("status")),
                  rs.getInt("attempts"),
                  rs.getString("error"),
                  sentAt == null ? null : sentAt.toInstant());
            })
        .optional();
  }

  void insertSeries(UUID municipality, SeriesDelivery d, Instant now) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, series_id, series_request_id, kind, event_id, recipient,
               language, status, attempts, error, sent_at, created_at, updated_at)
            values (:id, :tenant, :series, :request, :kind, :event, :recipient, :language,
                    :status, :attempts, :error, :sentAt, :now, :now)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("series", d.ofRequest() ? null : d.target(), Types.OTHER)
        .param("request", d.ofRequest() ? d.target() : null, Types.OTHER)
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

  /** Cambia o resultado dun correo dunha tempada xa rexistrado; devolve as filas tocadas. */
  int updateSeries(UUID municipality, SeriesDelivery d, Instant now) {
    return jdbc.sql(
            """
            update notification_delivery
               set recipient = :recipient, language = :language, status = :status,
                   attempts = :attempts, error = :error, sent_at = :sentAt, updated_at = :now
             where kind = :kind and event_id = :event
               and coalesce(series_id, series_request_id) = :target
               and municipality_id = :tenant
            """)
        .param("recipient", d.recipient())
        .param("language", d.language().code())
        .param("status", d.status().name())
        .param("attempts", d.attempts())
        .param("error", d.error(), Types.VARCHAR)
        .param("sentAt", utc(d.sentAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("now", utc(now))
        .param("kind", d.kind().name())
        .param("event", d.eventId())
        .param("target", d.target())
        .param("tenant", municipality)
        .update();
  }

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
