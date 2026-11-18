// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.OpenMatchNotice;
import gal.furar.booking.OpenMatchNotice.Kind;
import gal.furar.booking.OpenMatchNotice.Recipient;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Os seis correos das partidas abertas (ADR-0040, punto 12) nos dous idiomas: asunto, texto,
 * ligazóns, que só levan tarxeta os que a ADR di, e que o HTML escapa o texto de quen organiza.
 */
class OpenMatchMailTemplatesTest {

  static final UUID MATCH = UUID.fromString("019a0b6e-0000-7000-8000-000000000a01");

  final OpenMatchMailTemplates templates = new OpenMatchMailTemplates("https://reservas.example/");

  static OpenMatchNotice notice(
      Kind kind, @Nullable String title, boolean unfilled, boolean closed, Recipient to) {
    return new OpenMatchNotice(
        kind,
        MATCH,
        title,
        UUID.randomUUID(),
        "Pavillón do Alto",
        "Rúa do Alto, 12, 15570 Narón",
        "Fútbol sala",
        null,
        Instant.parse("2026-11-14T09:00:00Z"),
        Instant.parse("2026-11-14T10:00:00Z"),
        6,
        Instant.parse("2026-11-13T21:00:00Z"),
        "Ana Rey",
        unfilled,
        closed,
        kind == Kind.REQUESTED ? "Xan <b>Pérez</b>" : null,
        List.of(to));
  }

  static Recipient player(String language) {
    return new Recipient(null, UUID.randomUUID(), "xan@example.org", language, "Xan Pérez");
  }

  static Recipient organizer() {
    return new Recipient(UUID.randomUUID(), null, "ana@example.org", "gl", "Ana Rey");
  }

  OutgoingMail compose(OpenMatchNotice notice, Language language) {
    return templates.compose(
        notice,
        notice.recipients().getFirst(),
        language,
        BookingMailComposerTest.municipality("Narón"));
  }

  @Test
  void unfilledGoesToEveryoneWithTheCardAndTheListLink() {
    OpenMatchNotice n = notice(Kind.CANCELLED, "Pachanga", true, false, organizer());
    OutgoingMail gl = compose(n, Language.GL);

    assertThat(gl.subject()).isEqualTo("Partida cancelada: Pachanga");
    assertThat(gl.to()).isEqualTo("ana@example.org");
    assertThat(gl.text())
        .contains("Ola, Ana:")
        .contains(
            "Non chegou a 6 persoas antes do venres 13 de novembro ás 22:00, así que a partida"
                + " cancelouse, e con ela a reserva da pista.")
        .contains("Pavillón do Alto")
        .contains("Se queres xogar outro día, mira as partidas abertas de Narón.")
        .contains("Ver partidas abertas: https://reservas.example/naron")
        .contains("buscaches xente para xogar")
        .doesNotContain("Código", "QR");
    assertThat(gl.attachments()).isEmpty();
  }

  @Test
  void aCancelledBookingTellsThePlayersInTheirLanguage() {
    OutgoingMail es =
        compose(notice(Kind.CANCELLED, null, false, false, player("es")), Language.ES);

    assertThat(es.subject()).isEqualTo("Partida cancelada: Fútbol sala · sábado 14 a las 10:00");
    assertThat(es.text())
        .contains("Se ha cancelado la reserva de la pista y, con ella, la partida.")
        .contains("estás en una partida abierta");
  }

  @Test
  void aMoveSaysTheNewDayAndWhetherItStillLooksForPeople() {
    OutgoingMail closed =
        compose(notice(Kind.CHANGED, "Pachanga", false, true, player("gl")), Language.GL);

    assertThat(closed.subject()).isEqualTo("Cambiou a partida: Pachanga");
    assertThat(closed.text())
        .contains(
            "A partida pasa a sábado 14 de novembro, 10:00–11:00. Xa non se busca máis xente.")
        .contains("Se xa non podes ir, sae para deixar a praza.")
        .contains("Ver a partida: https://reservas.example/naron/partidas/" + MATCH);
  }

  @Test
  void aRequestGoesToTheOrganizerWithTheNameEscaped() {
    OutgoingMail gl =
        compose(notice(Kind.REQUESTED, "Pachanga", false, false, organizer()), Language.GL);

    assertThat(gl.subject()).isEqualTo("Alguén quere unirse: Pachanga");
    assertThat(gl.text())
        .contains("Xan <b>Pérez</b> pediu unirse á túa partida.")
        .contains("Ver as solicitudes: https://reservas.example/naron/partidas/" + MATCH)
        .doesNotContain("Pavillón do Alto\nRúa");
    assertThat(gl.html()).contains("Xan &lt;b&gt;Pérez&lt;/b&gt;").doesNotContain("<b>Pérez");
  }

  @Test
  void acceptedAndRejectedGoToWhoAsked() {
    OutgoingMail accepted =
        compose(notice(Kind.ACCEPTED, "Pachanga", false, false, player("es")), Language.ES);
    OutgoingMail rejected =
        compose(notice(Kind.REJECTED, "Pachanga", false, false, player("gl")), Language.GL);

    assertThat(accepted.subject()).isEqualTo("Ya estás en la partida: Pachanga");
    assertThat(accepted.text())
        .contains("Ana Rey ha aceptado tu solicitud.")
        .contains(
            "Se juega si hay 6 personas antes del viernes 13 de noviembre a las 22:00. Si no, te"
                + " avisamos.");
    assertThat(rejected.subject()).isEqualTo("Non entraches na partida: Pachanga");
    assertThat(rejected.text())
        .contains(
            "Quen organiza non aceptou a túa solicitude. Mira outras partidas abertas de Narón.")
        .contains("Ver partidas abertas: https://reservas.example/naron");
  }

  @Test
  void hiddenTellsTheOrganizerWhoToCall() {
    OutgoingMail gl =
        compose(notice(Kind.HIDDEN, "Pachanga", false, false, organizer()), Language.GL);

    assertThat(gl.subject()).isEqualTo("Ocultamos a túa partida: Pachanga");
    assertThat(gl.text())
        .contains(
            "O concello ocultou a túa partida: xa non se ve na portada e ninguén máis se pode"
                + " unir. A reserva segue. Se tes dúbidas, chama ao 981 000 700.");
  }
}
