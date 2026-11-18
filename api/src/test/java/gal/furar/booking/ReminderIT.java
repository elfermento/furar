// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.ReminderService;
import gal.furar.booking.domain.ReminderOptOutToken;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * O recordatorio por correo (ADR-0035): sae unha vez, 24 horas antes do prazo para cancelar de
 * balde, só ás reservas confirmadas feitas antes dese momento; a ligazón «Non quero recordatorios»
 * dáo de baixa por correo e a conta pode volver acendelo.
 */
class ReminderIT extends AbstractWaitIT {

  private static final Pattern LINK = Pattern.compile("recordatorios/baixa\\?t=([A-Za-z0-9._%-]+)");

  @Autowired RecordingMailSender mails;
  @Autowired ReminderService reminders;

  @Value("${furar.audit.hash-secret}")
  String secret;

  static String text(Part part) throws Exception {
    if (part.getContent() instanceof Multipart multipart) {
      StringBuilder all = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart body = multipart.getBodyPart(i);
        all.append(text(body));
      }
      return all.toString();
    }
    return part.isMimeType("text/plain") ? String.valueOf(part.getContent()) : "";
  }

  List<MimeMessage> remindersTo(String address) {
    return mails.sentTo(address).stream()
        .filter(
            m -> {
              try {
                return m.getSubject().startsWith("Lembra:")
                    || m.getSubject().startsWith("Recuerda:");
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  /**
   * Deixa a reserva cun prazo para cancelar de balde dentro de dúas horas (o momento do
   * recordatorio xa pasou) e feita hai días, ou agora se {@code late}.
   */
  void dueSoon(UUID booking, boolean late) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql("update booking set cancel_until = :until, created_at = :created where id = :id")
        .param("until", now.plusHours(2))
        .param("created", late ? now : now.minusDays(3))
        .param("id", booking)
        .update();
  }

  OffsetDateTime remindedAt(UUID booking) {
    return jdbc.sql("select reminded_at from booking where id = :id")
        .param("id", booking)
        .query(OffsetDateTime.class)
        .optional()
        .orElse(null);
  }

  Api.Response optOut(Facility f, String token) {
    return api()
        .post(
            "/api/v1/municipalities/" + f.slug() + "/public/reminder-opt-out",
            Map.of("token", token))
        .send();
  }

  String remindersUrl(Facility f) {
    return "/api/v1/municipalities/" + f.slug() + "/me/reminders";
  }

  @Test
  void theReminderGoesOnceWithTheDeadlineAndTheOptOutLink() throws Exception {
    Facility f = facility("recordatorio", true);
    UUID padel = whole(f, "Pádel", Map.of());
    String ana = "ana-" + UUID.randomUUID();
    UUID booking = booked(person(ana), f, padel, at(tomorrow(), 600));
    dueSoon(booking, false);

    reminders.apply();
    await(() -> remindersTo(email(ana)).size() == 1);
    assertThat(remindedAt(booking)).isNotNull();
    MimeMessage mail = remindersTo(email(ana)).getFirst();
    assertThat(mail.getSubject()).startsWith("Lembra: Pavillón do Alto, ").contains(" ás 10:00");
    String body = text(mail);
    assertThat(body)
        .contains("Tes unha reserva en ")
        .contains("Pádel")
        .contains("Se non podes ir, cancélaa antes do ")
        .contains("para que outra persoa a poida usar.")
        .contains("Ver a miña reserva")
        .contains("Non quero recordatorios")
        .doesNotContain(email(ana) + "/");
    Matcher link = LINK.matcher(body);
    assertThat(link.find()).isTrue();
    assertThat(link.group(1)).doesNotContain("example.org");

    // Nunca dúas veces: outra pasada non manda nada.
    reminders.apply();
    settle();
    assertThat(remindersTo(email(ana))).hasSize(1);
  }

  @Test
  void notForABookingMadeAfterTheMomentNorForACancelledOne() throws Exception {
    Facility f = facility("recordatorio-non", true);
    UUID padel = whole(f, "Pádel", Map.of());
    String bea = "bea-" + UUID.randomUUID();
    String token = person(bea);
    UUID late = booked(token, f, padel, at(tomorrow(), 600));
    dueSoon(late, true);
    UUID cancelled = booked(token, f, padel, at(tomorrow(), 660));
    assertThat(cancelMine(token, f, cancelled).status()).isEqualTo(200);
    jdbc.sql("update booking set cancel_until = :until, created_at = :created where id = :id")
        .param("until", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2))
        .param("created", OffsetDateTime.now(ZoneOffset.UTC).minusDays(3))
        .param("id", cancelled)
        .update();

    reminders.apply();
    settle();
    assertThat(remindersTo(email(bea))).isEmpty();
    assertThat(remindedAt(late)).isNull();
    assertThat(remindedAt(cancelled)).isNull();
  }

  @Test
  void theLinkTurnsRemindersOffAndTheAccountTurnsThemBackOn() throws Exception {
    Facility f = facility("recordatorio-baixa", true);
    UUID padel = whole(f, "Pádel", Map.of());
    String xan = "xan-" + UUID.randomUUID();
    String token = person(xan);
    UUID first = booked(token, f, padel, at(tomorrow(), 600));
    dueSoon(first, false);
    reminders.apply();
    await(() -> remindersTo(email(xan)).size() == 1);
    Matcher link = LINK.matcher(text(remindersTo(email(xan)).getFirst()));
    assertThat(link.find()).isTrue();
    String signed =
        java.net.URLDecoder.decode(link.group(1), java.nio.charset.StandardCharsets.UTF_8);

    assertThat(api().get(remindersUrl(f)).token(token).send().json().get("enabled").asBoolean())
        .isTrue();
    assertThat(optOut(f, signed).status()).isEqualTo(204);
    assertThat(optOut(f, signed).status()).as("repetir non cambia nada").isEqualTo(204);
    assertThat(api().get(remindersUrl(f)).token(token).send().json().get("enabled").asBoolean())
        .isFalse();

    // Xa non lle chegan.
    UUID second = booked(token, f, padel, at(tomorrow(), 660));
    dueSoon(second, false);
    reminders.apply();
    settle();
    assertThat(remindersTo(email(xan))).hasSize(1);
    assertThat(remindedAt(second)).as("marcada igual: nunca dúas veces").isNotNull();

    // Desde a conta, outra vez acendidos.
    Api.Response on = api().put(remindersUrl(f), Map.of("enabled", true)).token(token).send();
    assertThat(on.status()).as(on.toString()).isEqualTo(204);
    assertThat(api().get(remindersUrl(f)).token(token).send().json().get("enabled").asBoolean())
        .isTrue();
    UUID third = booked(token, f, padel, at(tomorrow(), 720));
    dueSoon(third, false);
    reminders.apply();
    await(() -> remindersTo(email(xan)).size() == 2);
  }

  @Test
  void aManipulatedOtherMunicipalityOrExpiredLinkDoesNothing() {
    Facility f = facility("recordatorio-mal", true);
    Facility other = facility("recordatorio-outro", true);
    UUID padel = whole(f, "Pádel", Map.of());
    String token = person();
    UUID booking = booked(token, f, padel, at(tomorrow(), 600));
    Instant now = Instant.now();
    String valid = ReminderOptOutToken.issue(secret, f.municipality(), booking, now);

    String tampered = valid.substring(0, valid.length() - 2) + "AA";
    assertThat(problem(optOut(f, tampered))).isEqualTo("422 booking.reminder-link-invalid");
    assertThat(problem(optOut(f, "v1.nada"))).isEqualTo("422 booking.reminder-link-invalid");
    assertThat(problem(optOut(other, valid))).isEqualTo("422 booking.reminder-link-invalid");
    String expired =
        ReminderOptOutToken.issue(
            secret, f.municipality(), booking, now.minus(Duration.ofDays(61)));
    assertThat(problem(optOut(f, expired))).isEqualTo("410 booking.reminder-link-expired");
    assertThat(api().get(remindersUrl(f)).token(token).send().json().get("enabled").asBoolean())
        .isTrue();
  }

  @Test
  void aWalkInBookingWithEmailIsRemindedAndOptsOutByEmail() throws Exception {
    Facility f = facility("recordatorio-mostrador", true);
    UUID padel = whole(f, "Pádel", Map.of());
    String walkIn = "mostrador-" + UUID.randomUUID() + "@example.org";
    Api.Response made = bookFor(f, padel, at(tomorrow(), 600), "Rosa Pena", walkIn);
    assertThat(made.status()).as(made.toString()).isEqualTo(201);
    UUID booking = id(made.json());
    dueSoon(booking, false);

    reminders.apply();
    await(() -> remindersTo(walkIn).size() == 1);
    String body = text(remindersTo(walkIn).getFirst());
    assertThat(body).contains("Non quero recordatorios").doesNotContain("Ver a miña reserva");
    Matcher link = LINK.matcher(body);
    assertThat(link.find()).isTrue();
    String signed =
        java.net.URLDecoder.decode(link.group(1), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(optOut(f, signed).status()).isEqualTo(204);
    assertThat(
            count(
                "select count(*) from booking_reminder_opt_out where municipality_id = :k",
                f.municipality()))
        .isEqualTo(1);
  }
}
