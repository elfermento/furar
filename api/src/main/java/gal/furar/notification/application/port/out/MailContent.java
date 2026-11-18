// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.booking.BookingNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.in.NoShowFacts;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Todo o que conta un correo: a reserva, o concello, o tipo, o idioma e, ao cambiar, o oco de
 * antes. {@code now} decide se aínda se pode cancelar de balde.
 *
 * @param optOutToken só no recordatorio: a proba asinada da ligazón «Non quero recordatorios»
 *     (ADR-0035)
 */
public record MailContent(
    MailKind kind,
    Language language,
    BookingNotice booking,
    MunicipalityMailContext municipality,
    @Nullable Instant previousStart,
    @Nullable Instant previousEnd,
    Instant now,
    @Nullable NoShowFacts noShow,
    @Nullable String optOutToken) {

  /** Un correo que non é o recordatorio. */
  public MailContent(
      MailKind kind,
      Language language,
      BookingNotice booking,
      MunicipalityMailContext municipality,
      @Nullable Instant previousStart,
      @Nullable Instant previousEnd,
      Instant now,
      @Nullable NoShowFacts noShow) {
    this(kind, language, booking, municipality, previousStart, previousEnd, now, noShow, null);
  }

  /** Un correo que non é o dunha falta. */
  public MailContent(
      MailKind kind,
      Language language,
      BookingNotice booking,
      MunicipalityMailContext municipality,
      @Nullable Instant previousStart,
      @Nullable Instant previousEnd,
      Instant now) {
    this(kind, language, booking, municipality, previousStart, previousEnd, now, null, null);
  }

  public MailContent {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(booking, "booking");
    Objects.requireNonNull(municipality, "municipality");
    Objects.requireNonNull(now, "now");
    if ((kind == MailKind.REMINDER) != (optOutToken != null)) {
      throw new IllegalArgumentException("Only a reminder has the opt-out link");
    }
  }
}
