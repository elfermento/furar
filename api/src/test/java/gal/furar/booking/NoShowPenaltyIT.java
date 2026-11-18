// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.BookingRetentionService;
import gal.furar.booking.application.service.NoShowService;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * A penalización das faltas de punta a punta (ADR-0032, punto 7): as faltas cóntanse por correo
 * entre o mostrador e internet, sen correo non contan, o bloqueo impide reservar por internet (e só
 * iso, e só neste concello), levántase ao desfacer unha das faltas que o causaron sen poñer a conta
 * a cero, un bloqueo cumprido si a pon, e apagada non bloquea.
 */
class NoShowPenaltyIT extends AbstractAttendanceIT {

  @Autowired NoShowService task;
  @Autowired BookingRetentionService retention;

  static final Instant LONG_AGO = Instant.now().minus(Duration.ofDays(10));

  /** Unha reserva xa rematada a nome dese correo, feita no mostrador. */
  UUID counter(Facility f, UUID use, int slot, String email) {
    Api.Response booked = bookFor(f, use, at(tomorrow(), OPENS + 60 * slot), "Ana Rey", email);
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID id = id(booked.json());
    move(id, Instant.now().minus(Duration.ofHours(2 + slot)));
    return id;
  }

  JsonNode block(Facility f, String token) {
    Api.Response found =
        api().get("/api/v1/municipalities/" + f.slug() + "/me/booking-block").token(token).send();
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    return found.json();
  }

  static LocalDate lastBlockedDay(int blockDays) {
    return LocalDate.now(ZONE).plusDays(blockDays);
  }

  @Test
  void faultsCountByEmailAcrossTheCounterAndTheInternetAndBlockOnlineBookingHere() {
    Facility f = facility("pena", true);
    Facility elsewhere = facility("pena-outro", true);
    UUID use = whole(f, Map.of());
    UUID otherUse = whole(elsewhere, Map.of());
    String staff = TestTokens.staff(f.slug());
    penalty(f, 2, 30, 15, LONG_AGO);
    String sub = "ana-" + UUID.randomUUID();
    String ana = person(sub);
    String email = sub + "@example.org";

    // Unha por internet e outra no mostrador, co mesmo correo escrito doutro xeito.
    Api.Response online = reserve(ana, f, use, at(tomorrow(), OPENS));
    UUID first = id(online.json());
    move(first, Instant.now().minus(Duration.ofHours(5)));
    UUID second = counter(f, use, 1, email.toUpperCase());
    UUID kept = id(reserve(ana, f, use, at(tomorrow().plusDays(1), OPENS)).json());

    JsonNode one = noShow(staff, f.slug(), first).json().get("noShow");
    assertThat(one.toString())
        .isEqualTo(
            "{\"faults\":1,\"windowDays\":30,\"threshold\":2,\"blockDays\":15,"
                + "\"blockedUntil\":null}");
    assertThat(block(f, ana).get("blocked").asBoolean()).isFalse();
    assertThat(detail(staff, f.slug(), first).get("noShow").toString())
        .as("ao reabrir a ficha, o mesmo aviso")
        .isEqualTo(one.toString());

    Api.Response marked = noShow(staff, f.slug(), second);
    assertThat(marked.status()).as(marked.toString()).isEqualTo(200);
    JsonNode two = marked.json().get("noShow");
    assertThat(two.get("faults").asInt()).isEqualTo(2);
    assertThat(two.get("blockedUntil").asString()).isEqualTo(lastBlockedDay(15).toString());
    assertThat(
            jdbc.sql(
                    """
                    select details ->> 'blocked' from audit_event
                    where municipality_id = :m and action = 'booking.no-show'
                    order by occurred_at, id
                    """)
                .param("m", f.municipality())
                .query(String.class)
                .list())
        .containsExactly("false", "true");

    // Bloqueada: a pantalla sábeo e reservar por internet dá 403 cos seus números.
    JsonNode blocked = block(f, ana);
    assertThat(blocked.toString())
        .isEqualTo(
            "{\"blocked\":true,\"until\":\""
                + lastBlockedDay(15)
                + "\",\"faults\":2,\"windowDays\":30}");
    Api.Response refused = reserve(ana, f, use, at(tomorrow(), OPENS + 120));
    assertThat(refused.status()).as(refused.toString()).isEqualTo(403);
    assertThat(code(refused)).isEqualTo("booking.blocked-no-show");
    assertThat(refused.json().get("params").get("until").asString())
        .isEqualTo(lastBlockedDay(15).toString());
    assertThat(refused.json().get("params").get("faults").asInt()).isEqualTo(2);
    assertThat(refused.json().get("params").get("windowDays").asInt()).isEqualTo(30);

    // Só as reservas novas por internet: no mostrador si, noutro concello si, e as feitas
    // mantéñense e pódense cancelar.
    assertThat(bookFor(f, use, at(tomorrow(), OPENS + 120), "Ana Rey", email).status())
        .isEqualTo(201);
    assertThat(reserve(ana, elsewhere, otherUse, at(tomorrow(), OPENS)).status()).isEqualTo(201);
    assertThat(block(elsewhere, ana).get("blocked").asBoolean()).isFalse();
    assertThat(
            api()
                .post(mine(f.slug()) + "/" + kept + "/cancel", null)
                .ifMatch(0)
                .token(ana)
                .send()
                .status())
        .isEqualTo(200);
    assertThat(
            jdbc.sql("select count(*) from booking_no_show_block_fault where municipality_id = :m")
                .param("m", f.municipality())
                .query(Integer.class)
                .single())
        .as("o bloqueo garda as reservas que o causaron")
        .isEqualTo(2);
  }

  @Test
  void withoutEmailItIsMarkedButNeitherCountsNorBlocksAndWhoSignsUpLaterIsStillBlocked() {
    Facility f = facility("pena-correo", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    penalty(f, 2, 30, 15, LONG_AGO);
    String sub = "bea-" + UUID.randomUUID();
    String email = sub + "@example.org";

    UUID nameless = counter(f, use, 0, null);
    Api.Response marked = noShow(staff, f.slug(), nameless);
    assertThat(marked.status()).isEqualTo(200);
    assertThat(marked.json().get("attendance").asString()).isEqualTo("NO_SHOW");
    assertThat(marked.json().get("noShow").isNull()).as("sen correo non conta").isTrue();

    // Dúas faltas no mostrador dun correo que aínda non ten conta.
    assertThat(noShow(staff, f.slug(), counter(f, use, 1, email)).status()).isEqualTo(200);
    JsonNode second = noShow(staff, f.slug(), counter(f, use, 2, email)).json().get("noShow");
    assertThat(second.get("blockedUntil").isNull()).isFalse();

    // Crea a conta despois e entra co correo verificado: segue bloqueada.
    String bea = person(sub);
    assertThat(block(f, bea).get("blocked").asBoolean()).isTrue();
    Api.Response refused = reserve(bea, f, use, at(tomorrow(), OPENS + 180));
    assertThat(refused.status()).isEqualTo(403);
    assertThat(code(refused)).isEqualTo("booking.blocked-no-show");
  }

  @Test
  void undoingOneOfItsFaultsLiftsTheBlockWithoutResettingTheCountAndAServedBlockResetsIt() {
    Facility f = facility("pena-levantar", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    penalty(f, 2, 30, 15, LONG_AGO);
    String sub = "carme-" + UUID.randomUUID();
    String carme = person(sub);
    String email = sub + "@example.org";
    UUID first = counter(f, use, 0, email);
    UUID second = counter(f, use, 1, email);
    assertThat(noShow(staff, f.slug(), first).status()).isEqualTo(200);
    assertThat(noShow(staff, f.slug(), second).status()).isEqualTo(200);
    assertThat(block(f, carme).get("blocked").asBoolean()).isTrue();

    // Desfacer calquera das que o causaron levántao.
    Api.Response undone = undo(staff, f.slug(), first);
    assertThat(undone.status()).as(undone.toString()).isEqualTo(200);
    assertThat(undone.json().get("blockLifted").asBoolean()).isTrue();
    assertThat(block(f, carme).get("blocked").asBoolean()).isFalse();
    assertThat(reserve(carme, f, use, at(tomorrow(), OPENS + 120)).status()).isEqualTo(201);
    assertThat(
            jdbc.sql(
                    """
                    select details ->> 'from' || ' ' || (details ->> 'blockLifted')
                    from audit_event
                    where municipality_id = :m and action = 'booking.attendance-undo'
                    """)
                .param("m", f.municipality())
                .query(String.class)
                .single())
        .isEqualTo("NO_SHOW true");

    // Un bloqueo levantado non pon a conta a cero: a outra falta segue contando.
    UUID third = counter(f, use, 3, email);
    JsonNode again = noShow(staff, f.slug(), third).json().get("noShow");
    assertThat(again.get("faults").asInt()).isEqualTo(2);
    assertThat(again.get("blockedUntil").isNull()).isFalse();
    assertThat(block(f, carme).get("blocked").asBoolean()).isTrue();

    // Cumprido o bloqueo, a conta empeza de cero.
    jdbc.sql(
            """
            update booking_no_show_block
            set starts_at = :from, ends_at = :to
            where municipality_id = :m and lifted_at is null
            """)
        .param("from", Instant.now().minus(Duration.ofDays(3)).atOffset(ZoneOffset.UTC))
        .param("to", Instant.now().minus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC))
        .param("m", f.municipality())
        .update();
    assertThat(block(f, carme).get("blocked").asBoolean()).isFalse();
    UUID fourth = counter(f, use, 4, email);
    JsonNode fresh = noShow(staff, f.slug(), fourth).json().get("noShow");
    assertThat(fresh.get("faults").asInt()).as("as do bloqueo cumprido xa non contan").isEqualTo(1);
    assertThat(fresh.get("blockedUntil").isNull()).isTrue();
  }

  @Test
  void validatingOrCancellingByTheStaffAFaultThatCausedABlockLiftsIt() {
    Facility f = facility("pena-validar", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    String manager = TestTokens.manager(f.slug());
    penalty(f, 1, 30, 15, LONG_AGO);
    String sub = "dora-" + UUID.randomUUID();
    String dora = person(sub);

    Api.Response booked = reserve(dora, f, use, at(tomorrow(), OPENS));
    UUID first = id(booked.json());
    move(first, Instant.now().minus(Duration.ofHours(3)));
    assertThat(noShow(staff, f.slug(), first).json().get("noShow").get("blockedUntil").isNull())
        .as("cunha soa falta xa bloquea")
        .isFalse();
    assertThat(block(f, dora).get("blocked").asBoolean()).isTrue();
    // Validala (fóra da marxe, co motivo) quita a falta e o bloqueo.
    assertThat(
            validate(staff, f.slug(), booked.json().get("code").asString(), true, "Si que veu")
                .json()
                .get("outcome")
                .asString())
        .isEqualTo("VALIDATED");
    assertThat(block(f, dora).get("blocked").asBoolean()).isFalse();

    // Outra falta que bloquea; a xestión cancela a reserva e o bloqueo levántase.
    UUID second = counter(f, use, 1, sub + "@example.org");
    assertThat(noShow(staff, f.slug(), second).status()).isEqualTo(200);
    assertThat(block(f, dora).get("blocked").asBoolean()).isTrue();
    long version = api().get(bookings(f.slug()) + "/" + second).token(manager).send().etagVersion();
    Api.Response cancelled =
        api()
            .post(
                bookings(f.slug()) + "/" + second + "/cancel",
                Map.of("channels", java.util.List.of()))
            .ifMatch(version)
            .token(manager)
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(cancelled.json().get("blockLifted").asBoolean()).isTrue();
    assertThat(cancelled.json().get("attendance").asString()).isEqualTo("NONE");
    assertThat(block(f, dora).get("blocked").asBoolean()).isFalse();
  }

  @Test
  void theRetentionTaskDeletesTheBlocksThatEndedLongAgoWithTheirFaults() {
    Facility f = facility("pena-conserva", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    penalty(f, 1, 30, 15, LONG_AGO);
    String old = "vello-" + UUID.randomUUID() + "@example.org";
    String recent = "novo-" + UUID.randomUUID() + "@example.org";
    assertThat(noShow(staff, f.slug(), counter(f, use, 0, old)).status()).isEqualTo(200);
    assertThat(noShow(staff, f.slug(), counter(f, use, 1, recent)).status()).isEqualTo(200);
    // O primeiro bloqueo rematou hai tres anos: pasou o prazo das reservas (2 anos).
    jdbc.sql(
            """
            update booking_no_show_block
            set starts_at = :from, ends_at = :to, created_at = :from
            where municipality_id = :m and holder_email_key = :email
            """)
        .param("from", Instant.now().minus(Duration.ofDays(1200)).atOffset(ZoneOffset.UTC))
        .param("to", Instant.now().minus(Duration.ofDays(1100)).atOffset(ZoneOffset.UTC))
        .param("m", f.municipality())
        .param("email", old)
        .update();

    retention.apply();

    assertThat(
            jdbc.sql(
                    """
                    select holder_email_key from booking_no_show_block where municipality_id = :m
                    """)
                .param("m", f.municipality())
                .query(String.class)
                .list())
        .as("o bloqueo vello bórrase co seu correo; o recente queda")
        .containsExactly(recent);
    assertThat(
            jdbc.sql("select count(*) from booking_no_show_block_fault where municipality_id = :m")
                .param("m", f.municipality())
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from booking where municipality_id = :m"
                        + " and attendance = 'NO_SHOW'")
                .param("m", f.municipality())
                .query(Integer.class)
                .single())
        .as("as reservas quedan coa súa marca")
        .isEqualTo(2);
  }

  @Test
  void withThePenaltyOffFaultsDoNotBlockAndTheTaskAlsoCountsAndBlocks() {
    Facility off = facility("pena-apagada", true);
    UUID offUse = whole(off, Map.of());
    String sub = "eva-" + UUID.randomUUID();
    String eva = person(sub);
    String email = sub + "@example.org";
    String offStaff = TestTokens.staff(off.slug());
    for (int slot = 0; slot < 3; slot++) {
      JsonNode marked = noShow(offStaff, off.slug(), counter(off, offUse, slot, email)).json();
      assertThat(marked.get("attendance").asString()).isEqualTo("NO_SHOW");
      assertThat(marked.get("noShow").isNull()).isTrue();
    }
    assertThat(block(off, eva).get("blocked").asBoolean()).isFalse();
    assertThat(reserve(eva, off, offUse, at(tomorrow(), OPENS + 240)).status()).isEqualTo(201);

    // Coa penalización acesa e control de entrada, a tarefa conta e bloquea igual.
    Facility on = facility("pena-tarefa", true);
    UUID onUse = whole(on, Map.of());
    penalty(on, 2, 30, 15, LONG_AGO);
    entryControl(on, LONG_AGO);
    counter(on, onUse, 0, email);
    counter(on, onUse, 1, email);
    task.apply();
    assertThat(block(on, eva).get("blocked").asBoolean()).isTrue();
    assertThat(
            jdbc.sql(
                    """
                    select (details ->> 'count') || ' ' || (details ->> 'blocks')
                    from audit_event
                    where municipality_id = :m and action = 'booking.no-show'
                    """)
                .param("m", on.municipality())
                .query(String.class)
                .single())
        .isEqualTo("2 1");
  }
}
