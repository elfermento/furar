// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.DeliveryResult;
import gal.furar.notification.IncidentMailer;
import gal.furar.notification.IncidentReportedMail;
import gal.furar.notification.IncidentResolvedMail;
import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.port.out.IncidentMailComposer;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.IncidentMailDelivery;
import gal.furar.notification.domain.IncidentMailDelivery.Kind;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envía os correos das incidencias (ADR-0036) e garda cada resultado nunha transacción curta
 * propia; o SMTP vai sempre fóra de toda transacción. «Nova incidencia» vai no idioma principal do
 * concello (o persoal non ten idioma propio); «Resolvemos o teu aviso», no da persoa. Os rexistros
 * técnicos levan só o identificador da incidencia, nunca os destinatarios.
 */
@Service
class IncidentMailService implements IncidentMailer {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentMailService.class);

  private final MunicipalityQueries municipalities;
  private final DeliveryLog log;
  private final IncidentMailComposer composer;
  private final MailTransport transport;
  private final Clock clock;

  IncidentMailService(
      MunicipalityQueries municipalities,
      DeliveryLog log,
      IncidentMailComposer composer,
      MailTransport transport,
      Clock clock) {
    this.municipalities = municipalities;
    this.log = log;
    this.composer = composer;
    this.transport = transport;
    this.clock = clock;
  }

  @Override
  public void sendReported(IncidentReportedMail mail) {
    outsideTransactions();
    if (mail.recipients().isEmpty()) {
      return;
    }
    MunicipalityMailContext municipality = municipality();
    Language language = Language.fromCode(municipality.defaultLanguage()).orElse(Language.GL);
    Set<String> done = log.incidentKeys(mail.incidentId(), Kind.INCIDENT_REPORTED);
    for (IncidentReportedMail.Recipient recipient : mail.recipients()) {
      String key = recipient.key().toString();
      if (done.contains(key)) {
        continue;
      }
      send(
          mail.incidentId(),
          Kind.INCIDENT_REPORTED,
          key,
          recipient.email(),
          language,
          composer.reported(mail, recipient.email(), language, municipality));
    }
  }

  @Override
  public DeliveryResult sendResolved(IncidentResolvedMail mail) {
    outsideTransactions();
    if (log.incidentKeys(mail.incidentId(), Kind.INCIDENT_RESOLVED)
        .contains(IncidentMailDelivery.ONCE)) {
      LOG.warn("Incident {} already has its resolution mail: not sent again", mail.incidentId());
      return DeliveryResult.SENT;
    }
    return send(
        mail.incidentId(),
        Kind.INCIDENT_RESOLVED,
        IncidentMailDelivery.ONCE,
        mail.recipient(),
        mail.language(),
        composer.resolved(mail, municipality()));
  }

  @Override
  public int forget(Collection<UUID> incidentIds) {
    TenantContext.require();
    return log.forgetIncidents(incidentIds);
  }

  private DeliveryResult send(
      UUID incident,
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
          new IncidentMailDelivery(
              incident,
              kind,
              key,
              recipient,
              language,
              DeliveryStatus.FAILED,
              e.getMessage(),
              null));
      LOG.warn("{} mail for incident {} not sent: {}", kind, incident, e.getMessage());
      return DeliveryResult.FAILED;
    }
    log.record(
        new IncidentMailDelivery(
            incident, kind, key, recipient, language, DeliveryStatus.SENT, null, clock.instant()));
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
          "The incident mails are sent after the commit, outside any transaction");
    }
  }
}
