// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.shared.audit.AuditCursor;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.security.Role;
import gal.furar.staff.domain.StaffMemberId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Que parte do rexistro de actividade ler (ADR-0020; filtros das cabeceiras, revisión do
 * 2026-11-10). Cada conxunto baleiro é «todos».
 *
 * @param from comezo do período, incluído; nulo = {@value
 *     gal.furar.shared.audit.AuditFilter#MAX_DAYS} días antes do fin
 * @param to fin do período, excluído; nulo = agora
 * @param area prefixo da acción ({@code staff}, {@code facility}, {@code booking}…); nulo = todas
 * @param memberIds só o que fixeron as persoas destas pertenzas
 * @param outcome só este resultado; nulo = os dous
 * @param after onde segue a páxina; nulo = a primeira
 * @param actions só estes códigos de acción
 * @param roles só o feito con algún destes roles
 * @param facilityIds só o que foi nestas instalacións
 */
public record ActivityQuery(
    @Nullable Instant from,
    @Nullable Instant to,
    @Nullable String area,
    Set<StaffMemberId> memberIds,
    @Nullable Outcome outcome,
    @Nullable AuditCursor after,
    Set<String> actions,
    Set<Role> roles,
    Set<UUID> facilityIds) {

  public ActivityQuery {
    memberIds = memberIds == null ? Set.of() : Set.copyOf(memberIds);
    actions = actions == null ? Set.of() : Set.copyOf(actions);
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    facilityIds = facilityIds == null ? Set.of() : Set.copyOf(facilityIds);
  }

  /** Só o período e a páxina: o que admite o rexistro da plataforma. */
  public static ActivityQuery period(
      @Nullable Instant from, @Nullable Instant to, @Nullable AuditCursor after) {
    return new ActivityQuery(from, to, null, Set.of(), null, after, Set.of(), Set.of(), Set.of());
  }
}
