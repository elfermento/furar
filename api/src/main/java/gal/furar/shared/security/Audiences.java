// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import gal.furar.shared.domain.Audience;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Calcula a audiencia dun actor nun concello para os campos personalizados (ADR-0011). */
public final class Audiences {

  private Audiences() {}

  /**
   * Persoal se ten {@link SharedPermission#STAFF_DATA_VIEW} no concello; titular se é o dono do
   * recurso; se non, público.
   */
  public static Audience of(
      Optional<Actor> actor, @Nullable String municipalitySlug, boolean isOwner) {
    if (actor.isEmpty()) {
      return Audience.PUBLIC;
    }
    if (actor.get().can(SharedPermission.STAFF_DATA_VIEW, municipalitySlug)) {
      return Audience.STAFF;
    }
    return isOwner ? Audience.OWNER : Audience.PUBLIC;
  }

  /**
   * Audiencia para os datos do propio concello: o «titular» dun concello é quen ten algún rol nel
   * (o persoal, {@code MANAGER} ou {@code STAFF}; a persoa que reserva non ten rol, ADR-0025). Ter
   * sesión non abonda: no realm calquera persoa pode rexistrarse, e non por iso está vinculada a
   * ese concello (SEC-03, ADR-0011).
   */
  public static Audience forMunicipality(Optional<Actor> actor, @Nullable String municipalitySlug) {
    return of(actor, municipalitySlug, actor.map(a -> a.hasRoleIn(municipalitySlug)).orElse(false));
  }

  /**
   * Audiencia para a configuración do concello (os campos do propio concello e das instalacións, e
   * as definicións): a administración da plataforma, coma o persoal, porque a deixa feita
   * (ADR-0028, revisión do 2026-11-03). Só para esa configuración: nunca para datos da veciñanza
   * (reservas).
   */
  public static Audience forConfiguration(
      Optional<Actor> actor, @Nullable String municipalitySlug) {
    return platformAdmin(actor) ? Audience.STAFF : forMunicipality(actor, municipalitySlug);
  }

  /** Coma {@link #forConfiguration}, para as instalacións: ninguén é o seu titular. */
  public static Audience forFacilities(Optional<Actor> actor, @Nullable String municipalitySlug) {
    return platformAdmin(actor) ? Audience.STAFF : of(actor, municipalitySlug, false);
  }

  private static boolean platformAdmin(Optional<Actor> actor) {
    return actor
        .map(a -> a.grants().stream().anyMatch(g -> g.role() == Role.PLATFORM_ADMIN))
        .orElse(false);
  }
}
