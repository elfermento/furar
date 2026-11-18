// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.security.Role;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento gardado no rexistro de actividade.
 *
 * @param id identificador
 * @param occurredAt cando pasou
 * @param actorSubject quen actuou ({@code sub}; {@code anonymous} sen sesión)
 * @param actorRoles os seus roles efectivos no concello da acción nese momento (baleiro nos eventos
 *     anteriores á columna)
 * @param action código da acción
 * @param outcome resultado
 * @param municipalityId concello, se o hai
 * @param municipalitySlug slug no momento da acción
 * @param requestId {@code X-Request-Id} da petición
 * @param details detalles sen datos persoais
 */
public record AuditRecord(
    UUID id,
    Instant occurredAt,
    String actorSubject,
    Set<Role> actorRoles,
    String action,
    AuditEvent.Outcome outcome,
    @Nullable UUID municipalityId,
    @Nullable String municipalitySlug,
    @Nullable String requestId,
    Map<String, Object> details) {

  public AuditRecord {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(occurredAt, "occurredAt");
    actorRoles = Set.copyOf(actorRoles);
    details = Map.copyOf(details);
  }

  /** Onde segue a páxina despois deste evento. */
  public AuditCursor cursor() {
    return new AuditCursor(occurredAt, id);
  }
}
