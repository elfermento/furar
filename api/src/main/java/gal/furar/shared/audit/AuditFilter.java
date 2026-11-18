// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Que eventos ler (filtros das cabeceiras, revisión do 2026-11-10). Cada conxunto baleiro é
 * «todos».
 *
 * @param from comezo do período, incluído
 * @param to fin do período, excluído; como moito {@value #MAX_DAYS} días despois de {@code from}
 * @param area prefixo da acción ({@code staff} → {@code staff.*}); nulo = todas
 * @param actorSubjects só o que fixeron estas persoas ({@code sub})
 * @param outcome só este resultado; nulo = os dous
 * @param actions só estes códigos de acción
 * @param roles só o feito con algún destes roles
 * @param facilities só o que foi nestas instalacións ({@code details.facilityId})
 */
public record AuditFilter(
    Instant from,
    Instant to,
    @Nullable String area,
    Set<String> actorSubjects,
    AuditEvent.@Nullable Outcome outcome,
    Set<String> actions,
    Set<Role> roles,
    Set<UUID> facilities) {

  /** O período máis longo que se le dunha vez: un ano, tamén bisesto. */
  public static final int MAX_DAYS = 366;

  /** Códigos como moito en {@code actions} e persoas ou instalacións nun filtro. */
  public static final int MAX_VALUES = 100;

  private static final Pattern AREA = Pattern.compile("[a-z]+(-[a-z]+)*");
  private static final Pattern ACTION = Pattern.compile("[a-z]+(-[a-z]+)*(\\.[a-z]+(-[a-z]+)*)+");

  public AuditFilter {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(MAX_DAYS)) > 0) {
      throw new DomainException(AuditError.RANGE_INVALID, Map.of("maxDays", MAX_DAYS));
    }
    if (area != null && !AREA.matcher(area).matches()) {
      throw invalid("area");
    }
    actorSubjects = actorSubjects == null ? Set.of() : Set.copyOf(actorSubjects);
    actions = actions == null ? Set.of() : Set.copyOf(actions);
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    facilities = facilities == null ? Set.of() : Set.copyOf(facilities);
    if (actions.size() > MAX_VALUES
        || actions.stream().anyMatch(a -> !ACTION.matcher(a).matches())) {
      throw invalid("action");
    }
    if (actorSubjects.size() > MAX_VALUES) {
      throw invalid("memberId");
    }
    if (facilities.size() > MAX_VALUES) {
      throw invalid("facilityId");
    }
  }

  /** Só o período, sen máis filtros. */
  public static AuditFilter between(Instant from, Instant to) {
    return new AuditFilter(from, to, null, Set.of(), null, Set.of(), Set.of(), Set.of());
  }

  /** O mesmo filtro noutro período. */
  public AuditFilter during(Instant newFrom, Instant newTo) {
    return new AuditFilter(
        newFrom, newTo, area, actorSubjects, outcome, actions, roles, facilities);
  }

  private static DomainException invalid(String name) {
    return new DomainException(CommonError.INVALID_PARAMETER, Map.of("name", name));
  }
}
