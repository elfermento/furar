// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

/**
 * {@link StaffAccounts} sen o módulo {@code staff}: ningunha conta é de traballo. Non é un bean:
 * úsase cando non hai ningún (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoStaffAccounts implements StaffAccounts {

  @Override
  public boolean isStaffAccount(String subject) {
    return false;
  }
}
