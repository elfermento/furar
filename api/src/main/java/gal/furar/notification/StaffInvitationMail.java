// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import gal.furar.shared.domain.Language;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * O correo dunha invitación ao persoal (plan do dominio 4, §2.2).
 *
 * <p>{@link #toString()} non mostra a ligazón (leva o token no fragmento) nin o destinatario: este
 * record nunca debe chegar enteiro a un rexistro.
 *
 * @param invitationId a invitación de {@code staff}: a clave do rexistro de envíos
 * @param recipient correo da persoa invitada, xa normalizado
 * @param language idioma do correo, o que escolleu quen invita
 * @param role {@code MANAGER} ou {@code STAFF}
 * @param inviterName nome de quen invita, como o mostra o seu token
 * @param link {@code ${FURAR_PUBLIC_URL}/{slug}/convite#<token>}
 * @param expiresAt cando caduca a invitación
 */
public record StaffInvitationMail(
    UUID invitationId,
    String recipient,
    Language language,
    String role,
    String inviterName,
    URI link,
    Instant expiresAt) {

  /** Os roles que se poden invitar (os do persoal dun concello). */
  public static final Set<String> ROLES = Set.of("MANAGER", "STAFF");

  public StaffInvitationMail {
    Objects.requireNonNull(invitationId, "invitationId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(inviterName, "inviterName");
    Objects.requireNonNull(link, "link");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (!ROLES.contains(role)) {
      throw new IllegalArgumentException("Not a staff role: " + role);
    }
    if (link.getRawFragment() == null || link.getRawFragment().isEmpty()) {
      throw new IllegalArgumentException("The invitation link carries the token in the fragment");
    }
  }

  @Override
  public String toString() {
    return "StaffInvitationMail[invitationId="
        + invitationId
        + ", language="
        + language
        + ", role="
        + role
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
