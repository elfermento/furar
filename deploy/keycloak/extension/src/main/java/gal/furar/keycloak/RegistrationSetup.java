// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

/**
 * O parámetro {@code furar_setup} do URL de acceso (revisión do 2026-11-03): a web pono, co valor
 * {@code 1}, só mentres non hai ningún concello. Daquela a páxina de rexistro recibe {@code
 * furarSetup} e o tema non mostra a frase de privacidade, que nomea un responsable que aínda non
 * existe. Só vista, sen efecto de seguridade: quen escriba o URL á man só agocha unha frase
 * informativa na súa propia vista da páxina; sen o parámetro, a frase sae coma sempre.
 */
final class RegistrationSetup {

  /** Parámetro do URL de acceso. */
  static final String PARAMETER = "furar_setup";

  /** Nota do cliente onde Keycloak garda o parámetro (AuthorizationEndpoint). */
  static final String CLIENT_NOTE = "client_request_param_" + PARAMETER;

  /** Atributo da páxina de rexistro que le o tema (register-commons.ftl). */
  static final String FORM_ATTRIBUTE = "furarSetup";

  private RegistrationSetup() {}

  /** Só conta o valor exacto {@code 1}; calquera outro, coma se faltase. */
  static boolean isSetup(String note) {
    return "1".equals(note);
  }
}
