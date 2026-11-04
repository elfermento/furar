// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Que eventos ler (plan do dominio 4, §1.2.12).
 *
 * @param from comezo do período, incluído
 * @param to fin do período, excluído; como moito {@value #MAX_DAYS} días despois de {@code from}
 * @param area prefixo da acción ({@code staff} → {@code staff.*}); nulo = todas
 * @param actorSubject só o que fixo esta persoa ({@code sub}); nulo = todas
 * @param outcome só este resultado; nulo = os dous
 */
public record AuditFilter(
    Instant from,
    Instant to,
    @Nullable String area,
    @Nullable String actorSubject,
    AuditEvent.@Nullable Outcome outcome) {

  /** O período máis longo que se le dunha vez: un ano, tamén bisesto. */
  public static final int MAX_DAYS = 366;

  private static final Pattern AREA = Pattern.compile("[a-z]+(-[a-z]+)*");

  public AuditFilter {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(MAX_DAYS)) > 0) {
      throw new DomainException(AuditError.RANGE_INVALID, Map.of("maxDays", MAX_DAYS));
    }
    if (area != null && !AREA.matcher(area).matches()) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("name", "area"));
    }
  }

  /** Só o período, sen máis filtros. */
  public static AuditFilter between(Instant from, Instant to) {
    return new AuditFilter(from, to, null, null, null);
  }
}
