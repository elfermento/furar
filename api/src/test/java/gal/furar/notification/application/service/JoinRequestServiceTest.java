// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.notification.application.port.in.SubmitJoinRequestUseCase.Submission;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.JoinRequest;
import gal.furar.notification.domain.JoinRequestRateLimitedException;
import gal.furar.notification.domain.PendingJoinRequest;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.JoinRequestContact;
import gal.furar.shared.domain.Language;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * «¿Es un concello?» (ADR-0042): sen a función, 404; os campos que non valen non contan no límite;
 * o campo trampa descarta en silencio; o correo vai a quen opera o servizo, no idioma do
 * formulario; un SMTP caído garda a solicitude para reintentala e só é 503 se tampouco se garda.
 */
class JoinRequestServiceTest {

  static final Instant NOW = Instant.parse("2026-11-17T17:05:00Z");

  final List<OutgoingMail> sent = new ArrayList<>();
  final List<JoinRequest> composed = new ArrayList<>();
  final List<Language> languages = new ArrayList<>();
  final InMemoryPendingJoinRequests pending = new InMemoryPendingJoinRequests();
  boolean smtpDown;

  JoinRequestService service(String email, int perDay) {
    return new JoinRequestService(
        JoinRequestContact.parse(email),
        (request, language, recipient, at) -> {
          composed.add(request);
          languages.add(language);
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
        perDay,
        Duration.ofMinutes(10));
  }

  static Submission submission(String client) {
    return submission(client, null);
  }

  static Submission submission(String client, String trap) {
    return new Submission(
        "Concello de Exemplo",
        "Ana Rey",
        "Concelleira",
        "ana@example.org",
        "981 000 000",
        null,
        trap,
        Language.ES,
        client);
  }

  @Test
  void theRequestGoesByMailToTheOperatorWithReplyToThePerson() {
    service("altas@example.org", 3).submit(submission("203.0.113.1"));
    assertThat(sent).hasSize(1);
    assertThat(sent.getFirst().to()).isEqualTo("altas@example.org");
    assertThat(sent.getFirst().replyTo()).isEqualTo("ana@example.org");
    assertThat(sent.getFirst().subject()).isEqualTo("Solicitude de alta: Concello de Exemplo");
    // No idioma no que se encheu o formulario; enviada, non se garda nada.
    assertThat(languages).containsExactly(Language.ES);
    assertThat(pending.rows).isEmpty();
  }

  @Test
  void withoutTheFeatureItIsNotFoundAndNothingIsSent() {
    DomainException e =
        catchThrowableOfType(
            DomainException.class, () -> service("", 3).submit(submission("203.0.113.1")));
    assertThat(e.errorCode().code()).isEqualTo("common.not-found");
    assertThat(sent).isEmpty();
  }

  @Test
  void invalidFieldsAreA400AndDoNotCountForTheLimit() {
    JoinRequestService service = service("altas@example.org", 1);
    Submission invalid =
        new Submission(
            "Concello", "Ana", null, "ana@example", "981", null, null, Language.GL, "203.0.113.2");
    DomainException e = catchThrowableOfType(DomainException.class, () -> service.submit(invalid));
    assertThat(e.errorCode().code()).isEqualTo("validation.failed");
    service.submit(submission("203.0.113.2"));
    assertThat(sent).hasSize(1);
  }

  @Test
  void theTrapFieldDiscardsTheRequestSilentlyButCountsForTheLimit() {
    JoinRequestService service = service("altas@example.org", 1);
    service.submit(submission("203.0.113.3", "https://spam.example.org"));
    assertThat(sent).isEmpty();
    assertThat(composed).isEmpty();
    catchThrowableOfType(
        JoinRequestRateLimitedException.class, () -> service.submit(submission("203.0.113.3")));
    assertThat(sent).isEmpty();
  }

  @Test
  void afterTheLimitTheSameAddressIsRefusedWithA429() {
    JoinRequestService service = service("altas@example.org", 2);
    service.submit(submission("203.0.113.4"));
    service.submit(submission("203.0.113.4"));
    JoinRequestRateLimitedException e =
        catchThrowableOfType(
            JoinRequestRateLimitedException.class, () -> service.submit(submission("203.0.113.4")));
    assertThat(e.errorCode().status()).isEqualTo(429);
    // Ata a medianoite de Galicia: 18:05 en Madrid, faltan 5 h 55 min.
    assertThat(e.retryAfterSeconds()).isEqualTo((5 * 60 + 55) * 60);
    assertThat(sent).hasSize(2);
    service.submit(submission("203.0.113.5"));
    assertThat(sent).hasSize(3);
  }

  @Test
  void aDownMailServerKeepsTheRequestForARetryAndThePersonSeesItAsReceived() {
    smtpDown = true;
    service("altas@example.org", 3).submit(submission("203.0.113.6"));
    assertThat(sent).isEmpty();
    assertThat(pending.rows).hasSize(1);
    PendingJoinRequest kept = pending.rows.values().iterator().next();
    assertThat(kept.request().email()).isEqualTo("ana@example.org");
    assertThat(kept.language()).isEqualTo(Language.ES);
    assertThat(kept.receivedAt()).isEqualTo(NOW);
    assertThat(kept.attempts()).isEqualTo(1);
    assertThat(pending.next.get(kept.id())).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
  }

  @Test
  void ifTheRequestCannotBeKeptEitherItIsA503SoThePersonSendsItAgain() {
    smtpDown = true;
    pending.broken = true;
    DomainException e =
        catchThrowableOfType(
            DomainException.class,
            () -> service("altas@example.org", 3).submit(submission("203.0.113.7")));
    assertThat(e.errorCode().code()).isEqualTo("notification.join-request-not-sent");
    assertThat(e.errorCode().status()).isEqualTo(503);
  }
}
