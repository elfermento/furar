// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.JoinRequest;
import gal.furar.notification.domain.PendingJoinRequest;
import gal.furar.shared.domain.JoinRequestContact;
import gal.furar.shared.domain.Language;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reintento das solicitudes de alta cuxo correo non saíu (ADR-0042, revisión do 2026-11-17): a que
 * toca sae no seu idioma e coa data na que chegou e bórrase; a que volve fallar queda para a volta
 * seguinte; a que aínda non toca, espera; e pasados sete días bórrase sen enviar.
 */
class JoinRequestRetryServiceTest {

  static final Instant NOW = Instant.parse("2026-11-17T17:05:00Z");

  final InMemoryPendingJoinRequests pending = new InMemoryPendingJoinRequests();
  final List<OutgoingMail> sent = new ArrayList<>();
  final List<Language> languages = new ArrayList<>();
  final List<Instant> dates = new ArrayList<>();
  boolean smtpDown;

  JoinRequestRetryService service(String email) {
    return new JoinRequestRetryService(
        JoinRequestContact.parse(email),
        (request, language, recipient, at) -> {
          languages.add(language);
          dates.add(at);
          return new OutgoingMail(
              "Reservas deportivas",
              recipient,
              "Solicitude de alta: " + request.municipality(),
              "<p>html</p>",
              "text",
              List.of(),
              List.of(),
              request.email());
        },
        mail -> {
          if (smtpDown) {
            throw new MailTransportException("MailSendException", null);
          }
          sent.add(mail);
        },
        pending,
        Clock.fixed(NOW, ZoneOffset.UTC),
        Duration.ofMinutes(10),
        Period.ofDays(7));
  }

  UUID keep(Instant receivedAt, Instant next) {
    UUID id = UUID.randomUUID();
    pending.save(
        new PendingJoinRequest(
            id,
            new JoinRequest(
                "Concello de Exemplo", "Ana Rey", null, "ana@example.org", "981 000 000", null),
            Language.ES,
            receivedAt,
            1),
        next);
    return id;
  }

  @Test
  void aDueRequestIsSentInItsLanguageWithItsDateAndThenDeleted() {
    Instant received = NOW.minus(Duration.ofMinutes(15));
    keep(received, NOW.minus(Duration.ofMinutes(5)));
    assertThat(service("altas@example.org").apply()).isEqualTo(1);
    assertThat(sent).hasSize(1);
    assertThat(sent.getFirst().to()).isEqualTo("altas@example.org");
    assertThat(sent.getFirst().replyTo()).isEqualTo("ana@example.org");
    assertThat(languages).containsExactly(Language.ES);
    assertThat(dates).containsExactly(received);
    assertThat(pending.rows).isEmpty();
  }

  @Test
  void aRequestThatFailsAgainWaitsForTheNextRound() {
    UUID id = keep(NOW.minus(Duration.ofMinutes(15)), NOW);
    smtpDown = true;
    assertThat(service("altas@example.org").apply()).isZero();
    assertThat(pending.rows.get(id).attempts()).isEqualTo(2);
    assertThat(pending.next.get(id)).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
  }

  @Test
  void aRequestThatIsNotDueYetWaits() {
    UUID id = keep(NOW, NOW.plus(Duration.ofMinutes(10)));
    assertThat(service("altas@example.org").apply()).isZero();
    assertThat(sent).isEmpty();
    assertThat(pending.rows).containsKey(id);
  }

  @Test
  void afterSevenDaysUnsentItIsDeleted() {
    keep(NOW.minus(Duration.ofDays(7)).minusSeconds(1), NOW);
    UUID recent = keep(NOW.minus(Duration.ofDays(6)), NOW);
    smtpDown = true;
    service("altas@example.org").apply();
    assertThat(pending.rows.keySet()).containsExactly(recent);
  }

  @Test
  void withoutTheFeatureNothingIsSentAndTheRetentionStillApplies() {
    keep(NOW.minus(Duration.ofDays(8)), NOW);
    UUID recent = keep(NOW.minus(Duration.ofMinutes(15)), NOW);
    assertThat(service("").apply()).isZero();
    assertThat(sent).isEmpty();
    assertThat(pending.rows.keySet()).containsExactly(recent);
  }
}
