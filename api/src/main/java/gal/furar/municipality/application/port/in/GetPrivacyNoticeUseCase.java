// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.shared.audit.AuditRetention;
import java.time.Period;

/**
 * Datos do despregue para a páxina de privacidade (RGPD art. 13, ADR-0017): os prazos de
 * conservación que fixa a configuración. O resto da cláusula (responsable, DPD, dereitos,
 * encargado) son datos legais de cada concello (revisión do 2026-11-03).
 */
public interface GetPrivacyNoticeUseCase {

  /**
   * Os prazos do rexistro de actividade do despregue (canto se ve e canto se garda), para a
   * primeira capa do persoal e a páxina de privacidade (ADR-0020, revisión do 2026-10-29).
   */
  AuditRetention activityLog();

  /**
   * Canto se gardan as reservas antes de anonimizalas ({@code furar.booking.retention}) e as que
   * teñen prezo ({@code furar.booking.paid-retention}): a privacidade di o prazo real, non un fixo
   * (revisión do 2026-11-09).
   */
  BookingRetention bookingRetention();

  /**
   * Prazos de conservación das reservas (as partidas abertas van coa súa reserva, ADR-0040) e das
   * solicitudes de tempada decididas ({@code furar.booking.series-request-retention}, ADR-0039).
   */
  record BookingRetention(Period retention, Period paidRetention, Period seriesRequestRetention) {}
}
