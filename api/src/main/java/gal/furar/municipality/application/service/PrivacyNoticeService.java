// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.in.GetPrivacyNoticeUseCase;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.security.PublicUseCase;
import org.springframework.stereotype.Service;

/** Información de protección de datos do despregue: pública, como esixe o art. 13 do RGPD. */
@Service
public class PrivacyNoticeService implements GetPrivacyNoticeUseCase {

  private final AuditRetention activityLog;

  public PrivacyNoticeService(AuditRetention activityLog) {
    this.activityLog = activityLog;
  }

  @Override
  @PublicUseCase
  public AuditRetention activityLog() {
    return activityLog;
  }
}
