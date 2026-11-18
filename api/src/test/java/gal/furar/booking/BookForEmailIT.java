// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * No mostrador, a reserva é do correo (ADR-0031, punto 7), de punta a punta: con conta, nace ligada
 * e sae en «as miñas reservas»; sen conta, lígase cando a persoa entra co correo verificado (e non
 * sen verificar), tamén noutro concello ao visitalo; sen correo non se liga; unha praza por correo
 * entre o mostrador e internet; as persoas a cargo da conta do correo, cada lectura no rexistro e
 * con máximo por hora; acompañantes sen repetir; e corrixir un correo mal escrito.
 */
class BookForEmailIT extends AbstractBookingIT {

  static final String DEPENDENTS = "/api/v1/me/dependents";

  static String bookings(String slug) {
    return "/api/v1/municipalities/" + slug + "/bookings";
  }

  /** Unha sesión da persoa, co {@code auth_time} que trae todo token real. */
  static String session(String subject) {
    return TestTokens.withoutRolesClaim(
        subject, Map.of("auth_time", Instant.now().getEpochSecond(), "name", "Persoa " + subject));
  }

  static String email(String subject) {
    return subject + "@example.org";
  }

  /** A persoa entra na web: a súa conta queda entre as coñecidas polo seu correo. */
  String known(String subject) {
    String token = session(subject);
    assertThat(api().get(DEPENDENTS).token(token).send().status()).isEqualTo(200);
    return token;
  }

  UUID dependent(String token, String name, int birthYear) {
    Api.Response created =
        api()
            .post(DEPENDENTS, Map.of("name", name, "birthYear", birthYear, "inMyCare", true))
            .token(token)
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  Api.Response bookFor(
      String staff,
      Facility f,
      UUID use,
      Instant start,
      Map<String, Object> holder,
      Map<String, Object> participants) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("holder", holder);
    if (participants != null) {
      body.put("participants", participants);
    }
    return api()
        .post(bookings(f.slug()), body)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .token(staff)
        .send();
  }

  Api.Response holderDependents(String staff, Facility f, Map<String, Object> body) {
    return api().post(bookings(f.slug()) + "/holder-dependents", body).token(staff).send();
  }

  JsonNode myBookings(Facility f, String token) {
    Api.Response list = api().get(mine(f.slug())).token(token).send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    return list.json().get("items");
  }

  static List<String> who(JsonNode booking) {
    List<String> names = new ArrayList<>();
    booking
        .get("participants")
        .forEach(
            p ->
                names.add(
                    p.get("kind").asString()
                        + ":"
                        + p.get("name").asString()
                        + (p.path("birthYear").isNumber()
                            ? ":" + p.get("birthYear").asInt()
                            : "")));
    return names;
  }

  @Test
  void withAnAccountTheBookingIsBornLinkedWithItsDependentsAndCompanions() {
    Facility f = facility("mostrador-conta", true);
    UUID swim =
        id(
            use(
                f,
                "Nado libre",
                "SEATS",
                Map.of("capacity", 6, "price", Map.of("kind", "PAID", "amountCents", 200))));
    Instant nine = at(tomorrow(), OPENS);
    String staff = TestTokens.staff(f.slug());
    String sub = "ana-" + UUID.randomUUID();
    String ana = known(sub);
    UUID uxia = dependent(ana, "Uxía Rey", 2016);

    // O persoal escribe o correo e ve as fichas desa conta; a lectura queda no rexistro.
    Api.Response read = holderDependents(staff, f, Map.of("email", email(sub).toUpperCase()));
    assertThat(read.status()).as(read.toString()).isEqualTo(200);
    assertThat(read.json().size()).isEqualTo(1);
    assertThat(read.json().get(0).get("name").asString()).isEqualTo("Uxía Rey");
    assertThat(read.json().get(0).get("birthYear").asInt()).isEqualTo(2016);
    assertThat(read.body()).as("o sub nunca sae").doesNotContain(sub);

    Api.Response created =
        bookFor(
            staff,
            f,
            swim,
            nine,
            Map.of("name", "Ana Rey", "email", email(sub), "phone", "600 111 222"),
            Map.of(
                "self",
                true,
                "dependentIds",
                List.of(uxia),
                "companions",
                List.of(Map.of("name", "Carme Souto", "birthYear", 1950))));

    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    JsonNode d = created.json();
    assertThat(d.get("seats").asInt()).isEqualTo(3);
    assertThat(d.get("price").get("amountCents").asInt()).isEqualTo(600);
    assertThat(who(d))
        .as("a xestión ve o ano das persoas a cargo e acompañantes")
        .containsExactly("SELF:Ana Rey", "DEPENDENT:Uxía Rey:2016", "COMPANION:Carme Souto:1950");
    assertThat(d.get("holderAccount").asBoolean()).as("fíxoa o persoal: corríxese").isFalse();
    assertThat(d.get("cancelChannels").toString()).contains("WEB");
    UUID id = id(d);
    assertThat(
            jdbc.sql("select holder_subject from booking where id = :id")
                .param("id", id)
                .query(String.class)
                .single())
        .isEqualTo(sub);

    // É súa desde ese momento: sae en «as miñas reservas», sen teléfono nin campos, e cancélaa.
    JsonNode mineNow = myBookings(f, ana);
    assertThat(mineNow.size()).isEqualTo(1);
    assertThat(mineNow.get(0).get("id").asString()).isEqualTo(id.toString());
    assertThat(who(mineNow.get(0)))
        .containsExactly("SELF:Ana Rey", "DEPENDENT:Uxía Rey", "COMPANION:Carme Souto");
    assertThat(mineNow.get(0).toString()).doesNotContain("600 111 222");

    // Unha praza por persoa entre o mostrador e internet: o correo xa ten praza a esa hora.
    Api.Response online = reserve(ana, f, swim, nine);
    assertThat(online.status()).isEqualTo(409);
    assertThat(code(online)).isEqualTo("booking.seat-taken-by-you");
    Api.Response again =
        bookFor(staff, f, swim, nine, Map.of("name", "Ana", "email", email(sub)), null);
    assertThat(again.status()).isEqualTo(409);
    assertThat(code(again)).isEqualTo("booking.seat-taken-by-you");

    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + id + "/cancel", null).ifMatch(0).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(count("select taken from use_slot where use_id = :k", swim)).isZero();
  }

  @Test
  void withoutAnAccountItIsLinkedWhenThePersonArrivesWithThatVerifiedEmail() {
    Facility f = facility("mostrador-sen-conta", true);
    Facility other = facility("mostrador-outro", true);
    UUID court = whole(f, Map.of());
    UUID otherCourt = whole(other, Map.of());
    String staff = TestTokens.staff(f.slug());
    String sub = "bea-" + UUID.randomUUID();
    Map<String, Object> bea = Map.of("name", "Bea Lago", "email", email(sub).toUpperCase());

    UUID id = id(bookFor(staff, f, court, at(tomorrow(), OPENS), bea, null).json());
    UUID elsewhere =
        id(
            bookFor(
                    TestTokens.staff(other.slug()),
                    other,
                    otherCourt,
                    at(tomorrow(), OPENS),
                    bea,
                    null)
                .json());
    UUID noMail =
        id(
            bookFor(staff, f, court, at(tomorrow(), 600), Map.of("name", "Sen Correo"), null)
                .json());
    assertThat(count("select count(*) from booking where id = :k and holder_subject is null", id))
        .as("sen conta non se crea ningunha: a reserva queda a nome do correo")
        .isEqualTo(1);

    // Co correo sen verificar non se liga nada.
    api().get(mine(f.slug())).token(TestTokens.person(sub, false)).send();
    assertThat(count("select count(*) from booking where id = :k and holder_subject is null", id))
        .isEqualTo(1);

    // Entra co correo verificado: a reserva deste concello pasa á súa conta, soa.
    String token = session(sub);
    JsonNode mineNow = myBookings(f, token);
    assertThat(mineNow.size()).isEqualTo(1);
    assertThat(mineNow.get(0).get("id").asString()).isEqualTo(id.toString());
    assertThat(
            jdbc.sql(
                    "select kind || ' ' || actor_subject from booking_event where booking_id = :id"
                        + " order by at, id")
                .param("id", id)
                .query(String.class)
                .list())
        .as("fila LINKED, coa propia persoa")
        .last()
        .isEqualTo("LINKED " + sub);
    assertThat(
            jdbc.sql("select count(*) from audit_event where actor_subject = :sub")
                .param("sub", sub)
                .query(Integer.class)
                .single())
        .as("ligar non vai ao rexistro de actividade, que é do persoal")
        .isZero();

    // Repetilo non cambia nada.
    myBookings(f, token);
    assertThat(count("select count(*) from booking_event where booking_id = :k", id)).isEqualTo(2);

    // A doutro concello lígase ao visitalo; a sen correo, nunca.
    assertThat(
            count(
                "select count(*) from booking where id = :k and holder_subject is null", elsewhere))
        .isEqualTo(1);
    assertThat(myBookings(other, token).size()).isEqualTo(1);
    assertThat(
            count("select count(*) from booking where id = :k and holder_subject is null", noMail))
        .isEqualTo(1);
  }

  @Test
  void readingDependentsNeverTellsWhetherTheEmailHasAnAccountAndIsLoggedAndLimited() {
    Facility f = facility("mostrador-lecturas", true);
    String staff = TestTokens.staff(f.slug());
    String sub = "con-" + UUID.randomUUID();
    known(sub);

    Api.Response noAccount = holderDependents(staff, f, Map.of("email", "ninguen@example.org"));
    Api.Response noDependents = holderDependents(staff, f, Map.of("email", email(sub)));

    assertThat(noAccount.status()).isEqualTo(200);
    assertThat(noDependents.status()).isEqualTo(200);
    assertThat(noAccount.body()).as("a mesma resposta: lista baleira").isEqualTo("[]");
    assertThat(noDependents.body()).isEqualTo("[]");

    // Só quen reserva no nome doutra persoa: nin a veciñanza nin outro concello.
    assertThat(holderDependents(person(), f, Map.of("email", email(sub))).status()).isEqualTo(403);
    assertThat(
            holderDependents(TestTokens.staff(uniqueSlug("outro")), f, Map.of("email", email(sub)))
                .status())
        .isEqualTo(403);
    assertThat(holderDependents(staff, f, Map.of()).status()).isEqualTo(400);

    // Cada lectura boa queda no rexistro, coa pegada do correo e sen o correo.
    List<String> details =
        jdbc.sql(
                """
                select details::text from audit_event
                where action = 'booking.dependents-read' and outcome = 'SUCCESS'
                  and municipality_id = :m
                order by occurred_at, id
                """)
            .param("m", f.municipality())
            .query(String.class)
            .list();
    assertThat(details).hasSize(2);
    assertThat(details.get(1)).contains("\"count\": 0").contains("emailHash");
    assertThat(String.join(" ", details)).doesNotContain("@example.org").doesNotContain(sub);
    String hash = details.get(1).replaceAll(".*\"emailHash\": \"([0-9a-f]{64})\".*", "$1");
    assertThat(hash).hasSize(64);
    holderDependents(staff, f, Map.of("email", email(sub).toUpperCase()));
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'booking.dependents-read'"
                        + " and details ->> 'emailHash' = :hash")
                .param("hash", hash)
                .query(Integer.class)
                .single())
        .as("o mesmo correo, escrito doutro xeito, dá a mesma pegada")
        .isEqualTo(2);

    // Como moito 30 por persoa do persoal e hora.
    for (int i = 3; i < 30; i++) {
      assertThat(holderDependents(staff, f, Map.of("email", "x" + i + "@example.org")).status())
          .isEqualTo(200);
    }
    Api.Response limited = holderDependents(staff, f, Map.of("email", email(sub)));
    assertThat(limited.status()).as(limited.toString()).isEqualTo(429);
    assertThat(code(limited)).isEqualTo("booking.dependents-rate-limited");
    assertThat(Long.parseLong(limited.header("Retry-After"))).isBetween(1L, 3601L);
  }

  @Test
  void companionsAreNotRepeatedAndAWorkAccountEmailIsRefused() {
    Facility f = facility("mostrador-acompanantes", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 20)));
    Instant nine = at(tomorrow(), OPENS);
    String staff = TestTokens.staff(f.slug());
    Map<String, Object> carme = Map.of("name", "Carme Souto");

    Api.Response repeated =
        bookFor(
            staff,
            f,
            swim,
            nine,
            carme,
            Map.of(
                "companions",
                List.of(
                    Map.of("name", "Xoán Souto", "birthYear", 2012),
                    Map.of("name", "xoán souto", "birthYear", 2012))));
    assertThat(repeated.status()).isEqualTo(422);
    assertThat(code(repeated)).isEqualTo("booking.participant-duplicate");

    List<Map<String, Object>> eleven = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      eleven.add(Map.of("name", "Acompañante " + i, "birthYear", 2010));
    }
    Api.Response twelve =
        bookFor(staff, f, swim, nine, carme, Map.of("self", true, "companions", eleven));
    assertThat(twelve.status()).isEqualTo(422);
    assertThat(code(twelve)).isEqualTo("booking.too-many-seats");
    assertThat(twelve.json().get("params").get("max").asInt()).isEqualTo(11);

    // Sen correo nin conta: a persoa e os acompañantes, e ningunha ficha.
    Api.Response created =
        bookFor(
            staff,
            f,
            swim,
            nine,
            carme,
            Map.of("companions", List.of(Map.of("name", "Xoán Souto", "birthYear", 2012))));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(who(created.json()))
        .containsExactly("SELF:Carme Souto", "COMPANION:Xoán Souto:2012");
    assertThat(
            jdbc.sql(
                    "select string_agg(left(person_key, 2), ',' order by position)"
                        + " from booking_participant where booking_id = :id")
                .param("id", id(created.json()))
                .query(String.class)
                .single())
        .as("sen correo e acompañante: só se comparan dentro da súa reserva")
        .isEqualTo("w:,w:");
    // Sen correo non hai «unha praza por persoa» entre reservas.
    assertThat(bookFor(staff, f, swim, nine, carme, null).status()).isEqualTo(201);

    // O correo dunha conta de traballo non se liga nunca.
    String worker = "persoal-" + UUID.randomUUID();
    String workerToken =
        TestTokens.withClaims(
            Map.of(
                "sub",
                worker,
                "email",
                email(worker),
                "email_verified",
                true,
                "auth_time",
                Instant.now().getEpochSecond()),
            f.slug() + ":STAFF");
    assertThat(api().get(bookings(f.slug())).token(workerToken).send().status()).isEqualTo(200);
    Api.Response work =
        bookFor(staff, f, swim, nine, Map.of("name", "Persoal", "email", email(worker)), null);
    assertThat(work.status()).as(work.toString()).isEqualTo(422);
    assertThat(code(work)).isEqualTo("booking.work-account-email");
    assertThat(holderDependents(staff, f, Map.of("email", email(worker))).body()).isEqualTo("[]");
  }

  @Test
  void correctingAMistypedEmailMovesTheBookingToTheRightPerson() {
    Facility f = facility("mostrador-corrixir", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 20)));
    Instant nine = at(tomorrow(), OPENS);
    String staff = TestTokens.staff(f.slug());
    String wrong = "outra-" + UUID.randomUUID();
    String right = "boa-" + UUID.randomUUID();
    String wrongToken = known(wrong);
    String rightToken = known(right);

    UUID id =
        id(
            bookFor(staff, f, swim, nine, Map.of("name", "Bea Lago", "email", email(wrong)), null)
                .json());
    assertThat(myBookings(f, wrongToken).size()).as("a outra persoa vería a reserva").isEqualTo(1);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", swim);
    body.put("start", nine.toString());
    body.put("slots", 1);
    body.put("holder", Map.of("name", "Bea Lago", "email", email(right)));
    Api.Response corrected =
        api().put(bookings(f.slug()) + "/" + id, body).ifMatch(0).token(staff).send();

    assertThat(corrected.status()).as(corrected.toString()).isEqualTo(200);
    assertThat(myBookings(f, wrongToken).size()).isZero();
    assertThat(myBookings(f, rightToken).size()).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select b.holder_email_key || ' ' || p.person_key from booking b"
                        + " join booking_participant p on p.booking_id = b.id and p.active"
                        + " where b.id = :id")
                .param("id", id)
                .query(String.class)
                .single())
        .as("a clave do correo e a praza do titular, recalculadas")
        .isEqualTo(email(right) + " e:" + email(right));
    assertThat(
            jdbc.sql(
                    "select count(*) from booking_event where booking_id = :id"
                        + " and kind = 'HOLDER_CHANGED'")
                .param("id", id)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    // A praza é agora do correo bo: o equivocado pode reservar esa hora; o bo, non.
    assertThat(reserve(wrongToken, f, swim, nine).status()).isEqualTo(201);
    assertThat(code(reserve(rightToken, f, swim, nine))).isEqualTo("booking.seat-taken-by-you");
  }

  @Test
  void theStaffAddsAndRemovesPeopleWhenChangingButAPaidBookingKeepsItsSeats() {
    Facility f = facility("mostrador-cambiar-prazas", true);
    UUID swim =
        id(
            use(
                f,
                "Nado libre",
                "SEATS",
                Map.of("capacity", 20, "price", Map.of("kind", "PAID", "amountCents", 200))));
    Instant nine = at(tomorrow(), OPENS);
    String staff = TestTokens.staff(f.slug());
    Map<String, Object> xoan = Map.of("name", "Xoán Souto", "birthYear", 2012);
    Map<String, Object> icia = Map.of("name", "Icía Souto", "birthYear", 2015);

    JsonNode created =
        bookFor(
                staff,
                f,
                swim,
                nine,
                Map.of("name", "Carme Souto"),
                Map.of("companions", List.of(xoan)))
            .json();
    UUID id = id(created);
    assertThat(created.get("seats").asInt()).isEqualTo(2);
    assertThat(created.get("price").get("amountCents").asInt()).isEqualTo(400);

    // Sen dicir para quen, o cambio de hora deixa as mesmas persoas.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", swim);
    body.put("start", at(tomorrow(), 600).toString());
    body.put("slots", 1);
    body.put("notifyPerson", false);
    Api.Response moved =
        api().put(bookings(f.slug()) + "/" + id, body).ifMatch(0).token(staff).send();
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(who(moved.json())).containsExactly("SELF:Carme Souto", "COMPANION:Xoán Souto:2012");

    // Engadir unha persoa sen cambiar de hora: tres prazas e o prezo novo.
    body.put("participants", Map.of("self", true, "companions", List.of(xoan, icia)));
    Api.Response more =
        api()
            .put(bookings(f.slug()) + "/" + id, body)
            .ifMatch(moved.etagVersion())
            .token(staff)
            .send();
    assertThat(more.status()).as(more.toString()).isEqualTo(200);
    assertThat(more.json().get("seats").asInt()).isEqualTo(3);
    assertThat(more.json().get("price").get("amountCents").asInt()).isEqualTo(600);
    assertThat(count("select coalesce(sum(taken), 0) from use_slot where use_id = :k", swim))
        .isEqualTo(3);
    assertThat(
            jdbc.sql(
                    """
                    select details::text from audit_event
                    where action = 'booking.change' and outcome = 'SUCCESS'
                      and municipality_id = :m
                    order by occurred_at desc, id desc limit 1
                    """)
                .param("m", f.municipality())
                .query(String.class)
                .single())
        .as("as prazas van co valor; as persoas, só que cambiaron")
        .contains("booking.seats")
        .contains("booking.participants")
        .doesNotContain("Icía", "Xoán");

    // Pagada, non cambia de número de prazas (cambiaría o prezo).
    body.put("paid", true);
    Api.Response paid =
        api()
            .put(bookings(f.slug()) + "/" + id, body)
            .ifMatch(more.etagVersion())
            .token(staff)
            .send();
    assertThat(paid.status()).as(paid.toString()).isEqualTo(200);
    body.put("participants", Map.of("self", true, "companions", List.of(xoan)));
    Api.Response fewer =
        api()
            .put(bookings(f.slug()) + "/" + id, body)
            .ifMatch(paid.etagVersion())
            .token(staff)
            .send();
    assertThat(fewer.status()).as(fewer.toString()).isEqualTo(422);
    assertThat(code(fewer)).isEqualTo("booking.change-price-paid");
    assertThat(count("select seats from booking where id = :k", id)).isEqualTo(3);
  }
}
