// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.booking.BookingQueries;
import gal.furar.booking.OpenMatchNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.application.port.in.MailNotSentException;
import gal.furar.notification.application.port.in.SendOpenMatchMail;
import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OpenMatchMailComposer;
import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.MailKind;
import gal.furar.notification.domain.OpenMatchDelivery;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Os correos das partidas abertas (ADR-0040, punto 12): pídelle a {@code booking} a quen escribir
 * co estado de agora e escribe a cada persoa por separado, no seu idioma. Non repite: quen xa o
 * recibiu por esta publicación non o recibe outra vez, e a un fallido escríbeselle só unha vez
 * máis. Un envío que falla queda {@code FAILED} e non corta os demais; se a algún lle queda un
 * intento, ao rematar lanza para que o evento se volva entregar. Os rexistros técnicos levan só a
 * partida e o tipo, nunca os destinatarios.
 */
@Service
public class OpenMatchMailService implements SendOpenMatchMail {

  private static final Logger LOG = LoggerFactory.getLogger(OpenMatchMailService.class);

  private final BookingQueries bookings;
  private final MunicipalityQueries municipalities;
  private final DeliveryLog log;
  private final OpenMatchMailComposer composer;
  private final MailTransport transport;
  private final Clock clock;

  public OpenMatchMailService(
      BookingQueries bookings,
      MunicipalityQueries municipalities,
      DeliveryLog log,
      OpenMatchMailComposer composer,
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
  public void send(OpenMatchNotice.Kind kind, UUID matchId, @Nullable UUID playerId, UUID eventId) {
    Optional<OpenMatchNotice> found = bookings.openMatchNotice(matchId, kind, playerId);
    if (found.isEmpty()) {
      return;
    }
    OpenMatchNotice notice = found.get();
    MailKind mailKind = MailKind.valueOf("MATCH_" + kind.name());
    String key = eventId.toString();
    Map<UUID, OpenMatchDelivery> previous = log.openMatchDeliveries(mailKind, key);
    MunicipalityMailContext municipality =
        municipalities
            .mailContext(TenantContext.require().id())
            .orElseThrow(() -> new IllegalStateException("No municipality for the mail"));
    MailTransportException pending = null;
    for (OpenMatchNotice.Recipient recipient : notice.recipients()) {
      UUID target =
          recipient.playerId() != null
              ? recipient.playerId()
              : Objects.requireNonNull(recipient.bookingId(), "bookingId");
      OpenMatchDelivery before = previous.get(target);
      if (before != null && before.settled()) {
        continue;
      }
      int attempt = before == null ? 1 : before.attempts() + 1;
      MailTransportException failure =
          sendOne(notice, recipient, mailKind, key, municipality, attempt);
      if (failure != null && attempt < Delivery.MAX_ATTEMPTS) {
        pending = failure;
      }
    }
    if (pending != null) {
      throw new MailNotSentException("Open match mail not sent; it will be retried", pending);
    }
  }

  /** Envía un correo e rexistra o resultado; devolve o fallo, se o houbo. */
  private @Nullable MailTransportException sendOne(
      OpenMatchNotice notice,
      OpenMatchNotice.Recipient recipient,
      MailKind kind,
      String key,
      MunicipalityMailContext municipality,
      int attempt) {
    Language language = Language.fromCode(recipient.language()).orElse(Language.GL);
    try {
      transport.send(composer.compose(notice, recipient, language, municipality));
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos (MailTransportException).
      log.record(
          new OpenMatchDelivery(
              kind,
              recipient.bookingId(),
              recipient.playerId(),
              key,
              recipient.email(),
              language,
              DeliveryStatus.FAILED,
              attempt,
              e.getMessage(),
              null));
      LOG.warn(
          "{} mail for open match {} not sent (attempt {}): {}",
          kind,
          notice.matchId(),
          attempt,
          e.getMessage());
      return e;
    }
    log.record(
        new OpenMatchDelivery(
            kind,
            recipient.bookingId(),
            recipient.playerId(),
            key,
            recipient.email(),
            language,
            DeliveryStatus.SENT,
            attempt,
            null,
            clock.instant()));
    return null;
  }
}
