// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.domain.Language;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Para quen é a reserva. Dúas formas (ADR-0025, punto 3; reserva por outra persoa, 2026-11-04):
 *
 * <ul>
 *   <li><b>Coa súa conta</b> ({@link #account}): {@code sub}, nome, correo verificado e idioma,
 *       copiados do token ao reservar. Sen teléfono.
 *   <li><b>No mostrador</b> ({@link #walkIn}): o persoal reserva co nome e, se os dá, o correo e o
 *       teléfono. A reserva é do correo (ADR-0031, punto 7): se ten conta, leva o seu {@code sub}
 *       ({@link #linkedTo}); se non, queda sen el ata que a persoa entre con ese correo verificado.
 *       Sen correo non sae en «as miñas reservas» de ninguén.
 * </ul>
 *
 * Datos persoais: conservación de ADR-0017.
 *
 * @param subject {@code sub} de Keycloak: filtra «as miñas reservas»; nulo sen conta
 * @param name nome para o mostrador e o correo
 * @param email correo: verificado coa conta; sen conta, o que deu a persoa (pode faltar)
 * @param phone teléfono, só nas que fai o persoal e só se o deu
 * @param language idioma dos correos
 */
public record Holder(
    @Nullable String subject,
    String name,
    @Nullable String email,
    @Nullable String phone,
    Language language) {

  public Holder {
    Objects.requireNonNull(language, "language");
    check("name", name, 1, 200);
    if (subject != null) {
      check("subject", subject, 1, 255);
      if (email == null) {
        throw new IllegalArgumentException("An account holder has an email");
      }
    }
    if (email != null) {
      check("email", email, 3, 254);
      if (!email.matches("^.+@.+$")) {
        throw new IllegalArgumentException("Invalid email");
      }
    }
    if (phone != null) {
      check("phone", phone, 3, 30);
      if (!phone.matches("^[0-9+() .-]+$")) {
        throw new IllegalArgumentException("Invalid phone");
      }
    }
  }

  /** Coa súa conta: os datos do token. */
  public Holder(String subject, String name, String email, Language language) {
    this(Objects.requireNonNull(subject, "subject"), name, email, null, language);
  }

  /** Coa súa conta: os datos do token. */
  public static Holder account(String subject, String name, String email, Language language) {
    return new Holder(subject, name, email, language);
  }

  /** Sen conta: o que lle deu a persoa ao persoal. */
  public static Holder walkIn(
      String name, @Nullable String email, @Nullable String phone, Language language) {
    return new Holder(null, name, email, phone, language);
  }

  /** Se a reserva ten conta: a de quen reservou ou, no mostrador, a do seu correo (ADR-0031). */
  public boolean hasAccount() {
    return subject != null;
  }

  /**
   * A reserva do mostrador a nome dun correo que xa ten conta (ADR-0031, punto 7): é desa conta
   * desde ese momento. O teléfono, se o deu, queda.
   */
  public Holder linkedTo(String account) {
    return new Holder(Objects.requireNonNull(account, "account"), name, email, phone, language);
  }

  /** O correo normalizado ({@link EmailKey}): de quen é a reserva; nulo sen correo. */
  public @Nullable String emailKey() {
    return email == null ? null : EmailKey.of(email);
  }

  private static void check(String field, @Nullable String value, int min, int max) {
    if (value == null || value.length() < min || value.length() > max) {
      throw new IllegalArgumentException(field + " length out of range");
    }
  }
}
