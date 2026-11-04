// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.booking.StaffAccounts;
import gal.furar.staff.application.port.out.StaffAccountLookup;
import org.springframework.stereotype.Service;

/**
 * A SPI {@link StaffAccounts} de {@code booking} (ADR-0027, revisión do 2026-10-30): unha conta con
 * pertenza activa en calquera concello é de traballo e non reserva como veciña. Non é un caso de
 * uso: chámaa a reserva, que xa comprobou a sesión.
 */
@Service
class StaffAccountsService implements StaffAccounts {

  private final StaffAccountLookup lookup;

  StaffAccountsService(StaffAccountLookup lookup) {
    this.lookup = lookup;
  }

  @Override
  public boolean isStaffAccount(String subject) {
    return lookup.isActiveAnywhere(subject);
  }
}
