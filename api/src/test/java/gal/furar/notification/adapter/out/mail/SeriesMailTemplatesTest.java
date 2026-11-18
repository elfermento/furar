// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.SeriesNotice;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Os correos dunha tempada (ADR-0039, punto 12) nos dous idiomas: o número de sesións concorda («1
 * sesión», «a sesión que quedaba») e nunca sae «as 1 sesións».
 */
class SeriesMailTemplatesTest {

  final SeriesMailTemplates templates = new SeriesMailTemplates("https://reservas.example/");

  static SeriesNotice notice(int sessions, @Nullable LocalDate endedFrom, int cancelled) {
    return new SeriesNotice(
        UUID.randomUUID(),
        "Club Demo Baloncesto",
        "club@example.org",
        "gl",
        "Pavillón do Alto",
        "Rúa do Alto, 12, 15570 Narón",
        "Baloncesto",
        null,
        List.of(2),
        600,
        660,
        LocalDate.of(2026, 11, 17),
        LocalDate.of(2026, 11, 30),
        sessions,
        List.of(),
        false,
        endedFrom,
        cancelled);
  }

  OutgoingMail created(int sessions, Language language) {
    return templates.series(
        notice(sessions, null, 0),
        null,
        0,
        language,
        BookingMailComposerTest.municipality("Narón"));
  }

  OutgoingMail ended(int cancelled, Language language) {
    LocalDate from = LocalDate.of(2026, 11, 24);
    return templates.series(
        notice(0, from, cancelled),
        from,
        cancelled,
        language,
        BookingMailComposerTest.municipality("Narón"));
  }

  @Test
  void aCreatedSeasonCountsItsSessions() {
    assertThat(created(2, Language.GL).subject()).isEqualTo("Tempada asignada: Pavillón do Alto");
    assertThat(created(2, Language.GL).text()).contains("· 2 sesións.");
    assertThat(created(1, Language.GL).text()).contains("· 1 sesión.").doesNotContain("1 sesións");
    assertThat(created(2, Language.ES).text()).contains("· 2 sesiones.");
    assertThat(created(1, Language.ES).text()).contains("· 1 sesión.").doesNotContain("1 sesiones");
  }

  @Test
  void anEndedSeasonSaysHowManySessionsWereCancelled() {
    assertThat(ended(5, Language.GL).text())
        .contains("cancelamos as 5 sesións que quedaban. As horas quedan libres.");
    assertThat(ended(1, Language.GL).text())
        .contains("cancelamos a sesión que quedaba. A hora queda libre.")
        .doesNotContain("as 1 ");
    assertThat(ended(0, Language.GL).text()).contains("xa non quedaban sesións por cancelar.");
    assertThat(ended(5, Language.ES).text())
        .contains("hemos cancelado las 5 sesiones que quedaban. Las horas quedan libres.");
    assertThat(ended(1, Language.ES).text())
        .contains("hemos cancelado la sesión que quedaba. La hora queda libre.");
    assertThat(ended(0, Language.ES).text()).contains("ya no quedaban sesiones por cancelar.");
  }
}
