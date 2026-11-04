// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Permiso que pide un caso de uso, no concello do {@code TenantContext} (ADR-0007). Todo método
 * público dun {@code *Service} que implementa un {@code *UseCase} leva esta anotación ou {@link
 * PublicUseCase} (regra A15). Leva o código dun {@link PermissionKey} (as constantes {@code Code}
 * de cada {@code enum}): unha anotación non pode recibir un tipo interface, e así cada módulo ten o
 * seu {@code enum} (ADR-0005 ARQ3-2). Un código sen rexistrar nega sempre e {@code
 * RolePermissionsTest} fai fallar o build.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

  /** Código do permiso ({@code municipality.update}). */
  String value();
}
