// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.BookingSummary;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.in.NoShowFacts;
import gal.furar.notification.application.port.out.MailContent;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O correo dunha reserva (ADR-0025, punto 15): asunto, textos en gl e es sen ningún oco nin texto
 * sen traducir, todo valor escapado, QR e escudo incrustados, {@code .ics} adxunto e o que cambia
 * en cada un dos catro correos.
 */
class BookingMailComposerTest {

  /** Martes 27 de outubro de 2026, 18:00–19:00 en Galicia. */
  static final Instant START = Instant.parse("2026-10-27T17:00:00Z");

  static final Instant END = Instant.parse("2026-10-27T18:00:00Z");
  static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
  static final UUID FACILITY = UUID.fromString("019a0b6e-0000-7000-8000-00000000f001");
  static final byte[] QR = {(byte) 0x89, 'P', 'N', 'G'};

  final BookingMailComposer composer = new BookingMailComposer("https://reservas.example/");

  static BookingNotice booking(
      String facility, String holder, String language, BookingSummary.Status status) {
    return booking(facility, holder, language, status, true);
  }

  static BookingNotice booking(
      String facility,
      String holder,
      String language,
      BookingSummary.Status status,
      boolean account) {
    return new BookingNotice(
        UUID.fromString("019a0b6e-0000-7000-8000-00000000b001"),
        "FUR-7K2Q",
        3,
        FACILITY,
        facility,
        "Rúa Buxo, 16, 15572 Narón",
        "Fútbol 7",
        "Metade sur",
        START,
        END,
        status,
        BookingNotice.Payment.PENDING,
        BookingNotice.PriceKind.PAID,
        3000,
        Instant.parse("2026-10-26T17:00:00Z"),
        holder,
        "ana@example.org",
        language,
        account,
        QR,
        "BEGIN:VCALENDAR\r\nMETHOD:"
            + (status == BookingSummary.Status.CONFIRMED ? "PUBLISH" : "CANCEL")
            + "\r\nEND:VCALENDAR\r\n",
        1,
        List.of(),
        !account);
  }

  static MunicipalityMailContext municipality(String name) {
    return new MunicipalityMailContext(
        UUID.randomUUID(),
        "naron",
        name,
        List.of("gl", "es"),
        "gl",
        "#0b4f8a",
        "#ffffff",
        new MunicipalityMailContext.Contact(
            "Servizo Municipal de Deportes",
            "Rúa Concepción Arenal, 1 · 15570 Narón",
            "981 000 700",
            "deportes@naron.example"),
        new MunicipalityMailContext.Text("No mostrador, ao chegar", "En el mostrador, al llegar"),
        new MunicipalityMailContext.Text(
            "O concello dirache como cha devolve", "El ayuntamiento te dirá cómo te lo devuelve"));
  }

  OutgoingMail compose(MailKind kind, Language language, BookingNotice booking) {
    return compose(kind, language, booking, municipality("Narón"));
  }

  OutgoingMail compose(
      MailKind kind, Language language, BookingNotice booking, MunicipalityMailContext m) {
    boolean changed = kind == MailKind.CHANGED;
    return composer.compose(
        new MailContent(
            kind,
            language,
            booking,
            m,
            changed ? Instant.parse("2026-10-26T08:00:00Z") : null,
            changed ? Instant.parse("2026-10-26T09:00:00Z") : null,
            NOW));
  }

  static BookingNotice confirmed(String language) {
    return booking("Campo de fútbol 1", "Ana Rey", language, BookingSummary.Status.CONFIRMED);
  }

  @Test
  void theConfirmationInGalicianHasTheExpectedTexts() {
    OutgoingMail mail = compose(MailKind.CONFIRMED, Language.GL, confirmed("gl"));

    assertThat(mail.subject())
        .isEqualTo("Reserva confirmada: Campo de fútbol 1, martes 27 de outubro, 18:00");
    assertThat(mail.fromName()).isEqualTo("Deportes Narón");
    assertThat(mail.to()).isEqualTo("ana@example.org");
    assertThat(mail.html())
        .startsWith("<!doctype html>")
        .contains("<html lang=\"gl\">")
        .contains("✓ Reserva confirmada")
        .contains("martes 27 de outubro<br>18:00–19:00")
        .contains("Ola, Ana:")
        .contains("A túa reserva está feita.")
        .contains("Campo de fútbol 1", "Rúa Buxo, 16, 15572 Narón", "Fútbol 7", "Metade sur")
        .contains("30,00 € · No mostrador, ao chegar")
        .contains("src=\"cid:qr\"", "src=\"cid:shield\"", "FUR-7K2Q", "Ensínao ao chegar")
        .contains("Podes cancelala de balde ata o <b>luns 26 ás 18:00</b>.")
        .contains(
            ">Ver a miña reserva</a>",
            ">Engadir ao calendario</a>",
            "href=\"https://reservas.example/naron/reservas\"")
        .doesNotContain("Narón · Servizo Municipal de Deportes")
        .contains("981 000 700", "mailto:deportes@naron.example")
        .contains("href=\"https://reservas.example/naron/privacidade\"", ">Privacidade</a>")
        .contains("bgcolor=\"#0b4f8a\"")
        .doesNotContain("SPDX", "{{", "}}", "<svg", "<script", "fonts.googleapis", "@font-face")
        .doesNotContainIgnoringCase("darse de baixa")
        .doesNotContainIgnoringCase("unsubscribe");
    assertThat(mail.text())
        .startsWith("✓ Reserva confirmada\nmartes 27 de outubro, 18:00–19:00\n")
        .contains("Uso: Fútbol 7", "Parte: Metade sur", "Código: FUR-7K2Q")
        .contains("Ver a miña reserva: https://reservas.example/naron/reservas")
        .doesNotContain("{{", "#", "<");
    assertThat(mail.inline()).extracting(OutgoingMail.Part::name).containsExactly("shield", "qr");
    assertThat(mail.inline().get(1).bytes()).isEqualTo(QR);
    assertThat(mail.attachments())
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.name()).isEqualTo("reserva-FUR-7K2Q.ics");
              assertThat(a.contentType()).isEqualTo("text/calendar; charset=UTF-8; method=PUBLISH");
              assertThat(new String(a.bytes(), StandardCharsets.UTF_8)).contains("METHOD:PUBLISH");
            });
  }

  /** A reserva por prazas para varias persoas (ADR-0031): «Para quen» e o prezo coas prazas. */
  static BookingNotice forTheFamily(String language, boolean bookedByStaff) {
    BookingNotice b = confirmed(language);
    return new BookingNotice(
        b.id(),
        b.code(),
        b.version(),
        b.facilityId(),
        "Piscina municipal",
        b.facilityAddress(),
        "Natación libre",
        null,
        b.start(),
        b.end(),
        b.status(),
        b.payment(),
        b.priceKind(),
        600,
        b.cancelUntil(),
        b.holderName(),
        b.holderEmail(),
        language,
        true,
        b.qrPng(),
        b.calendar(),
        2,
        List.of("Ana Rey", "Uxía <Rey>"),
        bookedByStaff);
  }

  @Test
  void aBookingForSeveralPeopleSaysWhoAndHowManySeats() {
    OutgoingMail gl = compose(MailKind.CONFIRMED, Language.GL, forTheFamily("gl", false));
    OutgoingMail es = compose(MailKind.CONFIRMED, Language.ES, forTheFamily("es", false));

    assertThat(gl.html())
        .contains(">Para quen</td>", "Ana Rey, Uxía &lt;Rey&gt;")
        .contains("6,00 € · 2 prazas · No mostrador, ao chegar")
        .doesNotContain("Se esta reserva non é túa");
    assertThat(gl.text()).contains("Para quen: Ana Rey, Uxía <Rey>");
    assertThat(es.html()).contains(">Para quién</td>", "· 2 plazas");
    // Unha soa praza para quen reserva: nin «Para quen» nin prazas.
    assertThat(compose(MailKind.CONFIRMED, Language.GL, confirmed("gl")).html())
        .doesNotContain("Para quen", "prazas");
  }

  @Test
  void theConfirmationOfABookingMadeByTheStaffSaysWhoToTellIfItIsNotYours() {
    OutgoingMail gl = compose(MailKind.CONFIRMED, Language.GL, forTheFamily("gl", true));
    OutgoingMail es = compose(MailKind.CONFIRMED, Language.ES, forTheFamily("es", true));
    OutgoingMail changed = compose(MailKind.CHANGED, Language.GL, forTheFamily("gl", true));

    assertThat(gl.html())
        .contains("Se esta reserva non é túa, avisa ao servizo de deportes: 981 000 700.")
        .contains("o persoal municipal rexistrou no teu nome")
        // A conta do correo xa a ten: a reserva é súa e leva a ligazón.
        .contains(">Ver a miña reserva</a>");
    assertThat(gl.text()).contains("Se esta reserva non é túa, avisa ao servizo de deportes");
    assertThat(es.html())
        .contains("Si esta reserva no es tuya, avisa al servicio de deportes: 981 000 700.");
    assertThat(changed.html()).as("só na confirmación").doesNotContain("Se esta reserva non é túa");
  }

  @Test
  void theConfirmationInSpanishHasNoGalicianText() {
    OutgoingMail mail = compose(MailKind.CONFIRMED, Language.ES, confirmed("es"));

    assertThat(mail.subject())
        .isEqualTo("Reserva confirmada: Campo de fútbol 1, martes 27 de octubre, 18:00");
    assertThat(mail.html())
        .contains("<html lang=\"es\">")
        .contains("Hola, Ana:", "Tu reserva está hecha.", "Deportes municipales")
        .contains("30,00 € · En el mostrador, al llegar")
        .contains(
            "Enséñalo al llegar", "Puedes cancelarla gratis hasta el <b>lunes 26 a las 18:00</b>.")
        .contains(">Ver mi reserva</a>", ">Añadir al calendario</a>", ">Privacidad</a>")
        .doesNotContain(
            "outubro", "Ola,", "túa", "Ensínao", "Podes", "miña", "Engadir", "Privacidade", "{{");
  }

  @Test
  void everyValueIsEscaped() {
    String evil = "<script>alert('x')</script>";
    OutgoingMail mail =
        compose(
            MailKind.CONFIRMED,
            Language.GL,
            booking(evil, evil + " Rey", "gl", BookingSummary.Status.CONFIRMED),
            municipality("Narón\"><script>alert(1)</script>"));

    assertThat(mail.html())
        .doesNotContain("<script")
        .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
        .contains("Narón&quot;&gt;&lt;script&gt;");
    assertThat(mail.subject()).as("o asunto é texto, non HTML").contains(evil);
  }

  @Test
  void theChangeTellsTheSlotBefore() {
    OutgoingMail mail = compose(MailKind.CHANGED, Language.GL, confirmed("gl"));

    assertThat(mail.subject()).startsWith("Reserva cambiada: Campo de fútbol 1, martes 27");
    assertThat(mail.html())
        .contains("✓ Reserva cambiada", "Cambiamos a túa reserva.")
        .contains(">Antes</td>", "luns 26 de outubro, 09:00–10:00")
        .contains("src=\"cid:qr\"", "Podes cancelala de balde");
    assertThat(mail.text()).contains("Antes: luns 26 de outubro, 09:00–10:00");
  }

  @Test
  void withoutAnAccountTheMailHasNoLinkToTheirBookingsButKeepsTheCode() {
    BookingNotice walkIn =
        booking("Campo de fútbol 1", "Ana Rey", "gl", BookingSummary.Status.CONFIRMED, false);
    for (MailKind kind : List.of(MailKind.CONFIRMED, MailKind.CHANGED)) {
      OutgoingMail mail = compose(kind, Language.GL, walkIn);

      assertThat(mail.html())
          .as(kind.name())
          .contains("src=\"cid:qr\"", "FUR-7K2Q", "Ensínao ao chegar")
          .contains("Tamén a tes no arquivo adxunto para o calendario.")
          .doesNotContain("Ver a miña reserva", "Engadir ao calendario", "/naron/reservas\"")
          .contains("o persoal municipal rexistrou no teu nome unha reserva")
          .doesNotContain("fixeches unha reserva")
          .doesNotContain("{{", "}}");
      assertThat(mail.text())
          .as(kind.name())
          .contains("Código: FUR-7K2Q", "Ensínao ao chegar")
          .doesNotContain("Ver a miña reserva", "Engadir ao calendario", "/naron/reservas");
      assertThat(mail.attachments()).hasSize(1);
    }

    OutgoingMail es =
        compose(
            MailKind.CONFIRMED,
            Language.ES,
            booking("Campo de fútbol 1", "Ana Rey", "es", BookingSummary.Status.CONFIRMED, false));
    assertThat(es.html())
        .contains("FUR-7K2Q", "el personal municipal registró a tu nombre una reserva")
        .doesNotContain("Ver mi reserva", "/naron/reservas", "hiciste una reserva");

    OutgoingMail cancelled =
        compose(
            MailKind.CANCELLED_BY_MUNICIPALITY,
            Language.GL,
            booking(
                "Campo de fútbol 1",
                "Ana Rey",
                "gl",
                BookingSummary.Status.CANCELLED_BY_MUNICIPALITY,
                false));
    assertThat(cancelled.html())
        .contains(">Código</td>", "FUR-7K2Q", ">Elixir outra hora</a>")
        .doesNotContain("Ver as miñas reservas");
    assertThat(cancelled.text()).doesNotContain("Ver as miñas reservas");
  }

  @Test
  void theCancellationByThePersonHasNoQrAndRemovesTheEvent() {
    OutgoingMail mail =
        compose(
            MailKind.CANCELLED_BY_PERSON,
            Language.GL,
            booking(
                "Campo de fútbol 1", "Ana Rey", "gl", BookingSummary.Status.CANCELLED_BY_PERSON));

    assertThat(mail.subject()).startsWith("Reserva cancelada: Campo de fútbol 1, martes 27");
    assertThat(mail.html())
        .contains("✕ Reserva cancelada", "#c9372c", "A túa reserva está cancelada")
        .contains(">Código</td>", "FUR-7K2Q")
        .contains(">Reservar outra hora</a>", ">Ver as miñas reservas</a>")
        .contains("href=\"https://reservas.example/naron/instalacions/" + FACILITY + "/reservar\"")
        .contains("O arquivo adxunto quítaa do teu calendario.")
        .doesNotContain("cid:qr", "Podes cancelala", "Ensínao");
    assertThat(mail.inline()).extracting(OutgoingMail.Part::name).containsExactly("shield");
    assertThat(mail.attachments().getFirst().contentType()).endsWith("method=CANCEL");
  }

  @Test
  void theCancellationByTheMunicipalityOffersAnotherTime() {
    OutgoingMail mail =
        compose(
            MailKind.CANCELLED_BY_MUNICIPALITY,
            Language.ES,
            booking(
                "Campo de fútbol 1",
                "Ana Rey",
                "es",
                BookingSummary.Status.CANCELLED_BY_MUNICIPALITY));

    assertThat(mail.subject())
        .isEqualTo(
            "El ayuntamiento canceló tu reserva: Campo de fútbol 1, martes 27 de octubre, 18:00");
    assertThat(mail.html())
        .contains("✕ Cancelada por el ayuntamiento", "Lo sentimos.")
        .contains(">Elegir otra hora</a>", ">Ver mis reservas</a>");
  }

  @Test
  void aPastDeadlineSaysSo() {
    BookingNotice b = confirmed("gl");
    OutgoingMail mail =
        composer.compose(
            new MailContent(
                MailKind.CONFIRMED,
                Language.GL,
                b,
                municipality("Narón"),
                null,
                null,
                b.cancelUntil().plusSeconds(60)));

    assertThat(mail.html())
        .contains("O prazo para cancelar de balde xa pasou.")
        .doesNotContain("Podes cancelala");
  }

  OutgoingMail noShow(Language language, BookingNotice booking, NoShowFacts facts) {
    return composer.compose(
        new MailContent(
            MailKind.NO_SHOW,
            language,
            booking,
            municipality("Narón"),
            null,
            null,
            END.plusSeconds(3600),
            facts));
  }

  @Test
  void theNoShowMailSaysHowManyFaultsAndWhenItBlocksWithoutQrNorCalendar() {
    OutgoingMail mail = noShow(Language.GL, confirmed("gl"), new NoShowFacts(2, 3, 30, 15, null));

    assertThat(mail.subject())
        .isEqualTo("Non te presentaches: Campo de fútbol 1, martes 27 ás 18:00");
    assertThat(mail.html())
        .contains("Non te presentaches")
        .contains("Non usaches a túa reserva en Narón.")
        .contains(
            "Levas 2 faltas en 30 días. Con 3 non poderás reservar por internet durante 15 días.")
        .contains("Se é un erro, chama ao 981 000 700.")
        .contains(">Ver as miñas reservas</a>", "FUR-7K2Q")
        .doesNotContain("cid:qr", "Podes cancelala", "O prazo para cancelar");
    assertThat(mail.text()).contains("Levas 2 faltas en 30 días.");
    assertThat(mail.inline()).extracting(OutgoingMail.Part::name).containsExactly("shield");
    assertThat(mail.attachments()).as("sen .ics").isEmpty();
  }

  @Test
  void theNoShowMailInSpanishUsesTheSingularAndTheBlockedText() {
    OutgoingMail one = noShow(Language.ES, confirmed("es"), new NoShowFacts(1, 3, 30, 15, null));
    assertThat(one.subject())
        .isEqualTo("No te presentaste: Campo de fútbol 1, martes 27 a las 18:00");
    assertThat(one.html())
        .contains("No te presentaste")
        .contains(
            "Llevas 1 falta en 30 días. Con 3 no podrás reservar por internet durante 15 días.")
        .contains("No usaste tu reserva en Narón.", "Si es un error, llama al 981 000 700.");

    OutgoingMail blocked =
        noShow(
            Language.ES,
            confirmed("es"),
            new NoShowFacts(3, 3, 30, 15, java.time.LocalDate.of(2026, 11, 24)));
    assertThat(blocked.html())
        .contains(
            "Llevas 3 faltas en 30 días: no puedes reservar por internet hasta el 24 de noviembre."
                + " En el mostrador sí puedes, y las reservas que ya tienes se mantienen.");

    OutgoingMail blockedAtOnce =
        noShow(
            Language.GL,
            confirmed("gl"),
            new NoShowFacts(1, 1, 30, 15, java.time.LocalDate.of(2026, 11, 24)));
    assertThat(blockedAtOnce.html())
        .contains("Levas 1 falta en 30 días: non podes reservar por internet ata o 24");
  }

  @Test
  void theNoShowMailWithThePenaltyOffAndWithoutAccountHasNoCountNorButton() {
    BookingNotice counter =
        booking("Campo de fútbol 1", "Ana Rey", "gl", BookingSummary.Status.CONFIRMED, false);

    OutgoingMail mail = noShow(Language.GL, counter, new NoShowFacts(0, 3, 30, 15, null));

    assertThat(mail.html())
        .contains("Non usaches a túa reserva en Narón.")
        .doesNotContain("Levas ", "Ver as miñas reservas");
  }

  /** «Quedou libre» (ADR-0033): o asunto sen o mes, sen saúdo, QR nin adxunto. */
  @Test
  void theFreedSlotMailFollowsTheApprovedSubjectInBothLanguages() {
    java.util.UUID facility = java.util.UUID.fromString("019a0b6e-0000-7000-8000-000000000202");
    java.util.UUID use = java.util.UUID.fromString("019a0b6e-0000-7000-8000-000000000234");
    gal.furar.booking.WaitNotice notice =
        new gal.furar.booking.WaitNotice(
            java.util.UUID.randomUUID(),
            "ana@example.org",
            "gl",
            facility,
            "Pista de pádel 1",
            "Rúa dos Carballos, 14, 15570 Narón",
            use,
            "Pádel",
            java.time.Instant.parse("2026-11-11T18:00:00Z"),
            java.time.Instant.parse("2026-11-11T19:00:00Z"));

    OutgoingMail gl =
        composer.compose(
            new gal.furar.notification.application.port.out.WaitMailContent(
                Language.GL, notice, municipality("Narón")));
    OutgoingMail es =
        composer.compose(
            new gal.furar.notification.application.port.out.WaitMailContent(
                Language.ES, notice, municipality("Narón")));

    assertThat(gl.subject()).isEqualTo("Quedou libre: Pista de pádel 1, mércores 11 ás 19:00");
    assertThat(es.subject())
        .isEqualTo("Ha quedado libre: Pista de pádel 1, miércoles 11 a las 19:00");
    assertThat(gl.to()).isEqualTo("ana@example.org");
    assertThat(gl.attachments()).isEmpty();
    assertThat(gl.text())
        .contains("Quedou libre a hora pola que esperabas en Narón.")
        .contains("mércores 11 de novembro, 19:00–20:00")
        .contains("Avisamos a todas as persoas que esperaban: resérvaa a primeira que entre.")
        .contains("/reservar?uso=" + use + "&data=2026-11-11&hora=19%3A00")
        .doesNotContain("Ola,", "Código");
  }

  @Test
  void bothLanguagesHaveTheSameNonBlankTexts() throws IOException {
    Properties gl = load("mail/notification_gl.properties");
    Properties es = load("mail/notification_es.properties");

    assertThat(gl.stringPropertyNames()).isEqualTo(es.stringPropertyNames()).isNotEmpty();
    for (MailKind kind : MailKind.values()) {
      assertThat(gl).containsKeys("subject." + kind, "title." + kind, "lead." + kind);
    }
    gl.forEach((k, v) -> assertThat((String) v).as("gl %s", k).isNotBlank());
    es.forEach((k, v) -> assertThat((String) v).as("es %s", k).isNotBlank());
  }

  @Test
  void aMissingValueIsAnError() {
    MailTemplate template = MailTemplate.text("{{a}} {{#b}}{{c}}{{/b}}");

    assertThat(template.render(Map.of("a", "1", "b", ""))).isEqualTo("1 ");
    assertThatThrownBy(() -> template.render(Map.of("a", "1", "b", "x")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("{{c}}");
  }

  @Test
  void colorsAreMixedForTheBand() {
    assertThat(BookingMailComposer.mix("#ffffff", "#000000", 0.5)).isEqualTo("#808080");
    assertThat(BookingMailComposer.firstName(" María José Pérez ")).isEqualTo("María");
  }

  private static Properties load(String name) throws IOException {
    Properties p = new Properties();
    try (InputStream in =
        BookingMailComposerTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as(name).isNotNull();
      p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return p;
  }

  @Test
  void withoutAPrivacyPageThereIsNoLink() {
    MunicipalityMailContext m = municipality("Narón");
    MunicipalityMailContext none =
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
            null,
            false,
            false,
            null);
    OutgoingMail mail = compose(MailKind.CONFIRMED, Language.GL, confirmed("gl"), none);
    assertThat(mail.html()).doesNotContain("/privacidade", ">Privacidade</a>", "{{");
    assertThat(mail.text()).doesNotContain("Privacidade:", "{{");
  }

  @Test
  void aFacilityNoteReplacesTheMunicipalityOne() {
    MunicipalityMailContext m =
        municipality("Narón")
            .withNotes(new MunicipalityMailContext.Text("Na billeteira", "En la taquilla"), null);
    OutgoingMail mail = compose(MailKind.CONFIRMED, Language.GL, confirmed("gl"), m);
    assertThat(mail.html()).contains("· Na billeteira").doesNotContain("No mostrador");
  }
}
