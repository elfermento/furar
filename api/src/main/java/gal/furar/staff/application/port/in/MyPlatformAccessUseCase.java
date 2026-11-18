// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Se a persoa da sesión é administración da plataforma (ADR-0028): a web móstralle a alta de
 * concellos e o seu menú. Só di si ou non; nunca o correo de {@code FURAR_ADMIN_EMAIL}.
 */
public interface MyPlatformAccessUseCase {

  /**
   * Se é administración da plataforma nesta petición (tamén se o acaba de ser no primeiro acceso).
   */
  boolean platformAdmin();
}
