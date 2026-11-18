// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Clase que usa SQL nativo (JDBC ou {@code createNativeQuery}). Hibernate non filtra o SQL nativo,
 * así que a clase declara o seu alcance e a regra A8b (ADR-0006) o fai cumprir no build:
 *
 * <ul>
 *   <li>{@link Scope#MUNICIPALITY}: toda sentenza leva {@code municipality_id = :param} e existe
 *       {@code <Clase>TenantIT} que usa a clase con datos en dous concellos;
 *   <li>{@link Scope#GLOBAL}: cun {@link #reason()} e só sobre táboas de {@code TenancyAllowlist}.
 * </ul>
 *
 * Só en {@code ..adapter.out.persistence..} (A8). O SQL ten que ser literal na propia clase para
 * que A8b o poida ler.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NativeSql {

  /** Alcance dos datos que le ou escribe a clase. */
  Scope scope();

  /** Obrigatorio en {@link Scope#GLOBAL}: por que non vai filtrado por concello. */
  String reason() default "";

  /** Alcance do SQL nativo. */
  enum Scope {
    /** Datos dun concello: o concello vai sempre como parámetro. */
    MUNICIPALITY,
    /** Datos sen concello: só táboas de {@code TenancyAllowlist}. */
    GLOBAL
  }
}
