// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * D4-07 (ADR-0027; plan do dominio 4, §1.2.6–§1.2.11 e §2.3): a xestión do persoal pola API. Os
 * tokens do persoal van <b>sen</b> o claim {@code furar_roles} (o rol sae da táboa) e con segundo
 * factor; a plataforma usa o seu claim {@code *:PLATFORM_ADMIN}. O correo sae polo {@link
 * RecordingMailSender}.
 */
class StaffManagementIT extends AbstractIT {

  static final Pattern LINK = Pattern.compile("/convite#([A-Za-z0-9_-]{43})");

  @Autowired RecordingMailSender mails;

  String naron;
  String ferrol;
  UUID naronId;
  UUID ferrolId;
  String manager;
  UUID managerId;

  @BeforeEach
  void municipalities() {
    naron = uniqueSlug("xestion-naron");
    ferrol = uniqueSlug("xestion-ferrol");
    naronId = id(createMunicipality(naron));
    ferrolId = id(createMunicipality(ferrol));
    manager = "sub-xestora-" + UUID.randomUUID();
    managerId = member(naronId, manager, "MANAGER", "ACTIVE");
  }

  // --- invitar e o correo ---

  @Test
  void invitingCreatesTheMembershipSendsTheMailAndRecordsTheDelivery() throws Exception {
    String email = email("conserxe");

    Api.Response created = invite(naron, token(manager), " " + email.toUpperCase() + " ", "STAFF");

    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    JsonNode body = created.json();
    UUID id = id(body);
    assertThat(created.header("Location")).isEqualTo(staff(naron) + "/" + id);
    assertThat(created.etagVersion()).isZero();
    assertThat(body.get("email").asString()).isEqualTo(email);
    assertThat(body.get("name").isNull()).isTrue();
    assertThat(body.get("role").asString()).isEqualTo("STAFF");
    assertThat(body.get("status").asString()).isEqualTo("INVITED");
    assertThat(body.get("language").asString()).isEqualTo("gl");
    assertThat(body.get("invitedBy").get("kind").asString()).isEqualTo("MEMBER");
    assertThat(body.get("invitedBy").get("name").asString()).isEqualTo("Persoa de proba");
    assertThat(body.get("acceptedAt").isNull()).isTrue();
    assertThat(body.get("version").asLong()).isZero();
    JsonNode invitation = body.get("invitation");
    assertThat(invitation.get("delivery").asString()).isEqualTo("SENT");
    Instant expiresAt = Instant.parse(invitation.get("expiresAt").asString());
    assertThat(Duration.between(Instant.now(), expiresAt))
        .isBetween(Duration.ofDays(7).minusMinutes(5), Duration.ofDays(7));

    // O correo leva a ligazón co token no fragmento; na base só o seu hash, e a API non o mostra.
    List<MimeMessage> sent = mails.sentTo(email);
    assertThat(sent).hasSize(1);
    String text = text(sent.getFirst());
    assertThat(text).contains("http://localhost:8080/" + naron + "/convite#", "Xestora Proba");
    String token = linkToken(text);
    assertThat(created.body()).doesNotContain(token);
    assertThat(
            jdbc.sql(
                    """
                    select delivery from staff_invitation
                     where member_id = :m and token_hash = :hash
                    """)
                .param("m", id)
                .param("hash", sha256(token))
                .query(String.class)
                .single())
        .isEqualTo("SENT");

    // O detalle é o mesmo co seu ETag.
    Api.Response detail = api().get(staff(naron) + "/" + id).token(token(manager)).send();
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.etagVersion()).isZero();
    assertThat(detail.json()).isEqualTo(body);

    JsonNode event = lastEvent("staff.invite", "SUCCESS");
    assertThat(event.get("memberId").asString()).isEqualTo(id.toString());
    assertThat(event.get("role").asString()).isEqualTo("STAFF");
  }

  @Test
  void anSmtpDownLeavesTheInvitationFailedAndVisibleInTheList() {
    String email = email("smtp");
    mails.failNext(1);

    Api.Response created = invite(naron, token(manager), email, "STAFF");

    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("invitation").get("delivery").asString()).isEqualTo("FAILED");
    assertThat(mails.sentTo(email)).isEmpty();
    JsonNode listed = listed(naron, id(created.json()));
    assertThat(listed.get("invitation").get("delivery").asString()).isEqualTo("FAILED");
    assertThat(
            jdbc.sql("select delivery from staff_invitation where member_id = :m")
                .param("m", id(created.json()))
                .query(String.class)
                .single())
        .isEqualTo("FAILED");
  }

  @Test
  void theMailLanguageIsTheChosenOneOrTheRequestOne() throws Exception {
    String chosen = email("idioma");
    String byHeader = email("cabeceira");

    Api.Response es = invite(naron, token(manager), chosen, "STAFF", "es");
    Api.Response header =
        api()
            .post(staff(naron), Map.of("email", byHeader, "role", "MANAGER"))
            .token(token(manager))
            .language("es")
            .send();

    assertThat(es.json().get("language").asString()).isEqualTo("es");
    assertThat(header.status()).isEqualTo(201);
    assertThat(header.json().get("language").asString()).isEqualTo("es");
    assertThat(mails.sentTo(chosen).getFirst().getSubject())
        .contains("te invita a atender las reservas de las instalaciones deportivas");
  }

  @Test
  void anInvalidEmailIs422AndAnExistingMembershipIs409() {
    for (String invalid : List.of("", "sen-arroba", "a@", "dous espazos@example.org")) {
      Api.Response response = invite(naron, token(manager), invalid, "STAFF");
      assertThat(response.status()).as(invalid).isEqualTo(422);
      assertThat(code(response)).isEqualTo("staff.email-invalid");
    }
    String email = email("dobre");
    assertThat(invite(naron, token(manager), email, "STAFF").status()).isEqualTo(201);

    Api.Response again = invite(naron, token(manager), email.toUpperCase(), "MANAGER");
    assertThat(again.status()).isEqualTo(409);
    assertThat(code(again)).isEqualTo("staff.already-member");
    assertThat(mails.sentTo(email)).hasSize(1);
  }

  /**
   * Enumeración: quen invita non pode saber se o correo xa é persoal doutro concello. A resposta
   * ten os mesmos campos e valores ca para un correo novo.
   */
  @Test
  void invitingSomeoneFromAnotherMunicipalityLooksTheSameAsSomeoneNew() {
    String elsewhere = email("ferrol");
    member(ferrolId, "sub-" + UUID.randomUUID(), "MANAGER", "ACTIVE", elsewhere);
    String fresh = email("nova");

    JsonNode a = invite(naron, token(manager), elsewhere, "MANAGER").json();
    JsonNode b = invite(naron, token(manager), fresh, "MANAGER").json();

    assertThat(fieldNames(a)).isEqualTo(fieldNames(b));
    for (String field : List.of("name", "role", "status", "acceptedAt", "lastSignInAt")) {
      assertThat(a.get(field)).as(field).isEqualTo(b.get(field));
    }
    assertThat(a.get("invitation").get("delivery")).isEqualTo(b.get("invitation").get("delivery"));
    assertThat(a.get("invitedBy")).isEqualTo(b.get("invitedBy"));
  }

  // --- límites ---

  @Test
  void theThirtyFirstSendInADayIs429WithRetryAfter() {
    UUID seeded = member(naronId, null, "STAFF", "INVITED");
    Instant now = Instant.now();
    for (int i = 0; i < 30; i++) {
      seedSend(naronId, seeded, now.minus(Duration.ofMinutes(15L + i)));
    }

    Api.Response response = invite(naron, token(manager), email("trixesima"), "STAFF");

    assertThat(response.status()).isEqualTo(429);
    assertThat(code(response)).isEqualTo("staff.invitation-rate-limited");
    long retryAfter = Long.parseLong(response.header("Retry-After"));
    // Libérase cando o envío máis vello dos 30 (hai 44 min) cumpra 24 h.
    assertThat(retryAfter)
        .isBetween(Duration.ofHours(23).toSeconds(), Duration.ofHours(24).toSeconds());
    assertThat(lastEvent("staff.invite", "FAILURE").get("code").asString())
        .isEqualTo("staff.invitation-rate-limited");
    // Ferrol non comparte o límite de Narón.
    String ferrolManager = "sub-" + UUID.randomUUID();
    member(ferrolId, ferrolManager, "MANAGER", "ACTIVE");
    assertThat(invite(ferrol, token(ferrolManager), email("ferrol"), "STAFF").status())
        .isEqualTo(201);
  }

  @Test
  void resendingBeforeTwoMinutesIs429WithRetryAfter() {
    Instant before = Instant.now();
    JsonNode created = invite(naron, token(manager), email("presa"), "STAFF").json();
    // A resposta xa di desde cando se pode reenviar, e a lista tamén.
    Instant availableAt =
        Instant.parse(created.get("invitation").get("resendAvailableAt").asString());
    assertThat(availableAt)
        .isBetween(before.plus(Duration.ofMinutes(2)), Instant.now().plus(Duration.ofMinutes(2)));
    assertThat(created.get("invitation").get("resendsExhausted").asBoolean()).isFalse();
    JsonNode listed = listed(naron, id(created)).get("invitation");
    assertThat(Instant.parse(listed.get("resendAvailableAt").asString())).isEqualTo(availableAt);

    Api.Response response = resend(naron, token(manager), id(created), 0);

    assertThat(response.status()).isEqualTo(429);
    assertThat(code(response)).isEqualTo("staff.invitation-rate-limited");
    assertThat(Long.parseLong(response.header("Retry-After"))).isBetween(110L, 120L);
    assertThat(lastEvent("staff.invite-resend", "FAILURE").get("code").asString())
        .isEqualTo("staff.invitation-rate-limited");
  }

  @Test
  void afterFiveSendsTheListSaysTheResendsAreExhausted() {
    UUID seeded = member(naronId, null, "STAFF", "INVITED");
    Instant now = Instant.now();
    for (int i = 0; i < 4; i++) {
      seedSend(naronId, seeded, now.minus(Duration.ofHours(i + 1L)));
    }
    JsonNode four = listed(naron, seeded).get("invitation");
    assertThat(four.get("resendsExhausted").asBoolean()).isFalse();
    assertThat(four.get("resendAvailableAt").isNull()).isTrue();

    seedSend(naronId, seeded, now.minus(Duration.ofHours(6)));

    JsonNode five = listed(naron, seeded).get("invitation");
    assertThat(five.get("resendsExhausted").asBoolean()).isTrue();
    assertThat(five.get("resendAvailableAt").isNull()).isTrue();
    Api.Response response = resend(naron, token(manager), seeded, 0);
    assertThat(response.status()).isEqualTo(429);
    assertThat(response.header("Retry-After")).isNull();
  }

  @Test
  void resendingReplacesTheInvitationAndSendsANewToken() throws Exception {
    String email = email("reenvio");
    UUID id = id(invite(naron, token(manager), email, "STAFF").json());
    // O primeiro envío foi hai 3 minutos.
    jdbc.sql(
            """
            update staff_invitation
               set created_at = created_at - interval '3 minutes',
                   expires_at = expires_at - interval '3 minutes'
             where member_id = :m
            """)
        .param("m", id)
        .update();

    // Pasados os 2 minutos, a lista xa non pon espera.
    assertThat(listed(naron, id).get("invitation").get("resendAvailableAt").isNull()).isTrue();

    Api.Response resent = resend(naron, token(manager), id, 0);

    assertThat(resent.status()).as(resent.toString()).isEqualTo(200);
    assertThat(resent.json().get("invitation").get("resendAvailableAt").isNull()).isFalse();
    assertThat(resent.etagVersion()).isEqualTo(1);
    assertThat(resent.json().get("invitation").get("delivery").asString()).isEqualTo("SENT");
    List<MimeMessage> sent = mails.sentTo(email);
    assertThat(sent).hasSize(2);
    String first = linkToken(text(sent.get(0)));
    String second = linkToken(text(sent.get(1)));
    assertThat(second).isNotEqualTo(first);
    assertThat(openInvitations(id)).containsExactly(sha256Hex(second));
    assertThat(lastEvent("staff.invite-resend", "SUCCESS").get("memberId").asString())
        .isEqualTo(id.toString());
    // O mesmo If-Match outra vez xa é vello.
    assertThat(resend(naron, token(manager), id, 0).status()).isEqualTo(412);
  }

  // --- transicións ---

  @Test
  void validTransitions() {
    UUID invited = id(invite(naron, token(manager), email("transicions"), "STAFF").json());

    // Rol dunha invitada.
    Api.Response promoted = changeRole(naron, token(manager), invited, 0, "MANAGER");
    assertThat(promoted.status()).isEqualTo(200);
    assertThat(promoted.json().get("role").asString()).isEqualTo("MANAGER");
    assertThat(promoted.etagVersion()).isEqualTo(1);
    JsonNode roleEvent = lastEvent("staff.role-change", "SUCCESS");
    assertThat(roleEvent.get("fromRole").asString()).isEqualTo("STAFF");
    assertThat(roleEvent.get("toRole").asString()).isEqualTo("MANAGER");

    // Baixa dunha invitada: a invitación aberta deixa de valer.
    Api.Response deactivated = deactivate(naron, token(manager), invited, 1);
    assertThat(deactivated.status()).isEqualTo(200);
    assertThat(deactivated.json().get("status").asString()).isEqualTo("DEACTIVATED");
    assertThat(deactivated.json().get("deactivatedAt").isNull()).isFalse();
    assertThat(deactivated.json().get("invitation").isNull()).isTrue();
    assertThat(openInvitations(invited)).isEmpty();

    // Activa: STAFF → MANAGER → STAFF (queda outro xestor) e desactivación.
    UUID active = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "ACTIVE");
    assertThat(changeRole(naron, token(manager), active, 0, "MANAGER").status()).isEqualTo(200);
    assertThat(changeRole(naron, token(manager), active, 1, "STAFF").status()).isEqualTo(200);
    Api.Response gone = deactivate(naron, token(manager), active, 2);
    assertThat(gone.status()).isEqualTo(200);
    assertThat(lastEvent("staff.deactivate", "SUCCESS").get("memberId").asString())
        .isEqualTo(active.toString());

    // O mesmo rol non cambia nada.
    UUID same = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "ACTIVE");
    Api.Response unchanged = changeRole(naron, token(manager), same, 0, "STAFF");
    assertThat(unchanged.status()).isEqualTo(200);
    assertThat(unchanged.etagVersion()).isZero();

    // Volver invitar a quen se desactivou crea outra pertenza: o historial queda.
    String email = email("volta");
    UUID first = id(invite(naron, token(manager), email, "STAFF").json());
    assertThat(deactivate(naron, token(manager), first, 0).status()).isEqualTo(200);
    Api.Response again = invite(naron, token(manager), email, "STAFF");
    assertThat(again.status()).isEqualTo(201);
    assertThat(id(again.json())).isNotEqualTo(first);
  }

  @Test
  void invalidTransitions() {
    UUID deactivated = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "DEACTIVATED");
    UUID active = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "ACTIVE");

    assertError(
        changeRole(naron, token(manager), deactivated, 0, "MANAGER"),
        409,
        "staff.transition-invalid");
    assertError(deactivate(naron, token(manager), deactivated, 0), 409, "staff.transition-invalid");
    assertError(resend(naron, token(manager), deactivated, 0), 409, "staff.transition-invalid");
    assertError(resend(naron, token(manager), active, 0), 409, "staff.transition-invalid");
    assertThat(lastEvent("staff.deactivate", "FAILURE").get("code").asString())
        .isEqualTo("staff.transition-invalid");

    // O último xestor activo: nin baixar de rol nin desactivarse (tampouco a si mesmo).
    assertError(
        changeRole(naron, token(manager), managerId, 0, "STAFF"), 409, "staff.last-manager");
    assertError(deactivate(naron, token(manager), managerId, 0), 409, "staff.last-manager");
    assertThat(lastEvent("staff.role-change", "FAILURE").get("code").asString())
        .isEqualTo("staff.last-manager");
    assertThat(status(managerId)).isEqualTo("ACTIVE");

    // Unha xestora invitada non conta como xestora activa.
    invite(naron, token(manager), email("xestora-invitada"), "MANAGER");
    assertError(deactivate(naron, token(manager), managerId, 0), 409, "staff.last-manager");

    // Con outra xestora activa, si (tamén a si mesma).
    member(naronId, "sub-" + UUID.randomUUID(), "MANAGER", "ACTIVE");
    assertThat(deactivate(naron, token(manager), managerId, 0).status()).isEqualTo(200);
    // E na seguinte petición xa non é xestora.
    assertThat(list(naron, token(manager)).status()).isEqualTo(403);
  }

  /**
   * Dúas desactivacións á vez dos dous últimos xestores: unha pasa e a outra, 409. Fai as dúas a
   * plataforma, para que a autorización non dependa de cal remata antes.
   */
  @Test
  void twoSimultaneousDeactivationsOfTheLastTwoManagersLeaveOne() throws Exception {
    UUID otherId = member(naronId, "sub-xestor-" + UUID.randomUUID(), "MANAGER", "ACTIVE");
    String platform = TestTokens.platformAdmin();
    CyclicBarrier together = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Api.Response> responses = new ArrayList<>();
    try {
      List<Future<Api.Response>> calls = new ArrayList<>();
      for (UUID target : List.of(managerId, otherId)) {
        calls.add(
            pool.submit(
                () -> {
                  together.await(10, TimeUnit.SECONDS);
                  return deactivate(naron, platform, target, 0);
                }));
      }
      for (Future<Api.Response> call : calls) {
        responses.add(call.get(60, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(responses).extracting(Api.Response::status).containsExactlyInAnyOrder(200, 409);
    Api.Response conflict =
        responses.stream().filter(r -> r.status() == 409).findFirst().orElseThrow();
    assertThat(code(conflict)).isEqualTo("staff.last-manager");
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from staff_member
                     where municipality_id = :m and role = 'MANAGER' and status = 'ACTIVE'
                    """)
                .param("m", naronId)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  // --- If-Match ---

  @Test
  void changesNeedACurrentIfMatch() {
    UUID active = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "ACTIVE");
    String base = staff(naron) + "/" + active;

    assertError(
        api().post(base + "/deactivate", Map.of()).token(token(manager)).send(),
        428,
        "common.precondition-required");
    assertError(
        api().put(base + "/role", Map.of("role", "MANAGER")).token(token(manager)).send(),
        428,
        "common.precondition-required");
    assertError(
        api().post(base + "/invitation", Map.of()).token(token(manager)).send(),
        428,
        "common.precondition-required");
    assertError(deactivate(naron, token(manager), active, 7), 412, "common.stale-version");
    assertError(
        changeRole(naron, token(manager), active, 7, "MANAGER"), 412, "common.stale-version");
    assertError(resend(naron, token(manager), active, 7), 412, "common.stale-version");
    assertThat(status(active)).isEqualTo("ACTIVE");
  }

  // --- quen pode ---

  @Test
  void thePlatformManagesAnyRoleButSeesNoBookingsNorIndicators() {
    String platform = TestTokens.platformAdmin();

    Api.Response managerInvite = invite(naron, platform, email("xestora-plataforma"), "MANAGER");
    assertThat(managerInvite.status()).as(managerInvite.toString()).isEqualTo(201);
    // Quen invitou é a plataforma, sen ningún dato da súa conta.
    assertThat(managerInvite.json().get("invitedBy").get("kind").asString()).isEqualTo("PLATFORM");
    assertThat(managerInvite.json().get("invitedBy").get("name").isNull()).isTrue();

    // Invita tamén conserxes e reenvía a súa invitación (revisión do 2026-11-02).
    String staffEmail = email("conserxe-plataforma");
    Api.Response staffInvite = invite(naron, platform, staffEmail, "STAFF");
    assertThat(staffInvite.status()).as(staffInvite.toString()).isEqualTo(201);
    assertThat(staffInvite.json().get("role").asString()).isEqualTo("STAFF");
    assertThat(mails.sentTo(staffEmail)).hasSize(1);
    UUID invitedStaff = id(invite(naron, token(manager), email("conserxe"), "STAFF").json());
    jdbc.sql(
            """
            update staff_invitation
               set created_at = created_at - interval '3 minutes',
                   expires_at = expires_at - interval '3 minutes'
             where member_id = :m
            """)
        .param("m", invitedStaff)
        .update();
    Api.Response resent = resend(naron, platform, invitedStaff, 0);
    assertThat(resent.status()).as(resent.toString()).isEqualTo(200);

    // Cambia o rol e desactiva a calquera.
    UUID active = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "ACTIVE");
    Api.Response promoted = changeRole(naron, platform, active, 0, "MANAGER");
    assertThat(promoted.status()).as(promoted.toString()).isEqualTo(200);
    assertThat(promoted.json().get("role").asString()).isEqualTo("MANAGER");
    assertThat(changeRole(naron, platform, active, 1, "STAFF").status()).isEqualTo(200);
    assertThat(list(naron, platform).status()).isEqualTo(200);
    assertThat(deactivate(naron, platform, active, 2).status()).isEqualTo(200);

    // E segue sen ver reservas nin indicadores.
    String base = "/api/v1/municipalities/" + naron;
    for (String path : List.of(base + "/bookings", base + "/indicators")) {
      Api.Response response = api().get(path).token(platform).send();
      assertThat(response.status()).as(path + " " + response).isEqualTo(403);
    }
  }

  @Test
  void staffCanDoNothingHere() {
    String conserxe = "sub-conserxe-" + UUID.randomUUID();
    member(naronId, conserxe, "STAFF", "ACTIVE");
    UUID other = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "INVITED");
    String t = token(conserxe);

    for (Api.Response response :
        List.of(
            list(naron, t),
            api().get(staff(naron) + "/" + other).token(t).send(),
            invite(naron, t, email("intento"), "STAFF"),
            resend(naron, t, other, 0),
            changeRole(naron, t, other, 0, "MANAGER"),
            deactivate(naron, t, other, 0))) {
      assertError(response, 403, "auth.forbidden");
    }
    assertThat(status(other)).isEqualTo("INVITED");
  }

  @Test
  void aMemberOfAnotherMunicipalityIsNotFound() {
    UUID ferrolMember = member(ferrolId, "sub-" + UUID.randomUUID(), "STAFF", "ACTIVE");
    String t = token(manager);

    assertError(
        api().get(staff(naron) + "/" + ferrolMember).token(t).send(), 404, "staff.not-found");
    assertError(changeRole(naron, t, ferrolMember, 0, "MANAGER"), 404, "staff.not-found");
    assertError(deactivate(naron, t, ferrolMember, 0), 404, "staff.not-found");
    assertError(resend(naron, t, ferrolMember, 0), 404, "staff.not-found");
    assertThat(list(naron, t).json().findValuesAsString("id"))
        .doesNotContain(ferrolMember.toString());
    // E no URL de Ferrol, a xestora de Narón non é ninguén.
    assertError(list(ferrol, t), 403, "auth.forbidden");
    assertThat(status(ferrolMember)).isEqualTo("ACTIVE");
  }

  @Test
  void theListFiltersByStatus() {
    UUID invited = id(invite(naron, token(manager), email("filtro"), "STAFF").json());
    UUID deactivated = member(naronId, "sub-" + UUID.randomUUID(), "STAFF", "DEACTIVATED");

    assertThat(ids(api().get(staff(naron) + "?status=INVITED").token(token(manager)).send()))
        .containsExactly(invited.toString());
    assertThat(ids(api().get(staff(naron) + "?status=DEACTIVATED").token(token(manager)).send()))
        .containsExactly(deactivated.toString());
    assertThat(ids(api().get(staff(naron) + "?status=ACTIVE").token(token(manager)).send()))
        .containsExactly(managerId.toString());
    JsonNode me = listed(naron, managerId);
    assertThat(me.get("name").asString()).isEqualTo("Persoa de proba");
    assertThat(me.get("invitation").isNull()).isTrue();
  }

  /** ADR-0020: os detalles do rexistro nunca levan o correo (nin o token). */
  @Test
  void theAuditTrailNeverCarriesTheEmail() throws Exception {
    String email = email("rastro");
    UUID id = id(invite(naron, token(manager), email, "STAFF").json());
    invite(naron, token(manager), email, "STAFF"); // 409
    invite(naron, token(manager), "mal", "STAFF"); // 422
    resend(naron, token(manager), id, 0); // 429
    changeRole(naron, token(manager), id, 0, "MANAGER");
    deactivate(naron, token(manager), id, 1);
    deactivate(naron, token(manager), id, 2); // 409
    String token = linkToken(text(mails.sentTo(email).getFirst()));

    List<String> details =
        jdbc.sql(
                """
                select action || ' ' || outcome || ' ' || details::text from audit_event
                 where municipality_id = :m and action like 'staff.%'
                """)
            .param("m", naronId)
            .query(String.class)
            .list();
    assertThat(details)
        .anyMatch(d -> d.startsWith("staff.invite SUCCESS"))
        .anyMatch(d -> d.startsWith("staff.invite FAILURE"))
        .anyMatch(d -> d.startsWith("staff.invite-resend FAILURE"))
        .anyMatch(d -> d.startsWith("staff.role-change SUCCESS"))
        .anyMatch(d -> d.startsWith("staff.deactivate SUCCESS"))
        .anyMatch(d -> d.startsWith("staff.deactivate FAILURE"));
    assertThat(details)
        .noneMatch(d -> d.contains("@") || d.contains("rastro") || d.contains(token));
  }

  // --- axudas ---

  static String token(String subject) {
    return TestTokens.withoutRolesClaim(subject, Map.of("name", "Xestora Proba"));
  }

  static String email(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.org";
  }

  static String staff(String slug) {
    return "/api/v1/municipalities/" + slug + "/staff";
  }

  Api.Response invite(String slug, String token, String email, String role) {
    return invite(slug, token, email, role, "gl");
  }

  Api.Response invite(String slug, String token, String email, String role, String language) {
    return api()
        .post(staff(slug), Map.of("email", email, "role", role, "language", language))
        .token(token)
        .send();
  }

  Api.Response resend(String slug, String token, UUID id, long version) {
    return api()
        .post(staff(slug) + "/" + id + "/invitation", Map.of())
        .token(token)
        .ifMatch(version)
        .send();
  }

  Api.Response changeRole(String slug, String token, UUID id, long version, String role) {
    return api()
        .put(staff(slug) + "/" + id + "/role", Map.of("role", role))
        .token(token)
        .ifMatch(version)
        .send();
  }

  Api.Response deactivate(String slug, String token, UUID id, long version) {
    return api()
        .post(staff(slug) + "/" + id + "/deactivate", Map.of())
        .token(token)
        .ifMatch(version)
        .send();
  }

  Api.Response list(String slug, String token) {
    return api().get(staff(slug)).token(token).send();
  }

  JsonNode listed(String slug, UUID id) {
    Api.Response response = list(slug, token(manager));
    assertThat(response.status()).isEqualTo(200);
    for (JsonNode item : response.json()) {
      if (item.get("id").asString().equals(id.toString())) {
        return item;
      }
    }
    throw new AssertionError(id + " not listed");
  }

  static List<String> ids(Api.Response response) {
    assertThat(response.status()).isEqualTo(200);
    return response.json().findValuesAsString("id");
  }

  static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    node.properties().forEach(e -> names.add(e.getKey()));
    return names;
  }

  static String code(Api.Response response) {
    return response.json().get("code").asString();
  }

  static void assertError(Api.Response response, int status, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(status);
    assertThat(code(response)).isEqualTo(code);
  }

  String status(UUID member) {
    return jdbc.sql("select status from staff_member where id = :id")
        .param("id", member)
        .query(String.class)
        .single();
  }

  /** O último evento desa acción e resultado no concello de Narón, cos seus detalles. */
  JsonNode lastEvent(String action, String outcome) {
    return jdbc.sql(
            """
            select details::text from audit_event
             where municipality_id = :m and action = :a and outcome = :o
             order by occurred_at desc limit 1
            """)
        .param("m", naronId)
        .param("a", action)
        .param("o", outcome)
        .query((rs, n) -> Api.JSON.readTree(rs.getString(1)))
        .single();
  }

  List<String> openInvitations(UUID member) {
    return jdbc.sql(
            """
            select encode(token_hash, 'hex') from staff_invitation
             where member_id = :m and used_at is null and superseded_at is null
            """)
        .param("m", member)
        .query(String.class)
        .list();
  }

  UUID member(UUID municipality, @Nullable String subject, String role, String status) {
    return member(municipality, subject, role, status, email("persoal"));
  }

  /** Unha pertenza por JDBC como dono da base (sen RLS). */
  UUID member(
      UUID municipality, @Nullable String subject, String role, String status, String email) {
    UUID id = UUID.randomUUID();
    boolean accepted = !"INVITED".equals(status);
    boolean deactivated = "DEACTIVATED".equals(status);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by, deactivated_at,
               created_at, updated_at, version)
            values (:id, :m, :email, :name, :subject, :role, :status, 'gl', 'sub-semente', :now,
                    :acceptedAt, :deactivatedBy, :deactivatedAt, :now, :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", email)
        .param("name", accepted ? "Persoa de proba" : null, Types.VARCHAR)
        .param(
            "subject", accepted ? (subject == null ? "sub-" + id : subject) : null, Types.VARCHAR)
        .param("role", role)
        .param("status", status)
        .param("now", now)
        .param("acceptedAt", accepted ? now : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .param("deactivatedBy", deactivated ? "sub-semente" : null, Types.VARCHAR)
        .param("deactivatedAt", deactivated ? now : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }

  /** Un envío xa pechado (substituído) dunha pertenza, na hora indicada. */
  void seedSend(UUID municipality, UUID member, Instant at) {
    OffsetDateTime created = at.atOffset(ZoneOffset.UTC);
    byte[] hash = new byte[32];
    new java.security.SecureRandom().nextBytes(hash);
    jdbc.sql(
            """
            insert into staff_invitation
              (id, municipality_id, member_id, token_hash, requested_by, created_at, expires_at,
               superseded_at, delivery)
            values (:id, :m, :member, :hash, 'sub-semente', :created, :expires, :created, 'SENT')
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("member", member)
        .param("hash", hash)
        .param("created", created)
        .param("expires", created.plusDays(7))
        .update();
  }

  static String linkToken(String mailText) {
    Matcher m = LINK.matcher(mailText);
    assertThat(m.find()).as("link in mail").isTrue();
    return m.group(1);
  }

  static byte[] sha256(String token) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
  }

  static String sha256Hex(String token) throws Exception {
    return java.util.HexFormat.of().formatHex(sha256(token));
  }

  /** A parte de texto do correo. */
  static String text(Part part) throws Exception {
    if (part.getContent() instanceof Multipart multipart) {
      StringBuilder found = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        found.append(text(multipart.getBodyPart(i)));
      }
      return found.toString();
    }
    return part.isMimeType("text/plain") ? (String) part.getContent() : "";
  }
}
