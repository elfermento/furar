// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.DeliveryResult;
import gal.furar.notification.EventDecisionMail;
import gal.furar.notification.EventMailer;
import gal.furar.notification.EventNoticeMail;
import gal.furar.notification.EventSessionMail;
import gal.furar.notification.EventWaitlistMail;
import gal.furar.notification.application.port.out.EventDeliveryLog;
import gal.furar.notification.application.port.out.EventMailComposer;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.EventMailDelivery;
import gal.furar.notification.domain.EventMailDelivery.Kind;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envía os correos dos eventos (ADR-0038) e garda cada resultado nunha transacción curta propia; o
 * SMTP vai sempre fóra de toda transacción. Cada persoa recíbeo no seu idioma. Os rexistros
 * técnicos levan só o identificador do evento, nunca os destinatarios.
 */
@Service
class EventMailService implements EventMailer {

  private static final Logger LOG = LoggerFactory.getLogger(EventMailService.class);

  private final MunicipalityQueries municipalities;
  private final EventDeliveryLog log;
  private final EventMailComposer composer;
  private final MailTransport transport;
  private final Clock clock;

  EventMailService(
      MunicipalityQueries municipalities,
      EventDeliveryLog log,
      EventMailComposer composer,
      MailTransport transport,
      Clock clock) {
    this.municipalities = municipalities;
    this.log = log;
    this.composer = composer;
    this.transport = transport;
    this.clock = clock;
  }

  @Override
  public DeliveryResult sendDecision(EventDecisionMail mail) {
    outsideTransactions();
    Kind kind = mail.accepted() ? Kind.EVENT_ACCEPTED : Kind.EVENT_REJECTED;
    UUID event = mail.event().eventId();
    String key = mail.registrationId().toString();
    if (log.keys(event, kind).contains(key)) {
      LOG.warn("Event {} already has this {} mail: not sent again", event, kind);
      return DeliveryResult.SENT;
    }
    return send(
        event,
        kind,
        key,
        mail.recipient(),
        mail.language(),
        composer.decision(mail, municipality()));
  }

  @Override
  public void sendNotice(EventNoticeMail mail) {
    outsideTransactions();
    if (mail.recipients().isEmpty()) {
      return;
    }
    MunicipalityMailContext municipality = municipality();
    UUID event = mail.event().eventId();
    Kind kind =
        mail.kind() == EventNoticeMail.Kind.MOVED ? Kind.EVENT_CHANGED : Kind.EVENT_CANCELLED;
    Set<String> done = log.keys(event, kind);
    for (EventNoticeMail.Recipient recipient : mail.recipients()) {
      String key = key(mail, recipient);
      if (done.contains(key)) {
        continue;
      }
      send(
          event,
          kind,
          key,
          recipient.email(),
          recipient.language(),
          composer.notice(mail, recipient, municipality));
    }
  }

  @Override
  public DeliveryResult sendWaitlistNotice(EventWaitlistMail mail) {
    outsideTransactions();
    UUID event = mail.event().eventId();
    String key = joined(mail.notifiedAt().toString(), mail.registrationId());
    if (log.keys(event, Kind.EVENT_WAITLIST_NOTIFIED).contains(key)) {
      LOG.warn("Event {} already has this waitlist mail: not sent again", event);
      return DeliveryResult.SENT;
    }
    return send(
        event,
        Kind.EVENT_WAITLIST_NOTIFIED,
        key,
        mail.recipient(),
        mail.language(),
        composer.waitlist(mail, municipality()));
  }

  @Override
  public void sendSessionNotice(EventSessionMail mail) {
    outsideTransactions();
    if (mail.recipients().isEmpty()) {
      return;
    }
    MunicipalityMailContext municipality = municipality();
    UUID event = mail.event().eventId();
    Kind kind =
        mail.kind() == EventSessionMail.Kind.SESSION_CANCELLED
            ? Kind.EVENT_SESSION_CANCELLED
            : Kind.EVENT_ENDED_EARLY;
    Set<String> done = log.keys(event, kind);
    for (EventNoticeMail.Recipient recipient : mail.recipients()) {
      String key = joined(mail.change().toString(), recipient.registrationId());
      if (done.contains(key)) {
        continue;
      }
      send(
          event,
          kind,
          key,
          recipient.email(),
          recipient.language(),
          composer.session(mail, recipient, municipality));
    }
  }

  /** Unha clave de 36 caracteres para dous valores. */
  private static String joined(String first, UUID registration) {
    String both = first + ":" + registration;
    return UUID.nameUUIDFromBytes(both.getBytes(StandardCharsets.UTF_8)).toString();
  }

  /**
   * A clave dun aviso: a inscrición (cancelar só pasa unha vez) ou, nun cambio, unha clave do
   * cambio e a inscrición (cada cambio avisa outra vez), de 36 caracteres.
   */
  static String key(EventNoticeMail mail, EventNoticeMail.Recipient recipient) {
    if (mail.kind() == EventNoticeMail.Kind.CANCELLED) {
      return recipient.registrationId().toString();
    }
    String both = Objects.requireNonNull(mail.change()) + ":" + recipient.registrationId();
    return UUID.nameUUIDFromBytes(both.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private DeliveryResult send(
      UUID event,
      Kind kind,
      String key,
      String recipient,
      Language language,
      OutgoingMail outgoing) {
    try {
      transport.send(outgoing);
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos.
      log.record(
          new EventMailDelivery(
              event, kind, key, recipient, language, DeliveryStatus.FAILED, e.getMessage(), null));
      LOG.warn("{} mail for event {} not sent: {}", kind, event, e.getMessage());
      return DeliveryResult.FAILED;
    }
    log.record(
        new EventMailDelivery(
            event, kind, key, recipient, language, DeliveryStatus.SENT, null, clock.instant()));
    return DeliveryResult.SENT;
  }

  private MunicipalityMailContext municipality() {
    return municipalities
        .mailContext(TenantContext.require().id())
        .orElseThrow(() -> new IllegalStateException("No municipality for the mail"));
  }

  private static void outsideTransactions() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "The event mails are sent after the commit, outside any transaction");
    }
  }
}
