// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.out;

import gal.furar.people.domain.AccountDeletionBlocker;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A conta en todos os concellos á vez (ADR-0043): o que impide eliminala e a súa limpeza ({@code
 * close_account}). As dúas son funcións da base que corren como o dono, porque a RLS só deixa ver o
 * concello da transacción; só devolven tipos e cifras.
 */
public interface AccountClosures {

  /** O que impide eliminar a conta; baleira se nada. */
  List<AccountDeletionBlocker> blockers(String subject);

  /**
   * Borra e desliga os datos da conta ({@code close_account}).
   *
   * @return cantas filas tocou en cada concello (os concellos sen ningunha non saen)
   */
  Map<UUID, Long> close(String subject);
}
