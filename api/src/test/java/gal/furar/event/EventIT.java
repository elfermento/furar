// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Eventos do concello de punta a punta (ADR-0038): alta e publicación na xestión, a portada e a
 * ficha sen sesión, apuntarse (tamén dúas persoas á vez pola última praza), «Apuntarse ata», «Con
 * aprobación» con aceptar e rexeitar, o pago, os correos, quen pode que (a plataforma configura
 * pero non ve persoas), eliminar e a baixa da conta.
 */
class EventIT extends AbstractBookingIT {

  @Autowired RecordingMailSender mails;

  static String base(String slug) {
    return "/api/v1/municipalities/" + slug + "/events";
  }

  static Instant inDays(int days) {
    return Instant.now().plus(Duration.ofDays(days)).truncatedTo(ChronoUnit.MINUTES);
  }

  static Map<String, Object> body(
      @Nullable UUID facility,
      @Nullable Integer capacity,
      @Nullable Integer price,
      boolean approval,
      @Nullable Instant deadline) {
    Map<String, Object> body = new HashMap<>();
    body.put("nameGl", "Carreira popular de Nadal");
    body.put("nameEs", "Carrera popular de Navidad");
    body.put("descriptionGl", "5 km pola pista.");
    body.put("descriptionEs", "5 km por la pista.");
    body.put("startsAt", inDays(20).toString());
    if (facility == null) {
      body.put("place", "Saída da Praza de Galicia");
    } else {
      body.put("facilityId", facility.toString());
    }
    body.put("capacity", capacity);
    body.put("priceCents", price);
    body.put("registrationDeadline", deadline == null ? null : deadline.toString());
    body.put("published", true);
    body.put("approval", approval);
    return body;
  }

  UUID created(Facility f, Map<String, Object> body) {
    Api.Response created =
        api().post(base(f.slug()) + "/manage", body).token(TestTokens.manager(f.slug())).send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return UUID.fromString(created.json().get("id").asString());
  }

  Api.Response join(Facility f, UUID event, String token) {
    return api().post(base(f.slug()) + "/" + event + "/registration", Map.of()).token(token).send();
  }

  JsonNode registrations(Facility f, UUID event, String token) {
    Api.Response list =
        api().get(base(f.slug()) + "/manage/" + event + "/registrations").token(token).send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    return list.json();
  }

  List<String> subjects(String address) {
    return mails.sentTo(address).stream()
        .map(
            m -> {
              try {
                return m.getSubject();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    while (!condition.getAsBoolean()) {
      assertThat(Instant.now()).as("agardando").isBefore(deadline);
      Thread.sleep(50);
    }
  }

  long audited(Facility f, String action) {
    return jdbc.sql("select count(*) from audit_event where municipality_id = :m and action = :a")
        .param("m", f.municipality())
        .param("a", action)
        .query(Long.class)
        .single();
  }

  @Test
  void theManagerPublishesAndResidentsSeeAndJoin() {
    Facility f = facility("ev-pub", true);
    UUID event = created(f, body(f.id(), 2, null, false, null));

    Api.Response upcoming = api().get(base(f.slug())).send();
    assertThat(upcoming.status()).as(upcoming.toString()).isEqualTo(200);
    JsonNode card = upcoming.json().get(0);
    assertThat(card.get("id").asString()).isEqualTo(event.toString());
    assertThat(card.get("facility").get("name").asString()).isEqualTo("Pavillón do Alto");
    assertThat(card.get("free").asInt()).isEqualTo(2);
    assertThat(card.get("open").asBoolean()).isTrue();
    assertThat(card.get("cover").get("thumb").asString()).isNotBlank();

    assertThat(join(f, event, "x").status()).isEqualTo(401);
    String subject = "ev-" + UUID.randomUUID();
    Api.Response joined = join(f, event, person(subject));
    assertThat(joined.status()).as(joined.toString()).isEqualTo(201);
    assertThat(joined.json().get("status").asString()).isEqualTo("CONFIRMED");
    assertThat(join(f, event, person(subject)).json().get("code").asString())
        .isEqualTo("event.already-registered");

    Api.Response mine =
        api().get(base(f.slug()) + "/" + event + "/registration").token(person(subject)).send();
    assertThat(mine.json().get("status").asString()).isEqualTo("CONFIRMED");
    assertThat(mine.json().get("event").get("free").asInt()).isEqualTo(1);
    Api.Response other =
        api().get(base(f.slug()) + "/" + event + "/registration").token(person()).send();
    JsonNode none = other.json().path("status");
    assertThat(none.isNull() || none.isMissingNode()).isTrue();
    JsonNode myEvents = api().get(base(f.slug()) + "/mine").token(person(subject)).send().json();
    assertThat(myEvents).hasSize(1);

    // Sen correo de confirmación ao apuntarse directamente (o aviso en pantalla abonda).
    assertThat(mails.sentTo(subject + "@example.org")).isEmpty();

    Api.Response leave =
        api().delete(base(f.slug()) + "/" + event + "/registration").token(person(subject)).send();
    assertThat(leave.status()).isEqualTo(204);
    assertThat(api().get(base(f.slug()) + "/" + event).send().json().get("free").asInt())
        .isEqualTo(2);
    assertThat(audited(f, "event.create")).isEqualTo(1);
  }

  @Test
  void twoPeopleAtOnceDoNotTakeTheLastSeat() throws Exception {
    Facility f = facility("ev-ultima", true);
    UUID event = created(f, body(null, 1, null, false, null));
    List<Callable<Api.Response>> calls =
        List.of(() -> join(f, event, person()), () -> join(f, event, person()));
    List<Api.Response> responses = together(calls);
    // Sen aprobación, a segunda entra na lista de espera (ADR-0041).
    assertThat(byStatus(responses)).containsEntry(201, 2L);
    assertThat(responses.stream().map(r -> r.json().get("status").asString()).toList())
        .containsExactlyInAnyOrder("CONFIRMED", "WAITING");
    JsonNode card = api().get(base(f.slug()) + "/" + event).send().json();
    assertThat(card.get("free").asInt()).isZero();
    assertThat(card.get("open").asBoolean()).isFalse();
    assertThat(card.get("waitlist").asBoolean()).isTrue();
    assertThat(card.get("waiting").asInt()).isEqualTo(1);
  }

  @Test
  void afterTheDeadlineNobodyJoins() {
    Facility f = facility("ev-ata", true);
    UUID event = created(f, body(null, null, null, false, Instant.now().minusSeconds(3600)));
    Api.Response late = join(f, event, person());
    assertThat(late.status()).isEqualTo(409);
    assertThat(code(late)).isEqualTo("event.closed");
  }

  @Test
  void withApprovalTheManagerAcceptsOrRejectsAndReceptionMarksThePayment() throws Exception {
    Facility f = facility("ev-aprob", true);
    UUID event = created(f, body(f.id(), 1, 500, true, null));
    String first = "ev-" + UUID.randomUUID();
    String second = "ev-" + UUID.randomUUID();
    assertThat(join(f, event, person(first)).json().get("status").asString()).isEqualTo("PENDING");
    join(f, event, person(second));
    String manager = TestTokens.manager(f.slug());
    String reception = TestTokens.staff(f.slug());
    assertThat(
            api()
                .get(base(f.slug()) + "/manage/pending-count")
                .token(reception)
                .send()
                .json()
                .get("count")
                .asLong())
        .isEqualTo(2);

    JsonNode people = registrations(f, event, reception);
    assertThat(people).hasSize(2);
    String firstId = people.get(0).get("id").asString();
    String secondId = people.get(1).get("id").asString();
    String accept = base(f.slug()) + "/manage/" + event + "/registrations/";
    // Recepción ve e marca o pago, pero non decide.
    assertThat(api().post(accept + firstId + "/accept", Map.of()).token(reception).send().status())
        .isEqualTo(403);
    Api.Response accepted =
        api().post(accept + firstId + "/accept", Map.of()).token(manager).send();
    assertThat(accepted.status()).as(accepted.toString()).isEqualTo(200);
    assertThat(accepted.json().get("payment").asString()).isEqualTo("PENDING");
    Api.Response full = api().post(accept + secondId + "/accept", Map.of()).token(manager).send();
    assertThat(code(full)).isEqualTo("event.full");
    Api.Response rejected =
        api().post(accept + secondId + "/reject", Map.of()).token(manager).send();
    assertThat(rejected.json().get("status").asString()).isEqualTo("REJECTED");

    Api.Response paid =
        api().put(accept + firstId + "/payment", Map.of("status", "PAID")).token(reception).send();
    assertThat(paid.status()).as(paid.toString()).isEqualTo(200);
    assertThat(paid.json().get("payment").asString()).isEqualTo("PAID");

    await(() -> subjects(first + "@example.org").contains("Tes praza: Carreira popular de Nadal"));
    await(
        () ->
            subjects(second + "@example.org")
                .contains("Non puidemos darche praza: Carreira popular de Nadal"));
    assertThat(audited(f, "event.accept")).isEqualTo(1);
    assertThat(audited(f, "event.payment")).isEqualTo(1);
    String details =
        jdbc.sql(
                "select string_agg(details::text, ' ') from audit_event where municipality_id = :m")
            .param("m", f.municipality())
            .query(String.class)
            .single();
    assertThat(details).doesNotContain("@example.org").doesNotContain("Persoa ");
  }

  @Test
  void movingOrCancellingTellsEveryoneOnce() throws Exception {
    Facility f = facility("ev-cambio", true);
    Map<String, Object> body = body(f.id(), null, null, false, null);
    UUID event = created(f, body);
    String subject = "ev-" + UUID.randomUUID();
    join(f, event, person(subject));
    String manager = TestTokens.manager(f.slug());
    String path = base(f.slug()) + "/manage/" + event;

    Map<String, Object> seats = new HashMap<>(body);
    seats.put("capacity", 30);
    assertThat(api().put(path, seats).ifMatch(0).token(manager).send().status()).isEqualTo(200);
    Map<String, Object> moved = new HashMap<>(seats);
    moved.put("startsAt", inDays(21).toString());
    assertThat(api().put(path, moved).ifMatch(0).token(manager).send().status()).isEqualTo(412);
    assertThat(api().put(path, moved).ifMatch(1).token(manager).send().status()).isEqualTo(200);
    String email = subject + "@example.org";
    await(() -> subjects(email).contains("Cambiou o evento: Carreira popular de Nadal"));

    assertThat(api().delete(path).token(manager).send().json().get("code").asString())
        .isEqualTo("event.has-registrations");
    Api.Response cancelled =
        api().post(path + "/cancel", Map.of()).ifMatch(2).token(manager).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(cancelled.json().get("cancelledAt").isNull()).isFalse();
    await(() -> subjects(email).contains("Cancelamos o evento: Carreira popular de Nadal"));
    assertThat(subjects(email)).hasSize(2);
    assertThat(api().get(base(f.slug())).send().json()).isEmpty();
    assertThat(api().get(base(f.slug()) + "/" + event).send().status()).isEqualTo(404);
  }

  @Test
  void thePlatformConfiguresButDoesNotSeePeople() {
    Facility f = facility("ev-plat", true);
    String platform = TestTokens.platformAdmin();
    Api.Response created =
        api()
            .post(base(f.slug()) + "/manage", body(null, 10, null, false, null))
            .token(platform)
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID event = UUID.fromString(created.json().get("id").asString());
    join(f, event, person());

    Api.Response list = api().get(base(f.slug()) + "/manage").token(platform).send();
    assertThat(list.status()).isEqualTo(200);
    assertThat(list.json().get(0).get("confirmed").asInt()).isEqualTo(1);
    assertThat(
            api()
                .get(base(f.slug()) + "/manage/" + event + "/registrations")
                .token(platform)
                .send()
                .status())
        .isEqualTo(403);
    assertThat(api().get(base(f.slug()) + "/manage/pending-count").token(platform).send().status())
        .isEqualTo(403);
    // Recepción ve os eventos pero non os crea; a veciñanza, nada da xestión.
    String reception = TestTokens.staff(f.slug());
    assertThat(api().get(base(f.slug()) + "/manage").token(reception).send().status())
        .isEqualTo(200);
    assertThat(
            api()
                .post(base(f.slug()) + "/manage", body(null, null, null, false, null))
                .token(reception)
                .send()
                .status())
        .isEqualTo(403);
    assertThat(api().get(base(f.slug()) + "/manage").token(person()).send().status())
        .isEqualTo(403);
    assertThat(api().get(base(f.slug()) + "/manage").send().status()).isEqualTo(401);

    Facility other = facility("ev-outro", true);
    String otherManager = TestTokens.manager(other.slug());
    assertThat(api().get(base(f.slug()) + "/manage").token(otherManager).send().status())
        .isEqualTo(403);
    assertThat(api().get(base(other.slug()) + "/" + event).send().status()).isEqualTo(404);
    assertThat(api().get(base(other.slug()) + "/manage").token(otherManager).send().json())
        .isEmpty();
  }

  @Test
  void anEventWithoutPeopleIsDeletedAndClosingAnAccountFreesTheSeat() {
    Facility f = facility("ev-baixa", true);
    String manager = TestTokens.manager(f.slug());
    UUID empty = created(f, body(null, null, null, false, null));
    assertThat(api().delete(base(f.slug()) + "/manage/" + empty).token(manager).send().status())
        .isEqualTo(204);

    UUID event = created(f, body(null, 5, null, false, null));
    String subject = "ev-" + UUID.randomUUID();
    join(f, event, person(subject));
    assertThat(api().get(base(f.slug()) + "/" + event).send().json().get("free").asInt())
        .isEqualTo(4);
    jdbc.sql("select close_account(:s)").param("s", subject).query().singleRow();
    assertThat(api().get(base(f.slug()) + "/" + event).send().json().get("free").asInt())
        .isEqualTo(5);
  }
}
