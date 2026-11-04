// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A ligazón «Privacidade e os teus dereitos» do rexistro (ADR-0017, revisión do 2026-11-03): o
 * camiño vén da web, a orixe sempre do {@code redirect_uri} xa validado. A páxina enteira próbase
 * en D51.
 */
class RegistrationPrivacyTest {

  static final String REDIRECT = "https://reservas.example.gal/naron?code=x#y";

  @Test
  void theNoteIsTheOneKeycloakKeepsForTheExtraParameter() {
    assertEquals("client_request_param_furar_privacy", RegistrationPrivacy.CLIENT_NOTE);
    assertEquals("furarPrivacyUrl", RegistrationPrivacy.FORM_ATTRIBUTE);
  }

  @Test
  void thePathGoesToTheOriginOfTheRedirect() {
    assertEquals(
        Optional.of("https://reservas.example.gal/naron/privacidade"),
        RegistrationPrivacy.link(REDIRECT, "/naron/privacidade"));
    assertEquals(
        Optional.of("http://localhost:4200/naron/privacidade"),
        RegistrationPrivacy.link("http://localhost:4200/", "/naron/privacidade"));
  }

  @Test
  void anythingButASimplePathIsIgnored() {
    for (String path :
        new String[] {
          null,
          "",
          "/",
          "naron/privacidade",
          "//evil.example/privacidade",
          "/\\evil.example",
          "https://evil.example/privacidade",
          "/naron/../admin",
          "/naron/./privacidade",
          "/naron/privacidade?x=1",
          "/naron/privacidade#persoal",
          "/naron/privacidade\"><script>",
          "/naron privacidade",
          "/" + "a".repeat(RegistrationPrivacy.MAX_LENGTH)
        }) {
      assertTrue(RegistrationPrivacy.link(REDIRECT, path).isEmpty(), String.valueOf(path));
    }
  }

  @Test
  void withoutAValidRedirectThereIsNoLink() {
    for (String redirect :
        new String[] {null, "", "/naron", "javascript:alert(1)", "ftp://x.example/", "https:///x"}) {
      assertTrue(
          RegistrationPrivacy.link(redirect, "/naron/privacidade").isEmpty(),
          String.valueOf(redirect));
    }
  }
}
