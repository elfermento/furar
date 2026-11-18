// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

/**
 * {@link PersonArrival} sen ningún módulo que reaccione. Non é un bean: úsase cando non hai ningún
 * (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoPersonArrival implements PersonArrival {

  @Override
  public void arrived(String subject, String verifiedEmail) {
    // Nada que ligar.
  }
}
