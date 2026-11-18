// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ADR-0029: o código por correo ten seis cifras, vale 5 minutos e unha vez, admite 5 intentos e
 * só se pode pedir outro cada 60 s, ata 5 por acceso.
 */
class EmailCodeTest {

  private static final long NOW = 1_800_000_000L;
  private static final SecureRandom RANDOM = new SecureRandom();

  private static EmailCode issued() {
    return EmailCode.issue(NOW, Optional.empty(), RANDOM);
  }

  @Test
  void sixRandomDigits() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      String code = issued().code();
      assertTrue(code.matches("[0-9]{6}"), code);
      seen.add(code);
    }
    assertTrue(seen.size() > 40, "os códigos repítense: " + seen.size());
  }

  @Test
  void theRightCodeInTimeIsValidAndSpacesDoNotCount() {
    EmailCode code = issued();
    assertEquals(EmailCode.Check.VALID, code.check(code.code(), NOW + 10));
    String spaced = code.code().substring(0, 3) + " " + code.code().substring(3);
    assertEquals(EmailCode.Check.VALID, code.check(spaced, NOW + 10));
  }

  @Test
  void itExpiresAfterFiveMinutes() {
    EmailCode code = issued();
    assertEquals(EmailCode.Check.VALID, code.check(code.code(), NOW + 299));
    assertEquals(EmailCode.Check.EXPIRED, code.check(code.code(), NOW + 300));
    assertFalse(code.usable(NOW + 300));
  }

  @Test
  void fiveWrongAttemptsUseItUp() {
    EmailCode code = issued();
    String wrong = code.code().equals("000000") ? "111111" : "000000";
    for (int i = 1; i < EmailCode.MAX_ATTEMPTS; i++) {
      assertEquals(EmailCode.Check.WRONG, code.check(wrong, NOW), "intento " + i);
      code = code.after();
    }
    // O quinto malo xa o esgota; despois nin o bo vale.
    assertEquals(EmailCode.Check.EXPIRED, code.check(wrong, NOW));
    code = code.after();
    assertEquals(EmailCode.Check.EXPIRED, code.check(code.code(), NOW));
    assertEquals(EmailCode.Check.WRONG, issued().check(null, NOW));
  }

  @Test
  void anotherCodeOnlyAfterSixtySecondsAndAtMostFivePerSignIn() {
    EmailCode code = issued();
    assertEquals(60, code.waitSeconds(NOW));
    assertEquals(1, code.waitSeconds(NOW + 59));
    assertEquals(0, code.waitSeconds(NOW + 60));
    for (int i = 2; i <= EmailCode.MAX_SENDS; i++) {
      assertTrue(code.sendsLeft(), "envío " + i);
      code = EmailCode.issue(NOW, Optional.of(code), RANDOM);
    }
    assertFalse(code.sendsLeft());
  }

  @Test
  void theNoteRoundTripsAndRejectsGarbage() {
    EmailCode code = issued().after();
    EmailCode back = EmailCode.fromNote(code.toNote()).orElseThrow();
    assertEquals(code.toNote(), back.toNote());
    assertEquals(Optional.empty(), EmailCode.fromNote(null));
    assertEquals(Optional.empty(), EmailCode.fromNote("12345:1:0:1"));
    assertEquals(Optional.empty(), EmailCode.fromNote("123456:x:0:1"));
    assertEquals(Optional.empty(), EmailCode.fromNote("123456:1:0"));
    assertTrue(code.toString().startsWith("EmailCode[***,"), code.toString());
  }
}
