// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.BookingSummary;
import gal.furar.booking.CancelledWaitsNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.out.CancelledWaitsMailContent;
import gal.furar.notification.application.port.out.MailContent;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O recordatorio (ADR-0035) e «Lista de espera cancelada» (ADR-0033, revisión do 2026-11-11): os
 * textos validados, coa mesma plantilla ca os demais correos das reservas.
 */
class ReminderAndCancelledWaitsMailTest {

  final BookingMailComposer composer = new BookingMailComposer("https://reservas.example/");

  OutgoingMail reminder(Language language, BookingNotice booking) {
    return composer.compose(
        new MailContent(
            MailKind.REMINDER,
            language,
            booking,
            BookingMailComposerTest.municipality("Narón"),
            null,
            null,
            Instant.parse("2026-10-25T17:00:00Z"),
            null,
            "v1.abc.123.sig"));
  }

  /** A mesma reserva, sen prazo: o prazo é o propio inicio. */
  static BookingNotice withoutDeadline(BookingNotice b) {
    return new BookingNotice(
        b.id(),
        b.code(),
        b.version(),
        b.facilityId(),
        b.facilityName(),
        b.facilityAddress(),
        b.useName(),
        b.partName(),
        b.start(),
        b.end(),
        b.status(),
        b.payment(),
        b.priceKind(),
        b.amountCents(),
        b.start(),
        b.holderName(),
        b.holderEmail(),
        b.holderLanguage(),
        b.holderHasAccount(),
        b.qrPng(),
        b.calendar(),
        b.seats(),
        b.participantNames(),
        b.bookedByStaff());
  }

  @Test
  void theReminderIsTheValidatedText() {
    OutgoingMail mail = reminder(Language.GL, BookingMailComposerTest.confirmed("gl"));

    assertThat(mail.subject()).isEqualTo("Lembra: Campo de fútbol 1, martes 27 ás 18:00");
    assertThat(mail.text())
        .contains("Tes unha reserva en Narón.")
        .contains("Campo de fútbol 1", "Fútbol 7", "martes 27 de outubro, 18:00–19:00")
        .contains("FUR-7K2Q")
        .contains(
            "Se non podes ir, cancélaa antes do luns 26 ás 18:00 para que outra persoa a poida"
                + " usar.")
        .contains("Ver a miña reserva: https://reservas.example/naron/reservas")
        .contains("Non quero recordatorios: https://reservas.example/naron/recordatorios/baixa?t=")
        .doesNotContain("Ola,", "30,00", "Podes cancelala de balde");
    assertThat(mail.html()).contains("cid:qr").contains("Non quero recordatorios");
    assertThat(mail.attachments()).isEmpty();
  }

  @Test
  void withoutDeadlineTheReminderDoesNotInventOne() {
    OutgoingMail gl =
        reminder(Language.GL, withoutDeadline(BookingMailComposerTest.confirmed("gl")));
    OutgoingMail es =
        reminder(Language.ES, withoutDeadline(BookingMailComposerTest.confirmed("es")));

    assertThat(gl.text())
        .contains("Se non podes ir, cancélaa para que outra persoa a poida usar.")
        .doesNotContain("antes do");
    assertThat(es.subject()).isEqualTo("Recuerda: Campo de fútbol 1, martes 27 a las 18:00");
    assertThat(es.text())
        .contains("Tienes una reserva en Narón.")
        .contains("Si no puedes ir, cancélala para que otra persona pueda usarla.")
        .contains("No quiero recordatorios");
  }

  @Test
  void withoutAccountTheReminderHasNoButtonButKeepsTheOptOut() {
    OutgoingMail mail =
        reminder(
            Language.GL,
            BookingMailComposerTest.booking(
                "Campo de fútbol 1", "Ana Rey", "gl", BookingSummary.Status.CONFIRMED, false));

    assertThat(mail.text())
        .doesNotContain("Ver a miña reserva")
        .contains("Non quero recordatorios");
  }

  @Test
  void onlyTheReminderCarriesTheOptOutLink() {
    assertThatThrownBy(
            () ->
                new MailContent(
                    MailKind.REMINDER,
                    Language.GL,
                    BookingMailComposerTest.confirmed("gl"),
                    BookingMailComposerTest.municipality("Narón"),
                    null,
                    null,
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static CancelledWaitsNotice waits(String language, int count) {
    List<CancelledWaitsNotice.Slot> slots =
        List.of(
                new CancelledWaitsNotice.Slot(
                    Instant.parse("2026-11-16T18:00:00Z"), Instant.parse("2026-11-16T19:00:00Z")),
                new CancelledWaitsNotice.Slot(
                    Instant.parse("2026-11-17T17:00:00Z"), Instant.parse("2026-11-17T18:00:00Z")))
            .subList(0, count);
    return new CancelledWaitsNotice(
        UUID.randomUUID(),
        "ana@example.org",
        language,
        UUID.randomUUID(),
        "Pista de pádel 1",
        slots);
  }

  static MunicipalityMailContext withoutPhone() {
    MunicipalityMailContext m = BookingMailComposerTest.municipality("Narón");
    return new MunicipalityMailContext(
        m.id(),
        m.slug(),
        m.name(),
        m.languages(),
        m.defaultLanguage(),
        m.primaryColor(),
        m.onPrimaryColor(),
        new MunicipalityMailContext.Contact(
            m.contact().name(), m.contact().address(), null, m.contact().email()),
        m.paymentNote(),
        m.refundNote(),
        null);
  }

  @Test
  void oneCancelledWaitIsTheTextAskedFor() {
    OutgoingMail mail =
        composer.compose(
            new CancelledWaitsMailContent(
                Language.GL, waits("gl", 1), BookingMailComposerTest.municipality("Narón")));

    assertThat(mail.subject()).isEqualTo("Lista de espera cancelada: Pista de pádel 1");
    assertThat(mail.to()).isEqualTo("ana@example.org");
    assertThat(mail.text())
        .contains(
            "Pista de pádel 1 xa non está dispoñible para reservar. Estabas en lista"
                + " de espera para o luns 16 de novembro, 19:00. Cancelamos a túa espera, porque"
                + " esa hora xa non se poderá reservar.")
        .contains("Podes buscar outra instalación ou chamar ao 981 000 700.");
    assertThat(mail.html()).doesNotContain("cid:qr");
    assertThat(mail.attachments()).isEmpty();
  }

  @Test
  void severalWaitsGoInOneMailAndWithoutPhoneTheSentenceEndsEarlier() {
    OutgoingMail gl =
        composer.compose(
            new CancelledWaitsMailContent(Language.GL, waits("gl", 2), withoutPhone()));
    OutgoingMail es =
        composer.compose(
            new CancelledWaitsMailContent(Language.ES, waits("es", 2), withoutPhone()));

    assertThat(gl.text())
        .contains(
            "Estabas en lista de espera para o luns 16 de novembro, 19:00 e o martes 17 de"
                + " novembro, 18:00. Cancelamos as túas esperas, porque esas horas xa non se"
                + " poderán reservar.")
        .contains("Podes buscar outra instalación.")
        .doesNotContain("chamar");
    assertThat(es.subject()).isEqualTo("Lista de espera cancelada: Pista de pádel 1");
    assertThat(es.text())
        .contains("Pista de pádel 1 ya no está disponible para reservar.")
        .contains("el lunes 16 de noviembre, 19:00 y el martes 17 de noviembre, 18:00")
        .contains("Puedes buscar otra instalación.");
  }
}
