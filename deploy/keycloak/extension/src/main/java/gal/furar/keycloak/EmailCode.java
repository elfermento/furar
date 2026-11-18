// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Un código de seis cifras enviado por correo (ADR-0029): o estado que se garda nunha nota da
 * sesión de autenticación de Keycloak (no servidor, nunca no navegador), sen dependencias de
 * Keycloak para probalo só.
 *
 * <ul>
 *   <li>Vale {@value #TTL_SECONDS} s e unha soa vez (quen chama borra a nota ao acertar).
 *   <li>{@value #MAX_ATTEMPTS} intentos por código; despois hai que pedir outro.
 *   <li>Outro código, como moito cada {@value #RESEND_SECONDS} s e {@value #MAX_SENDS} por acceso;
 *       despois hai que volver empezar (correo e contrasinal).
 * </ul>
 *
 * Os tempos van en segundos Unix.
 */
final class EmailCode {

  static final int DIGITS = 6;
  static final long TTL_SECONDS = 300;
  static final int MAX_ATTEMPTS = 5;
  static final long RESEND_SECONDS = 60;
  static final int MAX_SENDS = 5;

  /** Resultado de comprobar un código escrito. */
  enum Check {
    /** Correcto e a tempo. */
    VALID,
    /** Non coincide; quedan intentos. */
    WRONG,
    /** Caducou ou xa non quedan intentos: hai que pedir outro. */
    EXPIRED
  }

  private final String code;
  private final long issuedAt;
  private final int attempts;
  private final int sends;

  private EmailCode(String code, long issuedAt, int attempts, int sends) {
    this.code = code;
    this.issuedAt = issuedAt;
    this.attempts = attempts;
    this.sends = sends;
  }

  /** Un código novo; {@code previous} é o anterior deste acceso, se o houbo. */
  static EmailCode issue(long now, Optional<EmailCode> previous, SecureRandom random) {
    StringBuilder digits = new StringBuilder(DIGITS);
    for (int i = 0; i < DIGITS; i++) {
      digits.append((char) ('0' + random.nextInt(10)));
    }
    return new EmailCode(digits.toString(), now, 0, previous.map(p -> p.sends).orElse(0) + 1);
  }

  /** O código (para o correo). */
  String code() {
    return code;
  }

  /** Se aínda se pode escribir: a tempo e con intentos. */
  boolean usable(long now) {
    return now - issuedAt < TTL_SECONDS && attempts < MAX_ATTEMPTS;
  }

  /** Segundos que faltan para poder pedir outro (0 se xa se pode). */
  long waitSeconds(long now) {
    return Math.max(0, issuedAt + RESEND_SECONDS - now);
  }

  /** Se este acceso aínda pode pedir outro código (sen contar a espera). */
  boolean sendsLeft() {
    return sends < MAX_SENDS;
  }

  /** Comproba o que escribiu a persoa; o estado novo (un intento máis) devólveo {@link #after}. */
  Check check(String input, long now) {
    if (!usable(now)) {
      return Check.EXPIRED;
    }
    String typed = input == null ? "" : input.replaceAll("\\s", "");
    boolean same =
        MessageDigest.isEqual(
            typed.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII));
    if (same) {
      return Check.VALID;
    }
    return attempts + 1 >= MAX_ATTEMPTS ? Check.EXPIRED : Check.WRONG;
  }

  /** O estado despois dun intento fallido. */
  EmailCode after() {
    return new EmailCode(code, issuedAt, attempts + 1, sends);
  }

  /** O texto da nota da sesión. */
  String toNote() {
    return code + ":" + issuedAt + ":" + attempts + ":" + sends;
  }

  /** O estado desde a nota, ou baleiro se non hai ou non se entende. */
  static Optional<EmailCode> fromNote(String note) {
    if (note == null) {
      return Optional.empty();
    }
    String[] p = note.split(":", -1);
    if (p.length != 4 || !p[0].matches("[0-9]{" + DIGITS + "}")) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new EmailCode(
              p[0], Long.parseLong(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return "EmailCode[***, issuedAt=" + issuedAt + ", attempts=" + attempts + ", sends=" + sends + "]";
  }
}
