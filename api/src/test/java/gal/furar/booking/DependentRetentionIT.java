// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.application.service.BookingRetentionService;
import gal.furar.people.application.service.DependentRetentionService;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/**
 * Conservación das persoas a cargo e das contas coñecidas polo correo (ADR-0031, puntos 4 e 6): a
 * ficha desactivada consérvase mentres se conserve algunha das súas reservas e bórrase cando se
 * anonimiza a última; anonimizar unha reserva deixa as súas persoas sen nome, ano nin ficha; a
 * baixa dunha conta ({@code close_account}) só a pode executar o dono da base; e as contas que non
 * entran desde hai máis do prazo das reservas bórranse do directorio.
 */
class DependentRetentionIT extends AbstractBookingIT {

  static final String DEPENDENTS = "/api/v1/me/dependents";

  @Autowired BookingRetentionService bookingRetention;
  @Autowired DependentRetentionService dependentRetention;

  UUID dependent(String token, String name) {
    Api.Response created =
        api()
            .post(DEPENDENTS, Map.of("name", name, "birthYear", 2016, "inMyCare", true))
            .token(token)
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  UUID reserveFor(String token, Facility f, UUID use, Instant start, UUID dependent) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("participants", Map.of("self", true, "dependentIds", List.of(dependent)));
    Api.Response created =
        api()
            .post(mine(f.slug()), body)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .token(token)
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  /** A reserva pasa a ter rematado hai tres anos, sen cambios desde entón. */
  void age(UUID booking) {
    OffsetDateTime to = Instant.now().atOffset(ZoneOffset.UTC).minusDays(3 * 366);
    jdbc.sql(
            """
            update booking
            set period = tstzrange(:from, :to, '[)'), cancel_until = :from, created_at = :from,
                updated_at = :to
            where id = :id
            """)
        .param("from", to.minusHours(1))
        .param("to", to)
        .param("id", booking)
        .update();
  }

  int rows(String sql, Object key) {
    return jdbc.sql(sql).param("k", key).query(Integer.class).single();
  }

  @Test
  void aDeactivatedRecordIsPurgedOnceItsLastBookingIsAnonymised() {
    Facility f = facility("conserva-fichas", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 5)));
    String ana = person();
    UUID uxia = dependent(ana, "Uxía Rey");
    UUID never = dependent(ana, "Antón Rey");
    UUID booking = reserveFor(ana, f, swim, at(tomorrow(), OPENS), uxia);

    assertThat(api().delete(DEPENDENTS + "/" + uxia).token(ana).send().status()).isEqualTo(204);
    dependentRetention.apply();
    assertThat(rows("select count(*) from account_dependent where id = :k", uxia))
        .as("a súa reserva aínda se conserva: a ficha desactivada queda")
        .isEqualTo(1);

    age(booking);
    assertThat(bookingRetention.apply()).isGreaterThanOrEqualTo(1);

    assertThat(
            jdbc.sql(
                    """
                    select count(*) from booking_participant
                    where booking_id = :k and person_key like 'x:%' and not active
                      and name is null and birth_year is null and dependent_id is null
                    """)
                .param("k", booking)
                .query(Integer.class)
                .single())
        .as("o titular e a persoa a cargo, anonimizados e sen ficha")
        .isEqualTo(2);
    assertThat(
            rows(
                "select count(*) from booking where id = :k and holder_email_key is null", booking))
        .isEqualTo(1);
    // O persoal xa só ve o número de prazas.
    Api.Response detail =
        api()
            .get("/api/v1/municipalities/" + f.slug() + "/bookings/" + booking)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(detail.json().get("seats").asInt()).isEqualTo(2);
    assertThat(detail.json().get("participants").size()).isZero();

    assertThat(dependentRetention.apply()).isGreaterThanOrEqualTo(1);
    assertThat(rows("select count(*) from account_dependent where id = :k", uxia)).isZero();
    assertThat(rows("select count(*) from account_dependent where id = :k", never))
        .as("unha ficha activa non se toca")
        .isEqualTo(1);
  }

  @Test
  void closingAnAccountIsOnlyForTheOwnerOfTheDatabase() {
    Facility f = facility("baixa-conta", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 5)));
    String sub = "baixa-" + UUID.randomUUID();
    String ana =
        TestTokens.withoutRolesClaim(
            sub, Map.of("auth_time", Instant.now().getEpochSecond(), "name", "Ana Rey"));
    UUID used = dependent(ana, "Uxía Rey");
    UUID unused = dependent(ana, "Antón Rey");
    reserveFor(ana, f, swim, at(tomorrow(), OPENS), used);
    assertThat(rows("select count(*) from account_email where account_subject = :k", sub))
        .isEqualTo(1);

    if (rowLevelSecurityEnforced()) {
      assertThatThrownBy(
              () -> appJdbc.sql("select close_account(:k)").param("k", sub).query().singleRow())
          .as("o rol da API non pode chamala")
          .isInstanceOf(DataAccessException.class);
    }
    jdbc.sql("select close_account(:k)").param("k", sub).query().singleRow();

    assertThat(rows("select count(*) from account_email where account_subject = :k", sub)).isZero();
    assertThat(rows("select count(*) from account_dependent where id = :k", unused)).isZero();
    assertThat(
            rows(
                "select count(*) from account_dependent where id = :k"
                    + " and deactivated_at is not null",
                used))
        .as("a que ten reservas queda desactivada, co prazo delas")
        .isEqualTo(1);
  }

  @Test
  void accountsNotSeenForLongerThanTheBookingsRetentionLeaveTheDirectory() {
    String old = "vella-" + UUID.randomUUID();
    String recent = "nova-" + UUID.randomUUID();
    for (String[] row : new String[][] {{old, "3 years"}, {recent, "1 year"}}) {
      jdbc.sql(
              "insert into account_email (account_subject, email, seen_at)"
                  + " values (:s, :e, now() - cast(:age as interval))")
          .param("s", row[0])
          .param("e", row[0] + "@example.org")
          .param("age", row[1])
          .update();
    }

    dependentRetention.apply();

    assertThat(rows("select count(*) from account_email where account_subject = :k", old)).isZero();
    assertThat(rows("select count(*) from account_email where account_subject = :k", recent))
        .isEqualTo(1);
  }
}
