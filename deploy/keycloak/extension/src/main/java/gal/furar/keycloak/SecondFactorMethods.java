// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Os dous segundos factores do persoal (ADR-0029, revisión do 2026-11-09) e as regras que os
 * escollen, sen dependencias de Keycloak para probalas soas.
 *
 * <ul>
 *   <li><b>Aplicación de autenticación</b> (TOTP, a credencial {@code otp} de Keycloak): a
 *       recomendada e a única que cumpre op.acc.6 sen reservas.
 *   <li><b>Código por correo</b> (a credencial {@value #EMAIL_CODE_TYPE}, sen segredo: só marca o
 *       método escollido): seis cifras ao correo verificado da conta en cada acceso.
 * </ul>
 *
 * Quen ten as dúas usa a aplicación. A instalación pode retirar o código por correo con {@value
 * #EMAIL_VARIABLE}{@code =false}: daquela só se ofrece a aplicación nas altas novas.
 */
final class SecondFactorMethods {

  /** Tipo da credencial que marca o código por correo. */
  static final String EMAIL_CODE_TYPE = "furar-email-code";

  /** Variable da instalación: {@code false} retira o código por correo das altas novas. */
  static final String EMAIL_VARIABLE = "FURAR_SECOND_FACTOR_EMAIL";

  /** Os métodos, na orde en que se ofrecen: a aplicación primeiro (a recomendada). */
  enum Method {
    APP,
    EMAIL
  }

  private SecondFactorMethods() {}

  /**
   * Se a instalación ofrece o código por correo. Baleiro ou {@code true}: si (por defecto);
   * {@code false}: non. Calquera outro valor: non, o lado seguro (quen o escribiu quería cambiar
   * algo e só a aplicación é sempre válida).
   */
  static boolean emailOffered(String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    return "true".equals(value.strip().toLowerCase(Locale.ROOT));
  }

  /** Se a variable ten un valor que non se entende (para avisar no rexistro de Keycloak). */
  static boolean emailSettingUnknown(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String v = value.strip().toLowerCase(Locale.ROOT);
    return !v.equals("true") && !v.equals("false");
  }

  /** O método que xa ten configurado a conta: a aplicación se ten as dúas. */
  static Optional<Method> configured(boolean hasApp, boolean hasEmailCode) {
    if (hasApp) {
      return Optional.of(Method.APP);
    }
    return hasEmailCode ? Optional.of(Method.EMAIL) : Optional.empty();
  }

  /**
   * Os métodos que se ofrecen na alta: sempre a aplicación e, se a instalación o permite e a
   * conta ten correo, o código por correo.
   */
  static List<Method> offered(boolean emailOffered, boolean hasEmail) {
    return emailOffered && hasEmail ? List.of(Method.APP, Method.EMAIL) : List.of(Method.APP);
  }

  /**
   * Se a alta deixa volver á escolla desde a aplicación ou desde o código por correo: só se hai
   * máis dun método que escoller.
   */
  static boolean canGoBack(List<Method> offered) {
    return offered.size() > 1;
  }

  /** O método que se escolleu no formulario, se é un dos ofrecidos. */
  static Optional<Method> chosen(String value, List<Method> offered) {
    if (value == null) {
      return Optional.empty();
    }
    return offered.stream()
        .filter(m -> m.name().toLowerCase(Locale.ROOT).equals(value.strip()))
        .findFirst();
  }
}
