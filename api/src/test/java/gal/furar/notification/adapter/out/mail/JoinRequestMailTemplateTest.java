// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.JoinRequest;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * O correo dunha solicitude de alta (ADR-0042): asunto, todos os campos, data e Reply-To, no idioma
 * do formulario.
 */
class JoinRequestMailTemplateTest {

  final JoinRequestMailTemplate template = new JoinRequestMailTemplate();

  @Test
  void theMailCarriesEveryFieldTheDateAndReplyToThePerson() {
    JoinRequest request =
        new JoinRequest(
            "Concello de Exemplo",
            "Ana Rey",
            "Concelleira de deportes",
            "ana@example.org",
            "981 000 000",
            "Temos <seis> instalacións");
    OutgoingMail mail =
        template.compose(
            request, Language.GL, "altas@example.org", Instant.parse("2026-11-17T17:05:00Z"));

    assertThat(mail.to()).isEqualTo("altas@example.org");
    assertThat(mail.replyTo()).isEqualTo("ana@example.org");
    assertThat(mail.subject()).isEqualTo("Solicitude de alta: Concello de Exemplo");
    assertThat(mail.fromName()).isEqualTo("Reservas deportivas");
    assertThat(mail.text())
        .contains("Concello ou entidade: Concello de Exemplo")
        .contains("Nome e apelidos: Ana Rey")
        .contains("Cargo: Concelleira de deportes")
        .contains("Correo: ana@example.org")
        .contains("Teléfono: 981 000 000")
        .contains("Mensaxe: Temos <seis> instalacións")
        // Na hora de Galicia.
        .contains("Data: 17/11/2026 ás 18:05")
        .contains("encargo de tratamento");
    // Os valores, escapados no HTML.
    assertThat(mail.html()).contains("Temos &lt;seis&gt; instalacións").doesNotContain("<seis>");
    assertThat(mail.inline()).isEmpty();
    assertThat(mail.attachments()).isEmpty();
  }

  @Test
  void theOptionalFieldsOnlyAppearIfThereAreAny() {
    JoinRequest request =
        new JoinRequest(
            "Concello de Exemplo", "Ana Rey", null, "ana@example.org", "981000000", null);
    OutgoingMail mail =
        template.compose(
            request, Language.GL, "altas@example.org", Instant.parse("2026-11-17T00:30:00Z"));
    assertThat(mail.text())
        .doesNotContain("Cargo:")
        .doesNotContain("Mensaxe:")
        .contains("Data: 17/11/2026 á 01:30");
  }

  @Test
  void inSpanishIfTheFormWasFilledInSpanish() {
    JoinRequest request =
        new JoinRequest(
            "Concello de Exemplo", "Ana Rey", null, "ana@example.org", "981000000", "Hola");
    OutgoingMail mail =
        template.compose(
            request, Language.ES, "altas@example.org", Instant.parse("2026-11-17T17:05:00Z"));
    assertThat(mail.subject()).isEqualTo("Solicitud de alta: Concello de Exemplo");
    assertThat(mail.replyTo()).isEqualTo("ana@example.org");
    assertThat(mail.text())
        .startsWith("Solicitud de alta de un ayuntamiento")
        .contains("Llegó desde «¿Eres un ayuntamiento?» el 17/11/2026 a las 18:05.")
        .contains("Ayuntamiento o entidad: Concello de Exemplo")
        .contains("Nombre y apellidos: Ana Rey")
        .contains("Mensaje: Hola")
        .contains("Fecha: 17/11/2026 a las 18:05")
        .contains("encargo de tratamiento")
        .doesNotContain("Concello ou entidade");
    assertThat(mail.html()).contains("<html lang=\"es\">");
  }
}
