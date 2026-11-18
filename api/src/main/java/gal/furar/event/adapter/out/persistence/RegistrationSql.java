// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import gal.furar.event.domain.Dependent;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.Participant;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.event.domain.WaitlistNotice;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
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
 * As inscricións nos eventos en SQL, sen JPA, coma as reservas (ADR-0038): fóra de {@code
 * furar-export}, coa persoa en columnas propias. Desde ADR-0041, tamén a persoa a cargo, o ano de
 * nacemento e a lista de espera. Sempre dentro dunha transacción (RLS) e co concello como parámetro
 * (A8b, {@code RegistrationSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class RegistrationSql {

  private static final String COLUMNS =
      """
      id, event_id, status, person_subject, person_name, person_email, person_language,
      dependent_id, dependent_name, birth_year, birth_date, payment_status, decided_at, notified_at,
      exclusive_until, notice_mailed_at, covered_at, created_at, updated_at, version
      """;

  private final JdbcClient jdbc;

  RegistrationSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Bloquea a fila do evento ata o fin da transacción; falso se non existe. */
  boolean lockEvent(UUID municipality, UUID event) {
    return jdbc.sql(
            """
            select id
              from event
             where id = :event and municipality_id = :tenant
               for update
            """)
        .param("event", event)
        .param("tenant", municipality)
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  /** Por evento: confirmadas, pendentes, avisadas e esperando. */
  List<CountRow> counts(UUID municipality, Collection<UUID> events) {
    return jdbc.sql(
            """
            select event_id,
                   count(*) filter (where status = 'CONFIRMED') as confirmed,
                   count(*) filter (where status = 'PENDING') as pending,
                   count(*) filter (where status = 'NOTIFIED') as notified,
                   count(*) filter (where status = 'WAITING') as waiting
              from event_registration
             where event_id in (:events) and municipality_id = :tenant
             group by event_id
            """)
        .param("events", events)
        .param("tenant", municipality)
        .query(
            (rs, row) ->
                new CountRow(
                    rs.getObject("event_id", UUID.class),
                    rs.getInt("confirmed"),
                    rs.getInt("pending"),
                    rs.getInt("notified"),
                    rs.getInt("waiting")))
        .list();
  }

  Optional<Registration> find(UUID municipality, UUID id) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where id = :id and municipality_id = :tenant
                """)
        .param("id", id)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .optional();
  }

  /** A da conta para si. */
  Optional<Registration> findByPerson(UUID municipality, UUID event, String subject) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where event_id = :event and person_subject = :subject and dependent_id is null
                  and municipality_id = :tenant
                """)
        .param("event", event)
        .param("subject", subject)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .optional();
  }

  /** A da conta para esa persoa a cargo. */
  Optional<Registration> findByDependent(
      UUID municipality, UUID event, String subject, UUID dependent) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where event_id = :event and person_subject = :subject
                  and dependent_id = :dependent
                  and municipality_id = :tenant
                """)
        .param("event", event)
        .param("subject", subject)
        .param("dependent", dependent)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .optional();
  }

  /** As da conta nese evento: para si e para as súas persoas a cargo, pola orde de chegada. */
  List<Registration> listByAccount(UUID municipality, UUID event, String subject) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where event_id = :event and person_subject = :subject
                  and municipality_id = :tenant
                order by created_at, id
                """)
        .param("event", event)
        .param("subject", subject)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .list();
  }

  List<Registration> listForEvent(UUID municipality, UUID event) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where event_id = :event and municipality_id = :tenant
                order by created_at, id
                """)
        .param("event", event)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .list();
  }

  /** A lista de espera dun evento (esperando ou avisadas), pola orde de chegada. */
  List<Registration> waitlist(UUID municipality, UUID event) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where event_id = :event and status in ('WAITING', 'NOTIFIED')
                  and municipality_id = :tenant
                order by created_at, id
                """)
        .param("event", event)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .list();
  }

  List<Registration> listForPerson(UUID municipality, String subject) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where person_subject = :subject
                  and status in ('PENDING', 'CONFIRMED', 'WAITING', 'NOTIFIED')
                  and municipality_id = :tenant
                order by created_at, id
                """)
        .param("subject", subject)
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .list();
  }

  long countForEvent(UUID municipality, UUID event) {
    return jdbc.sql(
            """
            select count(*)
              from event_registration
             where event_id = :event and municipality_id = :tenant
            """)
        .param("event", event)
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  long countPendingFrom(UUID municipality, Instant now) {
    return jdbc.sql(
            """
            select count(*)
              from event_registration r
              join event e on e.id = r.event_id and e.municipality_id = r.municipality_id
             where r.status = 'PENDING' and e.cancelled_at is null and e.starts_at > :now
               and r.municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  /**
   * As esperas da conta (para si e as súas persoas a cargo) en eventos sen cancelar que aínda
   * admiten inscricións.
   */
  int countActiveWaits(UUID municipality, String subject, Instant now) {
    return jdbc.sql(
            """
            select count(*)
              from event_registration r
              join event e on e.id = r.event_id and e.municipality_id = r.municipality_id
             where r.person_subject = :subject and r.status in ('WAITING', 'NOTIFIED')
               and e.cancelled_at is null
               and coalesce(e.registration_deadline, e.ends_at) > :now
               and r.municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(Integer.class)
        .single();
  }

  /** Os avisos cuxo correo xa toca e aínda non saíu. */
  List<Registration> noticesToMail(UUID municipality, Instant now) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_registration
                where status = 'NOTIFIED' and notice_mailed_at is null and notified_at <= :now
                  and municipality_id = :tenant
                order by notified_at, id
                """)
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(RegistrationSql::registration)
        .list();
  }

  /** Os eventos abertos e sen cancelar con alguén na lista de espera. */
  List<UUID> eventsWithWaiting(UUID municipality, Instant now) {
    return jdbc.sql(
            """
            select distinct r.event_id
              from event_registration r
              join event e on e.id = r.event_id and e.municipality_id = r.municipality_id
             where r.status in ('WAITING', 'NOTIFIED') and e.cancelled_at is null and e.published
               and coalesce(e.registration_deadline, e.ends_at) > :now
               and r.municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(UUID.class)
        .list();
  }

  /**
   * Borra as esperas que non chegaron a praza en eventos que xa non admiten inscricións ou que se
   * cancelaron antes de {@code cancelledBefore} (o aviso de cancelación xa saíu).
   */
  int deleteClosedWaits(UUID municipality, Instant now, Instant cancelledBefore) {
    return jdbc.sql(
            """
            delete from event_registration r
             using event e
             where e.id = r.event_id and e.municipality_id = r.municipality_id
               and r.status in ('WAITING', 'NOTIFIED')
               and (e.cancelled_at < :cancelledBefore
                    or (e.cancelled_at is null
                        and coalesce(e.registration_deadline, e.ends_at) <= :now))
               and r.municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("cancelledBefore", utc(cancelledBefore))
        .param("tenant", municipality)
        .update();
  }

  /** O correo do aviso xa saíu (sen tocar a versión: non é un cambio da persoa nin do persoal). */
  int markNoticeMailed(UUID municipality, UUID id, Instant at) {
    return jdbc.sql(
            """
            update event_registration
               set notice_mailed_at = :at
             where id = :id and status = 'NOTIFIED' and notice_mailed_at is null
               and municipality_id = :tenant
            """)
        .param("at", utc(at))
        .param("id", id)
        .param("tenant", municipality)
        .update();
  }

  void insert(UUID municipality, Registration r) {
    Dependent dependent = r.dependent();
    WaitlistNotice notice = r.notice();
    jdbc.sql(
            """
            insert into event_registration
              (id, municipality_id, event_id, status, person_subject, person_name, person_email,
               person_language, dependent_id, dependent_name, birth_year, birth_date,
               payment_status,
               decided_at, notified_at, exclusive_until, notice_mailed_at, covered_at,
               created_at, updated_at, version)
            values (:id, :tenant, :event, :status, :subject, :name, :email, :language,
                    :dependent, :dependentName, :birthYear, :birthDate, :payment, :decidedAt, :notifiedAt,
                    :exclusiveUntil, :noticeMailed, :coveredAt, :createdAt, :updatedAt, 0)
            """)
        .param("id", r.id().value())
        .param("tenant", municipality)
        .param("event", r.eventId().value())
        .param("status", r.status().name())
        .param("subject", r.participant().subject(), Types.VARCHAR)
        .param("name", r.participant().name(), Types.VARCHAR)
        .param("email", r.participant().email(), Types.VARCHAR)
        .param("language", r.participant().language().code())
        .param("dependent", dependent == null ? null : dependent.id(), Types.OTHER)
        .param("dependentName", dependent == null ? null : dependent.name(), Types.VARCHAR)
        .param("birthYear", r.birthYear(), Types.SMALLINT)
        .param("birthDate", r.birthDate(), Types.DATE)
        .param("payment", r.payment() == null ? null : r.payment().name(), Types.VARCHAR)
        .param("decidedAt", utc(r.decidedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "notifiedAt",
            utc(notice == null ? null : notice.notifiedAt()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "exclusiveUntil",
            utc(notice == null ? null : notice.exclusiveUntil()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "noticeMailed",
            utc(notice == null ? null : notice.mailedAt()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param("coveredAt", utc(r.coveredAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("createdAt", utc(r.createdAt()))
        .param("updatedAt", utc(r.updatedAt()))
        .update();
  }

  /** Garda estado, pago, decisión e oferta se a versión é a lida; devolve as filas tocadas. */
  int update(UUID municipality, Registration r) {
    WaitlistNotice notice = r.notice();
    return jdbc.sql(
            """
            update event_registration
               set status = :status, payment_status = :payment, decided_at = :decidedAt,
                   notified_at = :notifiedAt, exclusive_until = :exclusiveUntil,
                   notice_mailed_at = :noticeMailed, covered_at = :coveredAt,
                   updated_at = :updatedAt,
                   version = version + 1
             where id = :id and version = :version and municipality_id = :tenant
            """)
        .param("status", r.status().name())
        .param("payment", r.payment() == null ? null : r.payment().name(), Types.VARCHAR)
        .param("decidedAt", utc(r.decidedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "notifiedAt",
            utc(notice == null ? null : notice.notifiedAt()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "exclusiveUntil",
            utc(notice == null ? null : notice.exclusiveUntil()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "noticeMailed",
            utc(notice == null ? null : notice.mailedAt()),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .param("coveredAt", utc(r.coveredAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("updatedAt", utc(r.updatedAt()))
        .param("id", r.id().value())
        .param("version", r.version())
        .param("tenant", municipality)
        .update();
  }

  int delete(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            delete from event_registration
             where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Quita a persoa das inscricións dos eventos que remataron antes do corte (un evento dun día,
   * cando empezou; unha actividade, a última sesión).
   */
  int anonymizeEndedBefore(UUID municipality, Instant cutoff, Instant now) {
    return jdbc.sql(
            """
            update event_registration r
               set person_subject = null, person_name = null, person_email = null,
                   dependent_id = null, dependent_name = null, birth_year = null,
                   birth_date = null,
                   anonymized_at = :now, updated_at = :now, version = r.version + 1
              from event e
             where e.id = r.event_id and e.municipality_id = r.municipality_id
               and e.ends_at < :cutoff and r.anonymized_at is null
               and r.municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("cutoff", utc(cutoff))
        .param("tenant", municipality)
        .update();
  }

  private static Registration registration(ResultSet rs, int row) throws SQLException {
    String payment = rs.getString("payment_status");
    UUID dependentId = rs.getObject("dependent_id", UUID.class);
    String dependentName = rs.getString("dependent_name");
    int birthYear = rs.getInt("birth_year");
    boolean noBirthYear = rs.wasNull();
    Instant notifiedAt = nullableInstant(rs, "notified_at");
    Instant exclusiveUntil = nullableInstant(rs, "exclusive_until");
    return new Registration(
        new RegistrationId(rs.getObject("id", UUID.class)),
        new EventId(rs.getObject("event_id", UUID.class)),
        RegistrationStatus.valueOf(rs.getString("status")),
        new Participant(
            rs.getString("person_subject"),
            rs.getString("person_name"),
            rs.getString("person_email"),
            Language.fromCode(rs.getString("person_language")).orElse(Language.GL)),
        dependentId == null || dependentName == null
            ? null
            : new Dependent(dependentId, dependentName),
        noBirthYear ? null : birthYear,
        rs.getObject("birth_date", LocalDate.class),
        payment == null ? null : PaymentStatus.valueOf(payment),
        nullableInstant(rs, "decided_at"),
        notifiedAt == null || exclusiveUntil == null
            ? null
            : new WaitlistNotice(
                notifiedAt, exclusiveUntil, nullableInstant(rs, "notice_mailed_at")),
        nullableInstant(rs, "covered_at"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
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

  /** Confirmadas, pendentes, avisadas e esperando dun evento. */
  record CountRow(UUID eventId, int confirmed, int pending, int notified, int waiting) {}
}
