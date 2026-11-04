// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.StatementCounter;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * A xestión das reservas de punta a punta (plan do dominio 2, D2-07; maquetas 26 e 27): cada filtro
 * e cada orde da lista, a busca por nome sen acentos e por código, a portada de «Cobros», o
 * detalle, o calendario sen N+1, o cobro (válido, inválido e desfacer), a cancelación polo concello
 * coas canles, os permisos (o persoal cobra pero non cancela), outro concello e o rexistro de
 * actividade.
 */
class ManageBookingsIT extends AbstractBookingIT {

  /**
   * Un concello con dúas instalacións e catro reservas, feitas nesta orde: Ánxela Núñez (O Alto,
   * mañá ás 10, pendente), Brais (O Alto, pasado mañá ás 9, pagada), Carme (Piñeiros, mañá ás 11,
   * gratis) e Dora (O Alto, mañá ás 12, cancelada por ela).
   */
  record World(
      Facility alto,
      Facility pineiros,
      UUID paidUse,
      UUID freeUse,
      LocalDate day1,
      LocalDate day2,
      UUID ana,
      UUID brais,
      UUID carme,
      UUID dora,
      String carmeCode) {

    String slug() {
      return alto.slug();
    }
  }

  World world(String prefix) {
    Facility alto = facility(prefix, true);
    Facility pineiros = facility(alto.slug(), alto.municipality(), "Pista de Piñeiros", true);
    UUID paid = whole(alto, Map.of("price", Map.of("kind", "PAID", "amountCents", 800)));
    UUID free = whole(pineiros, Map.of("price", Map.of("kind", "FREE")));
    LocalDate day1 = tomorrow();
    LocalDate day2 = tomorrow().plusDays(1);
    UUID ana = booked(reserve(person(), alto, paid, at(day1, 600)));
    jdbc.sql(
            "update booking set holder_name = 'Ánxela Núñez',"
                + " holder_email = 'Anxela.Mar@example.org' where id = :k")
        .param("k", ana)
        .update();
    UUID brais = booked(reserve(person(), alto, paid, at(day2, 540)));
    Api.Response carme = reserve(person(), pineiros, free, at(day1, 660));
    UUID carmeId = booked(carme);
    String dora = person();
    Api.Response doraBooking = reserve(dora, alto, paid, at(day1, 720));
    UUID doraId = booked(doraBooking);
    Api.Response cancelled =
        api()
            .post(mine(alto.slug()) + "/" + doraId + "/cancel", null)
            .ifMatch(0)
            .token(dora)
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    World world =
        new World(alto, pineiros, paid, free, day1, day2, ana, brais, carmeId, doraId, code(carme));
    Api.Response paidNow = payment(TestTokens.staff(world.slug()), world, brais, 0, "PAID");
    assertThat(paidNow.status()).as(paidNow.toString()).isEqualTo(200);
    return world;
  }

  static UUID booked(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return id(response.json());
  }

  static String bookings(String slug) {
    return "/api/v1/municipalities/" + slug + "/bookings";
  }

  Api.Response payment(String token, World w, UUID booking, long version, String status) {
    return api()
        .put(bookings(w.slug()) + "/" + booking + "/payment", Map.of("status", status))
        .ifMatch(version)
        .token(token)
        .send();
  }

  Api.Response cancelByMunicipality(
      String token, World w, UUID booking, long version, List<String> channels) {
    return api()
        .post(bookings(w.slug()) + "/" + booking + "/cancel", Map.of("channels", channels))
        .ifMatch(version)
        .token(token)
        .send();
  }

  /** Os ids da lista co filtro {@code query}, na orde da resposta. */
  List<UUID> list(World w, String query) {
    Api.Response response =
        api().get(bookings(w.slug()) + "?" + query).token(TestTokens.staff(w.slug())).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return ids(response.json().get("items"));
  }

  static List<UUID> ids(JsonNode items) {
    List<UUID> ids = new ArrayList<>();
    items.forEach(item -> ids.add(UUID.fromString(item.get("id").asString())));
    return ids;
  }

  @Test
  void eachFilterAndOrderOfTheList() {
    World w = world("xestion-lista");

    assertThat(list(w, ""))
        .as("as máis recentes primeiro")
        .containsExactly(w.dora(), w.carme(), w.brais(), w.ana());
    assertThat(list(w, "sort=START"))
        .as("por día e hora")
        .containsExactly(w.ana(), w.carme(), w.dora(), w.brais());
    assertThat(list(w, "sort=START&from=" + w.day1() + "&to=" + w.day1()))
        .containsExactly(w.ana(), w.carme(), w.dora());
    assertThat(list(w, "from=" + w.day2())).containsExactly(w.brais());
    assertThat(list(w, "sort=START&to=" + w.day1())).containsExactly(w.ana(), w.carme(), w.dora());
    assertThat(list(w, "facilityId=" + w.pineiros().id())).containsExactly(w.carme());
    assertThat(list(w, "facilityId=" + w.pineiros().id() + "&facilityId=" + w.alto().id()))
        .hasSize(4);
    assertThat(list(w, "payment=NONE")).as("gratis").containsExactly(w.carme());
    assertThat(list(w, "payment=PENDING")).containsExactly(w.dora(), w.ana());
    assertThat(list(w, "payment=PAID&payment=NONE")).containsExactly(w.carme(), w.brais());
    assertThat(list(w, "status=CANCELLED_BY_PERSON")).containsExactly(w.dora());
    assertThat(list(w, "status=CONFIRMED")).containsExactly(w.carme(), w.brais(), w.ana());
    assertThat(list(w, "status=CONFIRMED&payment=PENDING&facilityId=" + w.alto().id()))
        .containsExactly(w.ana());

    Api.Response second =
        api()
            .get(bookings(w.slug()) + "?sort=START&page=1&size=3")
            .token(TestTokens.manager(w.slug()))
            .send();
    assertThat(second.status()).isEqualTo(200);
    assertThat(ids(second.json().get("items"))).containsExactly(w.brais());
    assertThat(second.json().get("totalItems").asLong()).isEqualTo(4);

    JsonNode ana =
        api()
            .get(bookings(w.slug()) + "?q=anxela")
            .token(TestTokens.staff(w.slug()))
            .send()
            .json()
            .get("items")
            .get(0);
    assertThat(ana.get("holderName").asString()).isEqualTo("Ánxela Núñez");
    assertThat(ana.get("facilityName").asString()).isEqualTo("Pavillón do Alto");
    assertThat(ana.get("useName").asString()).isEqualTo("Pista enteira");
    assertThat(ana.get("paymentStatus").asString()).isEqualTo("PENDING");
    assertThat(ana.get("price").get("amountCents").asInt()).isEqualTo(800);
    assertThat(ana.get("reservedAt").asString()).isNotBlank();
    assertThat(ana.has("holderEmail")).as("o correo só no detalle").isFalse();

    Api.Response inverted =
        api()
            .get(bookings(w.slug()) + "?from=" + w.day2() + "&to=" + w.day1())
            .token(TestTokens.staff(w.slug()))
            .send();
    assertThat(inverted.status()).isEqualTo(400);
  }

  @Test
  void searchesByNameOrEmailWithoutAccentsOrByCode() {
    World w = world("xestion-busca");

    assertThat(list(w, "q=anxela")).containsExactly(w.ana());
    assertThat(list(w, "q=N%C3%9A%C3%91EZ")).as("NÚÑEZ").containsExactly(w.ana());
    assertThat(list(w, "q=xela%20nu")).containsExactly(w.ana());
    assertThat(list(w, "q=anxela.mar%40")).as("o correo, en minúsculas").containsExactly(w.ana());
    assertThat(list(w, "q=" + w.carmeCode())).containsExactly(w.carme());
    assertThat(list(w, "q=" + w.carmeCode().substring(4).toLowerCase(java.util.Locale.ROOT)))
        .as("só os catro caracteres, en minúsculas")
        .containsExactly(w.carme());
    assertThat(list(w, "q=%25")).as("o comodín é texto").isEmpty();
    assertThat(list(w, "q=ninguen")).isEmpty();
  }

  @Test
  void theSummaryCountsTheCollectionTabs() {
    World w = world("xestion-cobros");
    Api.Response cancelled =
        cancelByMunicipality(
            TestTokens.manager(w.slug()), w, w.brais(), 1, List.of("EMAIL", "WEB"));
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);

    Api.Response response =
        api().get(bookings(w.slug()) + "/summary").token(TestTokens.staff(w.slug())).send();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode summary = response.json();
    assertThat(ids(summary.get("latest"))).containsExactly(w.dora(), w.carme(), w.brais());
    assertThat(summary.get("pendingPayment").get("count").asLong()).isEqualTo(1);
    assertThat(ids(summary.get("pendingPayment").get("items"))).containsExactly(w.ana());
    assertThat(summary.get("toRefund").get("count").asLong()).isEqualTo(1);
    assertThat(ids(summary.get("toRefund").get("items"))).containsExactly(w.brais());
    assertThat(summary.get("cancelled").get("count").asLong()).isEqualTo(2);
    assertThat(ids(summary.get("cancelled").get("items"))).containsExactly(w.dora(), w.brais());

    Api.Response one =
        api()
            .get(bookings(w.slug()) + "/summary?size=1")
            .token(TestTokens.manager(w.slug()))
            .send();
    assertThat(ids(one.json().get("cancelled").get("items"))).containsExactly(w.dora());
    assertThat(one.json().get("cancelled").get("count").asLong()).isEqualTo(2);
  }

  @Test
  void theDetailHasTheEmailTheAddressAndWhatCanBeDone() {
    World w = world("xestion-detalle");

    Api.Response response =
        api().get(bookings(w.slug()) + "/" + w.ana()).token(TestTokens.staff(w.slug())).send();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    assertThat(response.etagVersion()).isZero();
    JsonNode b = response.json();
    assertThat(b.get("code").asString()).startsWith("FUR-");
    assertThat(b.get("holderName").asString()).isEqualTo("Ánxela Núñez");
    assertThat(b.get("holderEmail").asString()).endsWith("@example.org");
    assertThat(b.get("facilityAddress").asString()).isEqualTo("Rúa do Alto, 12, 15570 Narón");
    assertThat(b.get("paymentTargets").toString()).isEqualTo("[\"PAID\"]");
    assertThat(b.get("cancellable").asBoolean()).isTrue();
    assertThat(b.get("cancelChannels").toString()).isEqualTo("[\"EMAIL\",\"WEB\"]");

    JsonNode dora =
        api()
            .get(bookings(w.slug()) + "/" + w.dora())
            .token(TestTokens.manager(w.slug()))
            .send()
            .json();
    assertThat(dora.get("cancellable").asBoolean()).isFalse();
    assertThat(dora.get("cancelChannels").size()).isZero();
    assertThat(dora.get("paymentTargets").size()).as("cancelada sen pagar").isZero();
    assertThat(dora.get("cancelledAt").asString()).isNotBlank();
  }

  @Test
  void anotherMunicipalityDoesNotSeeTheBooking() {
    World w = world("xestion-alleo");
    String other = uniqueSlug("xestion-outro");
    createMunicipality(other);

    Api.Response detail =
        api().get(bookings(other) + "/" + w.ana()).token(TestTokens.manager(other)).send();
    Api.Response paid =
        api()
            .put(bookings(other) + "/" + w.ana() + "/payment", Map.of("status", "PAID"))
            .ifMatch(0)
            .token(TestTokens.manager(other))
            .send();
    Api.Response cancelled =
        api()
            .post(bookings(other) + "/" + w.ana() + "/cancel", Map.of("channels", List.of()))
            .ifMatch(0)
            .token(TestTokens.manager(other))
            .send();
    Api.Response list = api().get(bookings(other)).token(TestTokens.manager(other)).send();
    Api.Response foreignRole =
        api().get(bookings(w.slug()) + "/" + w.ana()).token(TestTokens.manager(other)).send();

    assertThat(detail.status()).isEqualTo(404);
    assertThat(code(detail)).isEqualTo("booking.not-found");
    assertThat(paid.status()).isEqualTo(404);
    assertThat(cancelled.status()).isEqualTo(404);
    assertThat(list.json().get("totalItems").asLong()).isZero();
    assertThat(foreignRole.status()).as("sen rol nese concello").isEqualTo(403);
  }

  @Test
  void paymentTransitionsValidInvalidAndUndoneAreAudited() {
    World w = world("xestion-cobro");
    String staff = TestTokens.staff(w.slug());

    Api.Response paid = payment(staff, w, w.ana(), 0, "PAID");
    assertThat(paid.status()).as(paid.toString()).isEqualTo(200);
    assertThat(paid.etagVersion()).isEqualTo(1);
    assertThat(paid.json().get("paymentStatus").asString()).isEqualTo("PAID");
    assertThat(paid.json().get("paymentTargets").toString()).isEqualTo("[\"PENDING\"]");

    Api.Response twice = payment(staff, w, w.ana(), 1, "PAID");
    assertThat(twice.status()).isEqualTo(422);
    assertThat(code(twice)).isEqualTo("booking.payment-transition-invalid");
    Api.Response refunded = payment(staff, w, w.ana(), 1, "REFUNDED");
    assertThat(refunded.status()).as("só unha cancelada se devolve").isEqualTo(422);
    Api.Response free = payment(staff, w, w.carme(), 0, "PAID");
    assertThat(free.status()).as("gratis: nada que cobrar").isEqualTo(422);
    Api.Response none = payment(staff, w, w.ana(), 1, "NONE");
    assertThat(none.status()).isEqualTo(422);
    Api.Response cancelledPending = payment(staff, w, w.dora(), 1, "PAID");
    assertThat(cancelledPending.status()).as("cancelada sen pagar").isEqualTo(422);

    Api.Response stale = payment(staff, w, w.ana(), 0, "PENDING");
    assertThat(stale.status()).isEqualTo(412);
    Api.Response withoutIfMatch =
        api()
            .put(bookings(w.slug()) + "/" + w.ana() + "/payment", Map.of("status", "PENDING"))
            .token(staff)
            .send();
    assertThat(withoutIfMatch.status()).isEqualTo(428);

    Api.Response undone = payment(staff, w, w.ana(), 1, "PENDING");
    assertThat(undone.status()).as(undone.toString()).isEqualTo(200);
    assertThat(undone.json().get("paymentStatus").asString()).isEqualTo("PENDING");

    assertThat(
            jdbc.sql("select kind from booking_event where booking_id = :k order by at, kind")
                .param("k", w.ana())
                .query(String.class)
                .list())
        .containsExactly("CREATED", "PAID", "PAYMENT_UNDONE");
    assertThat(
            jdbc.sql(
                    """
                    select outcome || ' ' || (details ->> 'to')
                      || coalesce(' ' || (details ->> 'code'), '')
                    from audit_event
                    where municipality_id = :k and action = 'booking.payment'
                      and details ->> 'bookingId' = :b
                    order by occurred_at
                    """)
                .param("k", w.alto().municipality())
                .param("b", w.ana().toString())
                .query(String.class)
                .list())
        .containsExactly(
            "SUCCESS PAID",
            "FAILURE PAID booking.payment-transition-invalid",
            "FAILURE REFUNDED booking.payment-transition-invalid",
            "FAILURE NONE booking.payment-transition-invalid",
            "FAILURE PENDING common.stale-version",
            "SUCCESS PENDING");
  }

  @Test
  void staffMarksPaidButOnlyTheManagerCancelsWithChannels() {
    World w = world("xestion-cancelar");

    Api.Response byStaff =
        cancelByMunicipality(TestTokens.staff(w.slug()), w, w.brais(), 1, List.of("EMAIL"));
    assertThat(byStaff.status()).isEqualTo(403);
    Api.Response phone =
        cancelByMunicipality(TestTokens.manager(w.slug()), w, w.brais(), 1, List.of("PHONE"));
    assertThat(phone.status()).as("«Chamar» non é unha canle").isEqualTo(400);

    List<Object> seen = new CopyOnWriteArrayList<>();
    ApplicationListener<ApplicationEvent> listener =
        e -> {
          if (e instanceof PayloadApplicationEvent<?> p
              && p.getPayload() instanceof BookingCancelled) {
            seen.add(p.getPayload());
          }
        };
    multicaster.addApplicationListener(listener);
    Api.Response cancelled;
    try {
      cancelled =
          cancelByMunicipality(
              TestTokens.manager(w.slug()), w, w.brais(), 1, List.of("WEB", "EMAIL"));
    } finally {
      multicaster.removeApplicationListener(listener);
    }

    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(cancelled.etagVersion()).isEqualTo(2);
    assertThat(cancelled.json().get("status").asString()).isEqualTo("CANCELLED_BY_MUNICIPALITY");
    assertThat(cancelled.json().get("paymentStatus").asString()).isEqualTo("TO_REFUND");
    assertThat(cancelled.json().get("paymentTargets").toString()).isEqualTo("[\"REFUNDED\"]");
    assertThat(seen)
        .containsExactly(
            new BookingCancelled(
                w.alto().municipality(),
                w.brais(),
                BookingCancelled.By.MUNICIPALITY,
                Set.of(BookingCancelled.Channel.EMAIL, BookingCancelled.Channel.WEB)));
    assertThat(
            jdbc.sql(
                    """
                    select array_to_string(channels, ',') from booking_event
                    where booking_id = :k and kind = 'CANCELLED_BY_MUNICIPALITY'
                    """)
                .param("k", w.brais())
                .query(String.class)
                .single())
        .isEqualTo("EMAIL,WEB");
    assertThat(
            count(
                "select count(*) from booking_occupancy where booking_id = :k and active",
                w.brais()))
        .as("o oco queda libre")
        .isZero();

    Api.Response again =
        cancelByMunicipality(TestTokens.manager(w.slug()), w, w.brais(), 2, List.of());
    assertThat(again.status()).isEqualTo(409);
    assertThat(code(again)).isEqualTo("booking.not-cancellable");

    String staff = TestTokens.staff(w.slug());
    Api.Response refunded = payment(staff, w, w.brais(), 2, "REFUNDED");
    assertThat(refunded.status()).as(refunded.toString()).isEqualTo(200);
    assertThat(refunded.json().get("paymentStatus").asString()).isEqualTo("REFUNDED");
    Api.Response paidAgain = payment(staff, w, w.brais(), 3, "PAID");
    assertThat(paidAgain.status()).as("cancelada: xa non se cobra").isEqualTo(422);
    Api.Response undone = payment(staff, w, w.brais(), 3, "TO_REFUND");
    assertThat(undone.status()).isEqualTo(200);
    assertThat(undone.json().get("paymentStatus").asString()).isEqualTo("TO_REFUND");

    Api.Response withoutChannels =
        cancelByMunicipality(TestTokens.manager(w.slug()), w, w.ana(), 0, List.of());
    assertThat(withoutChannels.status()).as("sen aviso tamén vale").isEqualTo(200);
    assertThat(withoutChannels.json().get("paymentStatus").asString()).isEqualTo("PENDING");

    assertThat(
            jdbc.sql(
                    """
                    select outcome || coalesce(' ' || (details ->> 'code'), '')
                    from audit_event
                    where municipality_id = :k and action = 'booking.cancel'
                    order by occurred_at
                    """)
                .param("k", w.alto().municipality())
                .query(String.class)
                .list())
        .as("o 403 é do permiso e o 400 da canle, antes do caso de uso")
        .containsExactly("SUCCESS", "FAILURE booking.not-cancellable", "SUCCESS");
  }

  @Test
  void theCalendarShowsTheConfirmedBookingsAndCountsWithoutNPlusOne() {
    World w = world("xestion-calendario");
    String staff = TestTokens.staff(w.slug());
    String base = bookings(w.slug()) + "/calendar?date=" + w.day1();

    JsonNode day = api().get(base + "&view=DAY").token(staff).send().json();
    assertThat(day.get("from").asString()).isEqualTo(w.day1().toString());
    assertThat(day.get("to").asString()).isEqualTo(w.day1().toString());
    assertThat(ids(day.get("blocks")))
        .as("a cancelada non ocupa")
        .containsExactly(w.ana(), w.carme());
    assertThat(day.get("blocks").get(1).get("facilityName").asString())
        .isEqualTo("Pista de Piñeiros");
    assertThat(day.get("days").size()).isZero();
    assertThat(openings(day))
        .as("os tramos abertos de cada instalación, para raiar o resto")
        .contains(
            w.alto().id() + " " + w.day1() + " " + OPENS + "-" + CLOSES,
            w.pineiros().id() + " " + w.day1() + " " + OPENS + "-" + CLOSES);

    JsonNode oneFacility =
        api().get(base + "&view=DAY&facilityId=" + w.pineiros().id()).token(staff).send().json();
    assertThat(ids(oneFacility.get("blocks"))).containsExactly(w.carme());

    JsonNode week =
        api().get(base + "&view=WEEK&facilityId=" + w.alto().id()).token(staff).send().json();
    LocalDate monday = w.day1().with(java.time.DayOfWeek.MONDAY);
    assertThat(week.get("from").asString()).isEqualTo(monday.toString());
    assertThat(week.get("to").asString()).isEqualTo(monday.plusDays(6).toString());
    List<UUID> inWeek = ids(week.get("blocks"));
    assertThat(openings(week))
        .as("os sete días, só da instalación pedida")
        .hasSize(7)
        .allMatch(o -> o.startsWith(w.alto().id().toString()));
    assertThat(inWeek).contains(w.ana()).doesNotContain(w.carme(), w.dora());
    assertThat(inWeek.contains(w.brais())).isEqualTo(!w.day2().isAfter(monday.plusDays(6)));

    JsonNode allWeek = api().get(base + "&view=WEEK").token(staff).send().json();
    assertThat(allWeek.get("blocks").size()).isZero();
    List<String> hours = new ArrayList<>();
    allWeek
        .get("hours")
        .forEach(
            h ->
                hours.add(
                    h.get("date").asString()
                        + " "
                        + h.get("hour").asInt()
                        + " "
                        + h.get("count").asLong()));
    assertThat(hours).contains(w.day1() + " 10 1", w.day1() + " 11 1");
    assertThat(hours).noneMatch(h -> h.startsWith(w.day1() + " 12 "));

    // Máis reservas no día: o mes segue sendo unha consulta.
    for (int hour : List.of(9, 11, 13)) {
      booked(reserve(person(), w.alto(), w.paidUse(), at(w.day1(), hour * 60)));
    }
    booked(reserve(person(), w.pineiros(), w.freeUse(), at(w.day1(), 540)));
    Api.Response month;
    List<String> statements;
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      month = api().get(base + "&view=MONTH").token(staff).send();
      statements = recording.statements();
    }
    assertThat(month.status()).as(month.toString()).isEqualTo(200);
    assertThat(month.json().get("from").asString())
        .isEqualTo(w.day1().withDayOfMonth(1).toString());
    assertThat(month.json().get("blocks").size()).isZero();
    assertThat(month.json().get("openings").size()).as("o mes non raia horas").isZero();
    List<String> days = new ArrayList<>();
    month
        .json()
        .get("days")
        .forEach(d -> days.add(d.get("date").asString() + " " + d.get("count").asLong()));
    assertThat(days).contains(w.day1() + " 6");
    assertThat(statements.stream().filter(s -> s.contains("from booking")).count())
        .as("unha consulta para todo o mes: %s", statements)
        .isEqualTo(1);
    assertThat(statements).noneMatch(s -> s.contains("facility_use"));

    List<String> dayStatements;
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      JsonNode busy = api().get(base + "&view=DAY").token(staff).send().json();
      assertThat(busy.get("blocks").size()).isEqualTo(6);
      dayStatements = recording.statements();
    }
    assertThat(dayStatements.stream().filter(s -> s.contains("from booking")).count()).isEqualTo(1);
    for (String table : List.of("season", "schedule_exception", "block")) {
      assertThat(
              dayStatements.stream().filter(s -> s.matches("(?s).*from " + table + "\\b(?!_).*")))
          .as("o horario de todas as instalacións nunha lectura de %s", table)
          .hasSize(1);
    }
    assertThat(dayStatements.stream().filter(s -> s.matches("(?s).*from facility_use\\b(?!_).*")))
        .as("os nomes dos usos nunha lectura, non un por reserva")
        .hasSize(1);
  }

  /** «instalación día inicio-fin» de cada tramo aberto do calendario. */
  private static List<String> openings(JsonNode calendar) {
    List<String> openings = new ArrayList<>();
    calendar
        .get("openings")
        .forEach(
            o ->
                openings.add(
                    o.get("facilityId").asString()
                        + " "
                        + o.get("date").asString()
                        + " "
                        + o.get("startMinute").asInt()
                        + "-"
                        + o.get("endMinute").asInt()));
    return openings;
  }
}
