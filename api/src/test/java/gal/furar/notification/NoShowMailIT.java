// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractAttendanceIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O correo de cada falta de punta a punta (ADR-0032, punto 10): sae despois de marcar a falta, sen
 * QR nin {@code .ics}, con cantas leva a persoa e cando se bloquea se o concello penaliza; unha soa
 * vez por falta; tamén ás reservas do mostrador con correo (sen o botón se non hai conta) e nunca
 * sen correo.
 */
class NoShowMailIT extends AbstractAttendanceIT {

  @Autowired RecordingMailSender mails;

  UUID ended(Api.Response booked, int hoursAgo) {
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID id = id(booked.json());
    move(id, Instant.now().minus(Duration.ofHours(hoursAgo + 1L)));
    return id;
  }

  @Test
  void eachFaultSendsOneMailWithTheCountAndThenTheBlock() throws Exception {
    Facility f = facility("correo-falta", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    penalty(f, 2, 30, 15, Instant.now().minus(Duration.ofDays(10)));
    String subject = "p-" + UUID.randomUUID();
    String ana = person(subject);
    String email = subject + "@example.org";

    UUID first = ended(reserve(ana, f, use, at(tomorrow(), OPENS)), 2);
    BookingMailIT.await(() -> mails.sentTo(email).size() >= 1);
    assertThat(noShow(staff, f.slug(), first).status()).isEqualTo(200);

    BookingMailIT.await(() -> mails.sentTo(email).size() >= 2);
    BookingMailIT.Mail fault = BookingMailIT.open(mails.sentTo(email).get(1));
    assertThat(fault.subject())
        .startsWith("Non te presentaches: Pavillón do Alto, ")
        .contains(" ás ");
    assertThat(fault.html())
        .contains("Non te presentaches", "Non usaches a túa reserva en Concello " + f.slug() + ".")
        .contains(
            "Levas 1 falta en 30 días. Con 2 non poderás reservar por internet durante 15 días.")
        .contains(">Ver as miñas reservas</a>")
        .doesNotContain("cid:qr", "Podes cancelala");
    assertThat(fault.inlineIds()).containsExactly("<shield>");
    assertThat(fault.attachments()).as("sen .ics").isEmpty();
    assertThat(fault.text()).contains("Levas 1 falta");

    // A segunda bloquea: o correo di ata cando.
    UUID second = ended(reserve(ana, f, use, at(tomorrow(), OPENS + 60)), 3);
    BookingMailIT.await(() -> mails.sentTo(email).size() >= 3);
    assertThat(noShow(staff, f.slug(), second).status()).isEqualTo(200);
    BookingMailIT.await(() -> mails.sentTo(email).size() >= 4);
    BookingMailIT.Mail blocked = BookingMailIT.open(mails.sentTo(email).get(3));
    assertThat(blocked.html())
        .contains("Levas 2 faltas en 30 días: non podes reservar por internet ata o ")
        .contains("No mostrador si podes, e as reservas que xa tes mantéñense.");

    BookingMailIT.settle();
    assertThat(mails.sentTo(email)).as("un correo por falta").hasSize(4);
    assertThat(kinds(first)).containsExactly("CONFIRMED SENT", "NO_SHOW SENT");
    // O detalle da xestión di cal foi o último correo.
    assertThat(detail(staff, f.slug(), first).get("lastMail").get("kind").asString())
        .isEqualTo("NO_SHOW");
  }

  @Test
  void aCounterBookingWithEmailIsWarnedWithoutTheButtonAndWithoutEmailThereIsNoMail()
      throws Exception {
    Facility f = facility("correo-falta-mostrador", true);
    UUID use = whole(f, Map.of());
    String staff = TestTokens.staff(f.slug());
    String email = "sen-conta-" + UUID.randomUUID() + "@example.org";

    UUID withEmail = ended(bookFor(f, use, at(tomorrow(), OPENS), "Brais Souto", email), 2);
    UUID withoutEmail = ended(bookFor(f, use, at(tomorrow(), OPENS + 60), "Noa Castro", null), 3);
    BookingMailIT.await(() -> mails.sentTo(email).size() >= 1);
    assertThat(noShow(staff, f.slug(), withEmail).status()).isEqualTo(200);
    assertThat(noShow(staff, f.slug(), withoutEmail).status()).isEqualTo(200);

    BookingMailIT.await(() -> mails.sentTo(email).size() >= 2);
    MimeMessage message = mails.sentTo(email).get(1);
    BookingMailIT.Mail fault = BookingMailIT.open(message);
    assertThat(fault.subject()).startsWith("Non te presentaches: ");
    assertThat(fault.html())
        .as("coa penalización apagada, sen a frase do bloqueo; sen conta, sen o botón")
        .contains("Non usaches a túa reserva en ")
        .doesNotContain("Levas ", "Ver as miñas reservas");
    BookingMailIT.settle();
    assertThat(kinds(withoutEmail)).as("sen correo non hai aviso").isEmpty();
  }

  /** «TIPO ESTADO» de cada correo da reserva, na orde en que se rexistrou. */
  List<String> kinds(UUID booking) {
    return jdbc.sql(
            """
            select kind || ' ' || status from notification_delivery
            where booking_id = :id order by created_at, id
            """)
        .param("id", booking)
        .query(String.class)
        .list();
  }
}
