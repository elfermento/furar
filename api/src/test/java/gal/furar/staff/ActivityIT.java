// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * D4-09 (ADR-0020, ADR-0027; plan do dominio 4, §1.2.12): o rexistro de actividade.
 *
 * <ul>
 *   <li>{@code actor_roles}: os roles efectivos de quen actúa no concello (táboa e plataforma),
 *       tamén en {@code staff.sign-in}, que se grava mentres se le o rol.
 *   <li>Lectura: o xestor ve só o seu concello, coas accións da plataforma marcadas; a plataforma,
 *       só as súas e as sen concello; páxinas estables con eventos no mesmo instante; filtros.
 *   <li>Bloqueo (LOPDGDD art. 32; ADR-0020, revisión do 2026-10-29): ningunha lista devolve o que
 *       pasou hai máis dun ano; o xestor expórtao cun motivo e unha referencia, que quedan no
 *       rexistro, coa identidade do persoal bloqueado; a plataforma non.
 * </ul>
 *
 * <p>A base é compartida e outras probas gravan eventos agora: os eventos de lectura van nunha
 * xanela propia no pasado ({@link #window}) e as consultas da plataforma limítanse a ela.
 */
class ActivityIT extends AbstractIT {

  static final Instant SIGN_IN = Instant.parse("2026-11-05T09:00:00Z");

  String naron;
  String ferrol;
  UUID naronId;
  UUID ferrolId;

  /** Comezo dunha xanela de dez minutos só desta proba, hai entre 20 e 30 días. */
  Instant window;

  @BeforeEach
  void municipalities() {
    naron = uniqueSlug("rexistro-naron");
    ferrol = uniqueSlug("rexistro-ferrol");
    naronId = id(createMunicipality(naron));
    ferrolId = id(createMunicipality(ferrol));
    window =
        Instant.now()
            .minus(20, ChronoUnit.DAYS)
            .minusSeconds(ThreadLocalRandom.current().nextLong(0, 10 * 24 * 3600))
            .truncatedTo(ChronoUnit.SECONDS);
  }

  // --- actor_roles ---

  @Test
  void managerStaffPlatformAndTwoRolesAreRecorded() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    String staff = "sub-persoal-" + UUID.randomUUID();
    String both = "sub-dobre-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    member(naronId, "STAFF", "ACTIVE", staff);
    member(naronId, "MANAGER", "ACTIVE", both);

    // Xestora da táboa: o primeiro acceso (staff.sign-in) e a invitación.
    assertThat(invite(naron, level2(manager), "STAFF").status()).isEqualTo(201);
    assertThat(roles(manager, "staff.sign-in")).containsExactly("MANAGER");
    assertThat(roles(manager, "staff.invite")).containsExactly("MANAGER");

    // Persoal da táboa: só o acceso, que se grava co rol que se está lendo.
    assertThat(api().get(bookings(naron)).token(level2(staff)).send().status()).isEqualTo(200);
    assertThat(roles(staff, "staff.sign-in")).containsExactly("STAFF");

    // Plataforma (táboa platform_admin, ADR-0028): invita a primeira persoa xestora.
    String platform = TestTokens.platformAdmin();
    assertThat(invite(naron, platform, "MANAGER").status()).isEqualTo(201);
    String platformSubject =
        jdbc.sql(
                """
                select actor_subject from audit_event
                 where action = 'staff.invite' and municipality_id = :m
                   and 'PLATFORM_ADMIN' = any(actor_roles)
                """)
            .param("m", naronId)
            .query(String.class)
            .single();
    assertThat(roles(platformSubject, "staff.invite")).containsExactly("PLATFORM_ADMIN");

    // Unha persoa con dous roles: plataforma e xestora, cada un na súa táboa.
    TestTokens.grantPlatformAdmin(both);
    String twoRoles =
        TestTokens.withoutRolesClaim(both, Map.of("auth_time", SIGN_IN.getEpochSecond()));
    assertThat(invite(naron, twoRoles, "STAFF").status()).isEqualTo(201);
    assertThat(roles(both, "staff.sign-in")).containsExactly("PLATFORM_ADMIN", "MANAGER");
    assertThat(roles(both, "staff.invite")).containsExactly("PLATFORM_ADMIN", "MANAGER");

    // A API devólveos igual, e en Ferrol a xestora de Narón non ten rol.
    JsonNode items = page(naron, level2(manager), "area=staff").get("items");
    assertThat(items)
        .anySatisfy(
            item -> {
              assertThat(item.get("action").asString()).isEqualTo("staff.invite");
              assertThat(texts(item.get("actor").get("roles")))
                  .containsExactly("PLATFORM_ADMIN", "MANAGER");
              assertThat(item.get("actor").get("kind").asString()).isEqualTo("MEMBER");
            });
  }

  // --- lectura do concello ---

  @Test
  void theManagerSeesOnlyTheirMunicipalityWithPlatformActionsMarked() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    String deactivated = "sub-desactivada-" + UUID.randomUUID();
    String platform = "sub-plataforma-" + UUID.randomUUID();
    UUID managerId = member(naronId, "MANAGER", "ACTIVE", manager);
    member(naronId, "STAFF", "DEACTIVATED", deactivated);
    UUID invited = member(naronId, "STAFF", "INVITED", null);

    UUID own =
        event(
            at(1),
            manager,
            "{MANAGER}",
            "staff.invite",
            "SUCCESS",
            naronId,
            naron,
            "{\"memberId\":\"" + invited + "\",\"role\":\"STAFF\"}");
    UUID byPlatform =
        event(
            at(2),
            platform,
            "{PLATFORM_ADMIN}",
            "facility.create",
            "SUCCESS",
            naronId,
            naron,
            "{\"facilityId\":\"" + UUID.randomUUID() + "\"}");
    UUID byDeactivated =
        event(
            at(3),
            deactivated,
            "{STAFF}",
            "booking.payment",
            "FAILURE",
            naronId,
            naron,
            "{\"bookingId\":\"" + UUID.randomUUID() + "\",\"code\":\"booking.not-found\"}");
    UUID byResident =
        event(at(4), "sub-veciña", "{}", "booking.cancel", "SUCCESS", naronId, naron, "{}");
    event(at(1), manager, "{MANAGER}", "facility.create", "SUCCESS", ferrolId, ferrol, "{}");
    event(at(2), platform, "{PLATFORM_ADMIN}", "municipality.import", "SUCCESS", null, null, "{}");

    JsonNode body = page(naron, level2(manager), period());
    List<JsonNode> items = list(body.get("items"));
    assertThat(ids(items)).containsExactly(byResident, byDeactivated, byPlatform, own);
    assertThat(body.get("nextCursor").isNull()).isTrue();

    JsonNode mine = items.get(3);
    assertThat(mine.get("action").asString()).isEqualTo("staff.invite");
    assertThat(mine.get("outcome").asString()).isEqualTo("SUCCESS");
    assertThat(mine.get("municipality").asString()).isEqualTo(naron);
    assertThat(mine.get("requestId").asString()).isEqualTo("req-test");
    JsonNode actor = mine.get("actor");
    assertThat(actor.get("kind").asString()).isEqualTo("MEMBER");
    assertThat(actor.get("name").asString()).isEqualTo("Persoa de proba");
    assertThat(actor.get("email").asString()).isEqualTo(email(managerId));
    assertThat(texts(actor.get("roles"))).containsExactly("MANAGER");
    JsonNode target = mine.get("target");
    assertThat(target.get("kind").asString()).isEqualTo("STAFF_MEMBER");
    assertThat(target.get("id").asString()).isEqualTo(invited.toString());
    assertThat(target.get("memberEmail").asString()).isEqualTo(email(invited));

    // A plataforma, marcada.
    JsonNode fromPlatform = items.get(2).get("actor");
    assertThat(fromPlatform.get("kind").asString()).isEqualTo("PLATFORM");
    assertThat(fromPlatform.get("name").isNull()).isTrue();
    assertThat(texts(fromPlatform.get("roles"))).containsExactly("PLATFORM_ADMIN");
    assertThat(items.get(2).get("target").get("kind").asString()).isEqualTo("FACILITY");

    // Unha persoa xa desactivada segue co seu nome; a veciñanza é «outra conta».
    assertThat(items.get(1).get("actor").get("kind").asString()).isEqualTo("MEMBER");
    assertThat(items.get(1).get("target").get("kind").asString()).isEqualTo("BOOKING");
    assertThat(items.get(0).get("actor").get("kind").asString()).isEqualTo("OTHER");
    assertThat(texts(items.get(0).get("actor").get("roles"))).isEmpty();
    assertThat(items.get(0).get("target").isNull()).isTrue();

    // Sen período (o último ano): nada doutro concello nin sen concello.
    for (JsonNode item : list(page(naron, level2(manager), "").get("items"))) {
      assertThat(item.get("municipality").asString()).isEqualTo(naron);
    }
  }

  @Test
  void onlyTheManagerAndThePlatformReadTheMunicipalityLog() {
    String staff = "sub-persoal-" + UUID.randomUUID();
    member(naronId, "STAFF", "ACTIVE", staff);
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(ferrolId, "MANAGER", "ACTIVE", manager);

    Api.Response asStaff = api().get(activity(naron, "")).token(level2(staff)).send();
    assertThat(asStaff.status()).isEqualTo(403);
    assertThat(code(asStaff)).isEqualTo("auth.forbidden");
    // A xestora de Ferrol non le Narón; a plataforma si le o rexistro do concello (ADR-0028,
    // revisión do 2026-11-03), pero non exporta os bloqueados.
    assertThat(api().get(activity(naron, "")).token(level2(manager)).send().status())
        .isEqualTo(403);
    assertThat(api().get(activity(naron, "")).token(TestTokens.platformAdmin()).send().status())
        .isEqualTo(200);
    // Nin o persoal nin a xestora len o da plataforma.
    assertThat(api().get(platformActivity("")).token(level2(manager)).send().status())
        .isEqualTo(403);
    assertThat(api().get(platformActivity("")).send().status()).isEqualTo(401);
  }

  // --- lectura da plataforma ---

  @Test
  void thePlatformSeesOnlyItsOwnActionsAndThoseWithoutMunicipality() {
    String platform = "sub-plataforma-" + UUID.randomUUID();
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    UUID invited = member(naronId, "MANAGER", "INVITED", null);

    UUID inNaron =
        event(
            at(1),
            platform,
            "{PLATFORM_ADMIN}",
            "staff.invite",
            "SUCCESS",
            naronId,
            naron,
            "{\"memberId\":\"" + invited + "\",\"role\":\"MANAGER\"}");
    UUID inFerrol =
        event(
            at(2),
            platform,
            "{PLATFORM_ADMIN}",
            "staff.deactivate",
            "FAILURE",
            ferrolId,
            ferrol,
            "{\"memberId\":\"" + UUID.randomUUID() + "\",\"code\":\"staff.not-found\"}");
    UUID imported =
        event(
            at(3),
            platform,
            "{PLATFORM_ADMIN}",
            "municipality.import",
            "SUCCESS",
            null,
            null,
            "{}");
    // Sen concello e anterior á columna (sen roles): tamén é da plataforma.
    UUID old = event(at(4), "anonymous", "{}", "municipality.import", "FAILURE", null, null, "{}");
    event(at(5), manager, "{MANAGER}", "staff.invite", "SUCCESS", naronId, naron, "{}");
    event(at(6), "sub-veciña", "{}", "booking.cancel", "SUCCESS", ferrolId, ferrol, "{}");

    JsonNode body =
        api().get(platformActivity(period())).token(TestTokens.platformAdmin()).send().json();
    List<JsonNode> items = list(body.get("items"));
    assertThat(ids(items)).containsExactly(old, imported, inFerrol, inNaron);
    JsonNode first = items.get(3);
    assertThat(first.get("municipality").asString()).isEqualTo(naron);
    assertThat(first.get("actor").get("kind").asString()).isEqualTo("PLATFORM");
    assertThat(first.get("target").get("memberEmail").asString()).isEqualTo(email(invited));
    assertThat(items.get(2).get("target").get("memberEmail").isNull()).isTrue();
    assertThat(items.get(1).get("municipality").isNull()).isTrue();
    assertThat(items.get(0).get("actor").get("kind").asString()).isEqualTo("OTHER");
  }

  // --- páxinas e filtros ---

  @Test
  void pagesAreStableWithEventsAtTheSameInstant() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    Set<UUID> all = new HashSet<>();
    for (int i = 0; i < 115; i++) {
      all.add(event(at(1), manager, "{MANAGER}", "staff.invite", "SUCCESS", naronId, naron, "{}"));
    }
    all.add(
        event(at(2), manager, "{MANAGER}", "staff.deactivate", "SUCCESS", naronId, naron, "{}"));
    String token = level2(manager);

    List<UUID> read = new ArrayList<>();
    List<Integer> sizes = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode body = page(naron, token, period() + (cursor == null ? "" : "&cursor=" + cursor));
      List<JsonNode> items = list(body.get("items"));
      sizes.add(items.size());
      read.addAll(ids(items));
      // Un evento novo entre páxinas vai diante da primeira: non move as seguintes.
      event(at(3), manager, "{MANAGER}", "staff.invite", "SUCCESS", naronId, naron, "{}");
      cursor = body.get("nextCursor").isNull() ? null : body.get("nextCursor").asString();
    } while (cursor != null);

    assertThat(sizes).containsExactly(50, 50, 16);
    assertThat(read).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(all);
  }

  @Test
  void filtersByAreaPersonAndOutcome() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    String staff = "sub-persoal-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    UUID staffId = member(naronId, "STAFF", "ACTIVE", staff);
    UUID ferrolMember = member(ferrolId, "STAFF", "ACTIVE", staff);
    UUID invited = member(naronId, "STAFF", "INVITED", null);

    UUID invite =
        event(at(1), manager, "{MANAGER}", "staff.invite", "SUCCESS", naronId, naron, "{}");
    UUID failed =
        event(at(2), manager, "{MANAGER}", "staff.deactivate", "FAILURE", naronId, naron, "{}");
    UUID payment =
        event(at(3), staff, "{STAFF}", "booking.payment", "SUCCESS", naronId, naron, "{}");
    UUID cancel = event(at(4), staff, "{STAFF}", "booking.cancel", "FAILURE", naronId, naron, "{}");
    // «staffing» non é da área staff.
    UUID other = event(at(5), manager, "{MANAGER}", "staffing.x", "SUCCESS", naronId, naron, "{}");
    String token = level2(manager);

    assertThat(ids(page(naron, token, period() + "&area=staff"))).containsExactly(failed, invite);
    assertThat(ids(page(naron, token, period() + "&area=booking")))
        .containsExactly(cancel, payment);
    assertThat(ids(page(naron, token, period() + "&outcome=FAILURE")))
        .containsExactly(cancel, failed);
    assertThat(ids(page(naron, token, period() + "&memberId=" + staffId)))
        .containsExactly(cancel, payment);
    assertThat(ids(page(naron, token, period() + "&memberId=" + staffId + "&outcome=SUCCESS")))
        .containsExactly(payment);
    // Unha pertenza doutro concello, inexistente ou sen aceptar non fixo nada aquí.
    assertThat(ids(page(naron, token, period() + "&memberId=" + ferrolMember))).isEmpty();
    assertThat(ids(page(naron, token, period() + "&memberId=" + UUID.randomUUID()))).isEmpty();
    assertThat(ids(page(naron, token, period() + "&memberId=" + invited))).isEmpty();
    // O período: [from, to).
    String narrow = "from=" + at(2) + "&to=" + at(4);
    assertThat(ids(page(naron, token, narrow))).containsExactly(payment, failed);
    assertThat(ids(page(naron, token, period()))).hasSize(5).contains(other);
  }

  @Test
  void aRangeLongerThanAYearOrBackwardsIsRejected() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    String token = level2(manager);

    for (String query :
        List.of(
            "from=2025-01-01T00:00:00Z&to=2026-01-02T00:00:01Z",
            "from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z",
            "from=2026-01-01T00:00:00Z&to=2026-01-01T00:00:00Z")) {
      Api.Response response = api().get(activity(naron, query)).token(token).send();
      assertThat(response.status()).as(query).isEqualTo(400);
      assertThat(code(response)).isEqualTo("audit.range-invalid");
    }
    Api.Response platform =
        api()
            .get(platformActivity("from=2024-01-01T00:00:00Z&to=2026-01-01T00:00:00Z"))
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(platform.status()).isEqualTo(400);
    assertThat(code(platform)).isEqualTo("audit.range-invalid");
    // Exactamente 366 días, si.
    assertThat(
            api()
                .get(activity(naron, "from=2024-01-01T00:00:00Z&to=2025-01-01T00:00:00Z"))
                .token(token)
                .send()
                .status())
        .isEqualTo(200);
    // Cursor ou área que non valen.
    for (String query : List.of("cursor=non-vale", "area=STAFF", "area=staff.%25")) {
      Api.Response response = api().get(activity(naron, query)).token(token).send();
      assertThat(response.status()).as(query).isEqualTo(400);
      assertThat(code(response)).isEqualTo("common.invalid-parameter");
    }
  }

  // --- bloqueo e exportación dos bloqueados ---

  @Test
  void noListReturnsBlockedEvents() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    // Bloqueado, pero preto do ano: o período que colle os dous tramos dura menos de 366 días.
    Instant old = Instant.now().minus(380, DAYS).truncatedTo(ChronoUnit.SECONDS);
    UUID blocked =
        event(old, manager, "{MANAGER}", "staff.invite", "SUCCESS", naronId, naron, "{}");
    UUID byPlatform =
        event(
            old,
            "sub-plataforma",
            "{PLATFORM_ADMIN}",
            "staff.invite",
            "SUCCESS",
            naronId,
            naron,
            "{}");
    UUID visible =
        event(at(1), manager, "{MANAGER}", "staff.invite", "SUCCESS", naronId, naron, "{}");
    String token = level2(manager);

    // Un período enteiro bloqueado: baleiro, sen erro.
    String oldPeriod = "from=" + old.minusSeconds(60) + "&to=" + old.plusSeconds(60);
    assertThat(ids(page(naron, token, oldPeriod))).isEmpty();
    // Un período que colle os dous tramos: só o visible.
    String both = "from=" + old.minusSeconds(60) + "&to=" + window.plusSeconds(600);
    List<UUID> read = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode body = page(naron, token, both + (cursor == null ? "" : "&cursor=" + cursor));
      read.addAll(ids(body));
      cursor = body.get("nextCursor").isNull() ? null : body.get("nextCursor").asString();
    } while (cursor != null);
    assertThat(read).contains(visible).doesNotContain(blocked, byPlatform);
    // Tampouco o da plataforma.
    JsonNode platform =
        api().get(platformActivity(oldPeriod)).token(TestTokens.platformAdmin()).send().json();
    assertThat(ids(platform)).isEmpty();
  }

  @Test
  void theManagerExportsBlockedEventsWithAReasonThatStaysInTheLog() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    String gone = "sub-desactivada-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    UUID goneId = member(naronId, "STAFF", "DEACTIVATED", gone, Instant.now().minus(500, DAYS));
    Instant old = blockedWindow();
    UUID first = event(old, gone, "{STAFF}", "booking.payment", "SUCCESS", naronId, naron, "{}");
    UUID second =
        event(
            old.plusSeconds(60),
            manager,
            "{MANAGER}",
            "staff.deactivate",
            "SUCCESS",
            naronId,
            naron,
            "{\"memberId\":\"" + goneId + "\",\"role\":\"STAFF\"}");
    event(
        old.plusSeconds(30),
        manager,
        "{MANAGER}",
        "staff.invite",
        "SUCCESS",
        ferrolId,
        ferrol,
        "{}");
    String token = level2(manager);
    Instant before = Instant.now().minusSeconds(1);

    // Na lista, a persoa desactivada hai máis dun ano está bloqueada.
    JsonNode people =
        api().get("/api/v1/municipalities/" + naron + "/staff").token(token).send().json();
    JsonNode goneItem =
        list(people).stream()
            .filter(i -> i.get("id").asString().equals(goneId.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(goneItem.get("blocked").asBoolean()).isTrue();
    assertThat(goneItem.get("email").isNull()).isTrue();

    Api.Response response =
        exportBlocked(
            naron,
            token,
            old.minusSeconds(60),
            old.plusSeconds(120),
            "COURT_ORDER",
            "  DP 123/2028 ");
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    List<JsonNode> items = list(response.json().get("items"));
    // Do máis vello ao máis novo e só do concello.
    assertThat(ids(items)).containsExactly(first, second);
    // Na exportación, a identidade bloqueada vese.
    JsonNode actor = items.get(0).get("actor");
    assertThat(actor.get("name").asString()).isEqualTo("Persoa de proba");
    assertThat(actor.get("email").asString()).isEqualTo(email(goneId));
    assertThat(actor.get("blocked").asBoolean()).isFalse();
    assertThat(items.get(1).get("target").get("memberEmail").asString()).isEqualTo(email(goneId));

    // A exportación queda no rexistro, co motivo e a referencia e sen datos persoais.
    Map<String, Object> logged =
        jdbc.sql(
                """
                select actor_subject, outcome, array_to_string(actor_roles, ',') as roles,
                       details::text as details
                  from audit_event
                 where action = 'audit.export-blocked' and municipality_id = :m
                   and occurred_at >= :before
                """)
            .param("m", naronId)
            .param("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC))
            .query()
            .singleRow();
    assertThat(logged.get("actor_subject")).isEqualTo(manager);
    assertThat(logged.get("outcome")).isEqualTo("SUCCESS");
    assertThat(logged.get("roles")).isEqualTo("MANAGER");
    String details = String.valueOf(logged.get("details"));
    assertThat(details)
        .contains("\"reason\": \"COURT_ORDER\"")
        .contains("\"reference\": \"DP 123/2028\"")
        .contains("\"count\": 2")
        .doesNotContain("@")
        .doesNotContain("Persoa de proba");
    // E vese na lista, na área «audit».
    assertThat(list(page(naron, token, "area=audit").get("items")))
        .anySatisfy(i -> assertThat(i.get("action").asString()).isEqualTo("audit.export-blocked"));
  }

  @Test
  void exportingBlockedEventsNeedsTheManagerAReferenceAndABlockedPeriod() {
    String manager = "sub-xestora-" + UUID.randomUUID();
    String staff = "sub-persoal-" + UUID.randomUUID();
    String ferrolManager = "sub-xestora-" + UUID.randomUUID();
    member(naronId, "MANAGER", "ACTIVE", manager);
    member(naronId, "STAFF", "ACTIVE", staff);
    member(ferrolId, "MANAGER", "ACTIVE", ferrolManager);
    Instant old = blockedWindow();
    Instant from = old.minusSeconds(60);
    Instant to = old.plusSeconds(60);

    // Só o xestor do concello; a plataforma tampouco.
    assertThat(exportBlocked(naron, level2(staff), from, to, "OPEN_PROCEEDINGS", "EXP-1").status())
        .isEqualTo(403);
    assertThat(
            exportBlocked(naron, level2(ferrolManager), from, to, "OPEN_PROCEEDINGS", "EXP-1")
                .status())
        .isEqualTo(403);
    assertThat(
            exportBlocked(naron, TestTokens.platformAdmin(), from, to, "OPEN_PROCEEDINGS", "EXP-1")
                .status())
        .isEqualTo(403);
    assertThat(
            api()
                .post(
                    "/api/v1/municipalities/" + naron + "/audit-events/blocked-exports",
                    Map.of("from", from.toString(), "to", to.toString()))
                .send()
                .status())
        .isEqualTo(401);

    String token = level2(manager);
    // Referencia baleira, longa de máis ou con caracteres de control.
    for (String reference : List.of("   ", "x".repeat(81), "EXP\n1")) {
      Api.Response response =
          exportBlocked(naron, token, from, to, "DATA_PROTECTION_AUTHORITY", reference);
      assertThat(response.status()).as(reference).isEqualTo(400);
      assertThat(code(response)).isEqualTo("audit.export-reference-invalid");
    }
    // Un motivo que non está na lista.
    assertThat(exportBlocked(naron, token, from, to, "CURIOSITY", "EXP-1").status()).isEqualTo(400);
    // Un período que aínda se ve enteiro.
    Api.Response visible =
        exportBlocked(
            naron, token, window, window.plusSeconds(600), "PUBLIC_PROSECUTOR", "DP 9/2028");
    assertThat(visible.status()).isEqualTo(400);
    assertThat(code(visible)).isEqualTo("audit.export-not-blocked");
    // Máis de 366 días.
    Api.Response tooLong =
        exportBlocked(naron, token, from.minus(400, DAYS), to, "PUBLIC_PROSECUTOR", "DP 9/2028");
    assertThat(tooLong.status()).isEqualTo(400);
    assertThat(code(tooLong)).isEqualTo("audit.range-invalid");
    // Os intentos fallidos do xestor tamén quedan no rexistro, co código.
    Long failures =
        jdbc.sql(
                """
                select count(*) from audit_event
                 where action = 'audit.export-blocked' and municipality_id = :m
                   and outcome = 'FAILURE' and actor_subject = :s
                """)
            .param("m", naronId)
            .param("s", manager)
            .query(Long.class)
            .single();
    assertThat(failures).isEqualTo(5L);
  }

  // --- axudas ---

  /** Un instante bloqueado (entre 400 e 700 días atrás), propio desta proba. */
  Instant blockedWindow() {
    return Instant.now()
        .minus(400, DAYS)
        .minusSeconds(ThreadLocalRandom.current().nextLong(0, 300L * 24 * 3600))
        .truncatedTo(ChronoUnit.SECONDS);
  }

  Api.Response exportBlocked(
      String slug, String token, Instant from, Instant to, String reason, String reference) {
    return api()
        .post(
            "/api/v1/municipalities/" + slug + "/audit-events/blocked-exports",
            Map.of(
                "from",
                from.toString(),
                "to",
                to.toString(),
                "reason",
                reason,
                "reference",
                reference))
        .token(token)
        .send();
  }

  Instant at(int minute) {
    return window.plusSeconds(minute * 60L);
  }

  /** A xanela da proba, enteira. */
  String period() {
    return "from=" + window + "&to=" + window.plusSeconds(600);
  }

  static String activity(String slug, String query) {
    return "/api/v1/municipalities/"
        + slug
        + "/audit-events"
        + (query.isEmpty() ? "" : "?" + query);
  }

  static String platformActivity(String query) {
    return "/api/v1/audit-events" + (query.isEmpty() ? "" : "?" + query);
  }

  static String bookings(String slug) {
    return "/api/v1/municipalities/" + slug + "/bookings";
  }

  JsonNode page(String slug, String token, String query) {
    Api.Response response = api().get(activity(slug, query)).token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  Api.Response invite(String slug, String token, String role) {
    return api()
        .post(
            "/api/v1/municipalities/" + slug + "/staff",
            Map.of(
                "email",
                "nova-" + UUID.randomUUID() + "@example.org",
                "role",
                role,
                "language",
                "gl"))
        .token(token)
        .send();
  }

  static String level2(String subject) {
    return TestTokens.withoutRolesClaim(subject, Map.of("auth_time", SIGN_IN.getEpochSecond()));
  }

  static String code(Api.Response response) {
    return response.json().get("code").asString();
  }

  static List<JsonNode> list(JsonNode array) {
    List<JsonNode> items = new ArrayList<>();
    array.forEach(items::add);
    return items;
  }

  static List<UUID> ids(List<JsonNode> items) {
    return items.stream().map(i -> UUID.fromString(i.get("id").asString())).toList();
  }

  static List<UUID> ids(JsonNode body) {
    return ids(list(body.get("items")));
  }

  static List<String> texts(JsonNode array) {
    return list(array).stream().map(JsonNode::asString).toList();
  }

  /** Os roles gardados no único evento desa persoa e acción. */
  List<String> roles(String subject, String action) {
    return jdbc
        .sql(
            """
            select array_to_string(actor_roles, ',') from audit_event
             where actor_subject = :s and action = :a
            """)
        .param("s", subject)
        .param("a", action)
        .query(String.class)
        .list()
        .stream()
        .map(r -> r.isEmpty() ? List.<String>of() : List.of(r.split(",")))
        .reduce(
            (a, b) -> {
              throw new AssertionError("more than one " + action + " by " + subject);
            })
        .orElseThrow(() -> new AssertionError("no " + action + " by " + subject));
  }

  /** Un evento por JDBC como dono da base, no instante indicado. */
  UUID event(
      Instant at,
      String subject,
      String roles,
      String action,
      String outcome,
      @Nullable UUID municipality,
      @Nullable String slug,
      String details) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into audit_event (id, occurred_at, actor_subject, actor_roles, action, outcome,
              municipality_id, municipality_slug, request_id, details)
            values (:id, :at, :subject, cast(:roles as text[]), :action, :outcome, :m, :slug,
                    'req-test', cast(:details as jsonb))
            """)
        .param("id", id)
        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
        .param("subject", subject)
        .param("roles", roles)
        .param("action", action)
        .param("outcome", outcome)
        .param("m", municipality, Types.OTHER)
        .param("slug", slug, Types.VARCHAR)
        .param("details", details)
        .update();
    return id;
  }

  /** Unha pertenza por JDBC como dono da base (sen RLS); invitada, sen {@code sub}. */
  UUID member(UUID municipality, String role, String status, @Nullable String subject) {
    return member(municipality, role, status, subject, Instant.now());
  }

  /** A mesma, desactivada (se é DEACTIVATED) en {@code deactivatedAt}. */
  UUID member(
      UUID municipality,
      String role,
      String status,
      @Nullable String subject,
      Instant deactivatedAt) {
    UUID id = UUID.randomUUID();
    boolean invited = "INVITED".equals(status);
    boolean deactivated = "DEACTIVATED".equals(status);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by, deactivated_at,
               created_at, updated_at, version)
            values (:id, :m, :email, :name, :subject, :role, :status, 'gl', 'sub-xestora', :now,
                    :acceptedAt, :deactivatedBy, :deactivatedAt, :now, :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", "persoal-" + id + "@example.org")
        .param("name", invited ? null : "Persoa de proba", Types.VARCHAR)
        .param("subject", invited ? null : subject, Types.VARCHAR)
        .param("role", role)
        .param("status", status)
        .param("now", now)
        .param("acceptedAt", invited ? null : now, Types.TIMESTAMP_WITH_TIMEZONE)
        .param("deactivatedBy", deactivated ? "sub-xestora" : null, Types.VARCHAR)
        .param(
            "deactivatedAt",
            deactivated ? OffsetDateTime.ofInstant(deactivatedAt, ZoneOffset.UTC) : null,
            Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }

  String email(UUID member) {
    return "persoal-" + member + "@example.org";
  }
}
