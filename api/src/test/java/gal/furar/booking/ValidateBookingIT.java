// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Validar o QR de punta a punta (ADR-0032, puntos 3, 4, 6 e 9): os catro resultados de ler un
 * código, forzar con e sen motivo, repetir a lectura, desfacer, o código doutro concello, os
 * permisos de Xestión, Recepción e plataforma, o motivo no rexistro de actividade e que unha
 * reserva validada xa non se cancela por internet.
 */
class ValidateBookingIT extends AbstractAttendanceIT {

  @Test
  void insideTheWindowItIsValidatedAtOnceAndReadingAgainChangesNothing() {
    Facility f = facility("validar", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    Api.Response booked = reserve(person(), f, use, at(tomorrow(), OPENS));
    UUID id = id(booked.json());
    String code = booked.json().get("code").asString();
    // Empezou hai dez minutos: dentro da marxe.
    move(id, Instant.now().minusSeconds(600));

    Api.Response read = validate(staff, f.slug(), "  " + code.toLowerCase() + " ");

    assertThat(read.status()).as(read.toString()).isEqualTo(200);
    JsonNode v = read.json();
    assertThat(v.get("outcome").asString()).isEqualTo("VALIDATED");
    assertThat(v.get("booking").get("id").asString()).isEqualTo(id.toString());
    assertThat(v.get("booking").get("attendance").asString()).isEqualTo("USED");
    assertThat(v.get("booking").get("status").asString())
        .as("segue confirmada")
        .isEqualTo("CONFIRMED");
    assertThat(v.get("booking").get("facilityName").asString()).isEqualTo("Pavillón do Alto");
    assertThat(v.get("attendanceAt").isNull()).isFalse();
    assertThat(mark(id)).startsWith("USED test-");
    assertThat(history(id)).containsExactly("CREATED", "VALIDATED");
    assertThat(audit(f.municipality(), "booking.validate")).containsExactly("SUCCESS false");

    Api.Response again = validate(staff, f.slug(), code);
    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json().get("outcome").asString()).isEqualTo("ALREADY_VALIDATED");
    assertThat(again.json().get("attendanceAt").asString())
        .as("cando se validou")
        .isEqualTo(v.get("attendanceAt").asString());
    // Nin forzando cambia nada.
    assertThat(validate(staff, f.slug(), code, true, "Outra vez").json().get("outcome").asString())
        .isEqualTo("ALREADY_VALIDATED");
    assertThat(history(id)).as("repetir non escribe").containsExactly("CREATED", "VALIDATED");
    assertThat(audit(f.municipality(), "booking.validate"))
        .as("as lecturas que non validan nada non se rexistran")
        .containsExactly("SUCCESS false");
  }

  @Test
  void outsideTheWindowItNeedsForceAndAReasonThatStaysInTheActivityLog() {
    Facility f = facility("validar-fora", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    Api.Response booked = reserve(person(), f, use, at(tomorrow(), OPENS));
    UUID id = id(booked.json());
    String code = booked.json().get("code").asString();

    Api.Response read = validate(staff, f.slug(), code);
    assertThat(read.status()).isEqualTo(200);
    assertThat(read.json().get("outcome").asString()).isEqualTo("OUT_OF_WINDOW");
    assertThat(read.json().get("booking").get("attendance").asString()).isEqualTo("NONE");
    assertThat(mark(id)).isEqualTo("NONE -");

    for (String blank : new String[] {null, "", "   "}) {
      Api.Response refused = validate(staff, f.slug(), code, true, blank);
      assertThat(refused.status()).as(refused.toString()).isEqualTo(422);
      assertThat(code(refused)).isEqualTo("booking.validation-reason-required");
    }
    assertThat(mark(id)).isEqualTo("NONE -");

    Api.Response forced = validate(staff, f.slug(), code, true, "  Chegou antes: adestramento  ");
    assertThat(forced.status()).as(forced.toString()).isEqualTo(200);
    assertThat(forced.json().get("outcome").asString()).isEqualTo("VALIDATED");
    assertThat(mark(id)).startsWith("USED test-");
    assertThat(audit(f.municipality(), "booking.validate"))
        .containsExactly(
            "FAILURE booking.validation-reason-required",
            "FAILURE booking.validation-reason-required",
            "FAILURE booking.validation-reason-required",
            "SUCCESS true");
    assertThat(
            jdbc.sql(
                    """
                    select details ->> 'reason' from audit_event
                    where municipality_id = :m and action = 'booking.validate'
                      and outcome = 'SUCCESS'
                    """)
                .param("m", f.municipality())
                .query(String.class)
                .single())
        .as("o motivo queda tal como se escribiu, con quen e cando")
        .isEqualTo("Chegou antes: adestramento");
  }

  @Test
  void aCancelledBookingAndAnUnknownCodeAreNotValidated() {
    Facility f = facility("validar-non", true);
    Facility other = facility("validar-outro", true);
    UUID use = whole(f, Map.of());
    UUID otherUse = whole(other, Map.of());
    String staff = TestTokens.staff(f.slug());
    String ana = person();
    Api.Response booked = reserve(ana, f, use, at(tomorrow(), OPENS));
    UUID id = id(booked.json());
    String code = booked.json().get("code").asString();
    assertThat(
            api()
                .post(mine(f.slug()) + "/" + id + "/cancel", null)
                .ifMatch(0)
                .token(ana)
                .send()
                .status())
        .isEqualTo(200);

    Api.Response read = validate(staff, f.slug(), code);
    assertThat(read.status()).isEqualTo(200);
    assertThat(read.json().get("outcome").asString()).isEqualTo("CANCELLED");
    assertThat(read.json().get("booking").get("status").asString())
        .isEqualTo("CANCELLED_BY_PERSON");
    assertThat(read.json().get("booking").get("cancelledAt").isNull()).isFalse();
    Api.Response forced = validate(staff, f.slug(), code, true, "Aínda así");
    assertThat(forced.status()).isEqualTo(409);
    assertThat(code(forced)).isEqualTo("booking.not-validatable");

    // Un código que non existe, un texto que non é un código e o código doutro concello.
    String theirs =
        reserve(person(), other, otherUse, at(tomorrow(), OPENS)).json().get("code").asString();
    for (String unknown : new String[] {"FUR-ZZZZ", "ola", theirs}) {
      if (unknown.equals(code)) {
        continue;
      }
      Api.Response missing = validate(staff, f.slug(), unknown);
      assertThat(missing.status()).as(unknown).isEqualTo(404);
      assertThat(code(missing)).isEqualTo("booking.not-found");
    }
    assertThat(audit(f.municipality(), "booking.validate"))
        .as("só o «Validar igualmente» rexeitado")
        .containsExactly("FAILURE booking.not-validatable");
  }

  @Test
  void managementAndFrontDeskValidateButNotThePlatformNorAPerson() {
    Facility f = facility("validar-permiso", true);
    Facility other = facility("validar-alleo", true);
    UUID use = whole(f, Map.of());
    String code = reserve(person(), f, use, at(tomorrow(), OPENS)).json().get("code").asString();

    assertThat(validate(null, f.slug(), code).status()).isEqualTo(401);
    assertThat(validate(TestTokens.platformAdmin(), f.slug(), code).status()).isEqualTo(403);
    assertThat(validate(person(), f.slug(), code).status()).isEqualTo(403);
    assertThat(validate(TestTokens.manager(other.slug()), f.slug(), code).status()).isEqualTo(403);
    assertThat(validate(TestTokens.manager(f.slug()), f.slug(), code).status()).isEqualTo(200);
    assertThat(validate(TestTokens.staff(f.slug()), f.slug(), code).status()).isEqualTo(200);
  }

  @Test
  void undoingTheValidationLeavesNoMarkAndAsksForTheCurrentVersion() {
    Facility f = facility("validar-desfacer", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    Api.Response booked = reserve(person(), f, use, at(tomorrow(), OPENS));
    UUID id = id(booked.json());
    move(id, Instant.now().minusSeconds(600));
    assertThat(validate(staff, f.slug(), booked.json().get("code").asString()).status())
        .isEqualTo(200);

    Api.Response stale =
        api().delete(bookings(f.slug()) + "/" + id + "/attendance").ifMatch(0).token(staff).send();
    assertThat(stale.status()).isEqualTo(412);

    Api.Response undone = undo(staff, f.slug(), id);
    assertThat(undone.status()).as(undone.toString()).isEqualTo(200);
    assertThat(undone.json().get("attendance").asString()).isEqualTo("NONE");
    assertThat(mark(id)).as("queda quen a desfixo").startsWith("NONE test-");
    assertThat(history(id)).containsExactly("CREATED", "VALIDATED", "ATTENDANCE_UNDONE");

    Api.Response nothing = undo(staff, f.slug(), id);
    assertThat(nothing.status()).isEqualTo(409);
    assertThat(code(nothing)).isEqualTo("booking.attendance-not-marked");
    assertThat(audit(f.municipality(), "booking.attendance-undo"))
        .containsExactly(
            "FAILURE common.stale-version", "SUCCESS ", "FAILURE booking.attendance-not-marked");
  }

  @Test
  void aValidatedBookingIsNotCancelledNorChangedOnlineButTheStaffCanAndThatClearsTheMark() {
    Facility f = facility("validar-marca", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    String ana = person();
    // Dentro de media hora: en prazo para cancelar e xa dentro da marxe para validar.
    Api.Response booked = reserve(ana, f, use, at(tomorrow(), OPENS));
    UUID id = id(booked.json());
    move(id, Instant.now().plusSeconds(1800));
    assertThat(
            validate(staff, f.slug(), booked.json().get("code").asString())
                .json()
                .get("outcome")
                .asString())
        .isEqualTo("VALIDATED");

    JsonNode seen = api().get(mine(f.slug()) + "/" + id).token(ana).send().json();
    assertThat(seen.get("canCancel").asBoolean()).isFalse();
    assertThat(seen.get("noShow").asBoolean()).isFalse();
    assertThat(seen.has("attendance")).as("a persoa non ve «Validada»").isFalse();
    long version = api().get(mine(f.slug()) + "/" + id).token(ana).send().etagVersion();
    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + id + "/cancel", null).ifMatch(version).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(409);
    assertThat(code(cancelled)).isEqualTo("booking.attendance-marked");
    Api.Response changed =
        api()
            .put(
                mine(f.slug()) + "/" + id + "/slot",
                Map.of("useId", use, "start", at(tomorrow(), OPENS + 60).toString(), "slots", 1))
            .ifMatch(version)
            .token(ana)
            .send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(409);
    assertThat(code(changed)).isEqualTo("booking.attendance-marked");

    // A xestión cancélaa: a marca bórrase na mesma operación.
    String manager = TestTokens.manager(f.slug());
    Api.Response byMunicipality =
        api()
            .post(
                bookings(f.slug()) + "/" + id + "/cancel", Map.of("channels", java.util.List.of()))
            .ifMatch(version)
            .token(manager)
            .send();
    assertThat(byMunicipality.status()).as(byMunicipality.toString()).isEqualTo(200);
    assertThat(byMunicipality.json().get("status").asString())
        .isEqualTo("CANCELLED_BY_MUNICIPALITY");
    assertThat(byMunicipality.json().get("attendance").asString()).isEqualTo("NONE");
    assertThat(mark(id)).startsWith("NONE test-");
    assertThat(history(id)).contains("VALIDATED", "ATTENDANCE_UNDONE", "CANCELLED_BY_MUNICIPALITY");
  }
}
