// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

/**
 * Bloqueos de punta a punta con PostgreSQL real (D1-16): {@code If-Match} por bloqueo, solape → 409
 * (no servizo e na exclusión da base), período inválido → 422, o bloqueo de 2:00 a 3:00 do 25 de
 * outubro de 2026 (hora repetida) gárdase coas dúas horas reais, modal de reservas afectadas cun
 * dobre (409 con {@code affected[]} e despois 201), {@code openingIntervals} sen as horas
 * bloqueadas, outro concello e rexistro de actividade.
 */
class FacilityBlockIT extends AbstractIT {

  @Autowired ProgrammableBookingImpact impact;
  @Autowired FacilityQueries queries;

  record Facility(String slug, UUID municipality, UUID id, String url) {
    String blocks() {
      return url + "/blocks";
    }

    TenantRef tenant() {
      return new TenantRef(municipality, slug);
    }
  }

  Facility facility() {
    String slug = uniqueSlug("blq");
    UUID municipality = id(createMunicipality(slug));
    String base = "/api/v1/municipalities/" + slug + "/facilities";
    Api.Response created =
        api()
            .post(
                base,
                Map.of("name", "Campo de fútbol 1 de O Alto", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    return new Facility(slug, municipality, id, base + "/" + id);
  }

  /** Tempada coa mesma franxa todos os días, tamén festivos. */
  void season(Facility f, String from, String to, int start, int end) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Tempada " + from);
    body.put("startDate", from);
    body.put("endDate", to);
    Api.Response created =
        api().post(f.url() + "/seasons", body).token(TestTokens.manager(f.slug())).send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    List<Map<String, Object>> week = new ArrayList<>();
    for (String day :
        List.of(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
            "HOLIDAYS")) {
      week.add(
          Map.of("day", day, "intervals", List.of(Map.of("startMinute", start, "endMinute", end))));
    }
    body.put("week", week);
    Api.Response updated =
        api()
            .put(f.url() + "/seasons/" + id(created.json()), body)
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
  }

  static Map<String, Object> block(String date, int start, int end) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date);
    body.put("startMinute", start);
    body.put("endMinute", end);
    body.put("reason", "Mantemento do céspede");
    return body;
  }

  Api.Response post(Facility f, Map<String, Object> body) {
    return api().post(f.blocks(), body).token(TestTokens.manager(f.slug())).send();
  }

  Api.Response put(Facility f, UUID block, long version, Map<String, Object> body) {
    return api()
        .put(f.blocks() + "/" + block, body)
        .ifMatch(version)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  Api.Response delete(Facility f, UUID block, long version) {
    return api()
        .method("DELETE", f.blocks() + "/" + block, null)
        .ifMatch(version)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  JsonNode created(Facility f, Map<String, Object> body) {
    Api.Response response = post(f, body);
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }

  JsonNode list(Facility f) {
    Api.Response response = api().get(f.blocks()).token(TestTokens.staff(f.slug())).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  static void assertProblem(Api.Response response, int status, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(status);
    assertThat(response.json().get("code").asString()).isEqualTo(code);
  }

  @Test
  void createReadUpdateAndDeleteWithIfMatchOfTheBlock() {
    Facility f = facility();
    assertThat(list(f).size()).isZero();
    Api.Response created = post(f, block("2026-11-03", 480, 720));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID blockId = id(created.json());
    assertThat(created.header("Location")).endsWith("/facilities/" + f.id() + "/blocks/" + blockId);
    assertThat(created.etagVersion()).isZero();
    JsonNode body = created.json();
    assertThat(body.get("date").asString()).isEqualTo("2026-11-03");
    assertThat(body.get("startMinute").asInt()).isEqualTo(480);
    assertThat(body.get("endMinute").asInt()).isEqualTo(720);
    assertThat(Instant.parse(body.get("start").asString()))
        .isEqualTo(Instant.parse("2026-11-03T07:00:00Z"));
    assertThat(Instant.parse(body.get("end").asString()))
        .isEqualTo(Instant.parse("2026-11-03T11:00:00Z"));
    assertThat(body.get("reason").asString()).isEqualTo("Mantemento do céspede");
    assertThat(
            jdbc.sql("select created_by from block where id = :id")
                .param("id", blockId)
                .query(String.class)
                .single())
        .isNotBlank();

    Api.Response one =
        api().get(f.blocks() + "/" + blockId).token(TestTokens.staff(f.slug())).send();
    assertThat(one.status()).isEqualTo(200);
    assertThat(one.etagVersion()).isZero();

    Api.Response updated = put(f, blockId, 0, block("2026-11-04", 600, 660));
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.etagVersion()).isEqualTo(1);
    assertThat(updated.json().get("date").asString()).isEqualTo("2026-11-04");

    assertProblem(put(f, blockId, 0, block("2026-11-04", 600, 660)), 412, "common.stale-version");
    Api.Response without =
        api()
            .put(f.blocks() + "/" + blockId, block("2026-11-04", 600, 660))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertProblem(without, 428, "common.precondition-required");
    assertThat(
            api()
                .post(f.blocks(), block("2026-11-05", 480, 720))
                .token(TestTokens.staff(f.slug()))
                .send()
                .status())
        .isEqualTo(403);
    assertThat(list(f).get(0).get("version").asLong()).isEqualTo(1);

    assertProblem(delete(f, blockId, 0), 412, "common.stale-version");
    assertThat(delete(f, blockId, 1).status()).isEqualTo(204);
    assertProblem(
        api().get(f.blocks() + "/" + blockId).token(TestTokens.staff(f.slug())).send(),
        404,
        "facility.block-not-found");
    assertThat(list(f).size()).isZero();
  }

  @Test
  void overlappingBlocksAreRefused() {
    Facility f = facility();
    UUID first = id(created(f, block("2026-11-03", 480, 720)));
    Api.Response clash = post(f, block("2026-11-03", 660, 780));
    assertProblem(clash, 409, "facility.block-overlap");
    assertThat(clash.json().get("params").get("blockId").asString()).isEqualTo(first.toString());
    // Seguido non choca; noutra instalación as mesmas horas tampouco.
    UUID next = id(created(f, block("2026-11-03", 720, 780)));
    assertProblem(put(f, next, 0, block("2026-11-03", 600, 780)), 409, "facility.block-overlap");
    created(facility(), block("2026-11-03", 480, 720));

    // A base tamén o garante, sen pasar polo servizo.
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        insert into block (id, municipality_id, facility_id, date, start_minute,
                            end_minute, starts_at, ends_at, created_by, created_at, updated_at,
                            version)
                        values (:id, :m, :f, date '2026-11-03', 600, 660,
                            timestamptz '2026-11-03 09:00:00+00',
                            timestamptz '2026-11-03 10:00:00+00', 'x', now(), now(), 0)
                        """)
                    .param("id", UUID.randomUUID())
                    .param("m", f.municipality())
                    .param("f", f.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("block_overlap_ex");
  }

  @Test
  void anInvalidPeriodIsRefusedWithItsField() {
    Facility f = facility();
    Api.Response backwards = post(f, block("2026-11-03", 720, 480));
    assertProblem(backwards, 422, "facility.block-invalid-period");
    assertThat(backwards.json().get("errors").get(0).get("field").asString())
        .isEqualTo("endMinute");
    assertProblem(post(f, block("2026-11-03", 485, 720)), 422, "facility.block-invalid-period");
    assertProblem(post(f, block("2026-11-03", 0, 1500)), 422, "facility.block-invalid-period");
    // 29 de marzo de 2026: de 2:00 a 3:00 non existe.
    assertProblem(post(f, block("2026-03-29", 120, 150)), 422, "facility.block-invalid-period");
    Map<String, Object> longReason = block("2026-11-03", 480, 720);
    longReason.put("reason", "x".repeat(201));
    // O tamaño do corpo xa o para a validación da petición.
    assertThat(post(f, longReason).status()).isEqualTo(400);
    assertThat(list(f).size()).isZero();
  }

  /** 25 de outubro de 2026: de 2:00 a 3:00 pasa dúas veces e o bloqueo cobre as dúas. */
  @Test
  void theBlockOfTheRepeatedHourIsStoredWhole() {
    Facility f = facility();
    JsonNode body = created(f, block("2026-10-25", 120, 180));
    assertThat(Instant.parse(body.get("start").asString()))
        .isEqualTo(Instant.parse("2026-10-25T00:00:00Z"));
    assertThat(Instant.parse(body.get("end").asString()))
        .isEqualTo(Instant.parse("2026-10-25T02:00:00Z"));
    Map<String, Object> row =
        jdbc.sql(
                """
                select start_minute, end_minute,
                       extract(epoch from upper(period) - lower(period))::int as seconds,
                       lower(period) = timestamptz '2026-10-25 02:00:00+02' as from_first_two,
                       upper(period) = timestamptz '2026-10-25 03:00:00+01' as to_second_three
                from block where id = :id
                """)
            .param("id", id(body))
            .query()
            .singleRow();
    assertThat(row.get("start_minute")).isEqualTo(120);
    assertThat(row.get("end_minute")).isEqualTo(180);
    assertThat(row.get("seconds")).isEqualTo(7200);
    assertThat(row.get("from_first_two")).isEqualTo(true);
    assertThat(row.get("to_second_three")).isEqualTo(true);
    // E volve igual: minutos de reloxo de parede.
    JsonNode listed = list(f).get(0);
    assertThat(listed.get("startMinute").asInt()).isEqualTo(120);
    assertThat(listed.get("endMinute").asInt()).isEqualTo(180);
  }

  @Test
  void aBlockWithBookingsOpensTheDialogAndThenIsCreated() {
    Facility f = facility();
    UUID booking = UUID.randomUUID();
    impact.affect(
        f.id(),
        List.of(
            new AffectedBooking(
                booking,
                "Club Deportivo Narón",
                true,
                LocalDate.of(2026, 11, 3),
                480,
                570,
                "Fútbol 11")));
    Api.Response first = post(f, block("2026-11-03", 480, 720));
    assertProblem(first, 409, "facility.affected-bookings");
    assertThat(first.json().get("affected").get(0).get("bookingId").asString())
        .isEqualTo(booking.toString());
    assertThat(list(f).size()).isZero();

    Map<String, Object> mute = block("2026-11-03", 480, 720);
    mute.put("decisions", List.of(Map.of("bookingId", booking.toString(), "action", "CANCEL")));
    assertProblem(post(f, mute), 422, "facility.decision-invalid");

    Map<String, Object> confirmed = block("2026-11-03", 480, 720);
    confirmed.put(
        "decisions",
        List.of(
            Map.of(
                "bookingId",
                booking.toString(),
                "action",
                "CANCEL",
                "channels",
                List.of("EMAIL", "WEB"))));
    Api.Response second = post(f, confirmed);
    assertThat(second.status()).as(second.toString()).isEqualTo(201);
    assertThat(impact.appliedTo(f.id()))
        .singleElement()
        .satisfies(
            a ->
                assertThat(a.change())
                    .isEqualTo(
                        new FacilityChange.Block(
                            f.id(),
                            null,
                            Instant.parse("2026-11-03T07:00:00Z"),
                            Instant.parse("2026-11-03T11:00:00Z"))));

    // Cambiar só o motivo e borrar non abren o modal.
    UUID blockId = id(second.json());
    Map<String, Object> renamed = block("2026-11-03", 480, 720);
    renamed.put("reason", "Pintura das liñas");
    assertThat(put(f, blockId, 0, renamed).status()).isEqualTo(200);
    assertThat(delete(f, blockId, 1).status()).isEqualTo(204);
    assertThat(impact.appliedTo(f.id())).hasSize(1);
  }

  @Test
  void openingIntervalsLeaveOutTheBlockedHours() {
    Facility f = facility();
    season(f, "2026-11-01", "2026-11-30", 480, 1320);
    created(f, block("2026-11-03", 480, 720));
    created(f, block("2026-11-03", 840, 900));
    List<OpeningInterval> intervals =
        TenantContext.runAs(
            f.tenant(),
            () ->
                queries.openingIntervals(
                    f.id(), LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-04")));
    assertThat(intervals)
        .extracting(OpeningInterval::date, OpeningInterval::startMinute, OpeningInterval::endMinute)
        .containsExactly(
            Tuple.tuple(LocalDate.parse("2026-11-03"), 720, 840),
            Tuple.tuple(LocalDate.parse("2026-11-03"), 900, 1320),
            Tuple.tuple(LocalDate.parse("2026-11-04"), 480, 1320));
    assertThat(intervals.get(0).start()).isEqualTo(Instant.parse("2026-11-03T11:00:00Z"));
    // Os anacos que deixan os bloqueos seguen a grella do tramo enteiro (ADR-0025, punto 6).
    assertThat(intervals)
        .extracting(OpeningInterval::alignFromMinute)
        .containsExactly(480, 480, 480);
  }

  @Test
  void anotherMunicipalityCannotSeeOrTouchTheBlocks() {
    Facility a = facility();
    Facility b = facility();
    UUID blockId = id(created(a, block("2026-11-03", 480, 720)));
    String foreign = "/api/v1/municipalities/" + b.slug() + "/facilities/" + a.id() + "/blocks";
    assertProblem(
        api().get(foreign).token(TestTokens.staff(b.slug())).send(), 404, "facility.not-found");
    assertProblem(
        api().get(foreign + "/" + blockId).token(TestTokens.staff(b.slug())).send(),
        404,
        "facility.block-not-found");
    assertProblem(
        api()
            .post(foreign, block("2026-11-04", 480, 720))
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertProblem(
        api()
            .put(foreign + "/" + blockId, block("2026-11-04", 480, 720))
            .ifMatch(0)
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertProblem(
        api()
            .method("DELETE", foreign + "/" + blockId, null)
            .ifMatch(0)
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertThat(list(a).get(0).get("date").asString()).isEqualTo("2026-11-03");
  }

  @Test
  void blockWritesAreAudited() {
    Facility f = facility();
    UUID blockId = id(created(f, block("2026-11-03", 480, 720)));
    post(f, block("2026-11-03", 600, 660));
    assertThat(put(f, blockId, 0, block("2026-11-03", 480, 660)).status()).isEqualTo(200);
    assertThat(delete(f, blockId, 1).status()).isEqualTo(204);
    List<String> rows =
        jdbc.sql(
                """
                select action || ' ' || outcome from audit_event
                where municipality_id = (select id from municipality where slug = :s)
                order by occurred_at
                """)
            .param("s", f.slug())
            .query(String.class)
            .list();
    assertThat(rows)
        .contains(
            "facility.block-create SUCCESS",
            "facility.block-create FAILURE",
            "facility.block-update SUCCESS",
            "facility.block-delete SUCCESS");
  }
}
