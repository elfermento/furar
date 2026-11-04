// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.booking.BookingNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Todo o que conta un correo: a reserva, o concello, o tipo, o idioma e, ao cambiar, o oco de
 * antes. {@code now} decide se aínda se pode cancelar de balde.
 */
public record MailContent(
    MailKind kind,
    Language language,
    BookingNotice booking,
    MunicipalityMailContext municipality,
    @Nullable Instant previousStart,
    @Nullable Instant previousEnd,
    Instant now) {

  public MailContent {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(booking, "booking");
    Objects.requireNonNull(municipality, "municipality");
    Objects.requireNonNull(now, "now");
  }
}
