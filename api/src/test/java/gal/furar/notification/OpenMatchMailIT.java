// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractOpenMatchIT;
import gal.furar.booking.OpenMatchRequested;
import gal.furar.booking.application.service.OpenMatchDeadlineService;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.internet.MimeMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

/**
 * Os correos das partidas abertas (ADR-0040, punto 12), despois do commit e no idioma de cada
 * persoa: solicitude nova a quen organiza, aceptada e non aceptada a quen a pediu, oculta a quen
 * organiza, cancelada ás persoas dentro (e a quen organiza se non chegou ao mínimo, sen o correo da
 * reserva) e cambiada ao mover a reserva. Unha vez por publicación e persoa; un fallido, unha vez
 * máis.
 */
class OpenMatchMailIT extends AbstractOpenMatchIT {

  @Autowired RecordingMailSender mails;
  @Autowired IncompleteEventPublications publications;
  @Autowired OpenMatchDeadlineService deadlines;

  record Ready(
      Facility f, UUID use, String organizer, String organizerSub, UUID booking, UUID match) {}

  Ready ready(String prefix, boolean approval, int day) {
    Facility f = facility(prefix, true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String sub = "ana-" + UUID.randomUUID();
    UUID booking = booked(person(sub), f, use, at(tomorrow().plusDays(day), 600));
    UUID match = opened(person(sub), f, booking, 2, 3, 10, approval);
    return new Ready(f, use, person(sub), sub, booking, match);
  }

  List<MimeMessage> subjects(String address, String prefix) {
    return mails.sentTo(address).stream()
        .filter(
            m -> {
              try {
                return m.getSubject().startsWith(prefix);
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  UUID playerId(String token, Facility f, UUID match) {
    return UUID.fromString(mine(token, f, match).json().get("me").get("playerId").asString());
  }

  int deliveries(UUID municipality, String kind) {
    return count(
        "select count(*) from notification_delivery where kind = '"
            + kind
            + "' and status = 'SENT' and municipality_id = :k",
        municipality);
  }

  @Test
  void requestsAndDecisionsGoToTheRightPersonInTheirLanguage() throws Exception {
    Ready r = ready("correo-partida", true, 2);
    String xan = "xan-" + UUID.randomUUID();
    String bea = "bea-" + UUID.randomUUID();

    assertThat(enter(person(xan), r.f(), r.match()).status()).isEqualTo(201);
    await(() -> subjects(email(r.organizerSub()), "Alguén quere unirse").size() == 1);
    MimeMessage request = subjects(email(r.organizerSub()), "Alguén quere unirse").getFirst();
    assertThat(request.getSubject()).isEqualTo("Alguén quere unirse: Pachanga");
    assertThat(WaitMailIT.text(request)).contains("Persoa " + xan + " pediu unirse á túa partida.");

    assertThat(
            decide(r.organizer(), r.f(), r.match(), playerId(person(xan), r.f(), r.match()), true)
                .status())
        .isEqualTo(200);
    await(() -> subjects(email(xan), "Xa estás na partida").size() == 1);
    assertThat(WaitMailIT.text(subjects(email(xan), "Xa estás na partida").getFirst()))
        .contains("Persoa " + r.organizerSub() + " aceptou a túa solicitude.")
        .contains("Xógase se hai 3 persoas antes do");

    String beaToken = TestTokens.person(bea, true, "es");
    assertThat(enter(beaToken, r.f(), r.match()).status()).isEqualTo(201);
    assertThat(
            decide(r.organizer(), r.f(), r.match(), playerId(beaToken, r.f(), r.match()), false)
                .status())
        .isEqualTo(200);
    await(() -> subjects(email(bea), "No has entrado en la partida").size() == 1);

    assertThat(hide(r.f(), r.booking(), true).status()).isEqualTo(200);
    await(() -> subjects(email(r.organizerSub()), "Ocultamos a túa partida").size() == 1);
    assertThat(hide(r.f(), r.booking(), false).status()).isEqualTo(200);
    settle();
    assertThat(subjects(email(r.organizerSub()), "Ocultamos a túa partida")).hasSize(1);
    assertThat(subjects(email(r.organizerSub()), "Alguén quere unirse"))
        .as("unha solicitude, un correo")
        .hasSize(2);
  }

  @Test
  void cancellingTheBookingTellsWhoIsInside() throws Exception {
    Ready r = ready("correo-cancela", false, 2);
    String xan = "xan-" + UUID.randomUUID();
    assertThat(enter(person(xan), r.f(), r.match()).status()).isEqualTo(201);

    assertThat(cancelMine(r.organizer(), r.f(), r.booking()).status()).isEqualTo(200);

    await(() -> subjects(email(xan), "Partida cancelada").size() == 1);
    assertThat(WaitMailIT.text(subjects(email(xan), "Partida cancelada").getFirst()))
        .contains("Cancelouse a reserva da pista e, con ela, a partida.");
    settle();
    assertThat(subjects(email(r.organizerSub()), "Partida cancelada"))
        .as("quen organiza recibe o correo da súa reserva")
        .isEmpty();
  }

  @Test
  void withoutTheMinimumEveryoneGetsTheMatchMailAndNotTheBookingOne() throws Exception {
    Ready r = ready("correo-prazo", false, 1);
    String xan = "xan-" + UUID.randomUUID();
    assertThat(enter(person(xan), r.f(), r.match()).status()).isEqualTo(201);
    // 2 + 1 de 3 xa chega: que falte un.
    jdbc.sql("update booking_open_match set minimum = 6 where id = :id")
        .param("id", r.match())
        .update();
    jdbc.sql("update municipality set open_match_cancel_hours = 72 where id = :id")
        .param("id", r.f().municipality())
        .update();

    deadlines.apply();

    await(
        () ->
            subjects(email(xan), "Partida cancelada").size() == 1
                && subjects(email(r.organizerSub()), "Partida cancelada").size() == 1);
    assertThat(WaitMailIT.text(subjects(email(r.organizerSub()), "Partida cancelada").getFirst()))
        .contains("Non chegou a 6 persoas antes do");
    settle();
    assertThat(subjects(email(r.organizerSub()), "O concello cancelou")).isEmpty();
  }

  @Test
  void movingTheBookingTellsWhoIsInside() throws Exception {
    Ready r = ready("correo-move", false, 2);
    String xan = "xan-" + UUID.randomUUID();
    assertThat(enter(person(xan), r.f(), r.match()).status()).isEqualTo(201);
    String manager = TestTokens.manager(r.f().slug());
    long version =
        api().get(bookings(r.f().slug()) + "/" + r.booking()).token(manager).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", r.use());
    body.put("start", at(tomorrow().plusDays(2), 720).toString());
    body.put("slots", 1);
    Api.Response moved =
        api()
            .put(bookings(r.f().slug()) + "/" + r.booking(), body)
            .ifMatch(version)
            .token(manager)
            .send();
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);

    await(() -> subjects(email(xan), "Cambiou a partida").size() == 1);
    assertThat(WaitMailIT.text(subjects(email(xan), "Cambiou a partida").getFirst()))
        .contains("12:00")
        .doesNotContain("Xa non se busca máis xente.");
  }

  @Test
  void aFailedMailIsTriedOnceMoreAndNeverTwiceToWhoGotIt() throws Exception {
    Ready r = ready("correo-reintento", true, 2);
    String xan = "xan-" + UUID.randomUUID();
    await(() -> !mails.sentTo(email(r.organizerSub())).isEmpty());
    mails.failNext(1);

    assertThat(enter(person(xan), r.f(), r.match()).status()).isEqualTo(201);
    await(
        () ->
            count(
                    "select count(*) from notification_delivery where kind = 'MATCH_REQUESTED'"
                        + " and status = 'FAILED' and municipality_id = :k",
                    r.f().municipality())
                == 1);
    UUID player = playerId(person(xan), r.f(), r.match());
    publications.resubmitIncompletePublications(
        p -> p.getEvent() instanceof OpenMatchRequested e && e.playerId().equals(player));
    await(() -> deliveries(r.f().municipality(), "MATCH_REQUESTED") == 1);
    publications.resubmitIncompletePublications(
        p -> p.getEvent() instanceof OpenMatchRequested e && e.playerId().equals(player));
    settle();
    assertThat(subjects(email(r.organizerSub()), "Alguén quere unirse")).hasSize(1);
  }
}
