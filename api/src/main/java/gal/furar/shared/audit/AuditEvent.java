// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.security.Role;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un feito auditado.
 *
 * @param action código estable da acción ({@code municipality.export})
 * @param outcome resultado
 * @param municipalityId concello afectado, se o hai
 * @param municipalitySlug slug no momento da acción
 * @param details datos sen información persoal: seccións, número de filas, código de erro
 * @param knownActorRoles roles de quen actúa que o caso de uso sabe e a petición aínda non ve:
 *     {@code staff.sign-in} grávase mentres se le o rol da táboa, así que o leva el. O adaptador
 *     súmaos aos da petición; case sempre van baleiros
 * @param systemActor quen actúa cando non é unha persoa senón unha tarefa de Furar, sen petición
 *     nin sesión (a conservación do persoal, {@code furar:staff-retention}); nulo nas accións das
 *     persoas. Sempre empeza por {@value #SYSTEM_PREFIX}, que non pode ser un {@code sub} de
 *     Keycloak (un UUID)
 */
public record AuditEvent(
    String action,
    Outcome outcome,
    @Nullable UUID municipalityId,
    @Nullable String municipalitySlug,
    Map<String, Object> details,
    Set<Role> knownActorRoles,
    @Nullable String systemActor) {

  /** Prefixo das contas de sistema: ningún {@code sub} de Keycloak empeza así. */
  public static final String SYSTEM_PREFIX = "furar:";

  public AuditEvent {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(outcome, "outcome");
    details = details == null ? Map.of() : Map.copyOf(details);
    knownActorRoles = knownActorRoles == null ? Set.of() : Set.copyOf(knownActorRoles);
    if (systemActor != null
        && (!systemActor.startsWith(SYSTEM_PREFIX) || systemActor.length() > 255)) {
      throw new IllegalArgumentException("A system actor starts with " + SYSTEM_PREFIX);
    }
  }

  /** Cos roles que o caso de uso sabe; quen actúa sae da petición. */
  public AuditEvent(
      String action,
      Outcome outcome,
      @Nullable UUID municipalityId,
      @Nullable String municipalitySlug,
      Map<String, Object> details,
      Set<Role> knownActorRoles) {
    this(action, outcome, municipalityId, municipalitySlug, details, knownActorRoles, null);
  }

  /** Sen roles coñecidos: os roles de quen actúa saen todos da petición. */
  public AuditEvent(
      String action,
      Outcome outcome,
      @Nullable UUID municipalityId,
      @Nullable String municipalitySlug,
      Map<String, Object> details) {
    this(action, outcome, municipalityId, municipalitySlug, details, Set.of(), null);
  }

  /**
   * Unha acción dunha tarefa de Furar, sen persoa: o rexistro garda {@code subject} como quen actúa
   * e ningún rol.
   */
  public static AuditEvent bySystem(
      String subject,
      String action,
      Outcome outcome,
      UUID municipalityId,
      @Nullable String municipalitySlug,
      Map<String, Object> details) {
    return new AuditEvent(
        action,
        outcome,
        Objects.requireNonNull(municipalityId, "municipalityId"),
        municipalitySlug,
        details,
        Set.of(),
        Objects.requireNonNull(subject, "subject"));
  }

  /** O mesmo evento sabendo que quen actúa ten tamén este rol. */
  public AuditEvent withActorRole(Role role) {
    Set<Role> roles = EnumSet.of(role);
    roles.addAll(knownActorRoles);
    return new AuditEvent(
        action, outcome, municipalityId, municipalitySlug, details, roles, systemActor);
  }

  /** Resultado da acción. */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }
}
