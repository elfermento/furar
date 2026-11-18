// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.testsupport.Api;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A baixa dunha conta ({@code close_account}) cos datos da persoa en todos os módulos á vez:
 * preferencias de visualización, esperas, conta coñecida polo correo, persoas a cargo, valoracións,
 * incidencias coas súas fotos, inscricións en eventos futuros e pasados e partidas abertas. Cada
 * módulo redefine a función sobre a anterior: esta proba garda que a última conserva o de todas. Os
 * datos doutra persoa no mesmo concello quedan igual.
 */
class CloseAccountIT extends AbstractBookingIT {

  record Seeded(
      UUID booking, UUID dependent, UUID incident, UUID pastRegistration, UUID openMatch) {}

  long rows(String sql, Object key) {
    return jdbc.sql(sql).param("k", key).query(Long.class).single();
  }

  Seeded seed(Facility f, UUID use, String subject, Instant start) {
    Api.Response booked = reserve(person(subject), f, use, start);
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID booking = id(booked.json());
    UUID m = f.municipality();
    String mail = subject + "@example.org";

    jdbc.sql(
            "insert into account_email (account_subject, email, seen_at) values (:s, :e, now())"
                + " on conflict do nothing")
        .param("s", subject)
        .param("e", mail)
        .update();
    jdbc.sql(
            "insert into account_display_preference (account_subject, text_size, updated_at)"
                + " values (:s, 'x-large', now())")
        .param("s", subject)
        .update();
    UUID dependent = UUID.randomUUID();
    jdbc.sql(
            "insert into account_dependent (id, account_subject, name, birth_year, declared_at,"
                + " created_at, updated_at, version) values (:i, :s, 'Uxía', 2016, now(), now(),"
                + " now(), 0)")
        .param("i", dependent)
        .param("s", subject)
        .update();
    jdbc.sql(
            "insert into booking_wait (id, municipality_id, facility_id, use_id, slot_start,"
                + " slot_end, account_subject, email_key, language, ordinal, created_at) values"
                + " (:i, :m, :f, :u, :a, :b, :s, :e, 'gl', 1, now())")
        .param("i", UUID.randomUUID())
        .param("m", m)
        .param("f", f.id())
        .param("u", use)
        .param("a", java.sql.Timestamp.from(start.plus(1, ChronoUnit.HOURS)))
        .param("b", java.sql.Timestamp.from(start.plus(2, ChronoUnit.HOURS)))
        .param("s", subject)
        .param("e", mail)
        .update();
    jdbc.sql(
            "insert into booking_review (id, municipality_id, booking_id, facility_id, use_id,"
                + " stars, comment, created_at) values (:i, :m, :b, :f, :u, 4, 'Ben', now())")
        .param("i", UUID.randomUUID())
        .param("m", m)
        .param("b", booking)
        .param("f", f.id())
        .param("u", use)
        .update();

    UUID incident = UUID.randomUUID();
    jdbc.sql(
            "insert into incident (id, municipality_id, facility_id, type, description, status,"
                + " origin, reporter_subject, reporter_name, reporter_email, reporter_language,"
                + " created_at, updated_at, version) values (:i, :m, :f, 'BREAKDOWN', 'Rede rota',"
                + " 'NEW', 'RESIDENT', :s, 'Ana', :e, 'gl', now(), now(), 0)")
        .param("i", incident)
        .param("m", m)
        .param("f", f.id())
        .param("s", subject)
        .param("e", mail)
        .update();
    UUID photo = UUID.randomUUID();
    jdbc.sql(
            "insert into incident_photo (id, municipality_id, incident_id, position, width, height,"
                + " content_type, created_at) values (:p, :m, :i, 0, 10, 10, 'image/jpeg', now())")
        .param("p", photo)
        .param("m", m)
        .param("i", incident)
        .update();
    jdbc.sql(
            "insert into incident_photo_content (municipality_id, photo_id, variant, bytes)"
                + " values (:m, :p, 'LARGE', '\\x01'::bytea)")
        .param("m", m)
        .param("p", photo)
        .update();

    UUID future = event(m, Instant.now().plus(20, ChronoUnit.DAYS));
    UUID past = event(m, Instant.now().minus(20, ChronoUnit.DAYS));
    registration(m, future, subject, mail);
    UUID pastRegistration = registration(m, past, subject, mail);
    UUID openMatch = UUID.randomUUID();
    jdbc.sql(
            "insert into booking_open_match (id, municipality_id, booking_id, organizer_subject,"
                + " organizer_name, known_count, minimum, maximum, status, created_at, updated_at,"
                + " version) values (:i, :m, :b, :s, 'Ana', 2, 4, 10, 'OPEN', now(), now(), 0)")
        .param("i", openMatch)
        .param("m", m)
        .param("b", booking)
        .param("s", subject)
        .update();
    return new Seeded(booking, dependent, incident, pastRegistration, openMatch);
  }

  /** {@code subject} únese á partida aberta {@code match}. */
  UUID player(UUID m, UUID match, String subject) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "insert into booking_open_match_player (id, municipality_id, match_id,"
                + " account_subject, email, name, language, status, created_at, decided_at)"
                + " values (:i, :m, :match, :s, :e, 'Xan', 'gl', 'CONFIRMED', now(), now())")
        .param("i", id)
        .param("m", m)
        .param("match", match)
        .param("s", subject)
        .param("e", subject + "@example.org")
        .update();
    return id;
  }

  UUID event(UUID m, Instant startsAt) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "insert into event (id, municipality_id, name_gl, name_es, starts_at, ends_at, place,"
                + " price_cents, published, approval, created_at, updated_at, version) values (:i,"
                + " :m, 'Carreira', 'Carrera', :t, :t, 'Praza', 500, true, false, now(), now(), 0)")
        .param("i", id)
        .param("m", m)
        .param("t", java.sql.Timestamp.from(startsAt))
        .update();
    return id;
  }

  UUID registration(UUID m, UUID event, String subject, String mail) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "insert into event_registration (id, municipality_id, event_id, status,"
                + " person_subject, person_name, person_email, person_language, payment_status,"
                + " created_at, updated_at, version) values (:i, :m, :ev, 'CONFIRMED', :s, 'Ana',"
                + " :e, 'gl', 'PAID', now(), now(), 0)")
        .param("i", id)
        .param("m", m)
        .param("ev", event)
        .param("s", subject)
        .param("e", mail)
        .update();
    return id;
  }

  /** Cantas filas ten cada módulo para esta persoa (as súas claves, non as doutra). */
  long left(String subject, Seeded s) {
    return rows("select count(*) from account_email where account_subject = :k", subject)
        + rows(
            "select count(*) from account_display_preference where account_subject = :k", subject)
        + rows("select count(*) from account_dependent where account_subject = :k", subject)
        + rows("select count(*) from booking_wait where account_subject = :k", subject)
        + rows("select count(*) from booking_review where booking_id = :k", s.booking())
        + rows("select count(*) from incident where reporter_subject = :k", subject)
        + rows("select count(*) from incident_photo where incident_id = :k", s.incident())
        + rows("select count(*) from event_registration where person_subject = :k", subject)
        + rows("select count(*) from booking_open_match where organizer_subject = :k", subject);
  }

  /**
   * As entidades (ADR-0039, punto 18): a baixa quita a persoa dos responsables e a súa conta e o
   * seu correo das solicitudes de tempada; a entidade e a solicitude quedan.
   */
  @Test
  void closingAnAccountRemovesItAsOrganizationManagerAndRequester() {
    Facility f = facility("baixa-entidade", true);
    UUID use = whole(f, Map.of());
    UUID m = f.municipality();
    String gone = "baixa-" + UUID.randomUUID();
    String mail = gone + "@example.org";
    jdbc.sql("insert into account_email (account_subject, email, seen_at) values (:s, :e, now())")
        .param("s", gone)
        .param("e", mail)
        .update();
    UUID organization = UUID.randomUUID();
    jdbc.sql(
            "insert into booking_organization (id, municipality_id, name, email, created_at,"
                + " updated_at, version) values (:i, :m, 'Club Demo', 'club@example.org', now(),"
                + " now(), 0)")
        .param("i", organization)
        .param("m", m)
        .update();
    for (String manager : List.of(mail, "queda@example.org")) {
      jdbc.sql(
              "insert into booking_organization_manager (municipality_id, organization_id,"
                  + " email_key, email, added_at, added_by) values (:m, :o, :k, :k, now(), 'sub')")
          .param("m", m)
          .param("o", organization)
          .param("k", manager)
          .update();
    }
    UUID request = UUID.randomUUID();
    jdbc.sql(
            "insert into booking_series_request (id, municipality_id, organization_id,"
                + " facility_id, use_id, rental_mode, weekdays, start_minute, end_minute,"
                + " first_date, last_date, requested_by_subject, requested_by_email, status,"
                + " created_at, updated_at, version) values (:i, :m, :o, :f, :u, 'WHOLE', '{1}',"
                + " 600, 690, current_date + 7, current_date + 30, :s, :e, 'PENDING', now(),"
                + " now(), 0)")
        .param("i", request)
        .param("m", m)
        .param("o", organization)
        .param("f", f.id())
        .param("u", use)
        .param("s", gone)
        .param("e", mail)
        .update();

    jdbc.sql("select close_account(:s)").param("s", gone).query(Object.class).list();

    assertThat(
            jdbc.sql(
                    "select email_key from booking_organization_manager where organization_id = :k")
                .param("k", organization)
                .query(String.class)
                .list())
        .containsExactly("queda@example.org");
    assertThat(
            rows(
                "select count(*) from booking_series_request where id = :k"
                    + " and requested_by_subject is null and requested_by_email is null",
                request))
        .isEqualTo(1);
  }

  @Test
  void closingAnAccountRemovesItsDataFromEveryModuleAndLeavesOthersAlone() {
    Facility f = facility("baixa", true);
    UUID use = whole(f, Map.of());
    Instant start = at(tomorrow(), OPENS);
    String gone = "baixa-" + UUID.randomUUID();
    String stays = "queda-" + UUID.randomUUID();
    Seeded mine = seed(f, use, gone, start);
    Seeded other = seed(f, use, stays, start.plus(2, ChronoUnit.HOURS));
    assertThat(left(gone, mine)).isEqualTo(10);
    // Cada unha na partida da outra (ADR-0040).
    UUID goneInOther = player(f.municipality(), other.openMatch(), gone);
    UUID staysInMine = player(f.municipality(), mine.openMatch(), stays);
    long before = left(stays, other);

    jdbc.sql("select close_account(:s)").param("s", gone).query(Object.class).list();

    assertThat(left(gone, mine)).as("nada da persoa en ningún módulo").isZero();
    assertThat(left(stays, other)).as("a outra persoa, igual").isEqualTo(before);
    assertThat(rows("select count(*) from incident where id = :k", mine.incident()))
        .as("a incidencia queda, sen persoa")
        .isEqualTo(1);
    assertThat(
            rows(
                "select count(*) from event_registration where id = :k and anonymized_at is not"
                    + " null and payment_status = 'PAID'",
                mine.pastRegistration()))
        .as("a inscrición dun evento pasado queda sen persoa, co seu pago")
        .isEqualTo(1);
    assertThat(rows("select count(*) from account_dependent where id = :k", mine.dependent()))
        .as("a persoa a cargo sen reservas bórrase")
        .isZero();
    assertThat(rows("select count(*) from booking_open_match_player where id = :k", goneInOther))
        .as("sae das partidas doutras persoas")
        .isZero();
    assertThat(rows("select count(*) from booking_open_match_player where id = :k", staysInMine))
        .as("a partida que organizaba bórrase coas súas persoas, sen avisalas")
        .isZero();
    assertThat(rows("select count(*) from booking_open_match where id = :k", other.openMatch()))
        .as("a partida da outra persoa queda")
        .isEqualTo(1);
    assertThat(rows("select count(*) from booking where id = :k", mine.booking()))
        .as("a reserva segue o seu propio prazo de conservación")
        .isEqualTo(1);
  }
}
