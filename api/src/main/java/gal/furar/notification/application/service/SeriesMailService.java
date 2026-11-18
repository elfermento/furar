// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.booking.BookingQueries;
import gal.furar.booking.SeriesNotice;
import gal.furar.booking.SeriesRequestNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.application.port.in.MailNotSentException;
import gal.furar.notification.application.port.in.SendSeriesMail;
import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.application.port.out.SeriesMailComposer;
import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.MailKind;
import gal.furar.notification.domain.SeriesDelivery;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Os correos das tempadas á entidade (ADR-0039, punto 12): pídelle a {@code booking} o estado de
 * agora, escribe no primeiro idioma do concello ao correo da entidade e rexistra o resultado. Unha
 * vez por publicación; a un fallido, só unha vez máis. Os rexistros técnicos levan só a serie ou a
 * solicitude e o tipo, nunca o destinatario.
 */
@Service
public class SeriesMailService implements SendSeriesMail {

  private static final Logger LOG = LoggerFactory.getLogger(SeriesMailService.class);

  /** A creación e o rexeitamento só se escriben unha vez. */
  static final String ONCE = "once";

  private final BookingQueries bookings;
  private final MunicipalityQueries municipalities;
  private final DeliveryLog log;
  private final SeriesMailComposer composer;
  private final MailTransport transport;
  private final Clock clock;

  public SeriesMailService(
      BookingQueries bookings,
      MunicipalityQueries municipalities,
      DeliveryLog log,
      SeriesMailComposer composer,
      MailTransport transport,
      Clock clock) {
    this.bookings = bookings;
    this.municipalities = municipalities;
    this.log = log;
    this.composer = composer;
    this.transport = transport;
    this.clock = clock;
  }

  @Override
  public void created(UUID seriesId) {
    Optional<SeriesNotice> notice = bookings.seriesNotice(seriesId);
    notice.ifPresent(
        n ->
            send(
                MailKind.SEASON_CREATED,
                seriesId,
                ONCE,
                n.email(),
                n.language(),
                m -> composer.series(n, null, 0, language(n.language()), m)));
  }

  @Override
  public void ended(UUID seriesId, LocalDate from, int cancelled, UUID noticeId) {
    Optional<SeriesNotice> notice = bookings.seriesNotice(seriesId);
    notice.ifPresent(
        n ->
            send(
                MailKind.SEASON_ENDED,
                seriesId,
                noticeId.toString(),
                n.email(),
                n.language(),
                m -> composer.series(n, from, cancelled, language(n.language()), m)));
  }

  @Override
  public void rejected(UUID requestId) {
    Optional<SeriesRequestNotice> notice = bookings.seriesRequestNotice(requestId);
    notice.ifPresent(
        n ->
            send(
                MailKind.SEASON_REQUEST_REJECTED,
                requestId,
                ONCE,
                n.email(),
                n.language(),
                m -> composer.rejected(n, language(n.language()), m)));
  }

  private void send(
      MailKind kind,
      UUID target,
      String eventId,
      String recipient,
      String languageCode,
      Function<MunicipalityMailContext, OutgoingMail> compose) {
    Optional<SeriesDelivery> previous = log.seriesDelivery(kind, target, eventId);
    if (previous.map(SeriesDelivery::settled).orElse(false)) {
      return;
    }
    int attempt = previous.map(d -> d.attempts() + 1).orElse(1);
    Language language = language(languageCode);
    MunicipalityMailContext municipality =
        municipalities
            .mailContext(TenantContext.require().id())
            .orElseThrow(() -> new IllegalStateException("No municipality for the mail"));
    try {
      transport.send(compose.apply(municipality));
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos (MailTransportException).
      log.record(
          new SeriesDelivery(
              kind,
              target,
              eventId,
              recipient,
              language,
              DeliveryStatus.FAILED,
              attempt,
              e.getMessage(),
              null));
      LOG.warn("{} mail for {} not sent (attempt {}): {}", kind, target, attempt, e.getMessage());
      if (attempt < Delivery.MAX_ATTEMPTS) {
        throw new MailNotSentException("Season mail not sent; it will be retried", e);
      }
      return;
    }
    log.record(
        new SeriesDelivery(
            kind,
            target,
            eventId,
            recipient,
            language,
            DeliveryStatus.SENT,
            attempt,
            null,
            clock.instant()));
  }

  private static Language language(String code) {
    return Language.fromCode(code).orElse(Language.GL);
  }
}
