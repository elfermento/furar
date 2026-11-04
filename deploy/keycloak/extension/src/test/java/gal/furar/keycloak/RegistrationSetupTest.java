// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * O parámetro {@code furar_setup} do rexistro (só vista, revisión do 2026-11-03): agocha a frase de
 * privacidade só co valor exacto {@code 1}. A páxina enteira próbase en D51.
 */
class RegistrationSetupTest {

  @Test
  void theNoteIsTheOneKeycloakKeepsForTheExtraParameter() {
    assertEquals("client_request_param_furar_setup", RegistrationSetup.CLIENT_NOTE);
    assertEquals("furarSetup", RegistrationSetup.FORM_ATTRIBUTE);
  }

  @Test
  void onlyTheExactValueOneHidesTheSentence() {
    assertTrue(RegistrationSetup.isSetup("1"));
    assertFalse(RegistrationSetup.isSetup(null));
    assertFalse(RegistrationSetup.isSetup(""));
    assertFalse(RegistrationSetup.isSetup("0"));
    assertFalse(RegistrationSetup.isSetup("true"));
    assertFalse(RegistrationSetup.isSetup(" 1"));
  }
}
