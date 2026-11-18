// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.notification.application.port.out.JoinRequestMailComposer;
import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.PendingJoinRequests;
import gal.furar.notification.domain.PendingJoinRequest;
import gal.furar.shared.domain.JoinRequestContact;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reintento e conservación das solicitudes de alta cuxo correo non saíu (ADR-0042, revisión do
 * 2026-11-17; docs/datos-persoais.md; RGPD art. 5.1.e). Cada {@code furar.join-request.retry-every}
 * (PT10M):
 *
 * <ol>
 *   <li>borra as que chegaron hai máis de {@code furar.join-request.retention} (P7D) sen enviarse:
 *       despois dunha semana co correo caído, quen opera o servizo ten que arranxalo e a persoa
 *       pode escribir outra vez; gardala máis sería gardar datos sen saber se chegarán;
 *   <li>volve enviar as que tocan, de vinte en vinte: cada unha resérvase antes (con varias
 *       réplicas, só unha a envía), o correo sae fóra de toda transacción e, enviada, bórrase.
 * </ol>
 *
 * Se o despregue xa non ten {@code FURAR_JOIN_REQUEST_EMAIL}, non envía nada e as gardadas caen coa
 * conservación. O rexistro técnico só leva cantas: nin correos nin nomes.
 */
@Service
public class JoinRequestRetryService {

  private static final Logger LOG = LoggerFactory.getLogger(JoinRequestRetryService.class);

  /** Solicitudes que se reintentan en cada volta, como moito. */
  static final int BATCH = 20;

  private final JoinRequestContact contact;
  private final JoinRequestMailComposer composer;
  private final MailTransport transport;
  private final PendingJoinRequests pending;
  private final Clock clock;
  private final Duration retryEvery;
  private final Period retention;

  JoinRequestRetryService(
      JoinRequestContact contact,
      JoinRequestMailComposer composer,
      MailTransport transport,
      PendingJoinRequests pending,
      Clock clock,
      @Value("${furar.join-request.retry-every:PT10M}") Duration retryEvery,
      @Value("${furar.join-request.retention:P7D}") Period retention) {
    this.contact = contact;
    this.composer = composer;
    this.transport = transport;
    this.pending = pending;
    this.clock = clock;
    this.retryEvery = retryEvery;
    this.retention = retention;
  }

  @Scheduled(
      initialDelayString = "${furar.join-request.retry-every:PT10M}",
      fixedDelayString = "${furar.join-request.retry-every:PT10M}")
  void scheduled() {
    apply();
  }

  /** Borra as caducadas e reintenta as que tocan; devolve cantas enviou. */
  public int apply() {
    Instant now = clock.instant();
    Instant cutoff = now.atOffset(ZoneOffset.UTC).minus(retention).toInstant();
    int expired = pending.purgeReceivedBefore(cutoff);
    if (expired > 0) {
      LOG.warn("join request retention deleted={} unsent after {}", expired, retention);
    }
    Optional<String> recipient = contact.email();
    if (recipient.isEmpty()) {
      return 0;
    }
    int sent = 0;
    int failed = 0;
    for (PendingJoinRequest request : pending.due(now, BATCH)) {
      if (!pending.claim(request.id(), now, now.plus(retryEvery))) {
        continue;
      }
      try {
        transport.send(
            composer.compose(
                request.request(), request.language(), recipient.get(), request.receivedAt()));
      } catch (MailTransportException e) {
        failed++;
        continue;
      }
      pending.delete(request.id());
      sent++;
    }
    if (sent + failed > 0) {
      LOG.info("join request retry sent={} failed={}", sent, failed);
    }
    return sent;
  }
}
