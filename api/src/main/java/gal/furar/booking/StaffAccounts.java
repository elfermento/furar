// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

/**
 * SPI cara a {@code staff} (ADR-0027, revisión do 2026-10-30; especificación, bloque 7, P1): se
 * unha conta é de traballo, para que non reserve como veciña. Implementaa {@code staff}, que xa
 * depende de {@code booking} ({@link BookingQueries#hasResidentBookings}); {@code booking} non pode
 * depender de {@code staff}, porque {@code staff → notification → booking} faría un ciclo
 * (ADR-0005: a dependencia invértese cun porto). Un só bean; sen ningún (probas dun módulo illado),
 * {@link NoStaffAccounts}.
 */
public interface StaffAccounts {

  /**
   * Se a persoa ten unha pertenza activa ao persoal de calquera concello: unha conta de traballo
   * non reserva como veciña en ningún, para que o traballo e a vida privada vaian en contas
   * distintas. Unha pertenza invitada ou desactivada non conta.
   *
   * @param subject {@code sub} do token
   */
  boolean isStaffAccount(String subject);
}
