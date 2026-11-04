// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * A conta de traballo vai á parte da persoal (ADR-0027, revisión do 2026-10-30; especificación,
 * bloque 7, P1), en todos os concellos: unha conta con pertenza activa ao persoal non reserva como
 * veciña (403 {@code booking.staff-account}) e unha conta que xa reservou, en calquera estado, non
 * acepta unha invitación (403 {@code staff.invitation-personal-account}) nin se reactiva (409
 * {@code staff.personal-account}). As dúas lecturas cruzan concellos cunha función da base que só
 * di si ou non; a RLS segue sen deixar ver nada doutro concello.
 */
class WorkAccountIT extends AbstractBookingIT {

  @Autowired RecordingMailSender mails;

  @Test
  void aWorkAccountDoesNotBookAsAResidentInAnyMunicipality() {
    Facility own = facility("traballo-propio", true);
    Facility other = facility("traballo-outro", true);
    UUID ownUse = whole(own, Map.of());
    UUID otherUse = whole(other, Map.of());
    String subject = "sub-traballo-" + UUID.randomUUID();
    UUID membership = member(own.municipality(), subject, "STAFF", "ACTIVE", null);
    String token = person(subject);
    Instant nine = at(tomorrow(), 540);

    for (Api.Response refused :
        List.of(reserve(token, own, ownUse, nine), reserve(token, other, otherUse, nine))) {
      assertThat(refused.status()).as(refused.toString()).isEqualTo(403);
      assertThat(code(refused)).isEqualTo("booking.staff-account");
    }
    assertThat(bookingsOf(subject)).isZero();
    // A web avísao antes: tamén no concello onde non é persoal.
    JsonNode me = me(other.slug(), token);
    assertThat(me.get("workAccount").asBoolean()).isTrue();
    assertThat(me.get("role").isNull()).isTrue();

    // Desactivada, xa non é unha conta de traballo: reserva.
    deactivate(membership);
    assertThat(me(other.slug(), token).get("workAccount").asBoolean()).isFalse();
    Api.Response booked = reserve(token, other, otherUse, nine);
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
  }

  @Test
  void anInvitationIsNotAWorkAccountAndAResidentBooksAsAlways() {
    Facility f = facility("traballo-invitada", true);
    UUID use = whole(f, Map.of());
    // Unha invitada aínda non ten persoa; unha veciña calquera reserva e non é conta de traballo.
    member(f.municipality(), null, "STAFF", "INVITED", null);
    String resident = person();
    assertThat(me(f.slug(), resident).get("workAccount").asBoolean()).isFalse();
    assertThat(reserve(resident, f, use, at(tomorrow(), 600)).status()).isEqualTo(201);
  }

  @Test
  void anAccountWithBookingsInAnyStateDoesNotAcceptAnInvitation() throws Exception {
    // Reservou noutro concello e cancelou: segue sendo unha conta persoal.
    Facility f = facility("persoal-reservas", true);
    UUID use = whole(f, Map.of());
    String subject = "sub-vecina-" + UUID.randomUUID();
    String email = StaffManagementIT.email("vecina");
    String resident = account(subject, email);
    Api.Response booked = reserve(resident, f, use, at(tomorrow(), 660));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    Api.Response cancelled =
        api()
            .post(mine(f.slug()) + "/" + id(booked.json()) + "/cancel", null)
            .ifMatch(0)
            .token(resident)
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);

    String slug = uniqueSlug("persoal-convite");
    UUID municipality = id(createMunicipality(slug));
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(municipality, manager, "MANAGER", "ACTIVE", null);
    Api.Response invited =
        api()
            .post(
                StaffManagementIT.staff(slug),
                Map.of("email", email, "role", "STAFF", "language", "gl"))
            .token(StaffManagementIT.token(manager))
            .send();
    assertThat(invited.status()).as(invited.toString()).isEqualTo(201);
    UUID memberId = id(invited.json());
    String token =
        StaffManagementIT.linkToken(StaffManagementIT.text(mails.sentTo(email).getLast()));

    Api.Response accepted = accept(slug, resident, token);
    assertThat(accepted.status()).as(accepted.toString()).isEqualTo(403);
    assertThat(code(accepted)).isEqualTo("staff.invitation-personal-account");
    assertThat(status(memberId)).isEqualTo("INVITED");
    assertThat(lastFailureCode(municipality, "staff.accept"))
        .isEqualTo("staff.invitation-personal-account");

    // Outra conta co correo invitado e sen reservas, si.
    String work = account("sub-traballo-" + UUID.randomUUID(), email);
    Api.Response ok = accept(slug, work, token);
    assertThat(ok.status()).as(ok.toString()).isEqualTo(200);
    assertThat(status(memberId)).isEqualTo("ACTIVE");
  }

  @Test
  void aMembershipWhoseAccountBookedWhileDeactivatedIsNotReactivated() {
    Facility f = facility("persoal-volta", true);
    UUID use = whole(f, Map.of());
    String manager = "sub-xestora-" + UUID.randomUUID();
    member(f.municipality(), manager, "MANAGER", "ACTIVE", null);
    String subject = "sub-conserxe-" + UUID.randomUUID();
    UUID membership = member(f.municipality(), subject, "STAFF", "ACTIVE", null);
    deactivate(membership);
    Api.Response booked = reserve(person(subject), f, use, at(tomorrow(), 720));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);

    Api.Response reactivated =
        api()
            .post(StaffManagementIT.staff(f.slug()) + "/" + membership + "/reactivate", Map.of())
            .token(StaffManagementIT.token(manager))
            .ifMatch(1)
            .send();
    assertThat(reactivated.status()).as(reactivated.toString()).isEqualTo(409);
    assertThat(code(reactivated)).isEqualTo("staff.personal-account");
    assertThat(status(membership)).isEqualTo("DEACTIVATED");
  }

  // --- axudas ---

  /** Unha conta (sen roles no token) co seu correo verificado. */
  static String account(String subject, String email) {
    return TestTokens.withoutRolesClaim(
        subject, Map.of("email", email, "email_verified", true, "name", "Persoa Nova"));
  }

  JsonNode me(String slug, String token) {
    Api.Response response =
        api().get("/api/v1/municipalities/" + slug + "/me/staff").token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  Api.Response accept(String slug, String session, String token) {
    return api()
        .post(
            "/api/v1/municipalities/" + slug + "/staff-invitations/accept", Map.of("token", token))
        .token(session)
        .send();
  }

  long bookingsOf(String subject) {
    return jdbc.sql("select count(*) from booking where holder_subject = :s")
        .param("s", subject)
        .query(Long.class)
        .single();
  }

  String status(UUID member) {
    return jdbc.sql("select status from staff_member where id = :id")
        .param("id", member)
        .query(String.class)
        .single();
  }

  String lastFailureCode(UUID municipality, String action) {
    return jdbc.sql(
            """
            select details->>'code' from audit_event
             where municipality_id = :m and action = :a and outcome = 'FAILURE'
             order by occurred_at desc limit 1
            """)
        .param("m", municipality)
        .param("a", action)
        .query(String.class)
        .single();
  }

  /** Desactiva a pertenza por JDBC (dono, sen RLS), como faría o caso de uso. */
  void deactivate(UUID member) {
    jdbc.sql(
            """
            update staff_member
               set status = 'DEACTIVATED', deactivated_by = 'sub-semente', deactivated_at = now(),
                   version = version + 1
             where id = :id
            """)
        .param("id", member)
        .update();
  }

  /** Unha pertenza por JDBC (dono, sen RLS); a invitada, sen persoa. */
  UUID member(
      UUID municipality,
      @Nullable String subject,
      String role,
      String status,
      @Nullable String email) {
    UUID id = UUID.randomUUID();
    boolean accepted = !"INVITED".equals(status);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, created_at, updated_at,
               version)
            values (:id, :m, :email, :name, :subject, :role, :status, 'gl', 'sub-semente', :now,
                    :acceptedAt, :now, :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", email == null ? StaffManagementIT.email("persoal") : email)
        .param("name", accepted ? "Persoa de proba" : null, Types.VARCHAR)
        .param("subject", accepted ? subject : null, Types.VARCHAR)
        .param("role", role)
        .param("status", status)
        .param("now", now)
        .param("acceptedAt", accepted ? now : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }
}
