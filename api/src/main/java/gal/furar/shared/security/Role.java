// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

/** Roles de Furar. Asígnanse por concello, salvo {@link #PLATFORM_ADMIN}, que é global. */
public enum Role {
  PLATFORM_ADMIN,
  MANAGER,
  STAFF,
  RESIDENT
}
