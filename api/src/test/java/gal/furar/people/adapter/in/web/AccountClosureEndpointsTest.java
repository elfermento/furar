// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.people.adapter.in.web.dto.AccountDeletionBlockerResponse;
import gal.furar.people.application.port.in.AccountClosureUseCase;
import gal.furar.people.application.port.in.AccountDeletionUseCase;
import gal.furar.people.application.port.in.AccountDeletionView;
import gal.furar.people.domain.AccountDeletionBlocker.Kind;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.InternalCallKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR-0043: os dous endpoints internos que chama Keycloak só responden coa clave do seu propósito
 * (nin a do segundo factor nin o segredo valen) e cun {@code sub} que se pode buscar.
 */
class AccountClosureEndpointsTest {

  static final String SECRET = "furar-demo-invitation-proof-secret-not-for-production";
  static final String KEY = InternalCallKey.derive(SECRET, InternalCallKey.ACCOUNT_CLOSURE).value();
  static final String OTHER_KEY =
      InternalCallKey.derive(SECRET, InternalCallKey.SECOND_FACTOR).value();

  final List<String> closed = new ArrayList<>();
  boolean allowed = true;

  final AccountDeletionUseCase deletion =
      new AccountDeletionUseCase() {
        @Override
        public List<AccountDeletionView> mine() {
          return List.of();
        }

        @Override
        public boolean allowed(String subject) {
          return allowed;
        }
      };

  final AccountClosureUseCase closure =
      subject -> {
        closed.add(subject);
        return 0;
      };

  @Test
  void deletionAnswersOnlyWithItsKey() {
    AccountDeletionEndpoint endpoint = new AccountDeletionEndpoint(deletion, SECRET);
    assertThat(endpoint.allowed(null, "sub").getStatus()).isEqualTo(403);
    assertThat(endpoint.allowed(OTHER_KEY, "sub").getStatus()).isEqualTo(403);
    assertThat(endpoint.allowed(SECRET, "sub").getStatus()).isEqualTo(403);
    assertThat(endpoint.allowed(KEY, " ").getStatus()).isEqualTo(400);
    assertThat(endpoint.allowed(KEY, "sub").getBody().allowed()).isTrue();
    allowed = false;
    assertThat(endpoint.allowed(KEY, "sub").getBody().allowed()).isFalse();
  }

  @Test
  void closedTouchesNothingWithoutItsKey() {
    AccountClosedEndpoint endpoint = new AccountClosedEndpoint(closure, SECRET);
    assertThat(endpoint.closed(OTHER_KEY, "sub").getStatus()).isEqualTo(403);
    assertThat(endpoint.closed(KEY, null).getStatus()).isEqualTo(400);
    assertThat(endpoint.closed(KEY, "furar:booking-retention").getStatus()).isEqualTo(400);
    assertThat(endpoint.closed(KEY, "x".repeat(256)).getStatus()).isEqualTo(400);
    assertThat(closed).isEmpty();
    assertThat(endpoint.closed(KEY, "sub").getBody().closed()).isTrue();
    assertThat(closed).containsExactly("sub");
  }

  @Test
  void theWebGetsTheEventNameInItsLanguage() {
    AccountDeletionView view =
        new AccountDeletionView(
            Kind.EVENT, "naron", "Narón", UUID.randomUUID(), "Carreira", "Carrera", 1);
    AccountDeletionBlockerResponse gl = MyAccountDeletionController.response(view, Language.GL);
    AccountDeletionBlockerResponse es = MyAccountDeletionController.response(view, Language.ES);
    assertThat(gl.eventName()).isEqualTo("Carreira");
    assertThat(es.eventName()).isEqualTo("Carrera");
    assertThat(gl.kind()).isEqualTo(AccountDeletionBlockerResponse.Kind.EVENT);
    assertThat(gl.municipality()).isEqualTo("naron");
  }
}
