// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.notification.application.service.JoinRequestRetryService;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * «¿Es un concello?» de punta a punta (ADR-0042), coa función activa ({@code
 * furar.join-request.email} en application-test.yaml): 202 e un correo a quen opera o servizo con
 * asunto, campos e {@code Reply-To}, no idioma da web; 400 cos erros por campo; o campo trampa non
 * envía nada; o límite por IP e día é 429 con {@code Retry-After}; enviada, nada queda na base de
 * datos; e se o correo non sae, gárdase, o reintento envíaa e bórraa (revisión do 2026-11-17). Cada
 * test pide desde a súa IP ({@code X-Forwarded-For}) para non gastar o límite dos outros.
 */
class MunicipalityJoinRequestIT extends AbstractIT {

  static final String URL = "/api/v1/municipality-join-requests";
  static final String OPERATOR = "altas@example.org";
  static final AtomicInteger NEXT_IP = new AtomicInteger(1);

  @Autowired RecordingMailSender mails;
  @Autowired JoinRequestRetryService retry;

  static String ip() {
    return "198.51.100." + NEXT_IP.getAndIncrement();
  }

  static Map<String, Object> body(String municipality) {
    Map<String, Object> body = new HashMap<>();
    body.put("municipality", municipality);
    body.put("name", "Ana Rey");
    body.put("role", "Concelleira de deportes");
    body.put("email", "ana@example.org");
    body.put("phone", "981 000 000");
    body.put("message", "Temos seis instalacións");
    body.put("website", "");
    return body;
  }

  Api.Response send(Map<String, Object> body, String ip) {
    return api().post(URL, body).header("X-Forwarded-For", ip).send();
  }

  List<MimeMessage> mailsAbout(String municipality) {
    return mailsWithSubject("Solicitude de alta: " + municipality);
  }

  List<MimeMessage> mailsWithSubject(String subject) {
    return mails.sentTo(OPERATOR).stream()
        .filter(
            m -> {
              try {
                return m.getSubject().equals(subject);
              } catch (MessagingException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  static String unique() {
    return "Concello de Proba " + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void aValidRequestIsA202AndAMailToTheOperatorWithReplyToThePerson() throws Exception {
    String municipality = unique();
    Api.Response response = send(body(municipality), ip());
    assertThat(response.status()).as(response.toString()).isEqualTo(202);

    List<MimeMessage> sent = mailsAbout(municipality);
    assertThat(sent).hasSize(1);
    MimeMessage message = sent.getFirst();
    assertThat(message.getReplyTo()[0].toString()).isEqualTo("ana@example.org");
    BookingMailIT.Mail mail = BookingMailIT.open(message);
    assertThat(mail.text())
        .contains("Concello ou entidade: " + municipality)
        .contains("Nome e apelidos: Ana Rey")
        .contains("Cargo: Concelleira de deportes")
        .contains("Correo: ana@example.org")
        .contains("Teléfono: 981 000 000")
        .contains("Mensaxe: Temos seis instalacións")
        .contains("Data: ");
  }

  @Test
  void theRequestIsNotStoredAnywhere() {
    String municipality = unique();
    // Un correo só desta proba: outros IT envían a ana@example.org.
    String email = "alta-" + UUID.randomUUID() + "@example.org";
    Map<String, Object> body = body(municipality);
    body.put("email", email);
    assertThat(send(body, ip()).status()).isEqualTo(202);
    // Nin a solicitude nin o envío: ningunha táboa garda o correo de quen a pide.
    Long rows =
        jdbc.sql(
                """
                select count(*) from notification_delivery where recipient = :email
                """)
            .param("email", email)
            .query(Long.class)
            .single();
    assertThat(rows).isZero();
    Long audit =
        jdbc.sql("select count(*) from audit_event where details::text like :m")
            .param("m", "%" + municipality + "%")
            .query(Long.class)
            .single();
    assertThat(audit).isZero();
    assertThat(kept(municipality)).isZero();
  }

  long kept(String municipality) {
    return jdbc.sql("select count(*) from join_request where municipality = :m")
        .param("m", municipality)
        .query(Long.class)
        .single();
  }

  @Test
  void theMailIsInTheLanguageOfTheWeb() throws Exception {
    String municipality = unique();
    Api.Response response =
        api()
            .post(URL, body(municipality))
            .header("X-Forwarded-For", ip())
            .header("Accept-Language", "es")
            .send();
    assertThat(response.status()).as(response.toString()).isEqualTo(202);
    List<MimeMessage> sent = mailsWithSubject("Solicitud de alta: " + municipality);
    assertThat(sent).hasSize(1);
    assertThat(BookingMailIT.open(sent.getFirst()).text())
        .contains("Ayuntamiento o entidad: " + municipality)
        .contains("Fecha: ");
  }

  @Test
  void missingOrMalformedFieldsAreA400AndNothingIsSent() {
    String municipality = unique();
    Map<String, Object> body = body(municipality);
    body.put("email", "ana@example");
    body.put("phone", "");
    body.remove("name");
    Api.Response response = send(body, ip());
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().get("code").asString()).isEqualTo("validation.failed");
    assertThat(response.body())
        .contains("\"field\":\"email\"")
        .contains("\"field\":\"phone\"")
        .contains("\"field\":\"name\"");
    assertThat(mailsAbout(municipality)).isEmpty();
  }

  @Test
  void theTrapFieldIsAcceptedButNothingIsSent() {
    String municipality = unique();
    Map<String, Object> body = body(municipality);
    body.put("website", "https://spam.example.org");
    assertThat(send(body, ip()).status()).isEqualTo(202);
    assertThat(mailsAbout(municipality)).isEmpty();
  }

  @Test
  void afterFiveRequestsInADayTheSameAddressGetsA429WithRetryAfter() {
    String ip = ip();
    for (int i = 0; i < 5; i++) {
      assertThat(send(body(unique()), ip).status()).isEqualTo(202);
    }
    String municipality = unique();
    Api.Response refused = send(body(municipality), ip);
    assertThat(refused.status()).as(refused.toString()).isEqualTo(429);
    assertThat(refused.json().get("code").asString())
        .isEqualTo("notification.join-request-rate-limited");
    assertThat(Long.parseLong(refused.header("Retry-After"))).isBetween(1L, 25 * 3600L);
    assertThat(mailsAbout(municipality)).isEmpty();
    // Outro enderezo, á súa conta.
    assertThat(send(body(unique()), ip()).status()).isEqualTo(202);
  }

  @Test
  void aDownMailServerKeepsTheRequestAndTheRetrySendsAndDeletesIt() throws Exception {
    String municipality = unique();
    mails.failNext(1);
    Api.Response response = send(body(municipality), ip());
    // A persoa ve o mesmo ca se saíse: «Recibimos a solicitude».
    assertThat(response.status()).as(response.toString()).isEqualTo(202);
    assertThat(mailsAbout(municipality)).isEmpty();
    assertThat(kept(municipality)).isOne();

    // O reintento só colle as que tocan: as gardadas agora esperan ata a volta seguinte.
    jdbc.sql(
            """
            update join_request set next_attempt_at = now() - interval '1 minute'
             where municipality = :m
            """)
        .param("m", municipality)
        .update();
    retry.apply();

    assertThat(mailsAbout(municipality)).hasSize(1);
    assertThat(BookingMailIT.open(mailsAbout(municipality).getFirst()).text())
        .contains("Concello ou entidade: " + municipality);
    assertThat(kept(municipality)).isZero();
  }

  @Test
  void anUnsentRequestIsDeletedAfterTheRetention() {
    String municipality = unique();
    mails.failNext(1);
    assertThat(send(body(municipality), ip()).status()).isEqualTo(202);
    jdbc.sql(
            """
            update join_request
               set received_at = now() - interval '8 days',
                   next_attempt_at = now() + interval '1 hour'
             where municipality = :m
            """)
        .param("m", municipality)
        .update();
    retry.apply();
    assertThat(kept(municipality)).isZero();
    assertThat(mailsAbout(municipality)).isEmpty();
  }

  @Test
  void theWebKnowsTheFeatureExistsButNeverTheAddress() {
    Api.Response settings = api().get("/api/v1/deployment-settings").send();
    assertThat(settings.json().get("joinRequests").asBoolean()).isTrue();
    assertThat(settings.body()).doesNotContain(OPERATOR);
  }
}
