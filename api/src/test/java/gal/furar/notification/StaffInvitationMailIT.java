// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.RecordingMailSender;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O correo da invitación ao persoal de punta a punta (plan do dominio 4, D4-06; ADR-0027, punto 6),
 * co {@code JavaMailSender} de proba: asunto, ligazón co token no fragmento, parte de texto e o
 * rexistro en {@code notification_delivery}; un SMTP caído devolve {@code FAILED} sen excepción;
 * nunca dentro dunha transacción; e o token non chega a ningún log nin á base de datos.
 */
class StaffInvitationMailIT extends AbstractIT {

  @Autowired StaffInvitationMailer mailer;
  @Autowired RecordingMailSender mails;
  @Autowired TransactionTemplate tx;

  record Municipality(UUID id, String slug) {}

  Municipality municipality(String prefix) {
    String slug = uniqueSlug(prefix);
    return new Municipality(id(createMunicipality(slug)), slug);
  }

  /** Un token coma os de {@code staff}: 32 bytes en base64url sen recheo. */
  static String token() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static StaffInvitationMail mail(
      Municipality m, String recipient, Language language, String token) {
    return new StaffInvitationMail(
        UUID.randomUUID(),
        recipient,
        language,
        "STAFF",
        "Ana Rey",
        URI.create("http://localhost:8080/" + m.slug() + "/convite#" + token),
        Instant.now().plus(7, ChronoUnit.DAYS));
  }

  <T> T as(Municipality m, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(m.id()), work);
  }

  @Test
  void theMailCarriesSubjectLinkWithFragmentAndTextAndIsRecordedOnce() throws Exception {
    Municipality m = municipality("convite");
    String token = token();
    String to = "conserxe-" + UUID.randomUUID() + "@example.org";
    StaffInvitationMail mail = mail(m, to, Language.GL, token);

    assertThat(as(m, () -> mailer.send(mail))).isEqualTo(DeliveryResult.SENT);

    assertThat(mails.sentTo(to)).hasSize(1);
    BookingMailIT.Mail sent = BookingMailIT.open(mails.sentTo(to).getFirst());
    String link = "http://localhost:8080/" + m.slug() + "/convite#" + token;
    assertThat(sent.subject())
        .isEqualTo(
            "Ana Rey invítate a atender as reservas das instalacións deportivas do Concello de Concello "
                + m.slug());
    assertThat(sent.from()).contains("Deportes Concello " + m.slug());
    assertThat(sent.html())
        .contains("href=\"" + link + "\"", ">Crear a miña conta</a>")
        .contains("Ana Rey invítate a rexistrarte co rol Recepción")
        .contains("Usa este mesmo correo (" + to + ")")
        .contains("(hora de Galicia).");
    assertThat(sent.text())
        .contains("Crear a miña conta:\n" + link + "\n")
        .doesNotContain("Rol:")
        .contains("Se non o esperabas, ignora este correo.");
    assertThat(sent.inlineIds()).containsExactly("<shield>");
    assertThat(sent.attachments()).isEmpty();
    assertThat(deliveries(mail.invitationId()))
        .containsExactly(Map.of("kind", "STAFF_INVITATION", "status", "SENT", "recipient", to));

    // A mesma invitación outra vez: non sae outro correo nin outra fila.
    assertThat(as(m, () -> mailer.send(mail))).isEqualTo(DeliveryResult.SENT);
    assertThat(mails.sentTo(to)).hasSize(1);
    assertThat(deliveries(mail.invitationId())).hasSize(1);
  }

  @Test
  void theMailGoesInTheChosenLanguage() throws Exception {
    Municipality m = municipality("convite-es");
    String to = "gestora-" + UUID.randomUUID() + "@example.org";

    assertThat(as(m, () -> mailer.send(mail(m, to, Language.ES, token()))))
        .isEqualTo(DeliveryResult.SENT);

    BookingMailIT.Mail sent = BookingMailIT.open(mails.sentTo(to).getFirst());
    assertThat(sent.subject())
        .startsWith("Ana Rey te invita a atender las reservas de las instalaciones deportivas");
    assertThat(sent.html()).contains("<html lang=\"es\">", ">Crear mi cuenta</a>");
    assertThat(
            jdbc.sql("select language from notification_delivery where recipient = :r")
                .param("r", to)
                .query(String.class)
                .single())
        .isEqualTo("es");
  }

  @Test
  void aDownSmtpIsFailedWithoutAnException() {
    Municipality m = municipality("convite-caido");
    String to = "conserxe-" + UUID.randomUUID() + "@example.org";
    StaffInvitationMail mail = mail(m, to, Language.GL, token());

    mails.failNext(1);
    assertThat(as(m, () -> mailer.send(mail))).isEqualTo(DeliveryResult.FAILED);

    assertThat(mails.sentTo(to)).isEmpty();
    assertThat(deliveries(mail.invitationId()))
        .containsExactly(Map.of("kind", "STAFF_INVITATION", "status", "FAILED", "recipient", to));
    assertThat(
            jdbc.sql("select error from notification_delivery where invitation_id = :i")
                .param("i", mail.invitationId())
                .query(String.class)
                .single())
        .isEqualTo("MailSendException: ConnectException");
  }

  @Test
  void neverInsideATransaction() {
    Municipality m = municipality("convite-tx");
    String to = "conserxe-" + UUID.randomUUID() + "@example.org";
    StaffInvitationMail mail = mail(m, to, Language.GL, token());

    assertThatThrownBy(() -> as(m, () -> tx.execute(s -> mailer.send(mail))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside any transaction");
    assertThat(mails.sentTo(to)).isEmpty();
    assertThat(deliveries(mail.invitationId())).isEmpty();
  }

  @Test
  void theTokenReachesNoLogAndNoTable() {
    Municipality m = municipality("convite-logs");
    String sentToken = token();
    String failedToken = token();
    String to = "conserxe-" + UUID.randomUUID() + "@example.org";
    String other = "conserxe-" + UUID.randomUUID() + "@example.org";

    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    // Os loggers da aplicación e do SQL (cos parámetros) ao máximo mentres dura a proba.
    List<Logger> verbose =
        List.of(
            (Logger) LoggerFactory.getLogger("gal.furar"),
            (Logger) LoggerFactory.getLogger("org.springframework.jdbc"),
            (Logger) LoggerFactory.getLogger("org.springframework.mail"));
    List<Level> before = verbose.stream().map(Logger::getLevel).toList();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    verbose.forEach(l -> l.setLevel(Level.TRACE));
    try {
      assertThat(as(m, () -> mailer.send(mail(m, to, Language.GL, sentToken))))
          .isEqualTo(DeliveryResult.SENT);
      mails.failNext(1);
      assertThat(as(m, () -> mailer.send(mail(m, other, Language.ES, failedToken))))
          .isEqualTo(DeliveryResult.FAILED);
    } finally {
      root.detachAppender(appender);
      for (int i = 0; i < verbose.size(); i++) {
        verbose.get(i).setLevel(before.get(i));
      }
    }

    assertThat(appender.list).as("a proba ve os rexistros").isNotEmpty();
    assertThat(appender.list)
        .extracting(StaffInvitationMailIT::everything)
        .noneMatch(line -> line.contains(sentToken) || line.contains(failedToken))
        .noneMatch(line -> line.contains("/convite#"))
        .anyMatch(line -> line.contains("mail not sent"));
    for (String token : List.of(sentToken, failedToken)) {
      assertThat(
              jdbc.sql(
                      "select count(*) from notification_delivery n"
                          + " where n::text like '%' || :t || '%'")
                  .param("t", token)
                  .query(Long.class)
                  .single())
          .isZero();
      assertThat(
              jdbc.sql(
                      "select count(*) from event_publication e"
                          + " where e::text like '%' || :t || '%'")
                  .param("t", token)
                  .query(Long.class)
                  .single())
          .isZero();
    }
  }

  /** A mensaxe xa formada, os argumentos e a excepción dun rexistro. */
  static String everything(ILoggingEvent event) {
    StringBuilder all = new StringBuilder(event.getFormattedMessage());
    if (event.getArgumentArray() != null) {
      for (Object argument : event.getArgumentArray()) {
        all.append(' ').append(argument);
      }
    }
    for (var t = event.getThrowableProxy(); t != null; t = t.getCause()) {
      all.append(' ').append(t.getClassName()).append(' ').append(t.getMessage());
    }
    return all.toString();
  }

  /** Tipo, estado e destinatario de cada correo desa invitación. */
  List<Map<String, String>> deliveries(UUID invitation) {
    return jdbc.sql(
            """
            select kind, status, recipient from notification_delivery
             where invitation_id = :i and booking_id is null and attempts = 1
            """)
        .param("i", invitation)
        .query(
            (rs, row) ->
                Map.of(
                    "kind", rs.getString("kind"),
                    "status", rs.getString("status"),
                    "recipient", rs.getString("recipient")))
        .list();
  }
}
