// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.StaffInvitationMail;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O correo da invitación ao persoal (plan do dominio 4, D4-06): «Crea a túa conta», quen invita, a
 * que concello (coa contracción galega do artigo) e para que rol, o botón coa ligazón e o seu
 * fragmento, o correo que hai que usar coa caducidade exacta en hora de Galicia e «se non o
 * esperabas, ignórao»; en gl e es sen ningún oco nin texto sen traducir, todo valor escapado e sen
 * «darse de baixa».
 */
class StaffInvitationMailComposerTest {

  /** Venres 6 de novembro de 2026, 18:30 en Galicia (UTC+1). */
  static final Instant EXPIRES = Instant.parse("2026-11-06T17:30:00Z");

  /** Token de proba co formato dun real (43 caracteres base64url), claramente falso. */
  static final String TOKEN = "test-invitation-token" + "0".repeat(22);

  static final URI LINK = URI.create("https://reservas.example/naron/convite#" + TOKEN);

  final StaffInvitationMailComposer composer =
      new StaffInvitationMailComposer("https://reservas.example/");

  static StaffInvitationMail mail(Language language, String role, String inviter) {
    return new StaffInvitationMail(
        UUID.fromString("019a0b6e-0000-7000-8000-00000000c001"),
        "conserxe@naron.example",
        language,
        role,
        inviter,
        LINK,
        EXPIRES);
  }

  OutgoingMail compose(StaffInvitationMail mail) {
    return composer.compose(mail, BookingMailComposerTest.municipality("Narón"));
  }

  @Test
  void theInvitationInGalicianTellsWhoWhereWhatAndUntilWhen() {
    OutgoingMail mail = compose(mail(Language.GL, "MANAGER", "Ana Rey"));

    assertThat(mail.subject())
        .isEqualTo("Ana Rey invítate a xestionar as instalacións deportivas do Concello de Narón");
    assertThat(mail.fromName()).isEqualTo("Deportes Narón");
    assertThat(mail.to()).isEqualTo("conserxe@naron.example");
    assertThat(mail.html())
        .startsWith("<!doctype html>")
        .contains("<html lang=\"gl\">")
        .contains(">Crea a túa conta</p>")
        .contains(
            "Ana Rey invítate a rexistrarte co rol Xestión para xestionar as instalacións deportivas"
                + " e as súas reservas do Concello de Narón.")
        .doesNotContain(">Rol</td>", ">Caduca</td>", "institucional", "Narón en Furar")
        .contains(
            "Usa este mesmo correo (conserxe@naron.example). A ligazón serve unha vez e caduca o"
                + " venres 6 de novembro ás 18:30 (hora de Galicia).")
        .contains("href=\"" + LINK + "\"", ">Crear a miña conta</a>")
        .as("os textos curtos van centrados (maqueta 51)")
        .contains("text-align:center;\">Ana Rey invítate", "text-align:center;\">Usa este mesmo")
        .contains("text-align:center;\">Non funciona o botón?", "text-align:center;\">Se non o")
        .contains("Non funciona o botón? Abre esta ligazón:")
        .contains("Se non o esperabas, ignora este correo.")
        .contains("mailto:deportes@naron.example")
        .doesNotContain("Narón · Servizo Municipal de Deportes")
        .contains(
            "O Concello de Narón trata os teus datos para xestionar a túa conta de traballo."
                + " <a href=\"https://reservas.example/naron/privacidade#persoal\"",
            ">Privacidade</a>")
        .contains(
            "Recibes este correo porque te invitaron á plataforma de xestión das instalacións"
                + " deportivas do Concello de Narón.")
        .contains("bgcolor=\"#0b4f8a\"", "src=\"cid:shield\"")
        .doesNotContain("SPDX", "{{", "}}", "<svg", "<script", "fonts.googleapis", "@font-face")
        .doesNotContainIgnoringCase("darse de baixa")
        .doesNotContainIgnoringCase("unsubscribe");
    assertThat(mail.text())
        .startsWith("Crea a túa conta\n\nAna Rey invítate a rexistrarte co rol Xestión")
        .doesNotContain("Rol:", "Caduca:")
        .contains("caduca o venres 6 de novembro ás 18:30 (hora de Galicia).")
        .contains("Crear a miña conta:\n" + LINK + "\n")
        .contains(
            "O Concello de Narón trata os teus datos para xestionar a túa conta de traballo.\n"
                + "Privacidade: https://reservas.example/naron/privacidade#persoal")
        .doesNotContain("{{", "<", "SPDX")
        .doesNotContainIgnoringCase("darse de baixa");
    assertThat(mail.inline()).extracting(OutgoingMail.Part::name).containsExactly("shield");
    assertThat(mail.attachments()).isEmpty();
  }

  @Test
  void theInvitationInSpanishHasNoGalicianText() {
    OutgoingMail mail = compose(mail(Language.ES, "STAFF", "Ana Rey"));

    assertThat(mail.subject())
        .isEqualTo(
            "Ana Rey te invita a atender las reservas de las instalaciones deportivas del"
                + " Concello de Narón");
    assertThat(mail.html())
        .contains(
            "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;\">Ana Rey te"
                + " invita a atender las reservas de las instalaciones deportivas del Concello"
                + " de Narón</div>")
        .contains("<html lang=\"es\">")
        .contains(">Crea tu cuenta</p>")
        .contains(
            "Ana Rey te invita a registrarte con el rol Recepción para atender las reservas de las"
                + " instalaciones deportivas del Concello de Narón.")
        .contains(
            "Usa este mismo correo (conserxe@naron.example). El enlace sirve una vez y caduca el"
                + " viernes 6 de noviembre a las 18:30 (hora de Galicia).")
        .contains(
            "Recibes este correo porque te han invitado a la plataforma de gestión de instalaciones"
                + " deportivas del Concello de Narón.")
        .contains(">Crear mi cuenta</a>", "Si no lo esperabas, ignora este correo.")
        .contains(
            "El Concello de Narón trata tus datos para gestionar tu cuenta de trabajo.",
            ">Privacidad</a>")
        .doesNotContain(
            "novembro",
            "venres",
            "invítate",
            "Recepción para atender as",
            "Crear a miña conta",
            "Privacidade",
            "institucional",
            "Narón en Furar")
        .doesNotContain("{{")
        .doesNotContainIgnoringCase("darse de baja");
    assertThat(mail.text()).contains("Crear mi cuenta:\n" + LINK);
  }

  @Test
  void theOneOClockHourIsSingular() {
    Instant one = Instant.parse("2026-11-06T00:15:00Z");
    StaffInvitationMail gl =
        new StaffInvitationMail(
            UUID.randomUUID(), "a@example.org", Language.GL, "STAFF", "Ana", LINK, one);
    StaffInvitationMail es =
        new StaffInvitationMail(
            UUID.randomUUID(), "a@example.org", Language.ES, "STAFF", "Ana", LINK, one);

    assertThat(compose(gl).text()).contains("venres 6 de novembro á 01:15 (hora de Galicia)");
    assertThat(compose(es).text()).contains("viernes 6 de noviembre a la 01:15 (hora de Galicia)");
  }

  @Test
  void everyValueIsEscaped() {
    String evil = "<script>alert('x')</script>";
    OutgoingMail mail =
        composer.compose(
            mail(Language.GL, "MANAGER", evil),
            BookingMailComposerTest.municipality("Narón\"><script>alert(1)</script>"));

    assertThat(mail.html())
        .doesNotContain("<script")
        .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; invítate")
        .contains("Narón&quot;&gt;&lt;script&gt;");
    assertThat(mail.subject()).as("o asunto é texto, non HTML").contains("<script>alert(1)");
    assertThat(mail.text()).contains(evil + " invítate");
  }

  @Test
  void aBlankInviterIsTheMunicipalityManagement() {
    OutgoingMail mail = compose(mail(Language.GL, "STAFF", "  "));

    assertThat(mail.text())
        .contains(
            "A xestión de Narón invítate a rexistrarte co rol Recepción para atender as reservas das"
                + " instalacións deportivas do Concello de Narón.");
  }

  @Test
  void galicianContractsTheArticleOfTheMunicipalityName() {
    OutgoingMail mail =
        composer.compose(
            mail(Language.GL, "STAFF", "  "), BookingMailComposerTest.municipality("A Coruña"));

    assertThat(mail.subject())
        .isEqualTo(
            "A xestión da Coruña invítate a atender as reservas das instalacións deportivas do"
                + " Concello da Coruña");
    assertThat(mail.text())
        .contains("A xestión da Coruña invítate a rexistrarte co rol Recepción")
        .contains("instalacións deportivas do Concello da Coruña.");
    OutgoingMail es =
        composer.compose(
            mail(Language.ES, "STAFF", "Ana"), BookingMailComposerTest.municipality("A Coruña"));
    assertThat(es.subject())
        .isEqualTo(
            "Ana te invita a atender las reservas de las instalaciones deportivas del Concello"
                + " de A Coruña");
  }

  @Test
  void theMailRecordKeepsTheLinkAndRecipientOutOfItsText() {
    StaffInvitationMail mail = mail(Language.GL, "MANAGER", "Ana Rey");

    assertThat(mail.toString()).doesNotContain(TOKEN, "convite", "conserxe@naron.example");
    assertThatThrownBy(
            () ->
                new StaffInvitationMail(
                    UUID.randomUUID(),
                    "a@example.org",
                    Language.GL,
                    "PLATFORM_ADMIN",
                    "Ana",
                    LINK,
                    EXPIRES))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new StaffInvitationMail(
                    UUID.randomUUID(),
                    "a@example.org",
                    Language.GL,
                    "STAFF",
                    "Ana",
                    URI.create("https://reservas.example/naron/convite?token=" + TOKEN),
                    EXPIRES))
        .as("o token só vai no fragmento")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bothLanguagesHaveTheSameNonBlankTexts() throws IOException {
    Properties gl = load("mail/staff-invitation_gl.properties");
    Properties es = load("mail/staff-invitation_es.properties");

    assertThat(gl.stringPropertyNames()).isEqualTo(es.stringPropertyNames()).isNotEmpty();
    for (String role : StaffInvitationMail.ROLES) {
      assertThat(gl).containsKeys("lead." + role, "subject." + role, "preheader." + role);
    }
    gl.forEach((k, v) -> assertThat((String) v).as("gl %s", k).isNotBlank().doesNotContain("'"));
    es.forEach((k, v) -> assertThat((String) v).as("es %s", k).isNotBlank().doesNotContain("'"));
  }

  private static Properties load(String name) throws IOException {
    Properties p = new Properties();
    try (InputStream in =
        StaffInvitationMailComposerTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as(name).isNotNull();
      p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return p;
  }

  @Test
  void theMunicipalityOwnPrivacyPageIsTheLink() {
    MunicipalityMailContext m = BookingMailComposerTest.municipality("Narón");
    MunicipalityMailContext own =
        new MunicipalityMailContext(
            m.id(),
            m.slug(),
            m.name(),
            m.languages(),
            m.defaultLanguage(),
            m.primaryColor(),
            m.onPrimaryColor(),
            m.contact(),
            m.paymentNote(),
            m.refundNote(),
            "https://naron.example/privacidade");
    OutgoingMail mail = composer.compose(mail(Language.GL, "STAFF", "Ana Rey"), own);
    assertThat(mail.html())
        .contains("href=\"https://naron.example/privacidade\"")
        .doesNotContain("reservas.example/naron/privacidade");
    assertThat(mail.text()).contains("Privacidade: https://naron.example/privacidade");
  }
}
