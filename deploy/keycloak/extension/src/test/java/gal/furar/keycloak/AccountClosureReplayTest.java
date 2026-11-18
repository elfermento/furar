// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gal.furar.keycloak.AccountClosureReplay.Closed;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

/**
 * ADR-0043: ningunha conta eliminada queda sen avisar. A marca do realm só avanza ata o último aviso
 * que a API recibiu; o que falla repítese na seguinte pasada. O listener só mira as eliminacións
 * que saíron ben.
 */
class AccountClosureReplayTest {

  static final List<Closed> CLOSED =
      List.of(new Closed(100, "a"), new Closed(200, "b"), new Closed(200, "c"), new Closed(300, "d"));

  @Test
  void allDeliveredMovesThePastTheLast() {
    List<String> sent = new ArrayList<>();
    long mark =
        AccountClosureReplay.advance(
            50,
            CLOSED,
            c -> {
              sent.add(c.subject());
              return true;
            });
    assertEquals(301, mark);
    assertEquals(List.of("a", "b", "c", "d"), sent);
  }

  @Test
  void aFailureStopsAndTheNextPassStartsThere() {
    List<String> sent = new ArrayList<>();
    long mark =
        AccountClosureReplay.advance(
            50,
            CLOSED,
            c -> {
              sent.add(c.subject());
              return !c.subject().equals("d");
            });
    assertEquals(201, mark, "d (300) vai na seguinte pasada, sen repetir b nin c");
    assertEquals(List.of("a", "b", "c", "d"), sent);
  }

  @Test
  void aFailureInTheSameMillisecondAsADeliveryKeepsThatMillisecond() {
    long mark = AccountClosureReplay.advance(50, CLOSED, c -> !c.subject().equals("c"));
    assertEquals(200, mark, "b repítese con c, e a API non fai nada a segunda vez");
  }

  @Test
  void theFirstFailingKeepsTheMark() {
    assertEquals(50, AccountClosureReplay.advance(50, CLOSED, c -> false));
    assertEquals(50, AccountClosureReplay.advance(50, List.of(), c -> true));
  }

  @Test
  void aMissingOrBrokenMarkStartsFromTheBeginning() {
    assertEquals(0, AccountClosureReplay.mark(null));
    assertEquals(0, AccountClosureReplay.mark(" "));
    assertEquals(0, AccountClosureReplay.mark("onte"));
    assertEquals(0, AccountClosureReplay.mark("-5"));
    assertEquals(1700000000000L, AccountClosureReplay.mark(" 1700000000000 "));
  }

  @Test
  void onlySuccessfulDeletionsWithAnAccountCount() {
    Event deleted = event(EventType.DELETE_ACCOUNT, "sub-1", null);
    assertEquals(Optional.of("sub-1"), AccountClosureListener.closedAccount(deleted));
    for (Event other :
        Set.of(
            event(EventType.DELETE_ACCOUNT_ERROR, "sub-1", "user_delete_error"),
            event(EventType.DELETE_ACCOUNT, "sub-1", "user_delete_error"),
            event(EventType.LOGIN, "sub-1", null),
            event(EventType.DELETE_ACCOUNT, null, null),
            event(EventType.DELETE_ACCOUNT, " ", null))) {
      assertTrue(AccountClosureListener.closedAccount(other).isEmpty(), other.getType().name());
    }
    assertTrue(AccountClosureListener.closedAccount(null).isEmpty());
  }

  @Test
  void theListenerIsGlobal() {
    AccountClosureListener listener = new AccountClosureListener();
    assertTrue(listener.isGlobal());
    assertEquals("furar-account-closure", listener.getId());
  }

  private static Event event(EventType type, String user, String error) {
    Event event = new Event();
    event.setType(type);
    event.setUserId(user);
    event.setError(error);
    return event;
  }
}
