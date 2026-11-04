// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.shared.audit.AuditRetention;

/**
 * Datos do despregue para a páxina de privacidade (RGPD art. 13, ADR-0017). O resto da cláusula
 * (responsable, DPD, dereitos, encargado) son datos legais de cada concello (revisión do
 * 2026-11-03).
 */
public interface GetPrivacyNoticeUseCase {

  /**
   * Os prazos do rexistro de actividade do despregue (canto se ve e canto se garda), para a
   * primeira capa do persoal e a páxina de privacidade (ADR-0020, revisión do 2026-10-29).
   */
  AuditRetention activityLog();
}
