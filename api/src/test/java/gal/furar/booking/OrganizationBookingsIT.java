// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A persoa responsable dunha entidade (ADR-0039, punto 10): ve e cancela as reservas da entidade,
 * reserva no seu nome co límite do uso para a entidade e sen usos por prazas; quen non é
 * responsable non ve nada e unha conta de traballo non actúa como entidade.
 */
class OrganizationBookingsIT extends AbstractSeasonIT {

  @Test
  void theManagerSeesCancelsAndBooksAsTheOrganization() {
    Facility f = facility("entidade-resp", true);
    // Unha reserva por día e persoa: para a entidade conta coma unha persoa.
    UUID use = whole(f, Map.of("rules", Map.of("capCount", 1, "capPeriod", "DAY")));
    String bea = "bea-" + UUID.randomUUID();
    UUID club = organization(f, "Club Demo Baloncesto", email(bea));
    LocalDate friday = nextWorking(5);
    UUID seriesId =
        created(f, schedule(club, f, use, null, List.of(5), 600, 690, friday, friday.plusDays(7)));
    String token = person(bea);

    Api.Response mine = api().get(myOrganizations(f.slug())).token(token).send();
    assertThat(mine.json()).hasSize(1);
    assertThat(mine.json().get(0).get("name").asString()).isEqualTo("Club Demo Baloncesto");
    JsonNode items = organizationBookings(token, f, club);
    assertThat(items).hasSize(2);
    JsonNode first = items.get(0);
    assertThat(first.get("organization").get("name").asString()).isEqualTo("Club Demo Baloncesto");
    assertThat(first.get("organization").get("series").get("weekdays").toString()).isEqualTo("[5]");
    assertThat(first.get("canCancel").asBoolean()).isTrue();
    assertThat(first.get("canReview").asBoolean()).isFalse();
    // Non saen nas súas propias reservas.
    assertThat(api().get(mine(f.slug())).token(token).send().json().get("items")).isEmpty();

    // Cancelar unha sesión: queda a persoa como quen cancelou.
    UUID session = id(first);
    Api.Response cancelled =
        api()
            .post(myOrganizations(f.slug()) + "/" + club + "/bookings/" + session + "/cancel", null)
            .ifMatch(first.get("version").asLong())
            .token(token)
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(cancelled.json().get("status").asString()).isEqualTo("CANCELLED_BY_PERSON");
    assertThat(
            jdbc.sql("select cancelled_by from booking where id = :id")
                .param("id", session)
                .query(String.class)
                .single())
        .isEqualTo(bea);
    assertThat(sessions(seriesId, "CONFIRMED")).isEqualTo(1);

    // Reservar como entidade: as sesións non contan no límite; a segunda do día, si.
    LocalDate saturday = friday.plusDays(1);
    Api.Response extra = reserveAs(token, f, club, use, at(saturday, 600));
    assertThat(extra.status()).as(extra.toString()).isEqualTo(201);
    assertThat(extra.header("Location")).contains("/me/organizations/" + club + "/bookings/");
    assertThat(extra.json().get("organization").get("id").asString()).isEqualTo(club.toString());
    assertThat(extra.json().get("organization").get("series").isNull()).isTrue();
    assertThat(extra.json().get("organization").get("seriesId").isNull()).isTrue();
    assertThat(problem(reserveAs(token, f, club, use, at(saturday, 720))))
        .isEqualTo("422 booking.cap-reached");
    // A persoa, polo seu lado, conserva o seu límite.
    assertThat(reserve(token, f, use, at(saturday, 720)).status()).isEqualTo(201);
    UUID extraId = id(extra.json());
    assertThat(
            jdbc.sql(
                    "select holder_email is null and holder_subject is null and booked_by = :b"
                        + " from booking where id = :id")
                .param("b", bea)
                .param("id", extraId)
                .query(Boolean.class)
                .single())
        .isTrue();
    // O persoal non lle cambia o titular.
    JsonNode detail = detail(TestTokens.manager(f.slug()), f.slug(), extraId);
    assertThat(detail.get("bookedBy").isNull()).isTrue();
    assertThat(detail.get("organization").get("name").asString()).isEqualTo("Club Demo Baloncesto");
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("useId", use);
    change.put("start", at(saturday, 600).toString());
    change.put("slots", 1);
    change.put("holder", Map.of("name", "Ana Rey", "email", "ana@example.org"));
    Api.Response holder =
        api()
            .put(bookings(f.slug()) + "/" + extraId, change)
            .ifMatch(detail.get("version").asLong())
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(problem(holder)).isEqualTo("422 booking.holder-not-editable");
  }

  @Test
  void othersSeeNothingAndTheWrongKindsOfBookingAreRefused() {
    Facility f = facility("entidade-non", true);
    UUID use = whole(f, Map.of());
    UUID seats = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 10)));
    String bea = "bea-" + UUID.randomUUID();
    UUID club = organization(f, "Club Demo Patinaxe", email(bea));
    String token = person(bea);
    LocalDate day = nextWorking(4);

    // Quen non é responsable: 404, coma se non existise.
    String xan = person("xan-" + UUID.randomUUID());
    assertThat(api().get(myOrganizations(f.slug())).token(xan).send().json()).isEmpty();
    assertThat(
            problem(
                api().get(myOrganizations(f.slug()) + "/" + club + "/bookings").token(xan).send()))
        .isEqualTo("404 booking.organization-not-found");
    assertThat(problem(reserveAs(xan, f, club, use, at(day, 600))))
        .isEqualTo("404 booking.organization-not-found");
    // Usos por prazas, non.
    assertThat(problem(reserveAs(token, f, club, seats, at(day, 600))))
        .isEqualTo("422 booking.organization-seats-use");
    // Bloqueada por faltas a persoa responsable, tampouco no nome da entidade.
    blockByNoShows(f, email(bea));
    assertThat(problem(reserveAs(token, f, club, use, at(day, 600))))
        .isEqualTo("403 booking.blocked-no-show");

    // Unha conta de traballo non actúa como entidade.
    String worker = "traballo-" + UUID.randomUUID();
    UUID other = organization(f, "Club Traballo", email(worker));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at, version)
            values (:id, :m, :email, 'Persoa de proba', :subject, 'STAFF', 'ACTIVE', 'gl',
                    'sub-semente', :now, :now, :now, :now, 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", f.municipality())
        .param("email", email(worker))
        .param("subject", worker)
        .param("now", now)
        .update();
    assertThat(
            problem(
                api()
                    .get(myOrganizations(f.slug()) + "/" + other + "/bookings")
                    .token(person(worker))
                    .send()))
        .isEqualTo("403 booking.staff-account");
  }

  @Test
  void anOrganizationArchivedMeanwhileIsNotFoundNotAnError() {
    Facility f = facility("entidade-arquivada", true);
    UUID use = whole(f, Map.of());
    String bea = "bea-" + UUID.randomUUID();
    UUID club = organization(f, "Club Efémero", email(bea));
    jdbc.sql(
            "update booking_organization set archived_at = now(), email = null,"
                + " contact_name = null, phone = null where id = :id")
        .param("id", club)
        .update();

    assertThat(problem(reserveAs(person(bea), f, club, use, at(next(2), 600))))
        .isEqualTo("404 booking.organization-not-found");
  }
}
