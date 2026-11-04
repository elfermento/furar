// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRetention;
import gal.furar.booking.application.service.BookingRetentionService;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;

/**
 * Conservación das reservas (ADR-0017 punto 5, revisión do 2026-11-04): a tarefa diaria anonimiza
 * as reservas rematadas hai máis de 2 anos (as de pago, 5) e deixa as demais. Unha anonimizada
 * perde a conta, o nome, o correo e os valores dos seus campos marcados como dato persoal; quen
 * cancelou e as filas da historia que fixo a persoa pasan a {@code furar:anonymized}; o resto
 * (franxa, estado, prezo, campos que non son dato persoal) queda. Cada lote deixa un evento no
 * rexistro de actividade; repetir non cambia nada e o rexistro técnico non leva correos. As
 * reservas sementanse pola API e despois avellécense por JDBC; o reloxo é o instante da proba (a
 * base é compartida).
 */
@ExtendWith(OutputCaptureExtension.class)
class BookingRetentionIT extends AbstractBookingIT {

  @Autowired MunicipalityDirectory municipalities;
  @Autowired BookingRetention bookings;
  @Autowired BookingCustomFields customFields;
  @Autowired AuditLog audit;
  @Autowired PlatformTransactionManager transactions;
  @Autowired BookingRetentionService scheduled;

  @Test
  void anonymisesOnlyTheExpiredBookingsAndTheirPersonalFields(CapturedOutput output) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    BookingRetentionService retention =
        new BookingRetentionService(
            municipalities,
            bookings,
            customFields,
            audit,
            transactions,
            Period.ofYears(2),
            Period.ofYears(5),
            Clock.fixed(now, ZoneOffset.UTC));
    Facility f = facility("conserva-reservas", true);
    UUID free = whole(f, Map.of());
    UUID priced =
        whole(f, "Pista de pago", Map.of("price", Map.of("kind", "PAID", "amountCents", 800)));
    createField(f.slug(), bookingField("federation_no", true));
    createField(f.slug(), bookingField("shirt_size", false));
    String subject = "ana-" + UUID.randomUUID();
    String ana = person(subject);

    UUID old =
        booked(
            ana,
            f,
            free,
            at(tomorrow(), OPENS),
            Map.of("federation_no", "GA-1", "shirt_size", "M"));
    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + old + "/cancel", null).ifMatch(0).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    UUID recent = booked(ana, f, free, at(tomorrow(), OPENS + 60), Map.of());
    UUID paidRecent = booked(ana, f, priced, at(tomorrow(), OPENS + 120), Map.of());
    UUID paidOld = booked(ana, f, priced, at(tomorrow(), OPENS + 180), Map.of());
    age(old, now.minus(Duration.ofDays(3 * 366)));
    age(recent, now.minus(Duration.ofDays(366)));
    age(paidRecent, now.minus(Duration.ofDays(3 * 366)));
    age(paidOld, now.minus(Duration.ofDays(6 * 366)));

    assertThat(retention.apply()).isGreaterThanOrEqualTo(2);

    assertThat(holder(old)).isNull();
    assertThat(holder(paidOld)).isNull();
    assertThat(holder(recent)).isEqualTo(subject);
    assertThat(holder(paidRecent)).isEqualTo(subject);
    Map<String, Object> row = row(old);
    assertThat(row.get("holder_name")).isNull();
    assertThat(row.get("holder_email")).isNull();
    assertThat(row.get("anonymized_at")).isNotNull();
    assertThat(row.get("cancelled_by")).isEqualTo(BookingRetention.ANONYMISED_ACTOR);
    assertThat(row.get("status")).isEqualTo("CANCELLED_BY_PERSON");
    assertThat(actors(old))
        .containsExactly(BookingRetention.ANONYMISED_ACTOR, BookingRetention.ANONYMISED_ACTOR);
    assertThat(actors(recent)).containsExactly(subject);
    assertThat(fieldCodes(old)).as("só os dato persoal").containsExactly("shirt_size");

    // O persoal xa non ve a persoa.
    JsonNode detail =
        api()
            .get("/api/v1/municipalities/" + f.slug() + "/bookings/" + old)
            .token(TestTokens.staff(f.slug()))
            .send()
            .json();
    assertThat(detail.path("holderName").isNull() || detail.path("holderName").isMissingNode())
        .isTrue();
    assertThat(detail.get("code").asString()).startsWith("FUR-");

    assertThat(
            count(
                """
                select count(*) from audit_event
                where municipality_id = :k and action = 'booking.anonymize'
                  and actor_subject = 'furar:booking-retention'
                """,
                f.municipality()))
        .isEqualTo(2);

    // Repetir: nada novo.
    long version = ((Number) row(old).get("version")).longValue();
    retention.apply();
    assertThat(((Number) row(old).get("version")).longValue()).isEqualTo(version);
    assertThat(holder(recent)).isEqualTo(subject);
    assertThat(output).contains("booking retention municipality=" + f.municipality());
    assertThat(output).doesNotContain(subject + "@example.org");
  }

  @Test
  void theScheduledBeanKeepsTwoYears() {
    Facility f = facility("conserva-reservas-bean", true);
    UUID free = whole(f, Map.of());
    String subject = "bea-" + UUID.randomUUID();
    UUID booking = booked(person(subject), f, free, at(tomorrow(), OPENS), Map.of());
    age(booking, Instant.now().minus(Duration.ofDays(400)));

    scheduled.apply();

    assertThat(holder(booking)).isEqualTo(subject);
  }

  private UUID booked(
      String token, Facility f, UUID use, Instant start, Map<String, Object> fieldValues) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("customFields", fieldValues);
    Api.Response created =
        api()
            .post(mine(f.slug()), body)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .token(token)
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return UUID.fromString(created.json().get("id").asString());
  }

  /** A reserva pasa a rematar en {@code end} (unha hora), sen cambios desde entón. */
  private void age(UUID booking, Instant end) {
    OffsetDateTime to = end.atOffset(ZoneOffset.UTC);
    OffsetDateTime from = to.minusHours(1);
    jdbc.sql(
            """
            update booking
            set period = tstzrange(:from, :to, '[)'), cancel_until = :from, created_at = :from,
                updated_at = :to,
                cancelled_at = case when cancelled_at is null then null else :to end
            where id = :id
            """)
        .param("from", from)
        .param("to", to)
        .param("id", booking)
        .update();
  }

  private String holder(UUID booking) {
    return (String) row(booking).get("holder_subject");
  }

  private Map<String, Object> row(UUID booking) {
    return jdbc.sql(
            """
            select holder_subject, holder_name, holder_email, anonymized_at, cancelled_by, status,
                   version
            from booking where id = :id
            """)
        .param("id", booking)
        .query()
        .singleRow();
  }

  private List<String> actors(UUID booking) {
    return jdbc.sql(
            "select actor_subject from booking_event where booking_id = :id order by at, kind")
        .param("id", booking)
        .query(String.class)
        .list();
  }

  private List<String> fieldCodes(UUID booking) {
    return jdbc.sql(
            """
            select f.code from custom_field_value v
            join custom_field f on f.municipality_id = v.municipality_id and f.id = v.field_id
            where v.entity_id = :id order by f.code
            """)
        .param("id", booking)
        .query(String.class)
        .list();
  }

  private static Map<String, Object> bookingField(String code, boolean personal) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("code", code);
    map.put("type", "TEXT");
    map.put("label", Map.of("gl", "Campo " + code, "es", "Campo " + code));
    map.put("personalData", personal);
    map.put(
        "usages", List.of(Map.of("target", "BOOKING", "visibility", "OWNER", "required", false)));
    return map;
  }
}
