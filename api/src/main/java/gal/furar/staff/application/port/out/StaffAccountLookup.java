// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

/**
 * Se unha conta é de traballo en calquera concello (ADR-0027, revisión do 2026-10-30): a única
 * lectura das pertenzas fóra do concello actual, e só di si ou non.
 */
public interface StaffAccountLookup {

  /**
   * @param subject {@code sub} da persoa
   * @return se ten unha pertenza activa nalgún concello
   */
  boolean isActiveAnywhere(String subject);
}
