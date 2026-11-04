// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha pertenza ao persoal do concello, para outros módulos.
 *
 * @param id identificador da pertenza
 * @param subject a persoa ({@code sub}); nulo mentres está invitada
 * @param name nome da persoa ao aceptar; nulo se o token non o traía ou aínda non aceptou
 * @param email correo da invitación; nulo se a pertenza está anonimizada (desactivada hai máis de
 *     {@code FURAR_AUDIT_RETENTION}: tampouco ten nome)
 * @param role {@code MANAGER} ou {@code STAFF}
 * @param status {@code INVITED}, {@code ACTIVE} ou {@code DEACTIVATED}
 * @param deactivatedAt cando se desactivou; nulo se non o está (a súa identidade bloquéase cando a
 *     desactivación sae do tramo visible do rexistro)
 */
public record StaffMemberSummary(
    UUID id,
    @Nullable String subject,
    @Nullable String name,
    @Nullable String email,
    Role role,
    Status status,
    @Nullable Instant deactivatedAt) {

  public StaffMemberSummary {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(status, "status");
  }

  /** Rol da pertenza. */
  public enum Role {
    MANAGER,
    STAFF
  }

  /** Estado da pertenza. */
  public enum Status {
    INVITED,
    ACTIVE,
    DEACTIVATED
  }
}
