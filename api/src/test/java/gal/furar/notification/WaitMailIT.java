// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractWaitIT;
import gal.furar.booking.BookingSlotReleased;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O correo «Quedou libre» (ADR-0033, puntos 7 e 11): a todas as persoas que esperan á vez, unha soa
 * vez por liberación, e só a quen lle toca co estado de cando sae.
 */
class WaitMailIT extends AbstractWaitIT {

  @Autowired RecordingMailSender mails;
  @Autowired IncompleteEventPublications publications;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate tx;

  /** Todo o texto do correo (as partes de texto, unha tras outra). */
  static String text(Part part) throws Exception {
    if (part.getContent() instanceof Multipart multipart) {
      StringBuilder all = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart body = multipart.getBodyPart(i);
        all.append(text(body));
      }
      return all.toString();
    }
    return part.isMimeType("text/*") ? String.valueOf(part.getContent()) : "";
  }

  static boolean hasAttachment(Part part) throws Exception {
    if (part.getContent() instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        if (hasAttachment(multipart.getBodyPart(i))) {
          return true;
        }
      }
      return false;
    }
    return part.isMimeType("text/calendar")
        || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
  }

  /** Os asuntos dos avisos «Quedou libre» que recibiu ese enderezo. */
  List<String> notices(String address) {
    return mails.sentTo(address).stream()
        .map(
            m -> {
              try {
                return m.getSubject();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .filter(s -> s.startsWith("Quedou libre") || s.startsWith("Ha quedado libre"))
        .toList();
  }

  MimeMessage notice(String address) {
    return mails.sentTo(address).stream()
        .filter(
            m -> {
              try {
                return m.getSubject().contains("libre");
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .findFirst()
        .orElseThrow();
  }

  /** Publica outra vez unha liberación, como faría outra cancelación ou un reintento. */
  void publish(Facility f, BookingSlotReleased event) {
    TenantContext.runAs(
        TenantRef.of(f.municipality()),
        () -> tx.executeWithoutResult(s -> events.publishEvent(event)));
  }

  static BookingSlotReleased again(BookingSlotReleased e, UUID releaseId, Instant releasedAt) {
    return new BookingSlotReleased(
        e.municipalityId(), releaseId, e.facilityId(), e.useId(), e.start(), e.end(), releasedAt);
  }

  /** «ESTADO intentos» de cada aviso desa liberación, por destinatario. */
  Map<String, String> deliveries(UUID releaseId) {
    return jdbc
        .sql(
            "select recipient, status || ' ' || attempts as result from notification_delivery"
                + " where kind = 'SLOT_RELEASED' and event_id = :e")
        .param("e", releaseId.toString())
        .query((rs, n) -> Map.entry(rs.getString("recipient"), rs.getString("result")))
        .list()
        .stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Test
  void everyoneWaitingIsToldAtOnceInTheirLanguageAndOnlyOnce() throws Exception {
    Facility f = facility("libre-todas", true);
    UUID use = whole(f, "Pádel", Map.of());
    LocalDate day = tomorrow();
    Instant ten = at(day, 600);
    String xan = person();
    UUID booking = booked(xan, f, use, ten);
    String ana = "ana-" + UUID.randomUUID();
    String bea = "bea-" + UUID.randomUUID();
    waiting(person(ana), f, use, ten);
    waiting(TestTokens.person(bea, true, "es"), f, use, ten);

    List<BookingSlotReleased> released =
        released(() -> assertThat(cancelMine(xan, f, booking).status()).isEqualTo(200));

    await(() -> notices(email(ana)).size() == 1 && notices(email(bea)).size() == 1);
    String dayGl = DateTimeFormatter.ofPattern("EEEE d", java.util.Locale.of("gl")).format(day);
    assertThat(notices(email(ana)))
        .containsExactly("Quedou libre: Pavillón do Alto, " + dayGl + " ás 10:00");
    assertThat(notices(email(bea)).getFirst()).startsWith("Ha quedado libre: Pavillón do Alto, ");
    MimeMessage mail = notice(email(ana));
    String body = text(mail);
    assertThat(body)
        .contains("Quedou libre a hora que esperabas")
        .contains("Quedou libre a hora pola que esperabas en ")
        .contains("Pavillón do Alto")
        .contains("Pádel")
        .contains("10:00–11:00")
        .contains("Avisamos a todas as persoas que esperaban: resérvaa a primeira que entre.")
        .contains("Reservar esta hora")
        .contains(
            "/"
                + f.slug()
                + "/instalacions/"
                + f.id()
                + "/reservar?uso="
                + use
                + "&data="
                + day
                + "&hora=10%3A00")
        .contains("Recibes este correo porque pediches que te avisásemos")
        // Sen saúdo, sen código e sen prazo de cancelación.
        .doesNotContain("Ola,", "Código", "cancelala de balde");
    assertThat(hasAttachment(mail)).as("sen .ics").isFalse();
    assertThat(text(notice(email(bea))))
        .contains(
            "Hemos avisado a todas las personas que esperaban: la reserva la primera que entre.");

    // O mesmo evento outra vez (un reintento): non repite.
    BookingSlotReleased event = released.getFirst();
    publish(f, event);
    settle();
    assertThat(notices(email(ana))).hasSize(1);
    assertThat(notices(email(bea))).hasSize(1);
    assertThat(deliveries(event.releaseId()))
        .isEqualTo(Map.of(email(ana), "SENT 1", email(bea), "SENT 1"));
    // Outra liberación da mesma hora aos poucos minutos: o mínimo entre avisos córtaa.
    publish(f, again(event, UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MICROS)));
    settle();
    assertThat(notices(email(ana))).hasSize(1);
    // Pasado o mínimo, unha liberación nova volve avisar.
    jdbc.sql(
            "update notification_delivery set sent_at = sent_at - interval '31 minutes'"
                + " where kind = 'SLOT_RELEASED' and event_id = :e")
        .param("e", event.releaseId().toString())
        .update();
    publish(f, again(event, UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MICROS)));
    await(() -> notices(email(ana)).size() == 2 && notices(email(bea)).size() == 2);
  }

  @Test
  void onlyThoseWhoShouldAreTold() throws Exception {
    Facility f = facility("libre-quen", true);
    UUID use = id(use(f, "Natación", "SEATS", Map.of("capacity", 2)));
    Instant ten = at(tomorrow(), 600);
    String xan = person();
    UUID one = booked(xan, f, use, ten);
    UUID two = booked(person(), f, use, ten);
    String ana = "ana-" + UUID.randomUUID();
    String dora = "dora-" + UUID.randomUUID();
    String eva = "eva-" + UUID.randomUUID();
    String late = "late-" + UUID.randomUUID();
    waiting(person(ana), f, use, ten);
    waiting(person(dora), f, use, ten);
    UUID evaWait = waiting(person(eva), f, use, ten);
    // Dora queda bloqueada por faltas; a outra reserva pasa a ser do correo de Eva.
    blockByNoShows(f, email(dora));
    jdbc.sql("update booking set holder_email_key = :e where id = :id")
        .param("e", email(eva))
        .param("id", two)
        .update();

    List<BookingSlotReleased> released =
        released(() -> assertThat(cancelMine(xan, f, one).status()).isEqualTo(200));

    await(() -> notices(email(ana)).size() == 1);
    settle();
    assertThat(notices(email(dora))).as("bloqueada por faltas").isEmpty();
    assertThat(notices(email(eva))).as("xa ten reserva nesa franxa").isEmpty();
    assertThat(evaWait).isNotNull();

    // Unha espera creada despois da liberación é dunha ocupación posterior: non recibe ese aviso.
    BookingSlotReleased event = released.getFirst();
    jdbc.sql(
            """
            insert into booking_wait
              (id, municipality_id, facility_id, use_id, slot_start, slot_end, account_subject,
               email_key, language, ordinal, created_at)
            values (:id, :m, :f, :u, :s, :s + interval '1 hour', :sub, :e, 'gl', 1, now())
            """)
        .param("id", UUID.randomUUID())
        .param("m", f.municipality())
        .param("f", f.id())
        .param("u", use)
        .param("s", ten.atOffset(java.time.ZoneOffset.UTC))
        .param("sub", late)
        .param("e", email(late))
        .update();
    publish(f, again(event, UUID.randomUUID(), event.releasedAt()));
    settle();
    assertThat(notices(email(late))).isEmpty();
    // Se a hora xa se colleu, a ninguén.
    booked(person(), f, use, ten);
    publish(f, again(event, UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MICROS)));
    settle();
    assertThat(notices(email(late))).isEmpty();
    assertThat(notices(email(ana))).hasSize(1);
  }

  /** Un destinatario que falla non corta os demais, e o reintento só escribe a quen falta. */
  @Test
  void aFailedRecipientDoesNotStopTheOthersAndTheRetryWritesOnlyToThem() throws Exception {
    Facility f = facility("libre-fallo", true);
    UUID use = whole(f, Map.of());
    Instant ten = at(tomorrow(), 600);
    String xan = person();
    UUID booking = booked(xan, f, use, ten);
    // A confirmación de Xan xa saíu: o fallo que vén é dun aviso.
    await(() -> !mails.sentTo(email(subjectOf(booking))).isEmpty());
    String ana = "ana-" + UUID.randomUUID();
    String bea = "bea-" + UUID.randomUUID();
    waiting(person(ana), f, use, ten);
    waiting(person(bea), f, use, ten);
    // Cancela a xestión sen avisar por correo: o único envío que pode fallar é un aviso.
    mails.failNext(1);
    List<BookingSlotReleased> released =
        released(() -> assertThat(cancelByStaff(f, booking).status()).isEqualTo(200));
    UUID release = released.getFirst().releaseId();

    await(() -> deliveries(release).size() == 2);
    assertThat(deliveries(release).values()).containsExactlyInAnyOrder("SENT 1", "FAILED 1");
    assertThat(notices(email(ana)).size() + notices(email(bea)).size()).isEqualTo(1);

    // O reintento do evento pendente, como fai MailRetry.
    publications.resubmitIncompletePublications(
        p -> p.getEvent() instanceof BookingSlotReleased r && r.releaseId().equals(release));
    await(() -> notices(email(ana)).size() == 1 && notices(email(bea)).size() == 1);
    settle();
    assertThat(deliveries(release).values()).containsExactlyInAnyOrder("SENT 1", "SENT 2");
    assertThat(notices(email(ana))).hasSize(1);
    assertThat(notices(email(bea))).hasSize(1);
    assertThat(
            count(
                "select count(*) from event_publication where completion_date is null"
                    + " and serialized_event like '%' || :k || '%'",
                release))
        .as("o evento queda pechado")
        .isZero();
  }

  String subjectOf(UUID booking) {
    return jdbc.sql("select holder_subject from booking where id = :id")
        .param("id", booking)
        .query(String.class)
        .single();
  }
}
