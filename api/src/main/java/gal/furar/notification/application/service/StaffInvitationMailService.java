// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.notification.DeliveryResult;
import gal.furar.notification.StaffInvitationMail;
import gal.furar.notification.StaffInvitationMailer;
import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.port.out.InvitationMailComposer;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envía o correo dunha invitación ao persoal (plan do dominio 4, §1.2.6; ADR-0027, punto 6): le o
 * concello, escribe o correo, envíao e garda o resultado nunha transacción curta propia. O SMTP vai
 * sempre fóra de toda transacción, para que un servidor lento non reteña conexións nin bloqueos. Un
 * fallo do SMTP non se lanza: devólvese {@link DeliveryResult#FAILED}. Os rexistros levan só o
 * identificador da invitación, nunca a ligazón (o token) nin o destinatario.
 */
@Service
class StaffInvitationMailService implements StaffInvitationMailer {

  private static final Logger LOG = LoggerFactory.getLogger(StaffInvitationMailService.class);

  private final MunicipalityQueries municipalities;
  private final DeliveryLog log;
  private final InvitationMailComposer composer;
  private final MailTransport transport;
  private final Clock clock;

  StaffInvitationMailService(
      MunicipalityQueries municipalities,
      DeliveryLog log,
      InvitationMailComposer composer,
      MailTransport transport,
      Clock clock) {
    this.municipalities = municipalities;
    this.log = log;
    this.composer = composer;
    this.transport = transport;
    this.clock = clock;
  }

  @Override
  public DeliveryResult send(StaffInvitationMail mail) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "The staff invitation mail is sent after the commit, outside any transaction");
    }
    UUID municipalityId = TenantContext.require().id();
    UUID invitation = mail.invitationId();
    Optional<DeliveryStatus> previous = log.invitationStatus(invitation);
    if (previous.isPresent()) {
      LOG.warn(
          "Staff invitation {} already has a mail ({}): not sent again",
          invitation,
          previous.get());
      return previous.get() == DeliveryStatus.SENT ? DeliveryResult.SENT : DeliveryResult.FAILED;
    }
    MunicipalityMailContext municipality =
        municipalities
            .mailContext(municipalityId)
            .orElseThrow(() -> new IllegalStateException("No municipality for the mail"));
    OutgoingMail outgoing = composer.compose(mail, municipality);
    try {
      transport.send(outgoing);
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos nin a ligazón.
      log.record(
          InvitationMailDelivery.failed(
              invitation, mail.recipient(), mail.language(), e.getMessage()));
      LOG.warn("Staff invitation {} mail not sent: {}", invitation, e.getMessage());
      return DeliveryResult.FAILED;
    }
    log.record(
        InvitationMailDelivery.sent(
            invitation, mail.recipient(), mail.language(), clock.instant()));
    return DeliveryResult.SENT;
  }

  @Override
  public int forget(Collection<UUID> invitationIds) {
    TenantContext.require();
    return log.forgetInvitations(invitationIds);
  }
}
