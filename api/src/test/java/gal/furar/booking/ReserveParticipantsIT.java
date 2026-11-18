// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Unha reserva con N prazas e as súas persoas (ADR-0031, puntos 5, 6 e 8), de punta a punta: só eu,
 * eu e dúas persoas a cargo, só unha persoa a cargo (e despois eu á mesma hora), o cupo, o importe,
 * a ficha allea ou eliminada, máis de 11, a clave de reintento, cambiar para quitar unha persoa e
 * cancelar. Despois de cada operación, as copias da reserva nos participantes seguen á par ({@link
 * #assertConsistent}: {@code BookingParticipantsConsistencyIT} do ADR vai aquí dentro).
 */
class ReserveParticipantsIT extends AbstractBookingIT {

  static final String DEPENDENTS = "/api/v1/me/dependents";

  UUID dependent(String token, String name, int birthYear) {
    Api.Response created =
        api()
            .post(DEPENDENTS, Map.of("name", name, "birthYear", birthYear, "inMyCare", true))
            .token(token)
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  Api.Response reserveFor(
      String token, Facility f, UUID use, Instant start, boolean self, List<UUID> dependents) {
    return reserveFor(token, f, use, start, self, dependents, UUID.randomUUID().toString());
  }

  Api.Response reserveFor(
      String token,
      Facility f,
      UUID use,
      Instant start,
      boolean self,
      List<UUID> dependents,
      String key) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("participants", Map.of("self", self, "dependentIds", dependents));
    return api().post(mine(f.slug()), body).header("Idempotency-Key", key).token(token).send();
  }

  static List<String> who(JsonNode booking) {
    List<String> names = new ArrayList<>();
    booking
        .get("participants")
        .forEach(p -> names.add(p.get("kind").asString() + ":" + p.get("name").asString()));
    return names;
  }

  /**
   * As copias á par da reserva (ADR-0031, punto 5): todo participante activo ten o uso e o período
   * da súa reserva, que está confirmada, e {@code seats} é o seu número; unha reserva por prazas
   * confirmada non queda sen participantes; os contadores suman as prazas das ocupacións activas.
   */
  void assertConsistent(Facility f) {
    assertThat(
            count(
                """
                select count(*) from booking_participant p
                join booking b on b.id = p.booking_id
                where p.municipality_id = :k and p.active
                  and (b.status <> 'CONFIRMED' or b.period <> p.period or b.use_id <> p.use_id)
                """,
                f.municipality()))
        .as("participantes activos fóra de sitio")
        .isZero();
    assertThat(
            count(
                """
                select count(*) from booking b
                where b.municipality_id = :k and b.status = 'CONFIRMED' and b.rental_mode = 'SEATS'
                  and b.seats <> (select count(*) from booking_participant p
                                  where p.booking_id = b.id and p.active)
                """,
                f.municipality()))
        .as("seats distinto do número de participantes activos")
        .isZero();
    assertThat(
            count(
                """
                select count(*) from use_slot s
                where s.municipality_id = :k
                  and s.taken <> (select coalesce(sum(o.seats), 0) from booking_occupancy o
                                  where o.use_id = s.use_id and o.active
                                    and o.period @> s.slot_start)
                """,
                f.municipality()))
        .as("contadores distintos das prazas ocupadas")
        .isZero();
  }

  @Test
  void aBookingForMeAndTwoDependentsTakesThreeSeatsAndOneCode() {
    Facility f = facility("prazas-familia", true);
    UUID swim =
        id(
            use(
                f,
                "Nado libre",
                "SEATS",
                Map.of("capacity", 5, "price", Map.of("kind", "PAID", "amountCents", 300))));
    Instant nine = at(tomorrow(), OPENS);
    String ana = person("ana-" + UUID.randomUUID());
    UUID uxia = dependent(ana, "Uxía Rey", 2016);
    UUID anton = dependent(ana, "Antón Rey", 2019);

    Api.Response created = reserveFor(ana, f, swim, nine, true, List.of(uxia, anton));

    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    JsonNode b = created.json();
    assertThat(b.get("seats").asInt()).isEqualTo(3);
    assertThat(b.get("price").get("amountCents").asInt()).as("importe × prazas").isEqualTo(900);
    assertThat(who(b)).hasSize(3).first().asString().startsWith("SELF:Persoa ana-");
    assertThat(who(b)).contains("DEPENDENT:Antón Rey", "DEPENDENT:Uxía Rey");
    assertThat(b.get("participants").get(1).path("birthYear").isNumber())
        .as("o ano só vai na xestión")
        .isFalse();
    assertThat(count("select taken from use_slot where use_id = :k", swim)).isEqualTo(3);
    assertThat(count("select count(*) from booking where use_id = :k", swim)).isEqualTo(1);
    assertConsistent(f);

    // «As miñas reservas» leva o mesmo «Para quen».
    JsonNode listed = api().get(mine(f.slug())).token(ana).send().json().get("items").get(0);
    assertThat(listed.get("seats").asInt()).isEqualTo(3);
    assertThat(who(listed)).hasSize(3);

    // Só quedan dúas prazas: tres non caben, e dise cantas quedan.
    String brais = person();
    UUID noa = dependent(brais, "Noa Vila", 2015);
    UUID iago = dependent(brais, "Iago Vila", 2017);
    Api.Response tooMany = reserveFor(brais, f, swim, nine, true, List.of(noa, iago));
    assertThat(tooMany.status()).as(tooMany.toString()).isEqualTo(409);
    assertThat(code(tooMany)).isEqualTo("booking.not-enough-seats");
    assertThat(tooMany.json().get("params").get("free").asInt()).isEqualTo(2);
    assertThat(reserveFor(brais, f, swim, nine, false, List.of(noa, iago)).status()).isEqualTo(201);
    assertThat(code(reserve(person(), f, swim, nine))).isEqualTo("booking.slot-taken");
    assertConsistent(f);

    // Cancelar devolve as tres prazas dunha vez.
    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + id(b) + "/cancel", null).ifMatch(0).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(who(cancelled.json())).as("a cancelada segue dicindo para quen era").hasSize(3);
    assertThat(count("select taken from use_slot where use_id = :k", swim)).isEqualTo(2);
    assertConsistent(f);
  }

  @Test
  void onlyForADependentAndLaterForMeAtTheSameHour() {
    Facility f = facility("prazas-so-filla", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 5)));
    Instant nine = at(tomorrow(), OPENS);
    String ana = person();
    UUID uxia = dependent(ana, "Uxía Rey", 2016);

    Api.Response forHer = reserveFor(ana, f, swim, nine, false, List.of(uxia));
    assertThat(forHer.status()).as(forHer.toString()).isEqualTo(201);
    assertThat(forHer.json().get("seats").asInt()).isEqualTo(1);
    assertThat(who(forHer.json())).containsExactly("DEPENDENT:Uxía Rey");

    // O titular non ocupaba praza: pode reservar despois esa hora para si.
    Api.Response forMe = reserve(ana, f, swim, nine);
    assertThat(forMe.status()).as(forMe.toString()).isEqualTo(201);

    // Pero ninguén ten dúas prazas na mesma franxa: nin ela, nin eu.
    Api.Response herAgain = reserveFor(ana, f, swim, nine, false, List.of(uxia));
    assertThat(herAgain.status()).isEqualTo(409);
    assertThat(code(herAgain)).isEqualTo("booking.dependent-seat-taken");
    assertThat(herAgain.json().get("params").get("name").asString()).isEqualTo("Uxía Rey");
    Api.Response meAgain = reserve(ana, f, swim, nine);
    assertThat(meAgain.status()).isEqualTo(409);
    assertThat(code(meAgain)).isEqualTo("booking.seat-taken-by-you");
    assertThat(count("select taken from use_slot where use_id = :k", swim)).isEqualTo(2);
    assertConsistent(f);
  }

  @Test
  void refusesNobodyTooManyForeignOrDeletedRecordsAndUsesWithoutSeats() {
    Facility f = facility("prazas-erros", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 30)));
    Instant nine = at(tomorrow(), OPENS);
    String ana = person();
    String brais = person();
    UUID uxia = dependent(ana, "Uxía Rey", 2016);
    UUID foreign = dependent(brais, "Noa Vila", 2015);

    Api.Response nobody = reserveFor(ana, f, swim, nine, false, List.of());
    assertThat(nobody.status()).isEqualTo(422);
    assertThat(code(nobody)).isEqualTo("booking.participants-required");

    Api.Response notMine = reserveFor(ana, f, swim, nine, true, List.of(foreign));
    assertThat(notMine.status()).isEqualTo(422);
    assertThat(code(notMine)).isEqualTo("booking.dependent-unknown");

    Api.Response twice = reserveFor(ana, f, swim, nine, true, List.of(uxia, uxia));
    assertThat(twice.status()).isEqualTo(422);
    assertThat(code(twice)).isEqualTo("booking.participant-duplicate");

    assertThat(api().delete(DEPENDENTS + "/" + uxia).token(ana).send().status()).isEqualTo(204);
    Api.Response deleted = reserveFor(ana, f, swim, nine, true, List.of(uxia));
    assertThat(code(deleted)).isEqualTo("booking.dependent-unknown");

    // Dez fichas e eu son once prazas: o máximo. O formulario non deixa máis; a API tampouco.
    List<UUID> ten = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      ten.add(dependent(ana, "Persoa " + i, 2010));
    }
    Api.Response eleven = reserveFor(ana, f, swim, nine, true, ten);
    assertThat(eleven.status()).as(eleven.toString()).isEqualTo(201);
    assertThat(eleven.json().get("seats").asInt()).isEqualTo(11);
    List<UUID> twelve = new ArrayList<>(ten);
    twelve.add(UUID.randomUUID());
    twelve.add(UUID.randomUUID());
    Api.Response over = reserveFor(brais, f, swim, nine, true, twelve);
    assertThat(over.status()).isEqualTo(400);

    // Só os usos por prazas levan persoas.
    UUID court = whole(f, "Pista", Map.of());
    Api.Response whole = reserveFor(brais, f, court, at(tomorrow(), 600), false, List.of(foreign));
    assertThat(whole.status()).isEqualTo(422);
    assertThat(code(whole)).isEqualTo("booking.participants-not-allowed");
    assertThat(reserve(brais, f, court, at(tomorrow(), 600)).json().get("seats").asInt())
        .isEqualTo(1);
    assertConsistent(f);
  }

  @Test
  void theRetryKeyCoversWhoTheBookingIsFor() {
    Facility f = facility("prazas-clave", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 5)));
    Instant nine = at(tomorrow(), OPENS);
    String ana = person();
    UUID uxia = dependent(ana, "Uxía Rey", 2016);
    String key = UUID.randomUUID().toString();

    Api.Response first = reserveFor(ana, f, swim, nine, true, List.of(uxia), key);
    Api.Response again = reserveFor(ana, f, swim, nine, true, List.of(uxia), key);
    Api.Response other = reserveFor(ana, f, swim, nine, true, List.of(), key);

    assertThat(first.status()).isEqualTo(201);
    assertThat(again.status()).isEqualTo(201);
    assertThat(id(again.json())).isEqualTo(id(first.json()));
    assertThat(other.status()).isEqualTo(422);
    assertThat(code(other)).isEqualTo("booking.idempotency-key-reused");
    assertThat(count("select taken from use_slot where use_id = :k", swim)).isEqualTo(2);
  }

  @Test
  void changingRemovesAPersonAndDeletingAUsedRecordOnlyDeactivatesIt() {
    Facility f = facility("prazas-cambiar", true);
    UUID swim = id(use(f, "Nado libre", "SEATS", Map.of("capacity", 5)));
    Instant nine = at(tomorrow(), OPENS);
    Instant ten = at(tomorrow(), 600);
    String ana = person();
    UUID uxia = dependent(ana, "Uxía Rey", 2016);
    UUID anton = dependent(ana, "Antón Rey", 2019);
    UUID booking = id(reserveFor(ana, f, swim, nine, true, List.of(uxia, anton)).json());

    // Cambiar de hora sen dicir para quen: as mesmas persoas.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", swim);
    body.put("start", ten.toString());
    body.put("slots", 1);
    Api.Response moved =
        api().put(mine(f.slug()) + "/" + booking + "/slot", body).ifMatch(0).token(ana).send();
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(moved.json().get("seats").asInt()).isEqualTo(3);
    assertConsistent(f);

    // Cambiar para quitar a Antón: dúas prazas, e a súa queda libre.
    body.put("participants", Map.of("self", true, "dependentIds", List.of(uxia)));
    Api.Response fewer =
        api().put(mine(f.slug()) + "/" + booking + "/slot", body).ifMatch(1).token(ana).send();
    assertThat(fewer.status()).as(fewer.toString()).isEqualTo(200);
    assertThat(fewer.json().get("seats").asInt()).isEqualTo(2);
    assertThat(who(fewer.json())).hasSize(2).contains("DEPENDENT:Uxía Rey");
    assertThat(count("select coalesce(sum(taken), 0) from use_slot where use_id = :k", swim))
        .isEqualTo(2);
    assertConsistent(f);

    // Eliminar unha ficha con reservas desactívaa: nin se lista nin se usa, pero a reserva segue
    // ligada a ela e co nome copiado.
    assertThat(api().delete(DEPENDENTS + "/" + uxia).token(ana).send().status()).isEqualTo(204);
    JsonNode list = api().get(DEPENDENTS).token(ana).send().json();
    assertThat(list.size()).isEqualTo(1);
    assertThat(list.get(0).get("name").asString()).isEqualTo("Antón Rey");
    assertThat(
            jdbc.sql("select deactivated_at is not null from account_dependent where id = :id")
                .param("id", uxia)
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(
            api()
                .put(
                    DEPENDENTS + "/" + uxia,
                    Map.of("name", "Uxía", "birthYear", 2016, "inMyCare", true))
                .ifMatch(1)
                .token(ana)
                .send()
                .status())
        .isEqualTo(404);
    assertThat(code(reserveFor(ana, f, swim, nine, false, List.of(uxia))))
        .isEqualTo("booking.dependent-unknown");
    JsonNode kept = api().get(mine(f.slug())).token(ana).send().json().get("items").get(0);
    assertThat(who(kept)).contains("DEPENDENT:Uxía Rey");
    // A FK impide borrar a ficha mentres a reserva a use, tamén ao dono da base.
    assertThat(
            jdbc.sql("select dependent_has_bookings(:id)")
                .param("id", uxia)
                .query(Boolean.class)
                .single())
        .isTrue();
    // Antón quitouse da reserva, pero figurou nela: a súa ficha tampouco se borra, desactívase.
    assertThat(api().delete(DEPENDENTS + "/" + anton).token(ana).send().status()).isEqualTo(204);
    assertThat(count("select count(*) from account_dependent where id = :k", anton)).isEqualTo(1);
  }
}
