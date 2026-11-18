// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.out.persistence;

import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentOrigin;
import gal.furar.incident.domain.IncidentPhoto;
import gal.furar.incident.domain.IncidentStatus;
import gal.furar.incident.domain.IncidentType;
import gal.furar.incident.domain.Reporter;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As incidencias en SQL, sen JPA, coma as reservas (ADR-0036): fóra de {@code furar-export}, coa
 * persoa en columnas propias. Sempre dentro dunha transacción (RLS) e co concello como parámetro
 * (A8b, {@code IncidentSqlTenantIT}). As fotos sen os bytes en {@code incident_photo}; os bytes, en
 * {@code incident_photo_content}.
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class IncidentSql {

  static final String LARGE = "LARGE";
  static final String THUMB = "THUMB";

  private static final String COLUMNS =
      """
      id, facility_id, booking_id, type, description, status, origin, reporter_subject,
      reporter_name, reporter_email, reporter_language, internal_note, resolution_message,
      resolved_at, photos_removed_at, anonymized_at, created_at, updated_at, version
      """;

  private final JdbcClient jdbc;

  IncidentSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(UUID municipality, Incident i) {
    jdbc.sql(
            """
            insert into incident
              (id, municipality_id, facility_id, booking_id, type, description, status, origin,
               reporter_subject, reporter_name, reporter_email, reporter_language, internal_note,
               resolution_message, resolved_at, photos_removed_at, anonymized_at, created_at,
               updated_at, version)
            values (:id, :tenant, :facility, :booking, :type, :description, :status, :origin,
                    :subject, :name, :email, :language, :note, :message, :resolvedAt,
                    :photosRemovedAt, :anonymizedAt, :createdAt, :updatedAt, 0)
            """)
        .param("id", i.id().value())
        .param("tenant", municipality)
        .param("facility", i.facilityId())
        .param("booking", i.bookingId(), Types.OTHER)
        .param("type", i.type().name())
        .param("description", i.description())
        .param("status", i.status().name())
        .param("origin", i.origin().name())
        .param("subject", i.reporter().subject(), Types.VARCHAR)
        .param("name", i.reporter().name(), Types.VARCHAR)
        .param("email", i.reporter().email(), Types.VARCHAR)
        .param("language", i.reporter().language().code())
        .param("note", i.internalNote(), Types.VARCHAR)
        .param("message", i.resolutionMessage(), Types.VARCHAR)
        .param("resolvedAt", utc(i.resolvedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("photosRemovedAt", utc(i.photosRemovedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("anonymizedAt", utc(i.anonymizedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("createdAt", utc(i.createdAt()))
        .param("updatedAt", utc(i.updatedAt()))
        .update();
  }

  void insertPhoto(
      UUID municipality, UUID incident, IncidentPhoto p, String contentType, Instant now) {
    jdbc.sql(
            """
            insert into incident_photo
              (id, municipality_id, incident_id, position, width, height, content_type, created_at)
            values (:id, :tenant, :incident, :position, :width, :height, :contentType, :now)
            """)
        .param("id", p.id())
        .param("tenant", municipality)
        .param("incident", incident)
        .param("position", p.position())
        .param("width", p.width())
        .param("height", p.height())
        .param("contentType", contentType)
        .param("now", utc(now))
        .update();
  }

  void insertContent(UUID municipality, UUID photo, String variant, byte[] bytes) {
    jdbc.sql(
            """
            insert into incident_photo_content (municipality_id, photo_id, variant, bytes)
            values (:tenant, :photo, :variant, :bytes)
            """)
        .param("tenant", municipality)
        .param("photo", photo)
        .param("variant", variant)
        .param("bytes", bytes)
        .update();
  }

  /** Garda os campos que cambia o persoal se a versión é a lida; devolve as filas tocadas. */
  int update(UUID municipality, Incident i) {
    return jdbc.sql(
            """
            update incident
               set status = :status, internal_note = :note, resolution_message = :message,
                   resolved_at = :resolvedAt, updated_at = :updatedAt, version = version + 1
             where id = :id and version = :version and municipality_id = :tenant
            """)
        .param("status", i.status().name())
        .param("note", i.internalNote(), Types.VARCHAR)
        .param("message", i.resolutionMessage(), Types.VARCHAR)
        .param("resolvedAt", utc(i.resolvedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("updatedAt", utc(i.updatedAt()))
        .param("id", i.id().value())
        .param("version", i.version())
        .param("tenant", municipality)
        .update();
  }

  /** A incidencia sen as fotos (van á parte, {@link #photos}). */
  Optional<Incident> find(UUID municipality, UUID id) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from incident
                where id = :id and municipality_id = :tenant
                """)
        .param("id", id)
        .param("tenant", municipality)
        .query(IncidentSql::incident)
        .optional();
  }

  List<Incident> recent(UUID municipality, int limit) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from incident
                where municipality_id = :tenant
                order by created_at desc, id desc
                limit :limit
                """)
        .param("tenant", municipality)
        .param("limit", limit)
        .query(IncidentSql::incident)
        .list();
  }

  /** As fotos destas incidencias, por incidencia e orde. */
  List<PhotoRow> photos(UUID municipality, Collection<UUID> incidents) {
    return jdbc.sql(
            """
            select incident_id, id, position, width, height
              from incident_photo
             where incident_id in (:incidents) and municipality_id = :tenant
             order by incident_id, position
            """)
        .param("incidents", incidents)
        .param("tenant", municipality)
        .query(
            (rs, row) ->
                new PhotoRow(
                    rs.getObject("incident_id", UUID.class),
                    new IncidentPhoto(
                        rs.getObject("id", UUID.class),
                        rs.getInt("position"),
                        rs.getInt("width"),
                        rs.getInt("height"))))
        .list();
  }

  long countByStatus(UUID municipality, String status) {
    return jdbc.sql(
            """
            select count(*)
              from incident
             where status = :status and municipality_id = :tenant
            """)
        .param("status", status)
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  long countReportedSince(UUID municipality, String subject, Instant since) {
    return jdbc.sql(
            """
            select count(*)
              from incident
             where reporter_subject = :subject and created_at >= :since
               and municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("since", utc(since))
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  Optional<PhotoData> photoContent(UUID municipality, UUID incident, UUID photo, String variant) {
    return jdbc.sql(
            """
            select c.bytes, p.content_type
              from incident_photo_content c
              join incident_photo p on p.id = c.photo_id and p.municipality_id = c.municipality_id
             where c.photo_id = :photo and c.variant = :variant and p.incident_id = :incident
               and c.municipality_id = :tenant
            """)
        .param("photo", photo)
        .param("variant", variant)
        .param("incident", incident)
        .param("tenant", municipality)
        .query((rs, row) -> new PhotoData(rs.getBytes("bytes"), rs.getString("content_type")))
        .optional();
  }

  /** Borra unha foto desa incidencia (os bytes van en cascada); devolve cantas. */
  int deletePhoto(UUID municipality, UUID incident, UUID photo) {
    return jdbc.sql(
            """
            delete from incident_photo
             where id = :photo and incident_id = :incident and municipality_id = :tenant
            """)
        .param("photo", photo)
        .param("incident", incident)
        .param("tenant", municipality)
        .update();
  }

  /** Sobe a versión despois de quitar unha foto (o detalle aberto noutro sitio queda vello). */
  int touch(UUID municipality, UUID incident, Instant now) {
    return jdbc.sql(
            """
            update incident
               set updated_at = :now, version = version + 1
             where id = :incident and municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("incident", incident)
        .param("tenant", municipality)
        .update();
  }

  /** Borra a incidencia (as fotos e os bytes van en cascada); devolve cantas. */
  int delete(UUID municipality, UUID incident) {
    return jdbc.sql(
            """
            delete from incident
             where id = :incident and municipality_id = :tenant
            """)
        .param("incident", incident)
        .param("tenant", municipality)
        .update();
  }

  private static Incident incident(ResultSet rs, int row) throws SQLException {
    return new Incident(
        new IncidentId(rs.getObject("id", UUID.class)),
        rs.getObject("facility_id", UUID.class),
        rs.getObject("booking_id", UUID.class),
        IncidentType.valueOf(rs.getString("type")),
        rs.getString("description"),
        IncidentStatus.valueOf(rs.getString("status")),
        IncidentOrigin.valueOf(rs.getString("origin")),
        new Reporter(
            rs.getString("reporter_subject"),
            rs.getString("reporter_name"),
            rs.getString("reporter_email"),
            Language.fromCode(rs.getString("reporter_language")).orElse(Language.GL)),
        rs.getString("internal_note"),
        rs.getString("resolution_message"),
        List.of(),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        nullableInstant(rs, "resolved_at"),
        nullableInstant(rs, "photos_removed_at"),
        nullableInstant(rs, "anonymized_at"),
        rs.getLong("version"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  private static @Nullable Instant nullableInstant(ResultSet rs, String column)
      throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  /** Unha foto e a súa incidencia. */
  record PhotoRow(UUID incidentId, IncidentPhoto photo) {}

  /** Os bytes dunha variante e o seu tipo. */
  record PhotoData(byte[] bytes, String contentType) {}
}
