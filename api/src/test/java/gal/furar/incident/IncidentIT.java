// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Incidencias de punta a punta (ADR-0036): o aviso da veciñanza con foto (multipart real, o
 * tratamento das fotos das instalacións e PostgreSQL), a lista, o contador, o detalle e as fotos da
 * xestión, «Gardar» con If-Match, os correos despois do commit, quen pode que, a conservación e a
 * baixa da conta.
 */
class IncidentIT extends AbstractBookingIT {

  private static final String BOUNDARY = "furar-incident-boundary";
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @Autowired RecordingMailSender mails;

  record Raw(int status, byte[] body, @Nullable String contentType) {
    String text() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  static String base(String slug) {
    return "/api/v1/municipalities/" + slug + "/incidents";
  }

  static byte[] image(String format, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(0x2a, 0x6f, 0xb0));
    g.fillRect(0, 0, width, height);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return out.toByteArray();
  }

  /** Un aviso multipart: os campos e as fotos, coma o navegador. */
  Raw post(String path, @Nullable String token, Map<String, String> fields, List<byte[]> photos) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    fields.forEach(
        (name, value) ->
            body.writeBytes(
                ("--"
                        + BOUNDARY
                        + "\r\nContent-Disposition: form-data; name=\""
                        + name
                        + "\"\r\n\r\n"
                        + value
                        + "\r\n")
                    .getBytes(StandardCharsets.UTF_8)));
    for (byte[] photo : photos) {
      body.writeBytes(
          ("--"
                  + BOUNDARY
                  + "\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"foto\"\r\n"
                  + "Content-Type: image/png\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      body.writeBytes(photo);
      body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    body.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return send(request.build());
  }

  Raw bytes(String path, String token) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build());
  }

  static Raw send(HttpRequest request) {
    try {
      HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Raw(
          response.statusCode(),
          response.body(),
          response.headers().firstValue("Content-Type").orElse(null));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  Raw report(Facility f, String token, @Nullable UUID booking, List<byte[]> photos) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("facilityId", f.id().toString());
    if (booking != null) {
      fields.put("bookingId", booking.toString());
    }
    fields.put("type", "BREAKDOWN");
    fields.put("description", "A rede está rota polo medio.");
    return post(base(f.slug()) + "/reports", token, fields, photos);
  }

  UUID reported(Facility f, String token) {
    Raw created = report(f, token, null, List.of(image("png", 64, 48)));
    assertThat(created.status()).as(created.text()).isEqualTo(201);
    return UUID.fromString(Api.JSON.readTree(created.body()).get("id").asString());
  }

  Api.Response review(String slug, String token, UUID id, long version, Map<String, Object> body) {
    return api().put(base(slug) + "/" + id, body).ifMatch(version).token(token).send();
  }

  String managerEmail(Facility f) {
    return jdbc.sql(
            "select email from staff_member where municipality_id = :m and role = 'MANAGER'"
                + " and status = 'ACTIVE'")
        .param("m", f.municipality())
        .query(String.class)
        .list()
        .getFirst();
  }

  List<String> subjects(String address) {
    return mails.sentTo(address).stream()
        .map(
            m -> {
              try {
                return m.getSubject();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  static String text(Part part) throws Exception {
    if (part.isMimeType("text/plain")) {
      return (String) part.getContent();
    }
    if (part.getContent() instanceof Multipart multipart) {
      StringBuilder all = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart child = multipart.getBodyPart(i);
        all.append(text(child));
      }
      return all.toString();
    }
    return "";
  }

  long audited(Facility f, String action) {
    return jdbc.sql("select count(*) from audit_event where municipality_id = :m and action = :a")
        .param("m", f.municipality())
        .param("a", action)
        .query(Long.class)
        .single();
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    while (!condition.getAsBoolean()) {
      assertThat(Instant.now()).as("agardando").isBefore(deadline);
      Thread.sleep(50);
    }
  }

  @Test
  void aResidentReportsWithAPhotoAndTheManagementSeesItAndIsTold() throws Exception {
    Facility f = facility("inc-aviso", true);
    whole(f, Map.of());
    String manager = TestTokens.manager(f.slug());
    String email = managerEmail(f);
    String subject = "v-" + UUID.randomUUID();

    UUID id = reported(f, person(subject));

    Api.Response list = api().get(base(f.slug())).token(manager).send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    JsonNode item = list.json().get(0);
    assertThat(item.get("id").asString()).isEqualTo(id.toString());
    assertThat(item.get("status").asString()).isEqualTo("NEW");
    assertThat(item.get("origin").asString()).isEqualTo("RESIDENT");
    assertThat(item.get("facilityName").asString()).isEqualTo("Pavillón do Alto");
    assertThat(item.get("reporter").get("email").asString()).isEqualTo(subject + "@example.org");
    assertThat(item.get("photos")).hasSize(1);
    assertThat(
            api()
                .get(base(f.slug()) + "/new-count")
                .token(manager)
                .send()
                .json()
                .get("count")
                .asLong())
        .isEqualTo(1);

    String photo = item.get("photos").get(0).get("id").asString();
    Raw thumb = bytes(base(f.slug()) + "/" + id + "/photos/" + photo + "/thumb", manager);
    assertThat(thumb.status()).isEqualTo(200);
    assertThat(thumb.contentType()).isEqualTo("image/jpeg");
    // PNG á entrada, JPEG sen metadatos á saída (o mesmo tratamento ca as instalacións).
    assertThat(thumb.body()[0]).isEqualTo((byte) 0xFF);
    assertThat(thumb.body()[1]).isEqualTo((byte) 0xD8);
    assertThat(bytes(base(f.slug()) + "/" + id + "/photos/" + photo + "/thumb", person()).status())
        .isEqualTo(403);

    await(() -> subjects(email).contains("Nova incidencia: Pavillón do Alto"));
    MimeMessage mail = mails.sentTo(email).getLast();
    assertThat(text(mail)).doesNotContain("A rede está rota").doesNotContain(subject);
    assertThat(audited(f, "incident.create")).isEqualTo(1);
    String details =
        jdbc.sql("select details::text from audit_event where municipality_id = :m and action = :a")
            .param("m", f.municipality())
            .param("a", "incident.create")
            .query(String.class)
            .single();
    assertThat(details).doesNotContain("rede").doesNotContain("@example.org");
  }

  @Test
  void receptionResolvesAndThePersonGetsTheMessageOnce() throws Exception {
    Facility f = facility("inc-resolve", true);
    whole(f, Map.of());
    String subject = "v-" + UUID.randomUUID();
    UUID id = reported(f, person(subject));
    String reception = TestTokens.staff(f.slug());

    Api.Response detail = api().get(base(f.slug()) + "/" + id).token(reception).send();
    assertThat(detail.status()).as(detail.toString()).isEqualTo(200);
    assertThat(detail.etagVersion()).isZero();

    Api.Response progress =
        review(
            f.slug(), reception, id, 0, Map.of("status", "IN_PROGRESS", "internalNote", "Avisado"));
    assertThat(progress.status()).as(progress.toString()).isEqualTo(200);
    assertThat(progress.json().get("internalNote").asString()).isEqualTo("Avisado");

    Api.Response stale = review(f.slug(), reception, id, 0, Map.of("status", "RESOLVED"));
    assertThat(stale.status()).isEqualTo(412);

    Api.Response resolved =
        review(
            f.slug(),
            reception,
            id,
            1,
            Map.of("status", "RESOLVED", "resolutionMessage", "Cambiamos a rede esta mañá."));
    assertThat(resolved.status()).as(resolved.toString()).isEqualTo(200);
    assertThat(resolved.json().get("resolvedAt").isNull()).isFalse();

    String email = subject + "@example.org";
    await(() -> subjects(email).contains("Resolvemos o teu aviso: Pavillón do Alto"));
    assertThat(text(mails.sentTo(email).getLast())).contains("Cambiamos a rede esta mañá.");
    assertThat(audited(f, "incident.status")).isEqualTo(1);
    assertThat(audited(f, "incident.resolve")).isEqualTo(1);

    // Reabrir e resolver outra vez non volve escribir.
    review(f.slug(), reception, id, 2, Map.of("status", "NEW"));
    review(f.slug(), reception, id, 3, Map.of("status", "RESOLVED"));
    Thread.sleep(1500);
    assertThat(subjects(email)).filteredOn(s -> s.startsWith("Resolvemos")).hasSize(1);
  }

  @Test
  void onlyTheStaffOfTheMunicipalitySeesIncidents() {
    Facility f = facility("inc-acceso", true);
    whole(f, Map.of());
    UUID id = reported(f, person());
    String list = base(f.slug());

    assertThat(report(f, null, null, List.of()).status()).isEqualTo(401);
    assertThat(api().get(list).send().status()).isEqualTo(401);
    assertThat(api().get(list).token(person()).send().status()).isEqualTo(403);
    // A plataforma non ve os datos da veciñanza (ADR-0028, punto 9).
    assertThat(api().get(list).token(TestTokens.platformAdmin()).send().status()).isEqualTo(403);
    assertThat(api().get(list + "/" + id).token(TestTokens.platformAdmin()).send().status())
        .isEqualTo(403);

    Facility other = facility("inc-outro", true);
    String otherManager = TestTokens.manager(other.slug());
    assertThat(api().get(list).token(otherManager).send().status()).isEqualTo(403);
    assertThat(api().get(base(other.slug()) + "/" + id).token(otherManager).send().status())
        .isEqualTo(404);
    assertThat(api().get(base(other.slug())).token(otherManager).send().json()).isEmpty();
  }

  @Test
  void theReportFollowsTheRules() {
    Facility f = facility("inc-regras", true);
    UUID use = whole(f, Map.of());
    String ana = person();

    Raw many =
        report(
            f,
            ana,
            null,
            List.of(
                image("png", 8, 8), image("png", 8, 8), image("png", 8, 8), image("png", 8, 8)));
    assertThat(many.status()).as(many.text()).isEqualTo(400);

    Raw gif = report(f, ana, null, List.of(image("gif", 8, 8)));
    assertThat(gif.status()).isEqualTo(415);

    Map<String, String> unknown = new LinkedHashMap<>();
    unknown.put("facilityId", UUID.randomUUID().toString());
    unknown.put("type", "OTHER");
    unknown.put("description", "Algo");
    Raw missing = post(base(f.slug()) + "/reports", ana, unknown, List.of());
    assertThat(missing.status()).isEqualTo(422);
    assertThat(missing.text()).contains("incident.facility-not-found");

    // A reserva ten que ser súa e xa empezada.
    Api.Response booked = reserve(ana, f, use, at(tomorrow(), OPENS));
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID booking = id(booked.json());
    assertThat(report(f, ana, booking, List.of()).text()).contains("incident.booking-invalid");
    jdbc.sql(
            "update booking set period = tstzrange(now() - interval '2 hours',"
                + " now() - interval '1 hour', '[)'), cancel_until = now() - interval '2 hours'"
                + " where id = :id")
        .param("id", booking)
        .update();
    assertThat(report(f, person(), booking, List.of()).text()).contains("incident.booking-invalid");
    Raw own = report(f, ana, booking, List.of());
    assertThat(own.status()).as(own.text()).isEqualTo(201);
    UUID id = UUID.fromString(Api.JSON.readTree(own.body()).get("id").asString());
    JsonNode detail =
        api().get(base(f.slug()) + "/" + id).token(TestTokens.manager(f.slug())).send().json();
    assertThat(detail.get("booking").get("id").asString()).isEqualTo(booking.toString());
    assertThat(detail.get("booking").get("useName").asString()).isEqualTo("Pista enteira");
  }

  @Test
  void theStaffRecordsAnIncidentInAnyFacility() {
    Facility f = facility("inc-persoal", true);
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("facilityId", f.id().toString());
    fields.put("type", "SAFETY");
    fields.put("description", "Valado solto na entrada.");
    Raw created = post(base(f.slug()), TestTokens.staff(f.slug()), fields, List.of());
    assertThat(created.status()).as(created.text()).isEqualTo(201);
    JsonNode body = Api.JSON.readTree(created.body());
    assertThat(body.get("origin").asString()).isEqualTo("STAFF");
    assertThat(body.get("type").asString()).isEqualTo("SAFETY");
    assertThat(post(base(f.slug()), person(), fields, List.of()).status()).isEqualTo(403);
    assertThat(post(base(f.slug()), TestTokens.platformAdmin(), fields, List.of()).status())
        .isEqualTo(403);
  }

  @Test
  void onlyTheManagementDeletesAPhotoOrTheIncident() {
    Facility f = facility("inc-eliminar", true);
    whole(f, Map.of());
    String manager = TestTokens.manager(f.slug());
    String reception = TestTokens.staff(f.slug());
    UUID id = reported(f, person());
    String url = base(f.slug()) + "/" + id;
    String photo =
        api().get(url).token(manager).send().json().get("photos").get(0).get("id").asString();

    assertThat(api().delete(url + "/photos/" + photo).token(reception).send().status())
        .isEqualTo(403);
    assertThat(api().delete(url).token(reception).send().status()).isEqualTo(403);
    assertThat(api().delete(url).token(TestTokens.platformAdmin()).send().status()).isEqualTo(403);

    Api.Response left = api().delete(url + "/photos/" + photo).token(manager).send();
    assertThat(left.status()).as(left.toString()).isEqualTo(200);
    assertThat(left.json().get("photos")).isEmpty();
    assertThat(left.etagVersion()).isEqualTo(1);
    assertThat(api().delete(url + "/photos/" + photo).token(manager).send().status())
        .isEqualTo(404);

    assertThat(api().delete(url).token(manager).send().status()).isEqualTo(204);
    assertThat(api().get(url).token(manager).send().status()).isEqualTo(404);
    assertThat(audited(f, "incident.photo-delete")).isEqualTo(1);
    assertThat(audited(f, "incident.delete")).isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from incident_photo_content where municipality_id = :m")
                .param("m", f.municipality())
                .query(Long.class)
                .single())
        .isZero();
  }

  /**
   * Eliminar queda ben documentado: acción propia, quen (a xestión), cando, a instalación, o tipo,
   * o día do aviso e cantas fotos tiña, sen o texto nin quen avisou; e a entrada segue no rexistro
   * do concello aínda que a incidencia xa non exista.
   */
  @Test
  void deletingLeavesAFullEntryInTheActivityLog() {
    Facility f = facility("inc-rexistro", true);
    whole(f, Map.of());
    String manager = TestTokens.manager(f.slug());
    String subject = "v-" + UUID.randomUUID();
    UUID id = reported(f, person(subject));
    String url = base(f.slug()) + "/" + id;
    String photo =
        api().get(url).token(manager).send().json().get("photos").get(0).get("id").asString();
    assertThat(api().delete(url + "/photos/" + photo).token(manager).send().status())
        .isEqualTo(200);
    Instant before = Instant.now();
    assertThat(api().delete(url).token(manager).send().status()).isEqualTo(204);
    assertThat(api().get(url).token(manager).send().status()).isEqualTo(404);

    var row =
        jdbc.sql(
                "select actor_subject, occurred_at, details::text as details from audit_event"
                    + " where municipality_id = :m and action = 'incident.delete'")
            .param("m", f.municipality())
            .query(
                (rs, n) ->
                    new String[] {
                      rs.getString("actor_subject"),
                      rs.getObject("occurred_at", java.time.OffsetDateTime.class).toString(),
                      rs.getString("details")
                    })
            .single();
    assertThat(
            jdbc.sql("select role from staff_member where municipality_id = :m and subject = :s")
                .param("m", f.municipality())
                .param("s", row[0])
                .query(String.class)
                .single())
        .isEqualTo("MANAGER");
    assertThat(java.time.OffsetDateTime.parse(row[1]).toInstant())
        .isAfterOrEqualTo(before.minusSeconds(1));
    assertThat(row[2])
        .contains(id.toString())
        .contains(f.id().toString())
        .contains("Pavillón do Alto")
        .contains("BREAKDOWN")
        .contains("incident.reportedAt")
        .contains("\"photos\": 0")
        .doesNotContain("rede")
        .doesNotContain(subject);

    JsonNode page =
        api()
            .get("/api/v1/municipalities/" + f.slug() + "/audit-events")
            .token(manager)
            .send()
            .json();
    JsonNode deleted = null;
    JsonNode photoDeleted = null;
    for (JsonNode item : page.get("items")) {
      String action = item.get("action").asString();
      if (action.equals("incident.delete")) {
        deleted = item;
      } else if (action.equals("incident.photo-delete")) {
        photoDeleted = item;
      }
    }
    assertThat(deleted).as("incident.delete no rexistro").isNotNull();
    assertThat(deleted.get("label").asString()).isEqualTo("Pavillón do Alto");
    assertThat(deleted.get("facilityId").asString()).isEqualTo(f.id().toString());
    assertThat(deleted.get("actor").toString()).contains("MANAGER");
    List<String> fields = new ArrayList<>();
    deleted.get("changes").forEach(c -> fields.add(c.get("field").asString()));
    assertThat(fields)
        .containsExactly(
            "incident.type", "incident.status", "incident.reportedAt", "incident.photos");
    assertThat(photoDeleted).as("incident.photo-delete no rexistro").isNotNull();
    assertThat(photoDeleted.get("changes").get(0).get("before").asString()).isEqualTo("1");
    assertThat(photoDeleted.get("changes").get(0).get("after").asString()).isEqualTo("0");
  }

  @Test
  void closingTheAccountRemovesThePersonAndThePhotos() {
    Facility f = facility("inc-baixa", true);
    whole(f, Map.of());
    String subject = "v-" + UUID.randomUUID();
    UUID id = reported(f, person(subject));

    jdbc.sql("select close_account(:s)").param("s", subject).query(Object.class).list();

    JsonNode detail =
        api().get(base(f.slug()) + "/" + id).token(TestTokens.manager(f.slug())).send().json();
    assertThat(detail.get("reporter").get("anonymized").asBoolean()).isTrue();
    assertThat(detail.get("reporter").get("email").isNull()).isTrue();
    assertThat(detail.get("photos")).isEmpty();
    assertThat(detail.get("description").asString()).isEqualTo("A rede está rota polo medio.");
    List<Long> left = new ArrayList<>();
    left.add(
        jdbc.sql(
                "select count(*) from incident_photo_content c join incident_photo p"
                    + " on p.id = c.photo_id where p.incident_id = :i")
            .param("i", id)
            .query(Long.class)
            .single());
    assertThat(left).containsExactly(0L);
  }
}
