// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.security.Role;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento do rexistro de actividade tal como o le a xestión (plan do dominio 4, §2.3).
 *
 * @param id identificador do evento
 * @param at cando pasou
 * @param action código da acción ({@code staff.invite})
 * @param outcome resultado
 * @param actor quen actuou
 * @param target sobre que, se a acción o di
 * @param municipalitySlug o concello no momento da acción; nulo nas accións sen concello
 * @param requestId {@code X-Request-Id} da petición, para buscala nos rexistros
 */
public record ActivityEntry(
    UUID id,
    Instant at,
    String action,
    Outcome outcome,
    Actor actor,
    @Nullable Target target,
    @Nullable String municipalitySlug,
    @Nullable String requestId) {

  /**
   * Quen actuou.
   *
   * @param kind se é persoal do concello, a plataforma ou outra conta
   * @param name o seu nome, só se é persoal e o ten
   * @param email o seu correo, só se é persoal
   * @param roles os seus roles efectivos no concello no momento da acción
   * @param blocked persoal desactivado hai máis do tramo visible e aínda sen anonimizar: sen nome
   *     nin correo («Persoa desactivada»), salvo na exportación dos bloqueados
   */
  public record Actor(
      ActorKind kind,
      @Nullable String name,
      @Nullable String email,
      Set<Role> roles,
      boolean blocked) {

    public Actor {
      Objects.requireNonNull(kind, "kind");
      roles = Set.copyOf(roles);
    }
  }

  /**
   * Que tipo de conta actuou. A web pon o texto: {@code PLATFORM} é «Administración da plataforma»
   * e {@code OTHER}, «Conta sen rol».
   */
  public enum ActorKind {
    /** Ten ou tivo pertenza no concello: sae co seu nome e correo, aínda que estea desactivada. */
    MEMBER,
    /** Sen pertenza no concello e con {@code PLATFORM_ADMIN}. */
    PLATFORM,
    /**
     * Unha tarefa de Furar sen persoa (a conservación do persoal, {@code furar:staff-retention}): a
     * web di «Furar (tarefa automática)».
     */
    SYSTEM,
    /** Ningunha das anteriores (a veciñanza, unha conta sen sesión). */
    OTHER
  }

  /**
   * Sobre que foi a acción, desde os detalles do evento.
   *
   * @param kind o tipo
   * @param id o identificador
   * @param memberEmail o correo da pertenza, se é unha pertenza do concello
   */
  public record Target(TargetKind kind, UUID id, @Nullable String memberEmail) {

    public Target {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(id, "id");
    }
  }

  /** Tipos de obxecto dunha acción. */
  public enum TargetKind {
    STAFF_MEMBER,
    BOOKING,
    FACILITY,
    PHOTO,
    MUNICIPALITY
  }
}
