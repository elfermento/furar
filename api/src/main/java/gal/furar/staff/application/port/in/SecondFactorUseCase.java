// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import org.jspecify.annotations.Nullable;

/**
 * Se unha conta ten que entrar co segundo factor (ADR-0029): pregúntao Keycloak en cada acceso con
 * contrasinal ou por un provedor de identidade, e ao crear unha conta. Só di si ou non.
 */
public interface SecondFactorUseCase {

  /**
   * Si para o persoal activo dalgún concello, a administración da plataforma e, mentres aínda non
   * hai ningunha, a conta co correo de {@code FURAR_ADMIN_EMAIL} (vai selo ao entrar). Un si de
   * máis só pide o código; por iso o correo non ten que estar verificado.
   *
   * @param subject o {@code sub} da conta en Keycloak
   * @param email o correo da conta, se o ten
   */
  boolean required(String subject, @Nullable String email);
}
