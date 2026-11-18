// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.booking.BookingQueries;
import gal.furar.booking.CancelledWaitsNotice;
import gal.furar.booking.WaitNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.application.port.in.MailNotSentException;
import gal.furar.notification.application.port.in.SendWaitMail;
import gal.furar.notification.application.port.in.WaitMailCommand;
import gal.furar.notification.application.port.out.CancelledWaitsMailContent;
import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.port.out.MailComposer;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.WaitMailContent;
import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.MailKind;
import gal.furar.notification.domain.WaitDelivery;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * O aviso «Quedou libre» (ADR-0033, punto 7): pídelle a {@code booking} a quen avisar desta
 * liberación (co estado de agora: se a hora xa se colleu, a ninguén) e escribe a cada persoa por
 * separado, todas na mesma pasada. Non repite: quen xa recibiu o aviso desta liberación non o
 * recibe outra vez, e a quen recibiu calquera aviso hai menos de {@code
 * furar.notification.wait-notice-gap} (PT30M por defecto) non se lle escribe (corta o bucle de quen
 * reserva e cancela a mesma hora). Un envío que falla queda {@code FAILED} e non corta os demais;
 * se a algún lle queda un intento, ao rematar lanza para que o evento se volva entregar.
 */
@Service
public class WaitMailService implements SendWaitMail {

  private static final Logger LOG = LoggerFactory.getLogger(WaitMailService.class);

  private final BookingQueries bookings;
  private final MunicipalityQueries municipalities;
  private final DeliveryLog log;
  private final MailComposer composer;
  private final MailTransport transport;
  private final Duration gap;
  private final Clock clock;

  public WaitMailService(
      BookingQueries bookings,
      MunicipalityQueries municipalities,
      DeliveryLog log,
      MailComposer composer,
      MailTransport transport,
      @Value("${furar.notification.wait-notice-gap:PT30M}") Duration gap,
      Clock clock) {
    this.bookings = bookings;
    this.municipalities = municipalities;
    this.log = log;
    this.composer = composer;
    this.transport = transport;
    this.gap = gap;
    this.clock = clock;
  }

  @Override
  public void send(WaitMailCommand command, Turn turn) {
    List<WaitNotice> notices =
        bookings.waitNotices(command.useId(), command.start(), command.releasedAt());
    if (notices.isEmpty()) {
      return;
    }
    List<UUID> ids = notices.stream().map(WaitNotice::waitId).toList();
    Map<UUID, WaitDelivery> previous =
        log.waitDeliveries(MailKind.SLOT_RELEASED, command.releaseId(), ids);
    Set<UUID> recent = log.waitsNoticedSince(ids, clock.instant().minus(gap));
    MunicipalityMailContext municipality =
        municipalities
            .mailContext(TenantContext.require().id())
            .orElseThrow(() -> new IllegalStateException("No municipality for the mail"));
    MailTransportException[] pending = new MailTransportException[1];
    for (WaitNotice notice : notices) {
      WaitDelivery before = previous.get(notice.waitId());
      if ((before != null && before.settled()) || recent.contains(notice.waitId())) {
        continue;
      }
      int attempt = before == null ? 1 : before.attempts() + 1;
      turn.take(
          () -> {
            MailTransportException failure =
                sendOne(command.releaseId(), notice, municipality, attempt);
            if (failure != null && attempt < Delivery.MAX_ATTEMPTS) {
              pending[0] = failure;
            }
          });
    }
    if (pending[0] != null) {
      throw new MailNotSentException("Wait notice not sent; it will be retried", pending[0]);
    }
  }

  @Override
  public void sendCancelled(UUID cancellationId, Turn turn) {
    List<CancelledWaitsNotice> notices = bookings.cancelledWaitNotices(cancellationId);
    if (notices.isEmpty()) {
      return;
    }
    Map<UUID, WaitDelivery> previous =
        log.waitDeliveries(
            MailKind.WAIT_CANCELLED,
            cancellationId,
            notices.stream().map(CancelledWaitsNotice::noticeId).toList());
    MunicipalityMailContext municipality =
        municipalities
            .mailContext(TenantContext.require().id())
            .orElseThrow(() -> new IllegalStateException("No municipality for the mail"));
    MailTransportException[] pending = new MailTransportException[1];
    for (CancelledWaitsNotice notice : notices) {
      WaitDelivery before = previous.get(notice.noticeId());
      if (before != null && before.settled()) {
        continue;
      }
      int attempt = before == null ? 1 : before.attempts() + 1;
      turn.take(
          () -> {
            MailTransportException failure =
                sendCancelledOne(cancellationId, notice, municipality, attempt);
            if (failure != null && attempt < Delivery.MAX_ATTEMPTS) {
              pending[0] = failure;
            }
          });
    }
    if (pending[0] != null) {
      throw new MailNotSentException(
          "Cancelled wait mail not sent; it will be retried", pending[0]);
    }
  }

  private @Nullable MailTransportException sendCancelledOne(
      UUID cancellationId,
      CancelledWaitsNotice notice,
      MunicipalityMailContext municipality,
      int attempt) {
    Language language = Language.fromCode(notice.language()).orElse(Language.GL);
    try {
      transport.send(
          composer.compose(new CancelledWaitsMailContent(language, notice, municipality)));
    } catch (MailTransportException e) {
      log.record(
          new WaitDelivery(
              MailKind.WAIT_CANCELLED,
              notice.noticeId(),
              cancellationId,
              notice.email(),
              language,
              DeliveryStatus.FAILED,
              attempt,
              e.getMessage(),
              null));
      LOG.warn(
          "WAIT_CANCELLED mail for cancellation {} not sent (attempt {}): {}",
          cancellationId,
          attempt,
          e.getMessage());
      return e;
    }
    log.record(
        new WaitDelivery(
            MailKind.WAIT_CANCELLED,
            notice.noticeId(),
            cancellationId,
            notice.email(),
            language,
            DeliveryStatus.SENT,
            attempt,
            null,
            clock.instant()));
    return null;
  }

  /** Envía un aviso e rexistra o resultado; devolve o fallo, se o houbo. */
  private @Nullable MailTransportException sendOne(
      UUID releaseId, WaitNotice notice, MunicipalityMailContext municipality, int attempt) {
    Language language = Language.fromCode(notice.language()).orElse(Language.GL);
    try {
      transport.send(composer.compose(new WaitMailContent(language, notice, municipality)));
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos (MailTransportException).
      log.record(
          new WaitDelivery(
              MailKind.SLOT_RELEASED,
              notice.waitId(),
              releaseId,
              notice.email(),
              language,
              DeliveryStatus.FAILED,
              attempt,
              e.getMessage(),
              null));
      LOG.warn(
          "SLOT_RELEASED mail for release {} not sent (attempt {}): {}",
          releaseId,
          attempt,
          e.getMessage());
      return e;
    }
    Instant now = clock.instant();
    log.record(
        new WaitDelivery(
            MailKind.SLOT_RELEASED,
            notice.waitId(),
            releaseId,
            notice.email(),
            language,
            DeliveryStatus.SENT,
            attempt,
            null,
            now));
    return null;
  }
}
