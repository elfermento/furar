// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Reserva por outra persoa de punta a punta (especificación, bloque 5, decidido o 2026-11-04;
 * maqueta 58): o persoal reserva para unha persoa sen conta (con pago, correo e teléfono), a clave
 * de reintento, quen a fixo no detalle e nas listas, o correo de confirmación, cambiar co mesmo
 * formulario (con e sen aviso), os datos dunha conta que non se corrixen, os permisos e o rexistro
 * de actividade.
 */
class BookForIT extends AbstractBookingIT {

  @Autowired RecordingMailSender mails;

  static String bookings(String slug) {
    return "/api/v1/municipalities/" + slug + "/bookings";
  }

  Api.Response bookFor(
      String token, Facility f, UUID use, Instant start, Map<String, Object> holder, String key) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("holder", holder);
    body.put("paid", true);
    body.put("language", "es");
    return api().post(bookings(f.slug()), body).header("Idempotency-Key", key).token(token).send();
  }

  Api.Response change(
      String token, Facility f, UUID booking, long version, Map<String, Object> body) {
    return api().put(bookings(f.slug()) + "/" + booking, body).ifMatch(version).token(token).send();
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
      Thread.sleep(100);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  @Test
  void receptionBooksForAPersonWithoutAccountAndChangesIt() throws Exception {
    Facility f = facility("reserva-por", true);
    UUID paid = whole(f, Map.of("price", Map.of("kind", "PAID", "amountCents", 800)));
    LocalDate day = tomorrow();
    String staff = TestTokens.staff(f.slug());
    String email = "ana-" + UUID.randomUUID() + "@example.org";
    Map<String, Object> ana = Map.of("name", "Ana Pérez", "email", email, "phone", "666 123 456");
    String key = UUID.randomUUID().toString();

    Api.Response created = bookFor(staff, f, paid, at(day, 600), ana, key);
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    JsonNode d = created.json();
    UUID id = UUID.fromString(d.get("id").asString());
    assertThat(d.get("holderName").asString()).isEqualTo("Ana Pérez");
    assertThat(d.get("holderPhone").asString()).isEqualTo("666 123 456");
    assertThat(d.get("holderAccount").asBoolean()).isFalse();
    assertThat(d.get("paymentStatus").asString()).isEqualTo("PAID");
    assertThat(d.get("bookedBy").get("role").asString()).isEqualTo("STAFF");
    assertThat(d.get("bookedBy").get("name").asString()).isEqualTo("Persoa de proba");
    // Sen conta: só o correo; o aviso na web non ten onde verse.
    assertThat(d.get("cancelChannels").toString()).isEqualTo("[\"EMAIL\"]");
    assertThat(
            jdbc.sql(
                    "select (holder_subject is null) and booked_by is not null from booking"
                        + " where id = :k")
                .param("k", id)
                .query(Boolean.class)
                .single())
        .isTrue();

    // A mesma clave e a mesma petición: a mesma reserva.
    Api.Response again = bookFor(staff, f, paid, at(day, 600), ana, key);
    assertThat(again.status()).isEqualTo(201);
    assertThat(again.json().get("id").asString()).isEqualTo(id.toString());

    // A confirmación de sempre, no idioma escollido.
    await(() -> mails.sentTo(email).size() == 1);
    await(
        () ->
            api()
                .get(bookings(f.slug()) + "/" + id)
                .token(staff)
                .send()
                .json()
                .path("lastMail")
                .isObject());

    // Na lista, quen a fixo.
    JsonNode row = api().get(bookings(f.slug())).token(staff).send().json().get("items").get(0);
    assertThat(row.get("bookedBy").get("name").asString()).isEqualTo("Persoa de proba");

    // Cambiar ao oco seguinte sen avisar e corrixir o teléfono: mesma reserva e sen correo.
    long version = api().get(bookings(f.slug()) + "/" + id).token(staff).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", paid);
    body.put("start", at(day, 660).toString());
    body.put("slots", 1);
    body.put("holder", Map.of("name", "Ana Pérez", "email", email, "phone", "600 000 000"));
    body.put("notifyPerson", false);
    Api.Response changed = change(staff, f, id, version, body);
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
    assertThat(changed.json().get("start").asString()).isEqualTo(at(day, 660).toString());
    assertThat(changed.json().get("holderPhone").asString()).isEqualTo("600 000 000");
    assertThat(changed.json().get("paymentStatus").asString()).isEqualTo("PAID");
    Thread.sleep(500);
    assertThat(mails.sentTo(email)).hasSize(1);

    // Outra vez, avisando: chega o correo do cambio.
    body.put("start", at(day, 720).toString());
    body.remove("notifyPerson");
    Api.Response notified = change(staff, f, id, changed.json().get("version").asLong(), body);
    assertThat(notified.status()).as(notified.toString()).isEqualTo(200);
    await(() -> mails.sentTo(email).size() == 2);

    assertThat(
            jdbc.sql(
                    """
                    select action || ' ' || outcome from audit_event
                    where municipality_id = :k and action like 'booking.%'
                    order by occurred_at
                    """)
                .param("k", f.municipality())
                .query(String.class)
                .list())
        .containsExactly(
            "booking.book-for SUCCESS",
            "booking.book-for SUCCESS",
            "booking.change SUCCESS",
            "booking.change SUCCESS");
  }

  @Test
  void withoutEmailNothingIsSentAndOnlyTheStaffBooks() throws Exception {
    Facility f = facility("reserva-por-sen", true);
    UUID free = whole(f, Map.of("price", Map.of("kind", "FREE")));
    LocalDate day = tomorrow();
    Map<String, Object> noMail = Map.of("name", "Xan");

    Api.Response resident =
        bookFor(person(), f, free, at(day, 600), noMail, UUID.randomUUID().toString());
    assertThat(resident.status()).isEqualTo(403);

    Api.Response created =
        bookFor(
            TestTokens.manager(f.slug()),
            f,
            free,
            at(day, 600),
            noMail,
            UUID.randomUUID().toString());
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.json().get("paymentStatus").asString()).isEqualTo("NONE");
    assertThat(created.json().get("cancelChannels").size()).isZero();
    UUID id = UUID.fromString(created.json().get("id").asString());
    Thread.sleep(500);
    assertThat(
            jdbc.sql("select count(*) from notification_delivery where booking_id = :k")
                .param("k", id)
                .query(Integer.class)
                .single())
        .isZero();

    // O oco xa está: 409 coas alternativas, como para a veciñanza.
    Api.Response taken =
        bookFor(
            TestTokens.staff(f.slug()),
            f,
            free,
            at(day, 600),
            noMail,
            UUID.randomUUID().toString());
    assertThat(taken.status()).isEqualTo(409);
    assertThat(taken.json().get("code").asString()).isEqualTo("booking.slot-taken");
  }

  @Test
  void theDataOfAnAccountAreNotCorrectedButItsSlotChanges() {
    Facility f = facility("reserva-por-conta", true);
    UUID free = whole(f, Map.of("price", Map.of("kind", "FREE")));
    LocalDate day = tomorrow();
    Api.Response mine = reserve(person(), f, free, at(day, 600));
    assertThat(mine.status()).isEqualTo(201);
    UUID id = UUID.fromString(mine.json().get("id").asString());
    String staff = TestTokens.staff(f.slug());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", free);
    body.put("start", at(day, 660).toString());
    body.put("slots", 1);
    body.put("holder", Map.of("name", "Outro nome"));
    Api.Response refused = change(staff, f, id, 0, body);
    assertThat(refused.status()).isEqualTo(422);
    assertThat(refused.json().get("code").asString()).isEqualTo("booking.holder-not-editable");

    body.remove("holder");
    Api.Response moved = change(staff, f, id, 0, body);
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(moved.json().get("holderAccount").asBoolean()).isTrue();
    assertThat(moved.json().path("bookedBy").isObject()).isFalse();
    assertThat(List.of(moved.json().get("start").asString()))
        .containsExactly(at(day, 660).toString());
  }
}
