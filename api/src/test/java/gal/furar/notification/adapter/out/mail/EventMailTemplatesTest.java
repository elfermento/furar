// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.EventDecisionMail;
import gal.furar.notification.EventMailFacts;
import gal.furar.notification.EventNoticeMail;
import gal.furar.notification.EventSessionMail;
import gal.furar.notification.EventWaitlistMail;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Os correos das actividades con horario e da lista de espera (ADR-0041), en gl e es. */
class EventMailTemplatesTest {

  final EventMailTemplates templates = new EventMailTemplates("https://reservas.example/");
  final UUID event = UUID.randomUUID();

  static MunicipalityMailContext municipality() {
    return new MunicipalityMailContext(
        UUID.randomUUID(),
        "naron",
        "Narón",
        List.of("gl", "es"),
        "gl",
        "#0b4f8a",
        "#ffffff",
        new MunicipalityMailContext.Contact(
            "Servizo Municipal de Deportes", null, "981 000 700", null),
        new MunicipalityMailContext.Text("No mostrador", "En el mostrador"),
        new MunicipalityMailContext.Text("O concello", "El ayuntamiento"));
  }

  EventMailFacts activity() {
    return new EventMailFacts(
        event,
        "Escola de atletismo",
        "Escuela de atletismo",
        "Pista",
        "Pista",
        Instant.parse("2026-12-01T17:00:00Z"),
        new EventMailFacts.Schedule(
            List.of(1, 3),
            1080,
            1140,
            LocalDate.parse("2026-12-01"),
            LocalDate.parse("2027-06-30")));
  }

  static Properties texts(String language) throws IOException {
    Properties p = new Properties();
    try (InputStream in =
        EventMailTemplatesTest.class.getResourceAsStream(
            "/mail/event_" + language + ".properties")) {
      p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    }
    return p;
  }

  @Test
  void bothLanguagesHaveTheSameKeys() throws IOException {
    assertThat(texts("gl").keySet()).isEqualTo(texts("es").keySet());
  }

  @Test
  void theWaitlistNoticeSaysUntilWhenTheSeatIsOnlyYours() {
    EventWaitlistMail mail =
        new EventWaitlistMail(
            activity(),
            UUID.randomUUID(),
            Instant.parse("2026-12-01T20:30:00Z"),
            Instant.parse("2026-12-02T07:30:00Z"),
            "persoa@example.org",
            Language.GL,
            "Ana Souto",
            "Xiana");
    OutgoingMail gl = templates.waitlist(mail, municipality());
    assertThat(gl.subject()).isEqualTo("Hai unha praza libre: Escola de atletismo");
    assertThat(gl.text())
        .contains("luns e mércores, de 18:00 a 19:00, do 1 de decembro ao 30 de xuño")
        .contains("08:30")
        .contains("Xiana")
        .contains("Confirmar a praza")
        .contains("/naron/eventos/" + event);
    assertThat(gl.text()).doesNotContain("{");
    OutgoingMail es =
        templates.waitlist(
            new EventWaitlistMail(
                activity(),
                mail.registrationId(),
                mail.notifiedAt(),
                mail.exclusiveUntil(),
                mail.recipient(),
                Language.ES,
                null,
                null),
            municipality());
    assertThat(es.subject()).isEqualTo("Hay una plaza libre: Escuela de atletismo");
    assertThat(es.text()).contains("lunes y miércoles, de 18:00 a 19:00, del 1 de diciembre");
  }

  @Test
  void aCancelledSessionAndAnEarlyEnd() {
    EventNoticeMail.Recipient ana =
        new EventNoticeMail.Recipient(UUID.randomUUID(), "ana@example.org", Language.GL, "Ana");
    OutgoingMail session =
        templates.session(
            new EventSessionMail(
                activity(),
                EventSessionMail.Kind.SESSION_CANCELLED,
                UUID.randomUUID(),
                LocalDate.parse("2026-12-03"),
                Instant.parse("2026-12-03T17:00:00Z"),
                List.of(ana)),
            ana,
            municipality());
    assertThat(session.subject()).isEqualTo("Cancelamos unha sesión: Escola de atletismo");
    assertThat(session.text()).contains("3 de decembro").contains("18:00");
    OutgoingMail ended =
        templates.session(
            new EventSessionMail(
                activity(),
                EventSessionMail.Kind.ENDED_EARLY,
                UUID.randomUUID(),
                LocalDate.parse("2027-03-01"),
                Instant.parse("2027-02-28T23:00:00Z"),
                List.of(ana)),
            ana,
            municipality());
    assertThat(ended.subject()).isEqualTo("Escola de atletismo remata antes");
    assertThat(ended.text()).contains("desde o 1 de marzo");
  }

  @Test
  void theCancellationAndDecisionOfAnActivityTellTheSchedule() {
    EventNoticeMail.Recipient ana =
        new EventNoticeMail.Recipient(UUID.randomUUID(), "ana@example.org", Language.ES, "Ana");
    OutgoingMail cancelled =
        templates.notice(
            new EventNoticeMail(activity(), EventNoticeMail.Kind.CANCELLED, null, List.of(ana)),
            ana,
            municipality());
    assertThat(cancelled.text()).contains("lunes y miércoles").contains("Ya no habrá más sesiones");
    OutgoingMail accepted =
        templates.decision(
            new EventDecisionMail(
                activity(), UUID.randomUUID(), true, "ana@example.org", Language.GL, "Ana"),
            municipality());
    assertThat(accepted.text()).contains("tes praza en Escola de atletismo (luns e mércores");
  }
}
