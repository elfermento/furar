// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.in.GetPrivacyNoticeUseCase;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.security.PublicUseCase;
import java.time.Period;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Información de protección de datos do despregue: pública, como esixe o art. 13 do RGPD. Os prazos
 * das reservas son as mesmas propiedades, cos mesmos valores por defecto, ca a tarefa que as
 * anonimiza ({@code BookingRetentionService}).
 */
@Service
public class PrivacyNoticeService implements GetPrivacyNoticeUseCase {

  private final AuditRetention activityLog;
  private final BookingRetention bookings;

  public PrivacyNoticeService(
      AuditRetention activityLog,
      @Value("${furar.booking.retention:P2Y}") Period bookingRetention,
      @Value("${furar.booking.paid-retention:P5Y}") Period paidBookingRetention,
      @Value("${furar.booking.series-request-retention:P1Y}") Period seriesRequestRetention) {
    this.activityLog = activityLog;
    this.bookings =
        new BookingRetention(bookingRetention, paidBookingRetention, seriesRequestRetention);
  }

  @Override
  @PublicUseCase
  public AuditRetention activityLog() {
    return activityLog;
  }

  @Override
  @PublicUseCase
  public BookingRetention bookingRetention() {
    return bookings;
  }
}
