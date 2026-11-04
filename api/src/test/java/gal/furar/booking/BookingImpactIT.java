// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import tools.jackson.databind.JsonNode;

/**
 * O {@link gal.furar.facility.BookingImpact} real con {@code facility} de verdade (plan do dominio
 * 2, D2-08; ADR-0025 punto 16): cada cambio da instalación abre o modal coas reservas confirmadas
 * que pisa (bloqueo, peche, baixa, tempada, uso que bloquea), cancelar avisa polas canles e deixa a
 * pagada «a devolver», manter deixa a reserva e a súa ocupación e unha fila {@code KEPT}; un uso
 * con reservas non se arquiva e unha instalación con historial non se borra.
 */
class BookingImpactIT extends AbstractBookingIT {

  static final Map<String, Object> PAID =
      Map.of("price", Map.of("kind", "PAID", "amountCents", 800));

  static UUID booked(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return id(response.json());
  }

  static String url(Facility f) {
    return facilities(f.slug()) + "/" + f.id();
  }

  static Map<String, Object> decision(UUID booking, String action, String... channels) {
    return Map.of("bookingId", booking, "action", action, "channels", List.of(channels));
  }

  static List<String> affected(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(409);
    assertThat(response.json().get("code").asString()).isEqualTo("facility.affected-bookings");
    List<String> ids = new ArrayList<>();
    response.json().get("affected").forEach(a -> ids.add(a.get("bookingId").asString()));
    return ids;
  }

  Api.Response markPaid(Facility f, UUID booking) {
    return api()
        .put(
            "/api/v1/municipalities/" + f.slug() + "/bookings/" + booking + "/payment",
            Map.of("status", "PAID"))
        .ifMatch(0)
        .token(TestTokens.staff(f.slug()))
        .send();
  }

  Api.Response block(Facility f, LocalDate date, int start, int end, List<Object> decisions) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date.toString());
    body.put("startMinute", start);
    body.put("endMinute", end);
    body.put("reason", "Mantemento");
    if (decisions != null) {
      body.put("decisions", decisions);
    }
    return api().post(url(f) + "/blocks", body).token(TestTokens.manager(f.slug())).send();
  }

  Api.Response status(Facility f, String status, List<Object> decisions) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status);
    if (decisions != null) {
      body.put("decisions", decisions);
    }
    return api()
        .put(url(f) + "/status", body)
        .ifMatch(version(f.slug(), f.id()))
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  String bookingStatus(UUID booking) {
    return jdbc.sql("select status || ' ' || payment_status from booking where id = :k")
        .param("k", booking)
        .query(String.class)
        .single();
  }

  int activeOccupancy(UUID booking) {
    return count(
        "select count(*) from booking_occupancy where booking_id = :k and active", booking);
  }

  /** Os {@link BookingCancelled} que se publican mentres corre {@code work}. */
  List<Object> cancelledEvents(Runnable work) {
    List<Object> seen = new CopyOnWriteArrayList<>();
    ApplicationListener<ApplicationEvent> listener =
        e -> {
          if (e instanceof PayloadApplicationEvent<?> p
              && p.getPayload() instanceof BookingCancelled) {
            seen.add(p.getPayload());
          }
        };
    multicaster.addApplicationListener(listener);
    try {
      work.run();
    } finally {
      multicaster.removeApplicationListener(listener);
    }
    return seen;
  }

  /**
   * A plataforma configura instalacións pero non ve reservas (ADR-0028, revisión do 2026-11-03): un
   * cambio que pisa reservas → 403 {@code booking.affected-needs-manager}, só co número, sen datos
   * das reservas e sen cambiar nada; un que non pisa ningunha, faise.
   */
  @Test
  void thePlatformCannotDecideOnAffectedBookings() {
    Facility f = facility("impacto-plataforma", true);
    UUID use = whole(f, PAID);
    LocalDate day = tomorrow();
    UUID hit = booked(reserve(person(), f, use, at(day, 600)));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", day.toString());
    body.put("startMinute", 570);
    body.put("endMinute", 660);
    body.put("reason", "Mantemento");

    Api.Response refused =
        api().post(url(f) + "/blocks", body).token(TestTokens.platformAdmin()).send();
    assertThat(refused.status()).as(refused.toString()).isEqualTo(403);
    assertThat(refused.json().get("code").asString()).isEqualTo("booking.affected-needs-manager");
    assertThat(refused.json().has("affected")).isFalse();
    assertThat(refused.body()).doesNotContain(hit.toString());
    assertThat(bookingStatus(hit)).isEqualTo("CONFIRMED PENDING");

    body.put("startMinute", 720);
    body.put("endMinute", 780);
    Api.Response free =
        api().post(url(f) + "/blocks", body).token(TestTokens.platformAdmin()).send();
    assertThat(free.status()).as(free.toString()).isEqualTo(201);
  }

  @Test
  void aBlockOverAPaidBookingAsksAndCancelsItByEmail() {
    Facility f = facility("impacto-bloqueo", true);
    UUID use = whole(f, PAID);
    LocalDate day = tomorrow();
    UUID hit = booked(reserve(person(), f, use, at(day, 600)));
    UUID spared = booked(reserve(person(), f, use, at(day, 720)));
    assertThat(markPaid(f, hit).status()).isEqualTo(200);

    Api.Response asked = block(f, day, 570, 660, null);
    assertThat(affected(asked)).containsExactly(hit.toString());
    JsonNode a = asked.json().get("affected").get(0);
    assertThat(a.get("date").asString()).isEqualTo(day.toString());
    assertThat(a.get("startMinute").asInt()).isEqualTo(600);
    assertThat(a.get("endMinute").asInt()).isEqualTo(660);
    assertThat(a.get("useName").asString()).isEqualTo("Pista enteira");
    assertThat(a.get("hasEmail").asBoolean()).isTrue();
    assertThat(bookingStatus(hit)).as("preguntar non cambia nada").isEqualTo("CONFIRMED PAID");

    List<Api.Response> response = new ArrayList<>();
    List<Object> events =
        cancelledEvents(
            () -> response.add(block(f, day, 570, 660, List.of(decision(hit, "CANCEL", "EMAIL")))));

    assertThat(response.getFirst().status()).as(response.toString()).isEqualTo(201);
    assertThat(bookingStatus(hit)).isEqualTo("CANCELLED_BY_MUNICIPALITY TO_REFUND");
    assertThat(activeOccupancy(hit)).as("o oco queda libre").isZero();
    assertThat(
            jdbc.sql(
                    """
                    select array_to_string(channels, ',') from booking_event
                    where booking_id = :k and kind = 'CANCELLED_BY_MUNICIPALITY'
                    """)
                .param("k", hit)
                .query(String.class)
                .single())
        .isEqualTo("EMAIL");
    assertThat(events)
        .containsExactly(
            new BookingCancelled(
                f.municipality(),
                hit,
                BookingCancelled.By.MUNICIPALITY,
                Set.of(BookingCancelled.Channel.EMAIL)));
    assertThat(bookingStatus(spared)).isEqualTo("CONFIRMED PENDING");
    assertThat(
            count(
                "select count(*) from audit_event where action = 'booking.cancel'"
                    + " and details ->> 'bookingId' = cast(:k as text)",
                hit))
        .isEqualTo(1);
  }

  @Test
  void keepingLeavesTheBookingInTheOccupancyWithAKeptEvent() {
    Facility f = facility("impacto-kept", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    UUID kept = booked(reserve(person(), f, use, at(day, 600)));
    assertThat(affected(block(f, day, 600, 720, null))).containsExactly(kept.toString());

    List<Object> events =
        cancelledEvents(
            () ->
                assertThat(block(f, day, 600, 720, List.of(decision(kept, "KEEP"))).status())
                    .isEqualTo(201));

    assertThat(events).isEmpty();
    assertThat(bookingStatus(kept)).isEqualTo("CONFIRMED NONE");
    assertThat(activeOccupancy(kept)).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    """
                    select kind || ':' || cardinality(channels) from booking_event
                    where booking_id = :k order by at, kind
                    """)
                .param("k", kept)
                .query(String.class)
                .list())
        .containsExactly("CREATED:0", "KEPT:0");
  }

  @Test
  void closingAndWithdrawingAskForEveryBookingNotYetDone() {
    Facility f = facility("impacto-peche", true);
    UUID use = whole(f, Map.of());
    UUID first = booked(reserve(person(), f, use, at(tomorrow(), 540)));
    UUID second = booked(reserve(person(), f, use, at(tomorrow().plusDays(3), 720)));
    String person = person();
    UUID mine = booked(reserve(person, f, use, at(tomorrow(), 660)));
    Api.Response byPerson =
        api().post(mine(f.slug()) + "/" + mine + "/cancel", null).ifMatch(0).token(person).send();
    assertThat(byPerson.status()).as(byPerson.toString()).isEqualTo(200);

    assertThat(affected(status(f, "TEMPORARILY_CLOSED", null)))
        .as("só as confirmadas, da primeira á última")
        .containsExactly(first.toString(), second.toString());
    Api.Response closed =
        status(
            f,
            "TEMPORARILY_CLOSED",
            List.of(decision(first, "CANCEL", "WEB"), decision(second, "KEEP")));
    assertThat(closed.status()).as(closed.toString()).isEqualTo(200);
    assertThat(bookingStatus(first)).isEqualTo("CANCELLED_BY_MUNICIPALITY NONE");
    assertThat(bookingStatus(second)).isEqualTo("CONFIRMED NONE");

    assertThat(status(f, "ACTIVE", null).status()).isEqualTo(200);
    assertThat(affected(status(f, "WITHDRAWN", null))).containsExactly(second.toString());
    Api.Response withdrawn =
        status(f, "WITHDRAWN", List.of(decision(second, "CANCEL", "EMAIL", "WEB")));
    assertThat(withdrawn.status()).as(withdrawn.toString()).isEqualTo(200);
    assertThat(bookingStatus(second)).isEqualTo("CANCELLED_BY_MUNICIPALITY NONE");
  }

  @Test
  void aUseWithBookingsIsNeitherArchivedNorChanged() {
    Facility f = facility("impacto-uso", true);
    JsonNode created = use(f, "Pista enteira", "WHOLE", Map.of());
    UUID use = id(created);
    booked(reserve(person(), f, use, at(tomorrow(), 600)));

    Api.Response archived =
        api()
            .post(url(f) + "/uses/" + use + "/archive", Map.of())
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(archived.status()).as(archived.toString()).isEqualTo(409);
    assertThat(archived.json().get("code").asString()).isEqualTo("facility.use-has-bookings");

    Map<String, Object> longer = new LinkedHashMap<>();
    longer.put("name", "Pista enteira");
    longer.put("rentalMode", "WHOLE");
    longer.put("slotMinutes", 90);
    longer.put("availability", "ALWAYS");
    Api.Response changed =
        api()
            .put(url(f) + "/uses/" + use, longer)
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(409);
    assertThat(changed.json().get("code").asString()).isEqualTo("facility.use-has-bookings");
  }

  @Test
  void shorterSeasonHoursAskOnlyForTheBookingsInTheLostHours() {
    Facility f = facility("impacto-tempada", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    UUID early = booked(reserve(person(), f, use, at(day, 540)));
    UUID late = booked(reserve(person(), f, use, at(day, 720)));

    JsonNode season =
        api()
            .get(url(f) + "/schedule")
            .token(TestTokens.manager(f.slug()))
            .send()
            .json()
            .get("seasons")
            .get(0);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Todo o ano");
    body.put("startDate", "2026-01-01");
    body.put("endDate", "2030-12-31");
    List<Map<String, Object>> week = new ArrayList<>();
    DAYS.forEach(
        d ->
            week.add(
                Map.of(
                    "day",
                    d,
                    "intervals",
                    List.of(Map.of("startMinute", OPENS, "endMinute", 660)))));
    body.put("week", week);
    String seasonUrl = url(f) + "/seasons/" + season.get("id").asString();
    long version = season.get("version").asLong();

    Api.Response asked =
        api().put(seasonUrl, body).ifMatch(version).token(TestTokens.manager(f.slug())).send();
    assertThat(affected(asked)).containsExactly(late.toString());

    body.put("decisions", List.of(decision(late, "CANCEL", "EMAIL")));
    Api.Response saved =
        api().put(seasonUrl, body).ifMatch(version).token(TestTokens.manager(f.slug())).send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(bookingStatus(late)).isEqualTo("CANCELLED_BY_MUNICIPALITY NONE");
    assertThat(bookingStatus(early)).isEqualTo("CONFIRMED NONE");
  }

  @Test
  void aBlockingUseAsksForTheOtherUsesOnlyOnItsWeekdays() {
    Facility f = facility("impacto-exclusivo", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    UUID hit = booked(reserve(person(), f, use, at(day, 600)));
    int dayBit = 1 << (day.getDayOfWeek().getValue() - 1);

    Map<String, Object> otherDays = tournament("Torneo de semana", day, 127 & ~dayBit);
    Api.Response elsewhere =
        api().post(url(f) + "/uses", otherDays).token(TestTokens.manager(f.slug())).send();
    assertThat(elsewhere.status()).as("noutros días da semana non pisa").isEqualTo(201);

    Facility g = facility(f.slug(), f.municipality(), "Pista de Piñeiros", true);
    UUID gUse = whole(g, Map.of());
    UUID gHit = booked(reserve(person(), g, gUse, at(day, 600)));
    Map<String, Object> onTheDay = tournament("Torneo", day, dayBit);
    Api.Response asked =
        api().post(url(g) + "/uses", onTheDay).token(TestTokens.manager(g.slug())).send();
    assertThat(affected(asked)).containsExactly(gHit.toString());

    onTheDay.put("decisions", List.of(decision(gHit, "CANCEL", "EMAIL")));
    Api.Response saved =
        api().post(url(g) + "/uses", onTheDay).token(TestTokens.manager(g.slug())).send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(201);
    assertThat(bookingStatus(gHit)).isEqualTo("CANCELLED_BY_MUNICIPALITY NONE");
    assertThat(bookingStatus(hit)).isEqualTo("CONFIRMED NONE");
  }

  static Map<String, Object> tournament(String name, LocalDate day, int weekdays) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("rentalMode", "WHOLE");
    body.put("slotMinutes", 60);
    body.put("availability", "DATES");
    body.put("blocksOthers", true);
    body.put("periods", List.of(Map.of("from", day.toString(), "to", day.plusDays(6).toString())));
    body.put("weekdays", weekdays);
    return body;
  }

  @Test
  void aFacilityThatEverHadBookingsIsNotDeleted() {
    Facility f = facility("impacto-borrar", true);
    UUID use = whole(f, Map.of());
    String person = person();
    UUID booking = booked(reserve(person, f, use, at(tomorrow(), 600)));
    assertThat(
            api()
                .post(mine(f.slug()) + "/" + booking + "/cancel", null)
                .ifMatch(0)
                .token(person)
                .send()
                .status())
        .isEqualTo(200);

    Api.Response read = api().get(url(f)).token(TestTokens.manager(f.slug())).send();
    assertThat(read.json().get("deletable").asBoolean()).isFalse();
    Api.Response refused =
        api().delete(url(f)).ifMatch(read.etagVersion()).token(TestTokens.manager(f.slug())).send();
    assertThat(refused.status()).as(refused.toString()).isEqualTo(409);
    assertThat(refused.json().get("code").asString()).isEqualTo("facility.has-bookings");
  }
}
