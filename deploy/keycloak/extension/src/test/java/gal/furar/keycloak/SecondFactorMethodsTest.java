// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gal.furar.keycloak.SecondFactorMethods.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ADR-0029 (punto 2): a aplicación sempre se ofrece e vai primeiro; o
 * código por correo, se a instalación non o retira e a conta ten correo. Quen ten as dúas usa a
 * aplicación.
 */
class SecondFactorMethodsTest {

  @Test
  void theEmailCodeIsOfferedUnlessTheInstallationSaysFalse() {
    assertTrue(SecondFactorMethods.emailOffered(null));
    assertTrue(SecondFactorMethods.emailOffered(""));
    assertTrue(SecondFactorMethods.emailOffered(" TRUE "));
    assertFalse(SecondFactorMethods.emailOffered("false"));
    assertFalse(SecondFactorMethods.emailOffered("False\n"));
    // Un valor que non se entende retira o correo (o lado seguro) e avísase.
    assertFalse(SecondFactorMethods.emailOffered("no"));
    assertTrue(SecondFactorMethods.emailSettingUnknown("no"));
    assertFalse(SecondFactorMethods.emailSettingUnknown("false"));
    assertFalse(SecondFactorMethods.emailSettingUnknown(null));
  }

  @Test
  void theSetUpGoesBackToTheChoiceOnlyWhenThereIsOne() {
    assertTrue(SecondFactorMethods.canGoBack(SecondFactorMethods.offered(true, true)));
    assertFalse(SecondFactorMethods.canGoBack(SecondFactorMethods.offered(false, true)));
    assertFalse(SecondFactorMethods.canGoBack(SecondFactorMethods.offered(true, false)));
  }

  @Test
  void theAppComesFirstAndAloneWhenTheEmailCodeIsNotOffered() {
    assertEquals(List.of(Method.APP, Method.EMAIL), SecondFactorMethods.offered(true, true));
    assertEquals(List.of(Method.APP), SecondFactorMethods.offered(false, true));
    assertEquals(List.of(Method.APP), SecondFactorMethods.offered(true, false));
  }

  @Test
  void theAppWinsWhenAnAccountHasBoth() {
    assertEquals(Optional.of(Method.APP), SecondFactorMethods.configured(true, true));
    assertEquals(Optional.of(Method.APP), SecondFactorMethods.configured(true, false));
    assertEquals(Optional.of(Method.EMAIL), SecondFactorMethods.configured(false, true));
    assertEquals(Optional.empty(), SecondFactorMethods.configured(false, false));
  }

  @Test
  void onlyAnOfferedMethodCanBeChosen() {
    List<Method> both = List.of(Method.APP, Method.EMAIL);
    assertEquals(Optional.of(Method.APP), SecondFactorMethods.chosen("app", both));
    assertEquals(Optional.of(Method.EMAIL), SecondFactorMethods.chosen("email", both));
    assertEquals(Optional.empty(), SecondFactorMethods.chosen("email", List.of(Method.APP)));
    assertEquals(Optional.empty(), SecondFactorMethods.chosen("EMAIL", both));
    assertEquals(Optional.empty(), SecondFactorMethods.chosen(null, both));
  }
}
