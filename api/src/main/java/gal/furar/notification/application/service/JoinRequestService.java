// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.notification.application.port.in.SubmitJoinRequestUseCase;
import gal.furar.notification.application.port.out.JoinRequestMailComposer;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.PendingJoinRequests;
import gal.furar.notification.domain.JoinRequest;
import gal.furar.notification.domain.JoinRequestError;
import gal.furar.notification.domain.PendingJoinRequest;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.JoinRequestContact;
import gal.furar.shared.security.PublicUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * «¿Es un concello?» (ADR-0042). Sen {@code FURAR_JOIN_REQUEST_EMAIL}, a función non existe (404).
 * Con ela: valida os campos (os que fallan non contan no límite), conta o intento no límite por IP,
 * descarta en silencio o que enche o campo trampa e envía o correo, no idioma do formulario e fóra
 * de toda transacción. Se o correo sae, non se garda nada. Se non sae, a solicitude gárdase para
 * que {@link JoinRequestRetryService} a reintente e a persoa ve o mesmo ca se saíse (revisión do
 * 2026-11-17); só se tampouco se pode gardar, 503 para que a volva enviar. Os rexistros técnicos
 * non levan ningún dato da solicitude.
 */
@Service
class JoinRequestService implements SubmitJoinRequestUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(JoinRequestService.class);

  private final JoinRequestContact contact;
  private final JoinRequestMailComposer composer;
  private final MailTransport transport;
  private final PendingJoinRequests pending;
  private final Clock clock;
  private final JoinRequestRateLimit limit;
  private final Duration retryEvery;

  JoinRequestService(
      JoinRequestContact contact,
      JoinRequestMailComposer composer,
      MailTransport transport,
      PendingJoinRequests pending,
      Clock clock,
      @Value("${furar.join-request.per-ip-per-day:5}") int perDay,
      @Value("${furar.join-request.retry-every:PT10M}") Duration retryEvery) {
    this.contact = contact;
    this.composer = composer;
    this.transport = transport;
    this.pending = pending;
    this.clock = clock;
    this.limit = new JoinRequestRateLimit(clock, perDay);
    this.retryEvery = retryEvery;
  }

  @Override
  @PublicUseCase
  public void submit(Submission submission) {
    String recipient =
        contact.email().orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    JoinRequest request =
        new JoinRequest(
            submission.municipality(),
            submission.name(),
            submission.role(),
            submission.email(),
            submission.phone(),
            submission.message());
    limit.check(submission.client());
    if (submission.trap() != null && !submission.trap().isBlank()) {
      LOG.info("Join request discarded: the trap field was filled");
      return;
    }
    Instant now = clock.instant();
    try {
      transport.send(composer.compose(request, submission.language(), recipient, now));
      LOG.info("Join request sent");
      return;
    } catch (MailTransportException e) {
      // A mensaxe é só o tipo do fallo e o código SMTP, sen enderezos.
      LOG.warn("Join request not sent, kept for a retry: {}", e.getMessage());
    }
    try {
      pending.save(
          new PendingJoinRequest(Ids.newV7(clock), request, submission.language(), now, 1),
          now.plus(retryEvery));
    } catch (RuntimeException e) {
      // Só o tipo: a mensaxe da base podería levar valores da solicitude.
      LOG.warn("Join request not kept: {}", e.getClass().getName());
      throw new DomainException(JoinRequestError.NOT_SENT);
    }
  }
}
