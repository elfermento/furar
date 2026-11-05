// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.BookingQueries;
import gal.furar.facility.FacilityPaymentTexts;
import gal.furar.facility.FacilityQueries;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.application.port.in.BookingMailCommand;
import gal.furar.notification.application.port.in.MailNotSentException;
import gal.furar.notification.application.port.in.SendBookingMail;
import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.port.out.MailComposer;
import gal.furar.notification.application.port.out.MailContent;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.MailRequest;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envía o correo dunha reserva unha soa vez (ADR-0025, punto 15): le a reserva e o concello, mira
 * no rexistro se xa se enviou, escribe o correo no idioma da reserva e envíao. Un fallo queda
 * rexistrado como {@code FAILED}; se queda o reintento, lanza para que o rexistro de publicacións o
 * volva intentar; se non, dáse por pechado. A reserva nunca depende disto: xa está gardada.
 */
@Service
public class BookingMailService implements SendBookingMail {

  private static final Logger LOG = LoggerFactory.getLogger(BookingMailService.class);

  private final BookingQueries bookings;
  private final MunicipalityQueries municipalities;
  private final FacilityQueries facilities;
  private final DeliveryLog log;
  private final MailComposer composer;
  private final MailTransport transport;
  private final Clock clock;

  public BookingMailService(
      BookingQueries bookings,
      MunicipalityQueries municipalities,
      FacilityQueries facilities,
      DeliveryLog log,
      MailComposer composer,
      MailTransport transport,
      Clock clock) {
    this.bookings = bookings;
    this.municipalities = municipalities;
    this.facilities = facilities;
    this.log = log;
    this.composer = composer;
    this.transport = transport;
    this.clock = clock;
  }

  @Override
  public void send(BookingMailCommand command) {
    Optional<Delivery> previous = log.find(command.bookingId(), command.kind(), command.eventId());
    if (previous.map(Delivery::settled).orElse(false)) {
      return;
    }
    Optional<BookingNotice> found = bookings.notice(command.bookingId());
    if (found.isEmpty()) {
      LOG.info(
          "Booking {} not found or without email: no {} mail", command.bookingId(), command.kind());
      return;
    }
    BookingNotice booking = found.get();
    MunicipalityMailContext municipality =
        notes(
            booking,
            municipalities
                .mailContext(TenantContext.require().id())
                .orElseThrow(() -> new IllegalStateException("No municipality for the mail")));
    Language language = Language.fromCode(booking.holderLanguage()).orElse(Language.GL);
    MailRequest request =
        new MailRequest(
            booking.id(), command.kind(), command.eventId(), booking.holderEmail(), language);
    int attempt = previous.map(d -> d.attempts() + 1).orElse(1);
    Instant now = clock.instant();
    try {
      transport.send(
          composer.compose(
              new MailContent(
                  command.kind(),
                  language,
                  booking,
                  municipality,
                  command.previousStart(),
                  command.previousEnd(),
                  now)));
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos (MailTransportException).
      log.record(Delivery.failed(request, attempt, e.getMessage()));
      LOG.warn(
          "{} mail for booking {} not sent (attempt {}): {}",
          command.kind(),
          booking.id(),
          attempt,
          e.getMessage());
      if (attempt < Delivery.MAX_ATTEMPTS) {
        throw new MailNotSentException("Mail not sent; it will be retried", e);
      }
      return;
    }
    log.record(Delivery.sent(request, attempt, now));
  }

  /**
   * Pago e devolución «Distinto por instalación» (D2-11): os textos da instalación da reserva; sen
   * texto, os de Furar (que xa trae o contexto do concello nese caso), nunca os do concello.
   */
  private MunicipalityMailContext notes(BookingNotice booking, MunicipalityMailContext m) {
    if (!m.paymentNotesByFacility()) {
      return m;
    }
    return facilities
        .paymentTexts(booking.facilityId())
        .map(t -> m.withNotes(text(t.payment()), text(t.refund())))
        .orElse(m);
  }

  private static MunicipalityMailContext.@Nullable Text text(
      FacilityPaymentTexts.@Nullable Text t) {
    return t == null ? null : new MunicipalityMailContext.Text(t.gl(), t.es());
  }
}
