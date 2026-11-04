// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.booking.BookingConfirmed;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Os correos das reservas de punta a punta (plan do dominio 2, D2-09; ADR-0025, punto 15): os catro
 * correos co asunto, o {@code .ics} adxunto, o QR incrustado e a parte de texto; o idioma da
 * reserva; un SMTP caído deixa a reserva feita e o envío {@code FAILED}, e o reintento envía unha
 * soa vez; o concello só avisa por correo se o escolle.
 */
class BookingMailIT extends AbstractBookingIT {

  @Autowired RecordingMailSender mails;
  @Autowired IncompleteEventPublications publications;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate tx;

  /** O que leva un correo, xa aberto. */
  record Mail(
      String subject,
      String from,
      String html,
      String text,
      List<String> inlineIds,
      Map<String, String> attachments) {}

  @Test
  void theFourMailsCarrySubjectCalendarQrAndText() throws Exception {
    Facility f = facility("correo-catro", true);
    UUID use =
        id(
            use(
                f,
                "Pádel",
                "WHOLE",
                Map.of(
                    "price",
                    Map.of("kind", "PAID", "amountCents", 800),
                    "rules",
                    Map.of("cancelHours", 2))));
    String subject = "p-" + UUID.randomUUID();
    String ana = person(subject);
    String email = subject + "@example.org";
    Instant ten = at(tomorrow().plusDays(1), 600);

    Api.Response booked = reserve(ana, f, use, ten);
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID id = id(booked.json());
    String code = code(booked);
    Mail confirmed = awaitMail(email, 1);
    assertThat(confirmed.subject())
        .startsWith("Reserva confirmada: Pavillón do Alto, ")
        .endsWith(", 10:00");
    assertThat(confirmed.from()).contains("Deportes Concello " + f.slug());
    assertThat(confirmed.html())
        .contains("src=\"cid:qr\"", code, "8,00 € · No mostrador, ao chegar")
        .contains("Podes cancelala de balde ata o");
    assertThat(confirmed.inlineIds()).contains("<qr>", "<shield>");
    assertThat(confirmed.attachments()).containsOnlyKeys("reserva-" + code + ".ics");
    assertThat(confirmed.attachments().get("reserva-" + code + ".ics"))
        .contains("METHOD:PUBLISH", "UID:" + id);
    assertThat(confirmed.text()).contains("Código: " + code, "Pádel");

    Map<String, Object> to = new LinkedHashMap<>();
    to.put("useId", use);
    to.put("start", at(tomorrow().plusDays(1), 660).toString());
    to.put("slots", 1);
    Api.Response changed =
        api().put(mine(f.slug()) + "/" + id + "/slot", to).ifMatch(0).token(ana).send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
    Mail change = awaitMail(email, 2);
    assertThat(change.subject())
        .startsWith("Reserva cambiada: Pavillón do Alto, ")
        .endsWith("11:00");
    assertThat(change.html()).contains("✓ Reserva cambiada", ">Antes</td>", "10:00–11:00");
    assertThat(change.attachments().get("reserva-" + code + ".ics")).contains("SEQUENCE:1");

    Api.Response cancelled =
        api().post(mine(f.slug()) + "/" + id + "/cancel", null).ifMatch(1).token(ana).send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    Mail cancel = awaitMail(email, 3);
    assertThat(cancel.subject()).startsWith("Reserva cancelada: Pavillón do Alto, ");
    assertThat(cancel.html()).contains("✕ Reserva cancelada").doesNotContain("cid:qr");
    assertThat(cancel.inlineIds()).containsExactly("<shield>");
    assertThat(cancel.attachments().get("reserva-" + code + ".ics")).contains("METHOD:CANCEL");

    // O concello cancela dúas: avisa por correo só a que leva EMAIL nas canles.
    UUID byEmail = id(reserve(ana, f, use, at(tomorrow().plusDays(1), 720)).json());
    awaitMail(email, 4);
    UUID byWeb = id(reserve(ana, f, use, at(tomorrow().plusDays(1), 780)).json());
    awaitMail(email, 5);
    assertThat(cancelByMunicipality(f, byWeb, List.of("WEB")).status()).isEqualTo(200);
    assertThat(cancelByMunicipality(f, byEmail, List.of("EMAIL", "WEB")).status()).isEqualTo(200);
    Mail municipality = awaitMail(email, 6);
    assertThat(municipality.subject()).startsWith("O concello cancelou a túa reserva: ");
    assertThat(municipality.html())
        .contains("Elixir outra hora", "/instalacions/" + f.id() + "/reservar");
    settle();
    assertThat(mails.sentTo(email)).as("ningún correo pola cancelación só na web").hasSize(6);

    assertThat(deliveries(id))
        .containsExactly(
            "CONFIRMED once SENT 1", "CHANGED v1 SENT 1", "CANCELLED_BY_PERSON once SENT 1");
    assertThat(deliveries(byEmail))
        .containsExactly("CONFIRMED once SENT 1", "CANCELLED_BY_MUNICIPALITY once SENT 1");
    assertThat(deliveries(byWeb)).containsExactly("CONFIRMED once SENT 1");
  }

  @Test
  void theMailGoesInTheLanguageOfTheBooking() throws Exception {
    Facility f = facility("correo-es", true);
    UUID use = whole(f, Map.of());
    String subject = "p-" + UUID.randomUUID();

    Api.Response booked =
        reserve(TestTokens.person(subject, true, "es"), f, use, at(tomorrow(), 600));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);

    Mail mail = awaitMail(subject + "@example.org", 1);
    assertThat(mail.subject()).startsWith("Reserva confirmada: Pavillón do Alto, ");
    assertThat(mail.html())
        .contains("<html lang=\"es\">", "Hola, Persoa:", "Enséñalo al llegar", "Ver mi reserva")
        .doesNotContain("Ensínao", "Ver a miña reserva");
    assertThat(mail.text()).contains("Ver mi reserva: http://localhost:8080/" + f.slug());
    assertThat(
            jdbc.sql("select language from notification_delivery where booking_id = :b")
                .param("b", id(booked.json()))
                .query(String.class)
                .single())
        .isEqualTo("es");
  }

  @Test
  void aDownSmtpKeepsTheBookingAndTheRetrySendsOnce() throws Exception {
    Facility f = facility("correo-caido", true);
    UUID use = whole(f, Map.of());
    String subject = "p-" + UUID.randomUUID();
    String email = subject + "@example.org";

    mails.failNext(1);
    Api.Response booked = reserve(person(subject), f, use, at(tomorrow(), 600));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID id = id(booked.json());
    await(() -> deliveries(id).equals(List.of("CONFIRMED once FAILED 1")));
    assertThat(count("select count(*) from booking where id = :k and status = 'CONFIRMED'", id))
        .isEqualTo(1);
    assertThat(mails.sentTo(email)).isEmpty();
    assertThat(
            jdbc.sql("select error from notification_delivery where booking_id = :b")
                .param("b", id)
                .query(String.class)
                .single())
        .isEqualTo("MailSendException: ConnectException")
        .doesNotContain(email);

    retry(id);
    awaitMail(email, 1);
    await(() -> deliveries(id).equals(List.of("CONFIRMED once SENT 2")));

    // Outro reintento ou o mesmo evento outra vez: xa está enviado, non sae outro.
    retry(id);
    TenantContext.runAs(
        TenantRef.of(f.municipality()),
        () ->
            tx.executeWithoutResult(
                s -> events.publishEvent(new BookingConfirmed(f.municipality(), id))));
    settle();
    assertThat(mails.sentTo(email)).hasSize(1);
    assertThat(deliveries(id)).containsExactly("CONFIRMED once SENT 2");
  }

  @Test
  void aMailThatFailsTwiceIsGivenUp() throws Exception {
    Facility f = facility("correo-dous-fallos", true);
    UUID use = whole(f, Map.of());
    String subject = "p-" + UUID.randomUUID();

    mails.failNext(2);
    UUID id = id(reserve(person(subject), f, use, at(tomorrow(), 600)).json());
    await(() -> deliveries(id).equals(List.of("CONFIRMED once FAILED 1")));
    retry(id);
    await(() -> deliveries(id).equals(List.of("CONFIRMED once FAILED 2")));
    retry(id);
    settle();

    assertThat(mails.sentTo(subject + "@example.org")).isEmpty();
    assertThat(deliveries(id)).containsExactly("CONFIRMED once FAILED 2");
    assertThat(
            count(
                "select count(*) from event_publication where completion_date is null"
                    + " and serialized_event like '%' || :k || '%'",
                id))
        .as("o evento queda pechado: non se reintenta máis")
        .isZero();
  }

  // --- Axudas ---

  Api.Response cancelByMunicipality(Facility f, UUID booking, List<String> channels) {
    return api()
        .post(
            "/api/v1/municipalities/" + f.slug() + "/bookings/" + booking + "/cancel",
            Map.of("channels", channels))
        .ifMatch(0)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  /** Volve entregar os eventos pendentes desta reserva, como fai o reintento programado. */
  void retry(UUID booking) {
    publications.resubmitIncompletePublications(
        p -> p.getEvent() instanceof BookingConfirmed c && c.bookingId().equals(booking));
  }

  /** «TIPO evento ESTADO intentos» de cada correo da reserva, na orde en que se rexistrou. */
  List<String> deliveries(UUID booking) {
    return jdbc.sql(
            """
            select kind || ' ' || event_id || ' ' || status || ' ' || attempts
              from notification_delivery where booking_id = :b order by created_at
            """)
        .param("b", booking)
        .query(String.class)
        .list();
  }

  /** O correo número {@code n} (desde 1) a ese enderezo, agardando a que chegue. */
  Mail awaitMail(String address, int n) throws Exception {
    await(() -> mails.sentTo(address).size() >= n);
    return open(mails.sentTo(address).get(n - 1));
  }

  /** Dálles tempo ás escoitas asíncronas que puidesen quedar. */
  static void settle() throws InterruptedException {
    Thread.sleep(1500);
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    while (!condition.getAsBoolean()) {
      assertThat(Instant.now()).as("agardando").isBefore(deadline);
      Thread.sleep(50);
    }
  }

  static Mail open(MimeMessage message) throws Exception {
    Map<String, String> found = new LinkedHashMap<>();
    List<String> inline = new ArrayList<>();
    Map<String, String> attachments = new LinkedHashMap<>();
    walk(message, found, inline, attachments);
    return new Mail(
        message.getSubject(),
        message.getFrom()[0].toString(),
        found.getOrDefault("html", ""),
        found.getOrDefault("text", ""),
        inline,
        attachments);
  }

  private static void walk(
      Part part, Map<String, String> found, List<String> inline, Map<String, String> attachments)
      throws Exception {
    if (part.getContent() instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart child = multipart.getBodyPart(i);
        walk(child, found, inline, attachments);
      }
      return;
    }
    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      part.getDataHandler().writeTo(out);
      attachments.put(part.getFileName(), out.toString(java.nio.charset.StandardCharsets.UTF_8));
    } else if (part.getHeader("Content-ID") != null) {
      inline.add(part.getHeader("Content-ID")[0]);
    } else if (part.isMimeType("text/html")) {
      found.put("html", (String) part.getContent());
    } else if (part.isMimeType("text/plain")) {
      found.put("text", (String) part.getContent());
    }
  }
}
