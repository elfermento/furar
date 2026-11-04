// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static gal.furar.staff.StaffManagementIT.linkToken;
import static gal.furar.staff.StaffManagementIT.staff;
import static gal.furar.staff.StaffManagementIT.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gal.furar.booking.BookingQueries;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.in.AcceptStaffInvitationCommand;
import gal.furar.staff.application.port.in.StaffInvitationPreview;
import gal.furar.staff.application.port.out.StaffAccessRepository;
import gal.furar.staff.application.port.out.StaffInvitationRepository;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.application.service.RegistrationProofs;
import gal.furar.staff.application.service.StaffInvitationService;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * D4-08 (ADR-0027; plan do dominio 4, §1.2.6, §1.2.13 e §2.3): ver e aceptar a invitación ao
 * persoal. As invitacións saen da API de xestión e o token, do correo ({@link
 * RecordingMailSender}), como na realidade. Os tokens das persoas van <b>sen</b> o claim {@code
 * furar_roles}: o rol sae da táboa. Todo token que non vale responde co mesmo corpo ca un token
 * inventado.
 */
class StaffInvitationSecurityIT extends AbstractIT {

  @Autowired RecordingMailSender mails;
  @Autowired StaffMemberRepository members;
  @Autowired StaffInvitationRepository invitationRepository;
  @Autowired StaffAccessRepository access;
  @Autowired BookingQueries bookings;
  @Autowired MunicipalityPlaces places;
  @Autowired AuditLog audit;
  @Autowired PlatformTransactionManager transactions;
  @Autowired RegistrationProofs proofs;

  String naron;
  String ferrol;
  UUID naronId;
  UUID ferrolId;
  String manager;

  @BeforeEach
  void municipalities() {
    naron = uniqueSlug("convite-naron");
    ferrol = uniqueSlug("convite-ferrol");
    naronId = id(createMunicipality(naron));
    ferrolId = id(createMunicipality(ferrol));
    manager = "sub-xestora-" + UUID.randomUUID();
    seedActiveMember(naronId, manager, "MANAGER", email("xestora"));
  }

  // --- ver ---

  @Test
  void previewWorksWithoutSessionShowsTheInvitationAndDoesNotSpendIt() {
    Invited invited = invite("STAFF");

    Api.Response preview = preview(naron, invited.token());

    assertThat(preview.status()).as(preview.toString()).isEqualTo(200);
    JsonNode body = preview.json();
    assertThat(body.get("municipalityName").asString()).isNotBlank();
    assertThat(body.get("role").asString()).isEqualTo("STAFF");
    assertThat(body.get("email").asString()).isEqualTo(invited.email());
    Instant expiresAt = Instant.parse(body.get("expiresAt").asString());
    assertThat(Duration.between(Instant.now(), expiresAt))
        .isBetween(Duration.ofDays(7).minusMinutes(5), Duration.ofDays(7));
    assertThat(preview.body()).doesNotContain(invited.token());
    // A proba para o rexistro de Keycloak: deste correo e caduca aos 30 minutos (ADR-0027).
    String[] proof = body.get("registrationProof").asString().split("\\.");
    assertThat(proof).hasSize(5);
    assertThat(proof[0]).isEqualTo("v1");
    assertThat(new String(Base64.getUrlDecoder().decode(proof[3]), StandardCharsets.UTF_8))
        .isEqualTo(invited.email());
    assertThat(Duration.between(Instant.now(), Instant.ofEpochSecond(Long.parseLong(proof[2]))))
        .isBetween(Duration.ofMinutes(25), Duration.ofMinutes(30));
    // Ver dúas veces non gasta nada: segue aberta e pódese aceptar.
    assertThat(preview(naron, invited.token()).status()).isEqualTo(200);
    assertThat(openInvitations(invited.memberId())).isEqualTo(1);
    assertThat(accept(naron, person(invited.email()), invited.token()).status()).isEqualTo(200);
  }

  // --- aceptar ---

  @Test
  void acceptingActivatesTheMembershipAndGivesAccessOnTheNextRequest() {
    Invited invited = invite("MANAGER");
    String subject = "sub-nova-" + UUID.randomUUID();
    String session = person(subject, invited.email().toUpperCase(), true);
    // Antes de aceptar non é persoal do concello.
    assertThat(api().get(staff(naron)).token(session).send().status()).isEqualTo(403);

    Api.Response accepted = accept(naron, session, invited.token());

    assertThat(accepted.status()).as(accepted.toString()).isEqualTo(200);
    assertThat(accepted.json().get("role").asString()).isEqualTo("MANAGER");
    assertThat(accepted.body()).doesNotContain(invited.token());
    Map<String, Object> row =
        jdbc.sql(
                """
                select status, subject, display_name, accepted_at is not null as accepted
                  from staff_member where id = :id
                """)
            .param("id", invited.memberId())
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("status", "ACTIVE")
        .containsEntry("subject", subject)
        .containsEntry("display_name", "Nova Persoa")
        .containsEntry("accepted", true);
    assertThat(
            jdbc.sql("select used_at is not null from staff_invitation where member_id = :m")
                .param("m", invited.memberId())
                .query(Boolean.class)
                .single())
        .isTrue();

    // A seguinte petición xa ten o rol, sen claim de roles no token.
    Api.Response mine = api().get(me(naron)).token(session).send();
    assertThat(mine.status()).isEqualTo(200);
    assertThat(mine.json().get("role").asString()).isEqualTo("MANAGER");
    assertThat(api().get(staff(naron)).token(session).send().status()).isEqualTo(200);
    // E só nese concello.
    assertThat(api().get(staff(ferrol)).token(session).send().status()).isEqualTo(403);

    JsonNode event = lastEvent("staff.accept", "SUCCESS");
    assertThat(event.get("memberId").asString()).isEqualTo(invited.memberId().toString());
    assertThat(event.get("role").asString()).isEqualTo("MANAGER");
    assertThat(event.has("obligationsVersion")).as("sen obrigas que aceptar").isFalse();
    assertThat(
            jdbc.sql(
                    """
                    select actor_subject from audit_event
                     where municipality_id = :m and action = 'staff.accept'
                    """)
                .param("m", naronId)
                .query(String.class)
                .single())
        .isEqualTo(subject);
  }

  @Test
  void anotherVerifiedEmailIs403AndTheMembershipStaysInvited() {
    Invited invited = invite("STAFF");

    Api.Response response = accept(naron, person(email("outra")), invited.token());

    assertError(response, 403, "staff.invitation-email-mismatch");
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
    assertThat(openInvitations(invited.memberId())).isEqualTo(1);
    JsonNode event = lastEvent("staff.accept", "FAILURE");
    assertThat(event.get("code").asString()).isEqualTo("staff.invitation-email-mismatch");
    assertThat(event.get("memberId").asString()).isEqualTo(invited.memberId().toString());
    // Despois, coa conta boa, aínda se pode aceptar.
    assertThat(accept(naron, person(invited.email()), invited.token()).status()).isEqualTo(200);
  }

  @Test
  void anUnverifiedEmailIs403EvenIfItIsTheInvitedOne() {
    Invited invited = invite("STAFF");

    Api.Response response =
        accept(naron, person("sub-" + UUID.randomUUID(), invited.email(), false), invited.token());

    assertError(response, 403, "auth.email-not-verified");
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
    assertThat(openInvitations(invited.memberId())).isEqualTo(1);
  }

  @Test
  void acceptingNeedsASession() {
    Invited invited = invite("STAFF");

    Api.Response response =
        api().post(invitations(naron) + "/accept", Map.of("token", invited.token())).send();

    assertError(response, 401, "auth.unauthenticated");
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
  }

  @Test
  void someoneAlreadyActiveInTheMunicipalityIs409() {
    String subject = "sub-xa-" + UUID.randomUUID();
    seedActiveMember(naronId, subject, "STAFF", email("vella"));
    Invited invited = invite("MANAGER");

    Api.Response response = accept(naron, person(subject, invited.email(), true), invited.token());

    assertError(response, 409, "staff.already-member");
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
  }

  // --- caducidade co reloxo ---

  /**
   * O servizo cun reloxo adiantado: ata un instante antes dos 7 días vale; desde ese momento, 410
   * ao ver e ao aceptar, e a pertenza segue invitada.
   */
  @Test
  void anExpiredTokenIs410ByTheClock() {
    Invited invited = invite("STAFF");
    Instant expiresAt =
        jdbc.sql("select expires_at from staff_invitation where member_id = :m")
            .param("m", invited.memberId())
            .query(OffsetDateTime.class)
            .single()
            .toInstant();
    String subject = "sub-tarde-" + UUID.randomUUID();
    Actor actor =
        new Actor(subject, Set.of(), true, new Actor.Profile("Nova Persoa", invited.email(), "gl"));
    TenantRef tenant = new TenantRef(naronId, naron);

    StaffInvitationService justBefore = serviceAt(expiresAt.minusMillis(1), actor);
    StaffInvitationPreview last =
        TenantContext.runAs(tenant, () -> justBefore.preview(invited.token()));
    assertThat(last.email()).isEqualTo(invited.email());
    // A proba nunca dura máis ca a invitación.
    assertThat(last.registrationProof().split("\\.")[2])
        .isEqualTo(Long.toString(expiresAt.getEpochSecond()));

    StaffInvitationService atExpiry = serviceAt(expiresAt, actor);
    assertThatThrownBy(() -> TenantContext.runAs(tenant, () -> atExpiry.preview(invited.token())))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("staff.invitation-expired"));
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    tenant,
                    () -> atExpiry.accept(new AcceptStaffInvitationCommand(invited.token()))))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("staff.invitation-expired"));
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
    assertThat(lastEvent("staff.accept", "FAILURE").get("code").asString())
        .isEqualTo("staff.invitation-expired");
  }

  /** Pola API: a mesma invitación emitida hai máis de 7 días (a base co reloxo atrasado). */
  @Test
  void anExpiredTokenIs410ThroughTheApi() {
    Invited invited = invite("STAFF");
    jdbc.sql(
            """
            update staff_invitation
               set created_at = created_at - interval '169 hours',
                   expires_at = expires_at - interval '169 hours'
             where member_id = :m
            """)
        .param("m", invited.memberId())
        .update();

    assertError(preview(naron, invited.token()), 410, "staff.invitation-expired");
    assertError(
        accept(naron, person(invited.email()), invited.token()), 410, "staff.invitation-expired");
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
  }

  // --- respostas uniformes ---

  @Test
  void usedSupersededDeactivatedAndForeignTokensLookExactlyLikeAnInventedOne() {
    JsonNode invented = problem(preview(naron, randomToken()));
    JsonNode inventedAccept = problem(accept(naron, person(email("x")), randomToken()));
    assertThat(invented.get("code").asString()).isEqualTo("staff.invitation-invalid");

    // Usado.
    Invited used = invite("STAFF");
    assertThat(accept(naron, person(used.email()), used.token()).status()).isEqualTo(200);
    // Substituído por un reenvío.
    Invited resent = invite("STAFF");
    // O reenvío pide 2 minutos desde o envío anterior: atrásase o primeiro.
    jdbc.sql(
            """
            update staff_invitation
               set created_at = created_at - interval '3 minutes',
                   expires_at = expires_at - interval '3 minutes'
             where member_id = :m
            """)
        .param("m", resent.memberId())
        .update();
    Api.Response again =
        api()
            .post(staff(naron) + "/" + resent.memberId() + "/invitation", Map.of())
            .token(managerToken())
            .ifMatch(0)
            .send();
    assertThat(again.status()).as(again.toString()).isEqualTo(200);
    String newToken = linkToken(lastMailText(resent.email()));
    assertThat(newToken).isNotEqualTo(resent.token());
    // Dunha pertenza desactivada.
    Invited deactivated = invite("STAFF");
    assertThat(
            api()
                .post(staff(naron) + "/" + deactivated.memberId() + "/deactivate", Map.of())
                .token(managerToken())
                .ifMatch(0)
                .send()
                .status())
        .isEqualTo(200);
    // Doutro concello: un token de Narón no URL de Ferrol.
    Invited naronToken = invite("STAFF");

    Map<String, String> cases = new HashMap<>();
    cases.put("used", used.token());
    cases.put("superseded", resent.token());
    cases.put("deactivated", deactivated.token());
    cases.put("malformed", "non-e-un-token");
    cases.put("non-canonical", naronToken.token().substring(0, 42) + "B");
    for (var c : cases.entrySet()) {
      assertThat(problem(preview(naron, c.getValue()))).as(c.getKey()).isEqualTo(invented);
      assertThat(problem(accept(naron, person(email("x")), c.getValue())))
          .as(c.getKey())
          .isEqualTo(inventedAccept);
    }
    // Tamén coa conta do correo invitado: nada distingue o usado do inventado.
    assertThat(problem(accept(naron, person(used.email()), used.token())))
        .isEqualTo(inventedAccept);
    assertThat(problem(accept(naron, person(deactivated.email()), deactivated.token())))
        .isEqualTo(inventedAccept);
    assertThat(problem(accept(naron, person(resent.email()), resent.token())))
        .isEqualTo(inventedAccept);

    JsonNode inFerrol = problem(preview(ferrol, naronToken.token()));
    assertThat(inFerrol).isEqualTo(invented);
    assertThat(problem(accept(ferrol, person(naronToken.email()), naronToken.token())))
        .isEqualTo(inventedAccept);
    assertThat(status(naronToken.memberId())).isEqualTo("INVITED");
    // O de Narón segue valendo en Narón, e o novo do reenvío tamén.
    assertThat(preview(naron, naronToken.token()).status()).isEqualTo(200);
    assertThat(preview(naron, newToken).status()).isEqualTo(200);
  }

  // --- carreira ---

  @Test
  void twoSimultaneousAcceptancesOfTheSameTokenActivateOnce() throws Exception {
    Invited invited = invite("STAFF");
    String a = person("sub-a-" + UUID.randomUUID(), invited.email(), true);
    String b = person("sub-b-" + UUID.randomUUID(), invited.email(), true);
    CyclicBarrier start = new CyclicBarrier(2);
    List<Api.Response> responses = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<Api.Response>> futures = new ArrayList<>();
      for (String session : List.of(a, b)) {
        futures.add(
            pool.submit(
                () -> {
                  start.await(10, TimeUnit.SECONDS);
                  return accept(naron, session, invited.token());
                }));
      }
      for (Future<Api.Response> f : futures) {
        responses.add(f.get(30, TimeUnit.SECONDS));
      }
    }

    assertThat(responses).extracting(Api.Response::status).containsExactlyInAnyOrder(200, 404);
    assertThat(status(invited.memberId())).isEqualTo("ACTIVE");
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from audit_event
                     where municipality_id = :m and action = 'staff.accept' and outcome = 'SUCCESS'
                    """)
                .param("m", naronId)
                .query(Long.class)
                .single())
        .isEqualTo(1L);
  }

  // --- o token non sae ---

  @Test
  void theTokenNeverReachesTheLogsTheResponsesOrTheAuditTrail() throws Exception {
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    List<Logger> verbose =
        List.of(
            (Logger) LoggerFactory.getLogger("gal.furar"),
            (Logger) LoggerFactory.getLogger("furar.audit"),
            (Logger) LoggerFactory.getLogger("org.springframework.web"),
            (Logger) LoggerFactory.getLogger("org.springframework.jdbc"),
            (Logger) LoggerFactory.getLogger("org.springframework.security"));
    List<Level> before = verbose.stream().map(Logger::getLevel).toList();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();

    Invited ok = invite("STAFF");
    Invited mismatch = invite("STAFF");
    Invited foreign = invite("STAFF");
    List<String> tokens = List.of(ok.token(), mismatch.token(), foreign.token());
    List<Api.Response> responses = new ArrayList<>();
    root.addAppender(appender);
    verbose.forEach(l -> l.setLevel(Level.TRACE));
    try {
      responses.add(preview(naron, ok.token()));
      responses.add(preview(ferrol, ok.token()));
      responses.add(accept(naron, person(email("outra")), mismatch.token()));
      responses.add(accept(naron, person(email("allea")), foreign.token()));
      responses.add(accept(naron, person(ok.email()), ok.token()));
      responses.add(accept(naron, person(ok.email()), ok.token()));
    } finally {
      root.detachAppender(appender);
      for (int i = 0; i < verbose.size(); i++) {
        verbose.get(i).setLevel(before.get(i));
      }
    }

    assertThat(responses)
        .extracting(Api.Response::status)
        .containsExactly(200, 404, 403, 403, 200, 404);
    assertThat(appender.list).as("a proba ve os rexistros").isNotEmpty();
    List<String> lines = appender.list.stream().map(StaffInvitationSecurityIT::everything).toList();
    assertThat(lines).anyMatch(l -> l.contains("staff.accept"));
    for (String token : tokens) {
      assertThat(lines).noneMatch(l -> l.contains(token));
      assertThat(responses).noneMatch(r -> r.body().contains(token));
      for (Api.Response r : responses) {
        r.raw()
            .headers()
            .map()
            .values()
            .forEach(v -> assertThat(v.toString()).doesNotContain(token));
      }
    }
    List<String> details =
        jdbc.sql(
                """
                select details::text from audit_event
                 where municipality_id = :m and action = 'staff.accept'
                """)
            .param("m", naronId)
            .query(String.class)
            .list();
    assertThat(details).hasSize(4);
    assertThat(details).noneMatch(d -> d.contains("@") || tokens.stream().anyMatch(d::contains));
  }

  /**
   * Sen segundo factor (decisión do mantedor do 2026-11-02): unha sesión só de contrasinal co
   * correo invitado acepta.
   */
  @Test
  void aPasswordOnlySessionAccepts() {
    Invited invited = invite("STAFF");
    String passwordOnly =
        TestTokens.withoutRolesClaim(
            "sub-pwd-" + UUID.randomUUID(),
            Map.of(
                "email",
                invited.email(),
                "email_verified",
                true,
                "name",
                "Nova Persoa",
                "acr",
                "furar-pwd",
                "amr",
                List.of("pwd")));
    Api.Response accepted = accept(naron, passwordOnly, invited.token());
    assertThat(accepted.status()).as(accepted.toString()).isEqualTo(200);
    assertThat(status(invited.memberId())).isEqualTo("ACTIVE");
  }

  /**
   * R2 (op.acc.3): nin a conta da plataforma nin quen fixo a invitación poden aceptala, aínda co
   * correo invitado; a invitación segue aberta.
   */
  @Test
  void neitherThePlatformNorTheInviterCanAccept() {
    Invited invited = invite("STAFF");
    Map<String, Object> platform = new HashMap<>();
    platform.put("email", invited.email());
    platform.put("email_verified", true);
    String platformSubject = "sub-plat-" + UUID.randomUUID();
    TestTokens.grantPlatformAdmin(platformSubject);
    String platformToken = TestTokens.withoutRolesClaim(platformSubject, platform);

    assertError(
        accept(naron, platformToken, invited.token()), 403, "staff.invitation-platform-account");
    assertError(
        accept(naron, person(manager, invited.email(), true), invited.token()),
        403,
        "staff.invitation-own");
    assertThat(status(invited.memberId())).isEqualTo("INVITED");
    assertThat(openInvitations(invited.memberId())).isEqualTo(1);
    assertThat(lastEvent("staff.accept", "FAILURE").get("code").asString())
        .isEqualTo("staff.invitation-own");
  }

  // --- axudas ---

  record Invited(UUID memberId, String email, String token) {}

  /** Unha invitación nova en Narón, pola API de xestión, co token do correo. */
  Invited invite(String role) {
    String email = email("convidada");
    Api.Response created =
        api()
            .post(staff(naron), Map.of("email", email, "role", role, "language", "gl"))
            .token(managerToken())
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return new Invited(id(created.json()), email, linkToken(lastMailText(email)));
  }

  String lastMailText(String email) {
    try {
      return text(mails.sentTo(email).getLast());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  String managerToken() {
    return StaffManagementIT.token(manager);
  }

  /** Persoa coa conta verificada co correo indicado. */
  static String person(String email) {
    return person("sub-" + UUID.randomUUID(), email, true);
  }

  static String person(String subject, String email, boolean verified) {
    return TestTokens.withoutRolesClaim(
        subject, Map.of("email", email, "email_verified", verified, "name", "Nova Persoa"));
  }

  static String email(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.org";
  }

  static String invitations(String slug) {
    return "/api/v1/municipalities/" + slug + "/staff-invitations";
  }

  static String me(String slug) {
    return "/api/v1/municipalities/" + slug + "/me/staff";
  }

  Api.Response preview(String slug, String token) {
    return api().post(invitations(slug) + "/preview", Map.of("token", token)).send();
  }

  Api.Response accept(String slug, String session, String token) {
    return api().post(invitations(slug) + "/accept", Map.of("token", token)).token(session).send();
  }

  /** Un token con forma boa que ninguén emitiu. */
  static String randomToken() {
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** O problema sen o que cambia en cada petición (instancia e traza). */
  static JsonNode problem(Api.Response response) {
    assertThat(response.contentType()).startsWith("application/problem+json");
    ObjectNode node = (ObjectNode) response.json();
    node.remove("instance");
    node.remove("traceId");
    return node;
  }

  static void assertError(Api.Response response, int status, String code) {
    StaffManagementIT.assertError(response, status, code);
  }

  /** O servizo co reloxo parado en {@code now} e esta persoa na sesión. */
  StaffInvitationService serviceAt(Instant now, Actor actor) {
    CurrentActor session = () -> Optional.of(actor);
    return new StaffInvitationService(
        members,
        invitationRepository,
        access,
        bookings,
        places,
        session,
        audit,
        transactions,
        proofs,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  String status(UUID member) {
    return jdbc.sql("select status from staff_member where id = :id")
        .param("id", member)
        .query(String.class)
        .single();
  }

  int openInvitations(UUID member) {
    return jdbc.sql(
            """
            select count(*) from staff_invitation
             where member_id = :m and used_at is null and superseded_at is null
            """)
        .param("m", member)
        .query(Integer.class)
        .single();
  }

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

  /** Unha pertenza activa por JDBC como dono da base (sen RLS). */
  void seedActiveMember(UUID municipality, @Nullable String subject, String role, String email) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at,
               version)
            values (:id, :m, :email, 'Persoa de proba', :subject, :role, 'ACTIVE', 'gl',
                    'sub-semente', :now, :now, :now, :now, 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("email", email)
        .param("subject", subject, Types.VARCHAR)
        .param("role", role)
        .param("now", now)
        .update();
  }

  static String everything(ILoggingEvent event) {
    StringBuilder all = new StringBuilder(event.getFormattedMessage());
    if (event.getArgumentArray() != null) {
      for (Object argument : event.getArgumentArray()) {
        all.append(' ').append(argument);
      }
    }
    for (var t = event.getThrowableProxy(); t != null; t = t.getCause()) {
      all.append(' ').append(t.getClassName()).append(' ').append(t.getMessage());
    }
    event.getMDCPropertyMap().values().forEach(v -> all.append(' ').append(v));
    return all.toString();
  }
}
