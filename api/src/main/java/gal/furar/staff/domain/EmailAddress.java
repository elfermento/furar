// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Correo dunha pertenza, normalizado (plan do dominio 4, §1.2.6 e §2.1): sen espazos ao redor e en
 * minúsculas, para comparar o invitado co verificado da sesión e para o índice único por concello.
 * Non pretende validar o correo enteiro: Keycloak verifícao ao aceptar.
 *
 * <p>Unha pertenza desactivada hai máis de {@code FURAR_AUDIT_RETENTION} gárdase cun correo
 * anonimizado ({@link #anonymised}, §1.2.14): {@code anon-<pertenza>@anonimizada.invalid}. O
 * dominio {@code .invalid} está reservado (RFC 2606), así que ningún correo real o ten e ningunha
 * invitación o pode usar.
 */
public record EmailAddress(String value) {

  public static final int MIN_LENGTH = 3;
  public static final int MAX_LENGTH = 254;

  /** Prefixo do correo anonimizado; despois vai o id da pertenza. */
  public static final String ANONYMISED_PREFIX = "anon-";

  /** Dominio do correo anonimizado (TLD reservado {@code .invalid}). */
  public static final String ANONYMISED_DOMAIN = "anonimizada.invalid";

  private static final String RESERVED_TLD = "invalid";

  public EmailAddress {
    Objects.requireNonNull(value, "value");
    if (!value.equals(normalize(value)) || !isWellFormed(value)) {
      throw new IllegalArgumentException("Not a normalized e-mail address");
    }
  }

  /**
   * O correo normalizado.
   *
   * @throws DomainException {@code staff.email-invalid} se, normalizado, non ten 3–254 caracteres,
   *     un {@code @} con algo a cada lado, se ten espazos dentro ou se o dominio é {@code .invalid}
   *     (reservado para as anonimizadas)
   */
  public static EmailAddress of(@Nullable String raw) {
    String normalized = raw == null ? "" : normalize(raw);
    if (!isWellFormed(normalized) || isReserved(normalized)) {
      throw new DomainException(StaffError.EMAIL_INVALID);
    }
    return new EmailAddress(normalized);
  }

  /** O correo que substitúe o dunha pertenza anonimizada: único por pertenza e nunca real. */
  public static EmailAddress anonymised(StaffMemberId member) {
    return new EmailAddress(ANONYMISED_PREFIX + member.value() + "@" + ANONYMISED_DOMAIN);
  }

  /** Se é o correo dunha pertenza anonimizada: a lectura non o mostra como un correo. */
  public boolean isAnonymised() {
    return value.endsWith("@" + ANONYMISED_DOMAIN);
  }

  /** Se dous correos son o mesmo despois de normalizar (o invitado e o da sesión). */
  public boolean matches(@Nullable String other) {
    return other != null && value.equals(normalize(other));
  }

  private static String normalize(String raw) {
    return raw.strip().toLowerCase(Locale.ROOT);
  }

  private static boolean isReserved(String email) {
    String domain = email.substring(email.lastIndexOf('@') + 1);
    return domain.equals(RESERVED_TLD) || domain.endsWith("." + RESERVED_TLD);
  }

  private static boolean isWellFormed(String email) {
    int at = email.indexOf('@');
    return email.length() >= MIN_LENGTH
        && email.length() <= MAX_LENGTH
        && at > 0
        && at < email.length() - 1
        && email.codePoints().noneMatch(Character::isWhitespace);
  }
}
