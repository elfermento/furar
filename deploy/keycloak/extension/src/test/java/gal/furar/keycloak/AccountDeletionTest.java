// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.InitiatedActionSupport;

/**
 * ADR-0043: a acción só borra se a API di que si; sen resposta, nada (fallo pechado). Sempre pide
 * entrar outra vez e lánzase desde a web.
 */
class AccountDeletionTest {

  @Test
  void onlyAYesFromTheApiDeletes() {
    assertEquals(AccountDeletion.State.CONFIRM, AccountDeletion.decide(Optional.of(true)));
    assertEquals(AccountDeletion.State.BLOCKED, AccountDeletion.decide(Optional.of(false)));
    assertEquals(AccountDeletion.State.UNAVAILABLE, AccountDeletion.decide(Optional.empty()));
  }

  @Test
  void theThemeGetsTheStateInLowerCase() {
    assertEquals("confirm", AccountDeletion.State.CONFIRM.attribute());
    assertEquals("blocked", AccountDeletion.State.BLOCKED.attribute());
    assertEquals("unavailable", AccountDeletion.State.UNAVAILABLE.attribute());
    assertEquals("deleted", AccountDeletion.State.DELETED.attribute());
  }

  @Test
  void itIsLaunchedFromTheWebAndAlwaysAsksToSignInAgain() {
    AccountDeletion action = new AccountDeletion();
    assertEquals("furar-delete-account", action.getId());
    assertEquals(InitiatedActionSupport.SUPPORTED, action.initiatedActionSupport());
    assertEquals(0, action.getMaxAuthAge(null));
  }
}
