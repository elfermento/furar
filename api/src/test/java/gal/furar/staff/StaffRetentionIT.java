// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.notification.StaffInvitationMailer;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.out.StaffInvitationRepository;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.application.service.StaffRetentionService;
import gal.furar.staff.application.service.StaffRetentionService.Result;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * D4-10 (ADR-0027, punto 14; plan do dominio 4, §1.2.14 e P8): a conservación do persoal, cun
 * reloxo fixo. As desactivadas hai máis de {@code FURAR_AUDIT_RETENTION} (P3Y, cando xa non queda
 * ningún evento seu; revisión do 2026-10-29) quedan sen nome e cun correo anonimizado; as máis
 * recentes (tamén as bloqueadas, desactivada hai máis dun ano) e as activas, intactas. As
 * invitacións non aceptadas caducadas hai máis de 30 días bórranse; as outras e as aceptadas
 * quedan. A invitada que perde así a última invitación (nunca aceptou) pasa a desactivada pola
 * tarefa e anonimízase na mesma pasada (RGPD art. 5.1.c); a que aínda ten unha, ou a invitada hai
 * pouco, queda. Repetir non cambia nada, cada concello só toca as súas filas, o rexistro di «Persoa
 * anonimizada» (persoal sen nome nin correo) e o rexistro técnico non leva correos nin nomes.
 *
 * <p>A base é compartida: o reloxo fixo é o instante da proba (non un futuro), así a tarefa non
 * anonimiza as desactivadas que sementan outras probas agora.
 */
@ExtendWith(OutputCaptureExtension.class)
class StaffRetentionIT extends AbstractIT {

  static final Instant SIGN_IN = Instant.parse("2026-11-05T09:00:00Z");

  @Autowired MunicipalityDirectory municipalities;
  @Autowired StaffMemberRepository members;
  @Autowired StaffInvitationRepository invitations;
  @Autowired StaffInvitationMailer mailer;
  @Autowired AuditLog audit;
  @Autowired PlatformTransactionManager transactions;
  @Autowired StaffRetentionService scheduled;

  Instant now;
  StaffRetentionService retention;

  @BeforeEach
  void fixedClock() {
    now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    retention =
        new StaffRetentionService(
            municipalities,
            members,
            invitations,
            mailer,
            audit,
            transactions,
            AuditRetention.DEFAULT,
            Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void anonymisesOldDeactivatedAndPurgesStaleInvitationsOnce(CapturedOutput output) {
    UUID m = id(createMunicipality(uniqueSlug("conservacion")));
    UUID oldDeactivated = member(m, "DEACTIVATED", "sub-vella-" + UUID.randomUUID(), days(1100));
    // Desactivada hai 400 días: bloqueada, pero aínda quedan eventos seus.
    UUID recentDeactivated =
        member(m, "DEACTIVATED", "sub-recente-" + UUID.randomUUID(), days(400));
    UUID active = member(m, "ACTIVE", "sub-activa-" + UUID.randomUUID(), null);
    Map<String, Object> before = row(oldDeactivated);

    UUID invited = member(m, "INVITED", null, null);
    UUID invitedLater = member(m, "INVITED", null, null);
    UUID invitedRecently = member(m, "INVITED", null, null);
    UUID resent = member(m, "INVITED", null, null);
    // Sen ningunha invitación pero invitada hai pouco (garda): queda.
    UUID invitedNow = member(m, "INVITED", null, null, now);
    // Caducada hai 31 días (aberta) e outra anterior substituída: bórranse.
    UUID superseded = invitation(m, invited, created(days(45)), Kind.SUPERSEDED);
    UUID staleOpen = invitation(m, invited, created(days(31)), Kind.OPEN);
    // Caducada hai 30 días xustos e hai 29: quedan.
    UUID thirty = invitation(m, invitedLater, created(days(30)), Kind.OPEN);
    UUID twentyNine = invitation(m, invitedRecently, created(days(29)), Kind.OPEN);
    // Unha vella caducada (bórrase) e un reenvío vixente (queda): segue invitada.
    UUID staleResent = invitation(m, resent, created(days(40)), Kind.SUPERSEDED);
    UUID openResent = invitation(m, resent, created(now.plus(Duration.ofDays(6))), Kind.OPEN);
    // Aceptada hai máis dun ano: queda coa pertenza.
    UUID accepted = invitation(m, active, created(days(400)), Kind.USED);

    Result first = retention.apply();

    assertThat(first.anonymised()).isGreaterThanOrEqualTo(1);
    assertThat(first.invitationsDeleted()).isGreaterThanOrEqualTo(3);
    assertThat(first.unacceptedDeactivated()).isGreaterThanOrEqualTo(1);
    Map<String, Object> after = row(oldDeactivated);
    assertThat(after.get("email")).isEqualTo("anon-" + oldDeactivated + "@anonimizada.invalid");
    assertThat(after.get("display_name")).isNull();
    assertThat(after.get("version")).isEqualTo(1L);
    assertThat(updatedAt(oldDeactivated)).isEqualTo(now);
    // Quedan a fila, a persoa, o rol e as datas.
    for (String kept :
        List.of("subject", "role", "status", "deactivated_at", "deactivated_by", "accepted_at")) {
      assertThat(after.get(kept)).as(kept).isEqualTo(before.get(kept));
    }
    assertThat(row(recentDeactivated).get("email")).isEqualTo(email(recentDeactivated));
    assertThat(row(recentDeactivated).get("display_name")).isEqualTo("Persoa de proba");
    assertThat(row(active).get("email")).isEqualTo(email(active));
    assertThat(row(active).get("version")).isEqualTo(0L);
    assertThat(invitationExists(superseded)).isFalse();
    assertThat(invitationExists(staleOpen)).isFalse();
    assertThat(invitationExists(thirty)).isTrue();
    assertThat(invitationExists(twentyNine)).isTrue();
    assertThat(invitationExists(accepted)).isTrue();
    assertThat(invitationExists(staleResent)).isFalse();
    assertThat(invitationExists(openResent)).isTrue();

    // A invitada que perdeu a última invitación: desactivada pola tarefa e anonimizada.
    Map<String, Object> closed = row(invited);
    assertThat(closed.get("status")).isEqualTo("DEACTIVATED");
    assertThat(closed.get("deactivated_by")).isEqualTo(StaffRetentionService.SUBJECT);
    assertThat(deactivatedAt(invited)).isEqualTo(now);
    assertThat(closed.get("email")).isEqualTo("anon-" + invited + "@anonimizada.invalid");
    assertThat(closed.get("display_name")).isNull();
    assertThat(closed.get("subject")).isNull();
    assertThat(closed.get("version")).isEqualTo(1L);
    assertThat(updatedAt(invited)).isEqualTo(now);
    // As que aínda teñen unha invitación, e a invitada agora, quedan como estaban.
    for (UUID kept : List.of(invitedLater, invitedRecently, resent, invitedNow)) {
      assertThat(row(kept).get("status")).as("status").isEqualTo("INVITED");
      assertThat(row(kept).get("email")).isEqualTo(email(kept));
      assertThat(row(kept).get("version")).isEqualTo(0L);
    }

    // Reaplicar non cambia nada: nin o correo nin a versión.
    Map<String, Object> once = row(oldDeactivated);
    Map<String, Object> closedOnce = row(invited);
    assertThat(retention.apply()).isEqualTo(new Result(0, 0, 0, 0));
    assertThat(row(oldDeactivated)).isEqualTo(once);
    assertThat(row(invited)).isEqualTo(closedOnce);
    assertThat(invitationExists(thirty)).isTrue();

    // O rexistro técnico: concello e contas, sen correos nin nomes.
    assertThat(output).contains("staff retention municipality=" + m);
    assertThat(output)
        .doesNotContain(email(oldDeactivated))
        .doesNotContain(email(invited))
        .doesNotContain("Persoa de proba")
        .doesNotContain(String.valueOf(before.get("subject")));
  }

  /**
   * Reactivar reinicia o contador (ADR-0027, revisión do 2026-10-30): a conservación conta desde a
   * última desactivación. Unha persoa desactivada hai máis de 3 anos que se reactiva antes da
   * pasada xa non se anonimiza; se se volve desactivar, conta desde esa nova data.
   */
  @Test
  void reactivatingRestartsTheRetentionFromTheNextDeactivation() {
    String slug = uniqueSlug("conservacion-volta");
    UUID m = id(createMunicipality(slug));
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(m, "ACTIVE", manager, null);
    UUID back = member(m, "DEACTIVATED", "sub-volta-" + UUID.randomUUID(), days(1100));
    String path = "/api/v1/municipalities/" + slug + "/staff/" + back;

    Api.Response reactivated =
        api()
            .post(path + "/reactivate", Map.of())
            .token(StaffManagementIT.token(manager))
            .ifMatch(0)
            .send();
    assertThat(reactivated.status()).as(reactivated.toString()).isEqualTo(200);
    assertThat(row(back).get("deactivated_at")).isNull();
    retention.apply();
    assertThat(row(back).get("email")).isEqualTo(email(back));
    assertThat(row(back).get("status")).isEqualTo("ACTIVE");

    Api.Response again =
        api()
            .post(path + "/deactivate", Map.of())
            .token(StaffManagementIT.token(manager))
            .ifMatch(1)
            .send();
    assertThat(again.status()).as(again.toString()).isEqualTo(200);
    assertThat(deactivatedAt(back)).isAfter(days(1));
    retention.apply();
    assertThat(row(back).get("email")).isEqualTo(email(back));
    assertThat(row(back).get("display_name")).isEqualTo("Persoa de proba");
  }

  @Test
  void eachMunicipalityTouchesOnlyItsOwnRows() {
    UUID a = id(createMunicipality(uniqueSlug("conservacion-a")));
    UUID b = id(createMunicipality(uniqueSlug("conservacion-b")));
    UUID deactivatedA = member(a, "DEACTIVATED", "sub-a-" + UUID.randomUUID(), days(1100));
    UUID deactivatedB = member(b, "DEACTIVATED", "sub-b-" + UUID.randomUUID(), days(1100));
    UUID invitedA = member(a, "INVITED", null, null);
    UUID invitedB = member(b, "INVITED", null, null);
    UUID staleA = invitation(a, invitedA, created(days(40)), Kind.OPEN);
    UUID staleB = invitation(b, invitedB, created(days(40)), Kind.OPEN);
    Instant deactivatedCutoff = AuditRetention.DEFAULT.keptSince(now);
    Instant invitationCutoff = now.minus(Duration.ofDays(30));
    Instant invitedCutoff = invitationCutoff.minus(Duration.ofDays(7));

    Result inA =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                new TransactionTemplate(transactions)
                    .execute(
                        s ->
                            new Result(
                                members.anonymiseDeactivatedBefore(deactivatedCutoff, now).size(),
                                invitations.deleteUnusedExpiredBefore(invitationCutoff).size(),
                                members
                                    .deactivateUnacceptedWithoutInvitation(
                                        invitedCutoff, StaffRetentionService.SUBJECT, now)
                                    .size(),
                                0)));

    assertThat(inA).isEqualTo(new Result(1, 1, 1, 0));
    assertThat(row(invitedA).get("status")).isEqualTo("DEACTIVATED");
    assertThat(row(deactivatedA).get("email"))
        .isEqualTo("anon-" + deactivatedA + "@anonimizada.invalid");
    assertThat(invitationExists(staleA)).isFalse();
    assertThat(row(deactivatedB).get("email")).isEqualTo(email(deactivatedB));
    assertThat(row(deactivatedB).get("display_name")).isEqualTo("Persoa de proba");
    assertThat(invitationExists(staleB)).isTrue();
    assertThat(row(invitedB).get("status")).isEqualTo("INVITED");
    assertThat(row(invitedB).get("email")).isEqualTo(email(invitedB));

    // A tarefa percorre todos os concellos: tamén B.
    retention.apply();
    assertThat(row(deactivatedB).get("email"))
        .isEqualTo("anon-" + deactivatedB + "@anonimizada.invalid");
    assertThat(invitationExists(staleB)).isFalse();
    assertThat(row(invitedB).get("status")).isEqualTo("DEACTIVATED");
    assertThat(row(invitedB).get("email")).isEqualTo("anon-" + invitedB + "@anonimizada.invalid");
  }

  @Test
  void theActivityLogShowsTheBlockedThenAnonymisedPersonAsStaffWithoutNameOrEmail() {
    String slug = uniqueSlug("conservacion-rexistro");
    UUID m = id(createMunicipality(slug));
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(m, "ACTIVE", manager, null);
    String gone = "sub-desactivada-" + UUID.randomUUID();
    UUID goneId = member(m, "DEACTIVATED", gone, days(1100));
    // Unha acción da persoa (aínda no rexistro) e outra do xestor sobre ela.
    Instant at = now.minus(Duration.ofDays(10));
    event(at, gone, "{STAFF}", "staff.invite", m, slug, "{}");
    event(at.plusSeconds(1), manager, "{MANAGER}", "staff.deactivate", m, slug, memberId(goneId));
    String query = "from=" + at.minusSeconds(1) + "&to=" + at.plusSeconds(2);

    // Desactivada hai máis dun ano: bloqueada, sen nome nin correo («Persoa desactivada»).
    JsonNode before = page(slug, level2(manager), query);
    assertThat(actorOf(before, "staff.invite").get("blocked").asBoolean()).isTrue();
    assertThat(absent(actorOf(before, "staff.invite").get("name"))).isTrue();
    assertThat(absent(actorOf(before, "staff.invite").get("email"))).isTrue();
    assertThat(absent(targetOf(before, "staff.deactivate").get("memberEmail"))).isTrue();
    assertThat(before.toString()).doesNotContain(email(goneId));

    retention.apply();

    JsonNode after = page(slug, level2(manager), query);
    JsonNode actor = actorOf(after, "staff.invite");
    assertThat(actor.get("kind").asString()).isEqualTo("MEMBER");
    assertThat(actor.get("blocked").asBoolean()).as("anonymised, no longer blocked").isFalse();
    assertThat(absent(actor.get("name"))).isTrue();
    assertThat(absent(actor.get("email"))).isTrue();
    assertThat(actor.get("roles").get(0).asString()).isEqualTo("STAFF");
    JsonNode target = targetOf(after, "staff.deactivate");
    assertThat(target.get("kind").asString()).isEqualTo("STAFF_MEMBER");
    assertThat(target.get("id").asString()).isEqualTo(goneId.toString());
    assertThat(absent(target.get("memberEmail"))).isTrue();
    assertThat(after.toString()).doesNotContain("anonimizada.invalid");
  }

  @Test
  void theStaffListShowsAnonymisedMembersWithoutEmail() {
    String slug = uniqueSlug("conservacion-lista");
    UUID m = id(createMunicipality(slug));
    String manager = "sub-xestora-" + UUID.randomUUID();
    UUID managerId = member(m, "ACTIVE", manager, null);
    UUID gone = member(m, "DEACTIVATED", "sub-desactivada-" + UUID.randomUUID(), days(1100));
    UUID blocked = member(m, "DEACTIVATED", "sub-bloqueada-" + UUID.randomUUID(), days(400));
    UUID recent = member(m, "DEACTIVATED", "sub-recente-" + UUID.randomUUID(), days(300));
    UUID neverAccepted = member(m, "INVITED", null, null);
    invitation(m, neverAccepted, created(days(40)), Kind.OPEN);

    retention.apply();

    Api.Response response =
        api().get("/api/v1/municipalities/" + slug + "/staff").token(level2(manager)).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode list = response.json();
    for (UUID anonymised : List.of(gone, neverAccepted)) {
      JsonNode item = memberOf(list, anonymised);
      assertThat(item.get("anonymized").asBoolean()).isTrue();
      assertThat(item.get("blocked").asBoolean()).isFalse();
      assertThat(absent(item.get("email"))).isTrue();
      assertThat(absent(item.get("name"))).isTrue();
      assertThat(item.get("status").asString()).isEqualTo("DEACTIVATED");
    }
    // Desactivada hai máis dun ano: bloqueada («Persoa desactivada»), sen correo nin nome.
    JsonNode blockedItem = memberOf(list, blocked);
    assertThat(blockedItem.get("blocked").asBoolean()).isTrue();
    assertThat(blockedItem.get("anonymized").asBoolean()).isFalse();
    assertThat(absent(blockedItem.get("email"))).isTrue();
    assertThat(absent(blockedItem.get("name"))).isTrue();
    assertThat(list.toString()).doesNotContain(email(blocked));
    // Desactivada hai menos dun ano: á vista.
    JsonNode recentItem = memberOf(list, recent);
    assertThat(recentItem.get("blocked").asBoolean()).isFalse();
    assertThat(recentItem.get("email").asString()).isEqualTo(email(recent));
    JsonNode current = memberOf(list, managerId);
    assertThat(current.get("anonymized").asBoolean()).isFalse();
    assertThat(current.get("blocked").asBoolean()).isFalse();
    assertThat(current.get("email").asString()).isEqualTo(email(managerId));
    assertThat(list.toString()).doesNotContain("anonimizada.invalid");
  }

  /**
   * A4: cada pertenza tocada deixa rastro, coa tarefa como quen actúa e sen datos persoais: {@code
   * staff.deactivate} (razón {@code retention}) e {@code staff.anonymize} para a invitada que nunca
   * aceptou, {@code staff.anonymize} para a desactivada vella. Na mesma pasada esquécense os envíos
   * (A2): o da invitación borrada e o da aceptada da pertenza anonimizada; o dunha invitación
   * vixente queda. A pantalla do rexistro mostra a tarefa como «SYSTEM».
   */
  @Test
  void leavesATraceAndForgetsTheDeliveries() {
    String slug = uniqueSlug("conservacion-rastro");
    UUID m = id(createMunicipality(slug));
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(m, "ACTIVE", manager, null);
    UUID oldDeactivated = member(m, "DEACTIVATED", "sub-vella-" + UUID.randomUUID(), days(1100));
    UUID acceptedOfOld = invitation(m, oldDeactivated, created(days(1200)), Kind.USED);
    UUID invited = member(m, "INVITED", null, null);
    UUID stale = invitation(m, invited, created(days(31)), Kind.OPEN);
    UUID pending = member(m, "INVITED", null, null, now.minus(Duration.ofDays(1)));
    UUID open = invitation(m, pending, now.minus(Duration.ofDays(1)), Kind.OPEN);
    for (UUID i : List.of(acceptedOfOld, stale, open)) {
      delivery(m, i);
    }
    Instant before = Instant.now().minusSeconds(1);

    Result result = retention.apply();

    assertThat(result.deliveriesForgotten()).isGreaterThanOrEqualTo(2);
    assertThat(deliveryExists(m, acceptedOfOld)).isFalse();
    assertThat(deliveryExists(m, stale)).isFalse();
    assertThat(deliveryExists(m, open)).isTrue();

    List<Map<String, Object>> events = retentionEvents(m, before);
    assertThat(events)
        .extracting(e -> e.get("action") + " " + e.get("member"))
        .containsExactlyInAnyOrder(
            "staff.deactivate " + invited,
            "staff.anonymize " + invited,
            "staff.anonymize " + oldDeactivated);
    for (Map<String, Object> e : events) {
      assertThat(e.get("actor_subject")).isEqualTo(StaffRetentionService.SUBJECT);
      assertThat(e.get("outcome")).isEqualTo("SUCCESS");
      assertThat(e.get("roles")).isEqualTo("{}");
      assertThat(e.get("municipality_slug")).isEqualTo(slug);
      String details = String.valueOf(e.get("details"));
      assertThat(details).contains("\"reason\": \"retention\"").contains("\"role\": \"STAFF\"");
      assertThat(details).doesNotContain("@").doesNotContain("Persoa de proba");
    }

    // Repetir non deixa eventos novos.
    retention.apply();
    assertThat(retentionEvents(m, before)).hasSize(3);

    // O xestor ve a tarefa no rexistro, sen nome nin roles.
    JsonNode page = page(slug, level2(manager), "from=" + before.minusSeconds(1) + "&area=staff");
    JsonNode actor = actorOf(page, "staff.anonymize");
    assertThat(actor.get("kind").asString()).isEqualTo("SYSTEM");
    assertThat(absent(actor.get("name"))).isTrue();
    assertThat(actor.get("roles")).isEmpty();
    assertThat(targetOf(page, "staff.deactivate").get("id").asString())
        .isEqualTo(invited.toString());
  }

  @Test
  void theScheduledBeanUsesTheAuditRetention() {
    // O bean da aplicación (P3Y por defecto) non anonimiza unha desactivada hai 400 días.
    UUID m = id(createMunicipality(uniqueSlug("conservacion-bean")));
    UUID recent = member(m, "DEACTIVATED", "sub-bean-" + UUID.randomUUID(), days(400));
    scheduled.apply();
    assertThat(row(recent).get("email")).isEqualTo(email(recent));
  }

  // --- axudas ---

  enum Kind {
    OPEN,
    SUPERSEDED,
    USED
  }

  Instant days(int ago) {
    return now.minus(Duration.ofDays(ago));
  }

  /** Cando se creou unha invitación que caducou hai {@code expiredAt}: 7 días antes. */
  static Instant created(Instant expiredAt) {
    return expiredAt.minus(Duration.ofHours(168));
  }

  /**
   * Unha pertenza por JDBC (dono, sen RLS) de hai 1500 días; a desactivada, en {@code
   * deactivatedAt}.
   */
  UUID member(
      UUID municipality, String status, @Nullable String subject, @Nullable Instant deactivatedAt) {
    return member(municipality, status, subject, deactivatedAt, now.minus(Duration.ofDays(1500)));
  }

  /** Unha pertenza por JDBC (dono, sen RLS) creada e invitada en {@code invitedAt}. */
  UUID member(
      UUID municipality,
      String status,
      @Nullable String subject,
      @Nullable Instant deactivatedAt,
      Instant invitedAt) {
    UUID id = UUID.randomUUID();
    boolean invited = "INVITED".equals(status);
    boolean deactivated = "DEACTIVATED".equals(status);
    OffsetDateTime since = utc(invitedAt);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by, deactivated_at,
               created_at, updated_at, version)
            values (:id, :m, :email, :name, :subject, :role, :status, 'gl', 'sub-xestora', :since,
                    :acceptedAt, :deactivatedBy, :deactivatedAt, :since, :since, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", email(id))
        .param("name", invited ? null : "Persoa de proba", Types.VARCHAR)
        .param("subject", invited ? null : subject, Types.VARCHAR)
        .param("role", subject != null && subject.startsWith("sub-xestora") ? "MANAGER" : "STAFF")
        .param("status", status)
        .param("since", since)
        .param("acceptedAt", invited ? null : since, Types.TIMESTAMP_WITH_TIMEZONE)
        .param("deactivatedBy", deactivated ? "sub-xestora" : null, Types.VARCHAR)
        .param(
            "deactivatedAt", deactivated ? utc(deactivatedAt) : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }

  UUID invitation(UUID municipality, UUID member, Instant createdAt, Kind kind) {
    UUID id = UUID.randomUUID();
    byte[] hash = new byte[32];
    new java.security.SecureRandom().nextBytes(hash);
    OffsetDateTime created = utc(createdAt);
    OffsetDateTime closed = utc(createdAt.plus(Duration.ofHours(1)));
    jdbc.sql(
            """
            insert into staff_invitation
              (id, municipality_id, member_id, token_hash, requested_by, created_at, expires_at,
               used_at, superseded_at, delivery)
            values (:id, :m, :member, :hash, 'sub-xestora', cast(:created as timestamptz),
                    cast(:created as timestamptz) + interval '168 hours', :usedAt, :supersededAt,
                    'SENT')
            """)
        .param("id", id)
        .param("m", municipality)
        .param("member", member)
        .param("hash", hash)
        .param("created", created)
        .param("usedAt", kind == Kind.USED ? closed : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "supersededAt", kind == Kind.SUPERSEDED ? closed : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }

  void event(
      Instant at,
      String subject,
      String roles,
      String action,
      UUID municipality,
      String slug,
      String details) {
    jdbc.sql(
            """
            insert into audit_event (id, occurred_at, actor_subject, actor_roles, action, outcome,
              municipality_id, municipality_slug, request_id, details)
            values (:id, :at, :subject, cast(:roles as text[]), :action, 'SUCCESS', :m, :slug,
                    'req-test', cast(:details as jsonb))
            """)
        .param("id", UUID.randomUUID())
        .param("at", utc(at))
        .param("subject", subject)
        .param("roles", roles)
        .param("action", action)
        .param("m", municipality)
        .param("slug", slug)
        .param("details", details)
        .update();
  }

  /** O envío dunha invitación, como o garda {@code notification} (correo de destino incluído). */
  void delivery(UUID municipality, UUID invitation) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, invitation_id, kind, event_id, recipient, language, status,
               attempts, sent_at, created_at, updated_at)
            values (:id, :m, :i, 'STAFF_INVITATION', 'once', 'persoal@example.org', 'gl', 'SENT',
                    1, now(), now(), now())
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("i", invitation)
        .update();
  }

  boolean deliveryExists(UUID municipality, UUID invitation) {
    return jdbc.sql(
                "select count(*) from notification_delivery where municipality_id = :m"
                    + " and invitation_id = :i")
            .param("m", municipality)
            .param("i", invitation)
            .query(Integer.class)
            .single()
        == 1;
  }

  /** Os eventos da conservación nese concello desde {@code since}. */
  List<Map<String, Object>> retentionEvents(UUID municipality, Instant since) {
    return jdbc.sql(
            """
            select action, actor_subject, outcome, cast(actor_roles as text) as roles,
                   municipality_slug, details ->> 'memberId' as member,
                   cast(details as text) as details
              from audit_event
             where municipality_id = :m and occurred_at >= :since and actor_subject = :subject
            """)
        .param("m", municipality)
        .param("since", utc(since))
        .param("subject", StaffRetentionService.SUBJECT)
        .query()
        .listOfRows();
  }

  Map<String, Object> row(UUID member) {
    return jdbc.sql(
            """
            select email, display_name, subject, role, status, deactivated_at, deactivated_by,
                   accepted_at, updated_at, version
              from staff_member where id = :id
            """)
        .param("id", member)
        .query()
        .singleRow();
  }

  Instant updatedAt(UUID member) {
    return jdbc.sql("select updated_at from staff_member where id = :id")
        .param("id", member)
        .query(OffsetDateTime.class)
        .single()
        .toInstant();
  }

  Instant deactivatedAt(UUID member) {
    return jdbc.sql("select deactivated_at from staff_member where id = :id")
        .param("id", member)
        .query(OffsetDateTime.class)
        .single()
        .toInstant();
  }

  boolean invitationExists(UUID id) {
    return jdbc.sql("select count(*) from staff_invitation where id = :id")
            .param("id", id)
            .query(Integer.class)
            .single()
        == 1;
  }

  JsonNode page(String slug, String token, String query) {
    Api.Response response =
        api().get("/api/v1/municipalities/" + slug + "/audit-events?" + query).token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  static JsonNode item(JsonNode page, String action) {
    for (JsonNode item : page.get("items")) {
      if (item.get("action").asString().equals(action)) {
        return item;
      }
    }
    throw new AssertionError("No " + action + " in " + page);
  }

  static JsonNode memberOf(JsonNode list, UUID id) {
    for (JsonNode item : list) {
      if (item.get("id").asString().equals(id.toString())) {
        return item;
      }
    }
    throw new AssertionError("No " + id + " in " + list);
  }

  static JsonNode actorOf(JsonNode page, String action) {
    return item(page, action).get("actor");
  }

  static JsonNode targetOf(JsonNode page, String action) {
    return item(page, action).get("target");
  }

  static boolean absent(@Nullable JsonNode node) {
    return node == null || node.isNull();
  }

  static String memberId(UUID id) {
    return "{\"memberId\":\"" + id + "\"}";
  }

  static String email(UUID member) {
    return "persoal-" + member + "@example.org";
  }

  static String level2(String subject) {
    return TestTokens.withoutRolesClaim(subject, Map.of("auth_time", SIGN_IN.getEpochSecond()));
  }

  static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
