// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.util.Set;

/**
 * Un permiso (ADR-0005 ARQ3-2, ADR-0007). Cada módulo declara os seus nun {@code enum} propio
 * ({@code MunicipalityPermission}, {@code CustomFieldPermission}) cun código estable e os roles que
 * o teñen: a fila de roles vive no módulo, non nun ficheiro central. {@code PLATFORM_ADMIN} só ten
 * os que o nomean aquí (ADR-0007). {@link RolePermissions} xúntaos e falla se dous repiten código.
 */
public interface PermissionKey {

  /** Código estable, {@code <modulo>.<accion>} ({@code municipality.update}). */
  String code();

  /** Roles que o teñen, {@code PLATFORM_ADMIN} incluído se lle corresponde. */
  Set<Role> roles();
}
