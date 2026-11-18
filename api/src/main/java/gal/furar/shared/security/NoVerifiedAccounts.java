// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * {@link VerifiedAccounts} sen o módulo {@code people}: non se garda ningunha conta. Non é un bean:
 * úsase cando non hai ningún (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoVerifiedAccounts implements VerifiedAccounts {

  @Override
  public void seen(String subject, String verifiedEmail, @Nullable Instant authTime) {
    // Nada que gardar.
  }
}
