// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.shared.security.InternalCallKey;
import gal.furar.testsupport.Api;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalManagementPort;
import tools.jackson.databind.JsonNode;

/**
 * Eliminar a propia conta (ADR-0043), de punta a punta na API e cunha base real.
 *
 * <ul>
 *   <li>O que impide eliminala (unha reserva futura, ser persoal) sae igual na web ({@code GET
 *       /api/v1/me/account-deletion}) e para Keycloak ({@code /actuator/accountdeletion});
 *       cancelado, xa se pode.
 *   <li>Cando Keycloak avisa ({@code /actuator/accountclosed}): {@code close_account}, as reservas
 *       sen pago anonimizadas xa, as de pago intactas ata o seu prazo e un evento no rexistro do
 *       concello só coas cifras. Unha segunda vez non fai nada nin deixa outro evento.
 *   <li>Sen a clave do seu propósito, 403 e nada.
 * </ul>
 */
class AccountClosureIT extends AbstractBookingIT {

  static final String ME = "/api/v1/me/account-deletion";

  @LocalManagementPort int managementPort;

  @Value("${furar.invitation.proof-secret}")
  String sharedSecret;

  String key() {
    return InternalCallKey.derive(sharedSecret, InternalCallKey.ACCOUNT_CLOSURE).value();
  }

  @Test
  void whatBlocksTheDeletionIsTheSameForTheWebAndForKeycloak() throws Exception {
    Facility f = facility("baixa-web", true);
    UUID use = whole(f, Map.of());
    String subject = "baixa-" + UUID.randomUUID();
    String token = person(subject);

    JsonNode free = api().get(ME).token(token).send().json();
    assertThat(free.get("allowed").asBoolean()).isTrue();
    assertThat(free.get("blockers")).isEmpty();
    assertThat(internal("accountdeletion", key(), subject)).isEqualTo("200 {\"allowed\":true}");

    Api.Response booked = reserve(token, f, use, at(tomorrow(), OPENS));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID booking = id(booked.json());

    JsonNode blocked = api().get(ME).token(token).send().json();
    assertThat(blocked.get("allowed").asBoolean()).isFalse();
    JsonNode first = blocked.get("blockers").get(0);
    assertThat(first.get("kind").asString()).isEqualTo("BOOKING");
    assertThat(first.get("municipality").asString()).isEqualTo(f.slug());
    assertThat(first.get("count").asLong()).isEqualTo(1);
    assertThat(internal("accountdeletion", key(), subject)).isEqualTo("200 {\"allowed\":false}");

    jdbc.sql(
            "update booking set status = 'CANCELLED_BY_PERSON', cancelled_at = now(),"
                + " cancelled_by = holder_subject, version = version + 1 where id = :b")
        .param("b", booking)
        .update();
    assertThat(api().get(ME).token(token).send().json().get("allowed").asBoolean()).isTrue();

    staff(f.municipality(), subject);
    JsonNode staff = api().get(ME).token(token).send().json();
    assertThat(staff.get("allowed").asBoolean()).isFalse();
    assertThat(staff.get("blockers").get(0).get("kind").asString()).isEqualTo("STAFF");
    assertThat(internal("accountdeletion", key(), subject)).isEqualTo("200 {\"allowed\":false}");

    assertThat(api().get(ME).send().status()).isEqualTo(401);
  }

  @Test
  void closingCleansAnonymisesTheUnpaidKeepsThePaidAndLeavesOnlyFigures() throws Exception {
    Facility f = facility("baixa-api", true);
    UUID use = whole(f, Map.of());
    String subject = "baixa-" + UUID.randomUUID();
    String token = person(subject);
    Instant start = at(tomorrow(), OPENS);
    UUID unpaid = id(reserve(token, f, use, start).json());
    UUID paid = id(reserve(token, f, use, start.plus(2, ChronoUnit.HOURS)).json());
    jdbc.sql(
            "update booking set price_kind = 'PAID', amount_cents = 500,"
                + " payment_status = 'PENDING' where id = :b")
        .param("b", paid)
        .update();
    jdbc.sql(
            "insert into account_display_preference (account_subject, text_size, updated_at)"
                + " values (:s, 'large', now())")
        .param("s", subject)
        .update();
    jdbc.sql(
            "insert into booking_review (id, municipality_id, booking_id, facility_id, use_id,"
                + " stars, comment, created_at) values (:i, :m, :b, :f, :u, 5, 'Ben', now())")
        .param("i", UUID.randomUUID())
        .param("m", f.municipality())
        .param("b", unpaid)
        .param("f", f.id())
        .param("u", use)
        .update();

    assertThat(internal("accountclosed", "mala", subject)).startsWith("403");
    assertThat(
            count("select count(*) from booking where id = :k and anonymized_at is null", unpaid))
        .as("sen a clave, nada")
        .isEqualTo(1);

    assertThat(internal("accountclosed", key(), subject)).isEqualTo("200 {\"closed\":true}");

    assertThat(
            count(
                "select count(*) from booking where id = :k and anonymized_at is not null"
                    + " and holder_subject is null and holder_email is null",
                unpaid))
        .as("a reserva sen pago, anonimizada xa")
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from booking where id = :k and anonymized_at is null"
                    + " and holder_subject is not null",
                paid))
        .as("a de pago, ata o seu prazo")
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from account_display_preference where account_subject = :s")
                .param("s", subject)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(count("select count(*) from booking_review where booking_id = :k", unpaid)).isZero();

    Map<String, Object> event =
        jdbc.sql(
                "select actor_subject, details::text as details from audit_event"
                    + " where municipality_id = :m and action = 'people.account-close'")
            .param("m", f.municipality())
            .query()
            .singleRow();
    assertThat(event.get("actor_subject")).isEqualTo("furar:account-closure");
    String details = (String) event.get("details");
    assertThat(details).contains("\"rows\"").contains("\"bookingsAnonymized\": 1");
    assertThat(details).doesNotContain(subject);

    // Keycloak volve avisar (a tarefa de reintentos): nada que facer, ningún evento máis.
    assertThat(internal("accountclosed", key(), subject)).isEqualTo("200 {\"closed\":true}");
    assertThat(
            count(
                "select count(*) from audit_event where municipality_id = :k"
                    + " and action = 'people.account-close'",
                f.municipality()))
        .isEqualTo(1);
  }

  @Test
  void theSecondFactorKeyDoesNotCloseAccounts() throws Exception {
    String other = InternalCallKey.derive(sharedSecret, InternalCallKey.SECOND_FACTOR).value();
    assertThat(internal("accountclosed", other, "calquera")).startsWith("403");
    assertThat(internal("accountdeletion", other, "calquera")).startsWith("403");
    assertThat(internal("accountclosed", key(), "furar:booking-retention")).startsWith("400");
    assertThat(api().post("/actuator/accountclosed", Map.of("subject", "x")).send().status())
        .as("non está no porto da API")
        .isNotEqualTo(200);
  }

  private String internal(String endpoint, String key, String subject)
      throws IOException, InterruptedException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("key", key);
    body.put("subject", subject);
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + managementPort + "/actuator/" + endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Api.JSON.writeValueAsString(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    return (response.statusCode() + " " + response.body()).strip();
  }

  private void staff(UUID municipality, String subject) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at, version)
            values (:id, :m, :email, 'Persoa de proba', :subject, 'STAFF', 'ACTIVE', 'gl',
                    'sub-xestora', :now, :now, :now, :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", "persoal-" + id + "@example.org")
        .param("subject", subject)
        .param("now", now)
        .update();
  }
}
