// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As valoracións de punta a punta (ADR-0037): só quen reservou, unha vez, unha reserva pasada á que
 * foi; a media e a lista pública (ordes, sen nomes, o comentario oculto non sae pero a nota conta);
 * a táboa da xestión, ocultar co seu rastro sen o texto e quen o pode facer.
 */
class BookingReviewsIT extends AbstractAttendanceIT {

  static String review(String slug, UUID booking) {
    return mine(slug) + "/" + booking + "/review";
  }

  static String publicReviews(String slug, UUID facility) {
    return "/api/v1/municipalities/" + slug + "/public/facilities/" + facility + "/reviews";
  }

  static String managed(String slug) {
    return "/api/v1/municipalities/" + slug + "/booking-reviews";
  }

  /** Unha reserva que rematou hai {@code endedAgo}, da persoa {@code token}. */
  UUID past(String token, Facility f, UUID use, int slot, Duration endedAgo) {
    Api.Response booked = reserve(token, f, use, at(tomorrow(), OPENS + 60 * slot));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID id = id(booked.json());
    move(id, Instant.now().minus(endedAgo).minus(Duration.ofHours(1)));
    return id;
  }

  Api.Response rate(
      String token, String slug, UUID booking, Object stars, @Nullable String comment) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("stars", stars);
    if (comment != null) {
      body.put("comment", comment);
    }
    return api().post(review(slug, booking), body).token(token).send();
  }

  JsonNode history(String token, String slug, UUID booking) {
    Api.Response list = api().get(mine(slug) + "?scope=HISTORY&size=50").token(token).send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    for (JsonNode item : list.json().get("items")) {
      if (item.get("id").asString().equals(booking.toString())) {
        return item;
      }
    }
    throw new AssertionError("non está no historial: " + booking);
  }

  @Test
  void onlyWhoBookedReviewsOnceAnEndedBookingTheyWentTo() {
    Facility f = facility("valora-unha", true);
    UUID use = whole(f, Map.of());
    String ana = person("sub-ana-" + UUID.randomUUID());
    String staff = TestTokens.staff(f.slug());
    UUID ended = past(ana, f, use, 0, Duration.ofMinutes(5));
    UUID upcoming = id(reserve(ana, f, use, at(tomorrow(), OPENS + 60)).json());
    UUID missed = past(ana, f, use, 2, Duration.ofHours(2));
    assertThat(noShow(staff, f.slug(), missed).status()).isEqualTo(200);

    assertThat(history(ana, f.slug(), ended).get("canReview").asBoolean()).isTrue();
    assertThat(history(ana, f.slug(), missed).get("canReview").asBoolean()).isFalse();

    Api.Response other = rate(person(), f.slug(), ended, 5, null);
    assertThat(other.status()).as("doutra persoa: coma se non existise").isEqualTo(404);
    assertThat(code(other)).isEqualTo("booking.not-found");
    Api.Response early = rate(ana, f.slug(), upcoming, 5, null);
    assertThat(early.status()).isEqualTo(422);
    assertThat(code(early)).isEqualTo("booking.review-not-allowed");
    Api.Response noShow = rate(ana, f.slug(), missed, 5, null);
    assertThat(code(noShow)).isEqualTo("booking.review-not-allowed");
    assertThat(rate(ana, f.slug(), ended, 6, null).status()).isEqualTo(400);
    Api.Response tooLong = rate(ana, f.slug(), ended, 4, "a".repeat(301));
    assertThat(tooLong.status()).isEqualTo(422);
    assertThat(code(tooLong)).isEqualTo("booking.review-invalid");

    Api.Response done = rate(ana, f.slug(), ended, 4, "  Boa pista.  ");
    assertThat(done.status()).as(done.toString()).isEqualTo(201);
    assertThat(done.json().get("stars").asInt()).isEqualTo(4);
    assertThat(done.json().get("comment").asString()).isEqualTo("Boa pista.");
    JsonNode card = history(ana, f.slug(), ended);
    assertThat(card.get("canReview").asBoolean()).isFalse();
    assertThat(card.get("reviewStars").asInt()).isEqualTo(4);

    Api.Response again = rate(ana, f.slug(), ended, 1, null);
    assertThat(again.status()).as("unha por reserva, sen cambios").isEqualTo(409);
    assertThat(code(again)).isEqualTo("booking.review-exists");
    assertThat(api().post(review(f.slug(), ended), Map.of("stars", 3)).send().status())
        .as("sen sesión")
        .isEqualTo(401);
  }

  @Test
  void withEntryControlOnlyAValidatedBookingIsReviewed() {
    Facility f = facility("valora-porta", true);
    UUID use = whole(f, Map.of());
    String ana = person("sub-ana-" + UUID.randomUUID());
    String staff = TestTokens.staff(f.slug());
    entryControl(f, Instant.now().minus(Duration.ofDays(2)));
    UUID unchecked = past(ana, f, use, 0, Duration.ofMinutes(5));
    Api.Response booked = reserve(ana, f, use, at(tomorrow(), OPENS + 60));
    UUID used = id(booked.json());
    move(used, Instant.now().minus(Duration.ofMinutes(70)));
    assertThat(
            validate(staff, f.slug(), booked.json().get("code").asString())
                .json()
                .get("outcome")
                .asString())
        .isEqualTo("VALIDATED");

    Api.Response waiting = rate(ana, f.slug(), unchecked, 5, null);
    assertThat(waiting.status()).isEqualTo(422);
    assertThat(code(waiting)).isEqualTo("booking.review-not-allowed");
    assertThat(rate(ana, f.slug(), used, 5, null).status()).isEqualTo(201);
  }

  @Test
  void thePublicSeesTheAverageAndTheListWithoutNamesAndAHiddenCommentStillCounts() {
    Facility f = facility("valora-publica", true);
    UUID use = whole(f, Map.of());
    String manager = TestTokens.manager(f.slug());
    List<UUID> reviewed = new ArrayList<>();
    int[] stars = {5, 2, 4};
    @Nullable String[] comments = {"Moi ben", "Auga fría", null};
    for (int i = 0; i < stars.length; i++) {
      String token = person("sub-v" + i + "-" + UUID.randomUUID());
      UUID booking = past(token, f, use, i, Duration.ofMinutes(5));
      assertThat(rate(token, f.slug(), booking, stars[i], comments[i]).status()).isEqualTo(201);
      reviewed.add(booking);
      // Cada unha un pouco despois da anterior: a orde «máis recentes» é estable.
      jdbc.sql(
              "update booking_review set created_at = created_at - (:n * interval '1 minute')"
                  + " where booking_id = :b")
          .param("n", stars.length - i)
          .param("b", booking)
          .update();
    }

    String summary = publicReviews(f.slug(), f.id()) + "/summary";
    JsonNode tally = api().get(summary).send().json();
    assertThat(tally.get("reviews").asLong()).isEqualTo(3);
    assertThat(tally.get("average").asDouble()).isCloseTo(11 / 3.0, within());

    assertThat(stars(api().get(publicReviews(f.slug(), f.id())).send().json()))
        .as("por defecto, as máis recentes")
        .containsExactly(4, 2, 5);
    assertThat(stars(api().get(publicReviews(f.slug(), f.id()) + "?sort=OLDEST").send().json()))
        .containsExactly(5, 2, 4);
    assertThat(stars(api().get(publicReviews(f.slug(), f.id()) + "?sort=HIGHEST").send().json()))
        .containsExactly(5, 4, 2);
    assertThat(stars(api().get(publicReviews(f.slug(), f.id()) + "?sort=LOWEST").send().json()))
        .containsExactly(2, 4, 5);
    JsonNode page = api().get(publicReviews(f.slug(), f.id()) + "?size=2").send().json();
    assertThat(page.get("items")).hasSize(2);
    assertThat(page.get("totalItems").asLong()).isEqualTo(3);
    assertThat(page.toString()).doesNotContain("sub-v").doesNotContain("@");

    // A xestión ve a táboa e oculta o comentario frío.
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
    String table = managed(f.slug()) + "?from=" + today.minusDays(1) + "&to=" + today;
    JsonNode list = api().get(table).token(manager).send().json();
    assertThat(list.get("totalItems").asLong()).isEqualTo(3);
    assertThat(list.get("withComment").asLong()).isEqualTo(2);
    assertThat(list.get("byStars").get(0).get("stars").asInt()).isEqualTo(5);
    assertThat(list.get("byStars").get(0).get("count").asLong()).isEqualTo(1);
    String cold = null;
    for (JsonNode item : list.get("items")) {
      if (!item.get("comment").isNull() && "Auga fría".equals(item.get("comment").asString())) {
        cold = item.get("id").asString();
        assertThat(item.get("facilityName").asString()).isEqualTo("Pavillón do Alto");
      }
    }
    assertThat(cold).isNotNull();
    JsonNode onlyTwo = api().get(table + "&stars=2").token(manager).send().json();
    assertThat(onlyTwo.get("totalItems").asLong()).isEqualTo(1);
    assertThat(onlyTwo.get("byStars").get(3).get("count").asLong())
        .as("os recontos por nota, sen o filtro de nota")
        .isEqualTo(1);

    String hide = managed(f.slug()) + "/" + cold + "/hidden";
    assertThat(
            api()
                .put(hide, Map.of("hidden", true))
                .token(TestTokens.staff(f.slug()))
                .send()
                .status())
        .as("recepción non oculta")
        .isEqualTo(403);
    assertThat(api().get(table).token(TestTokens.staff(f.slug())).send().status()).isEqualTo(403);
    assertThat(api().get(table).token(TestTokens.platformAdmin()).send().status())
        .as("a plataforma non ve comentarios")
        .isEqualTo(403);
    Api.Response hidden = api().put(hide, Map.of("hidden", true)).token(manager).send();
    assertThat(hidden.status()).as(hidden.toString()).isEqualTo(200);
    assertThat(hidden.json().get("hidden").asBoolean()).isTrue();
    assertThat(hidden.json().get("comment").asString()).isEqualTo("Auga fría");

    JsonNode after = api().get(publicReviews(f.slug(), f.id()) + "?sort=LOWEST").send().json();
    assertThat(after.get("items").get(0).get("stars").asInt()).isEqualTo(2);
    assertThat(after.get("items").get(0).get("comment").isNull()).isTrue();
    assertThat(after.toString()).doesNotContain("Auga fría");
    assertThat(api().get(summary).send().json().get("reviews").asLong())
        .as("a nota segue contando")
        .isEqualTo(3);

    Api.Response shown = api().put(hide, Map.of("hidden", false)).token(manager).send();
    assertThat(shown.json().get("hidden").asBoolean()).isFalse();
    assertThat(audit(f.municipality(), "booking.review-hide")).containsExactly("SUCCESS ");
    assertThat(audit(f.municipality(), "booking.review-show")).containsExactly("SUCCESS ");
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where municipality_id = :m"
                        + " and details::text like '%fría%'")
                .param("m", f.municipality())
                .query(Integer.class)
                .single())
        .as("o rexistro non leva o texto")
        .isZero();
  }

  @Test
  void theIndicatorsCountTheAverageRating() {
    Facility f = facility("valora-indic", true);
    UUID use = whole(f, Map.of());
    for (int i = 0; i < 2; i++) {
      String token = person("sub-i" + i + "-" + UUID.randomUUID());
      UUID booking = past(token, f, use, i, Duration.ofMinutes(5));
      assertThat(rate(token, f.slug(), booking, 3 + i, null).status()).isEqualTo(201);
    }
    Api.Response panel =
        api()
            .get("/api/v1/municipalities/" + f.slug() + "/indicators")
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(panel.status()).as(panel.toString()).isEqualTo(200);
    JsonNode rating = panel.json().get("kpis").get("rating");
    assertThat(rating.get("value").get("reviews").asLong()).isEqualTo(2);
    assertThat(rating.get("value").get("starsSum").asLong()).isEqualTo(7);
    assertThat(
            rating
                .get("evolution")
                .get(rating.get("evolution").size() - 1)
                .get("value")
                .get("reviews")
                .asLong())
        .isEqualTo(2);
  }

  static List<Integer> stars(JsonNode page) {
    List<Integer> list = new ArrayList<>();
    page.get("items").forEach(i -> list.add(i.get("stars").asInt()));
    return list;
  }

  static Offset<Double> within() {
    return Offset.offset(0.001);
  }
}
