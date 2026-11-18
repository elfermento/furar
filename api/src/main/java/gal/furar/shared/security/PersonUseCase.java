// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Caso de uso da persoa con conta, sen rol no concello (ADR-0025, punto 3; ADR-0007): reservar e
 * xestionar as súas reservas. A conta é común a todos os concellos (ADR-0017), así que non se pide
 * permiso nin rol: só sesión ({@code 401 auth.unauthenticated} sen ela) e o correo verificado no
 * token ({@code 403 auth.email-not-verified}). O caso de uso filtra sempre polo {@code sub} de
 * {@link Actor#subject()}: outra persoa non ve nin cambia o alleo. Alternativa a {@link
 * RequiresPermission} e {@link PublicUseCase} (regra A15: exactamente unha das tres).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersonUseCase {}
