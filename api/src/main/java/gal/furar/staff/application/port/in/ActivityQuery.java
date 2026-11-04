// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.shared.audit.AuditCursor;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.staff.domain.StaffMemberId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Que parte do rexistro de actividade ler (plan do dominio 4, §1.2.12).
 *
 * @param from comezo do período, incluído; nulo = {@value
 *     gal.furar.shared.audit.AuditFilter#MAX_DAYS} días antes do fin
 * @param to fin do período, excluído; nulo = agora
 * @param area prefixo da acción ({@code staff}, {@code facility}, {@code booking}…); nulo = todas
 * @param memberId só o que fixo a persoa desta pertenza; nulo = todas
 * @param outcome só este resultado; nulo = os dous
 * @param after onde segue a páxina; nulo = a primeira
 */
public record ActivityQuery(
    @Nullable Instant from,
    @Nullable Instant to,
    @Nullable String area,
    @Nullable StaffMemberId memberId,
    @Nullable Outcome outcome,
    @Nullable AuditCursor after) {

  /** Só o período e a páxina: o que admite o rexistro da plataforma. */
  public static ActivityQuery period(
      @Nullable Instant from, @Nullable Instant to, @Nullable AuditCursor after) {
    return new ActivityQuery(from, to, null, null, null, after);
  }
}
