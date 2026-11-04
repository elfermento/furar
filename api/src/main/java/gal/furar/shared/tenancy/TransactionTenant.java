// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Concello con que comezou cada transacción aberta do fío (ADR-0006). O enche {@link
 * TenantJpaTransactionManager}; o le {@link TenantContext} para impedir cambiar de concello dentro
 * dunha transacción, onde a RLS seguiría co concello do comezo. Unha transacción xa confirmada ou
 * desfeita (fase {@code afterCommit}) xa non conta: o que se faga aí abre transacción nova.
 */
final class TransactionTenant {

  /** Estado do fío ao comezar a transacción; {@code done} tras o commit ou rollback. */
  private static final class Entry {
    final Object state;
    boolean done;

    Entry(Object state) {
      this.state = state;
    }
  }

  private static final ThreadLocal<Deque<Entry>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

  private TransactionTenant() {}

  static void begin(Object state) {
    STACK.get().push(new Entry(state));
  }

  static void complete() {
    Entry top = STACK.get().peek();
    if (top != null) {
      top.done = true;
    }
  }

  static void end() {
    Deque<Entry> stack = STACK.get();
    stack.poll();
    if (stack.isEmpty()) {
      STACK.remove();
    }
  }

  /** Estado do concello da transacción en curso, se a hai e aínda non rematou. */
  static Optional<Object> active() {
    Entry top = STACK.get().peek();
    return top == null || top.done ? Optional.empty() : Optional.of(top.state);
  }
}
