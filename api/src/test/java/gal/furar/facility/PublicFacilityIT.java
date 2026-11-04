// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Listado público (portada da veciñanza) de punta a punta: sen sesión, só o publicado (nin borrador
 * nin de baixa), na orde do idioma, sen datos de xestión e só do seu concello.
 */
class PublicFacilityIT extends AbstractIT {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final String BOUNDARY = "furar-public-boundary";

  static String publicList(String slug) {
    return "/api/v1/municipalities/" + slug + "/public/facilities";
  }

  JsonNode create(String slug, String name) {
    Api.Response created =
        api()
            .post(FacilityIT.base(slug), Map.of("name", name, "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return created.json();
  }

  String url(String slug, JsonNode facility) {
    return FacilityIT.base(slug) + "/" + facility.get("id").asString();
  }

  long version(String slug, JsonNode facility) {
    return api().get(url(slug, facility)).token(TestTokens.manager(slug)).send().etagVersion();
  }

  /** Deportes, un uso e unha tempada con horario ata 2030: deixa de estar en borrador. */
  JsonNode publish(String slug, String name) {
    JsonNode facility = create(slug, name);
    Api.Response details =
        api()
            .put(
                url(slug, facility) + "/details",
                FacilityDataIT.details(
                    List.of(FacilityDataIT.catalog("padel"), FacilityDataIT.catalog("football"))))
            .ifMatch(version(slug, facility))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(details.status()).as(details.toString()).isEqualTo(200);
    Api.Response use =
        api()
            .post(url(slug, facility) + "/uses", FacilityUseIT.use("Pista completa", "WHOLE"))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(use.status()).as(use.toString()).isEqualTo(201);
    Api.Response season =
        api()
            .post(
                url(slug, facility) + "/seasons",
                FacilityScheduleIT.season("Todo o ano", "2026-01-01", "2030-12-31"))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(season.status()).as(season.toString()).isEqualTo(201);
    Api.Response week =
        api()
            .put(
                url(slug, facility) + "/seasons/" + season.json().get("id").asString(),
                FacilityScheduleIT.withWeek(
                    FacilityScheduleIT.season("Todo o ano", "2026-01-01", "2030-12-31"),
                    List.of(FacilityScheduleIT.day("MONDAY", 480, 1320))))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(week.status()).as(week.toString()).isEqualTo(200);
    Api.Response check = api().get(url(slug, facility)).token(TestTokens.manager(slug)).send();
    assertThat(check.json().get("missing").size()).as(check.toString()).isZero();
    assertThat(check.json().get("published").asBoolean()).isFalse();
    assertThat(names(api().get(publicList(slug)).send()))
        .as("completa pero sen «Publicar», a veciñanza non a ve")
        .doesNotContain(name);
    Api.Response published =
        api()
            .put(url(slug, facility) + "/publication", Map.of())
            .ifMatch(check.etagVersion())
            .token(TestTokens.manager(slug))
            .send();
    assertThat(published.status()).as(published.toString()).isEqualTo(200);
    assertThat(published.json().get("published").asBoolean()).isTrue();
    return published.json();
  }

  void status(String slug, JsonNode facility, String status) {
    Api.Response changed =
        api()
            .put(url(slug, facility) + "/status", Map.of("status", status))
            .ifMatch(version(slug, facility))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
  }

  /** Sobe unha foto á biblioteca (queda pendente ata gardala nunha instalación); devolve o id. */
  static String uploadPhoto(int port, String slug) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"foto\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.writeBytes(TestImages.halves(300, 200));
    body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/api/v1/municipalities/" + slug + "/photos"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .header("Authorization", "Bearer " + TestTokens.manager(slug))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();
    try {
      HttpResponse<String> created = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
      return Api.JSON.readTree(created.body()).get("id").asString();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** A imaxe dunha foto para o {@code PUT …/images}, co seu texto alternativo. */
  static Map<String, Object> photoImage(String photo) {
    return Map.of(
        "photoId",
        photo,
        "alt",
        Map.of(
            "gl", "Pista de pádel coa rede no medio",
            "es", "Pista de pádel con la red en medio"));
  }

  /**
   * Sobe unha foto á biblioteca e gárdaa como portada da instalación; devolve o estado do gardado.
   */
  int upload(String slug, JsonNode facility) {
    String photo = uploadPhoto(port, slug);
    return api()
        .put(url(slug, facility) + "/images", Map.of("images", List.of(photoImage(photo))))
        .ifMatch(version(slug, facility))
        .token(TestTokens.manager(slug))
        .send()
        .status();
  }

  static List<String> names(Api.Response response) {
    List<String> names = new ArrayList<>();
    response.json().forEach(f -> names.add(f.get("name").asString()));
    return names;
  }

  @Test
  void anonymousSeesOnlyPublishedFacilitiesWithSportsAndCover() {
    String slug = uniqueSlug("pub");
    createMunicipality(slug);
    JsonNode court = publish(slug, "Pista de pádel 1");
    JsonNode field = publish(slug, "Campo de fútbol 1");
    JsonNode pool = publish(slug, "Piscina de baixa");
    create(slug, "Ximnasio en borrador");
    status(slug, field, "TEMPORARILY_CLOSED");
    status(slug, pool, "WITHDRAWN");
    assertThat(upload(slug, court)).isEqualTo(200);

    Api.Response list = api().get(publicList(slug)).send();

    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    assertThat(names(list)).containsExactly("Campo de fútbol 1", "Pista de pádel 1");
    JsonNode closed = list.json().get(0);
    assertThat(closed.get("temporarilyClosed").asBoolean()).isTrue();
    assertThat(closed.get("cover").get("kind").asString())
        .as("sen fotos, o debuxo suxerido")
        .isEqualTo("DRAWING");
    assertThat(closed.get("cover").get("id").asString()).isEqualTo("football_field");
    JsonNode padel = list.json().get(1);
    assertThat(padel.get("id").asString()).isEqualTo(court.get("id").asString());
    assertThat(padel.get("temporarilyClosed").asBoolean()).isFalse();
    assertThat(padel.get("address").get("street").asString()).isNotBlank();
    assertThat(padel.get("address").get("latitude").isNumber())
        .as("o punto do mapa, sen pedir a ficha")
        .isTrue();
    assertThat(padel.get("address").get("longitude").isNumber()).isTrue();
    assertThat(padel.get("address").has("ineCode")).isFalse();
    assertThat(padel.has("covered")).as("cuberta: o dato ou nulo").isTrue();
    assertThat(padel.get("sports").get(0).get("code").asString()).isEqualTo("padel");
    assertThat(padel.get("sports").get(0).get("label").get("gl").asString()).isNotBlank();
    // Nada de xestión: nin estado interno, borrador, contacto, RIDEGA nin versión.
    for (String internal :
        List.of("status", "missing", "details", "phone", "ridegaCode", "version", "customFields")) {
      assertThat(padel.has(internal)).as(internal).isFalse();
    }
    JsonNode cover = padel.get("cover");
    assertThat(cover.get("alt").get("gl").asString()).isEqualTo("Pista de pádel coa rede no medio");
    String thumb = cover.get("thumb").asString();
    assertThat(thumb)
        .isEqualTo(
            "/api/v1/municipalities/" + slug + "/photos/" + cover.get("id").asString() + "/thumb");
    assertThat(cover.get("large").asString()).endsWith("/large");
    Api.Response image = api().get(thumb).send();
    assertThat(image.status()).as("a portada sérvese sen sesión").isEqualTo(200);
  }

  @Test
  void orderFollowsTheLanguageAndOtherMunicipalitiesAreNotListed() {
    String a = uniqueSlug("pua");
    String b = uniqueSlug("pub");
    createMunicipality(a);
    createMunicipality(b);
    publish(a, "Ágora");
    publish(a, "Abeiro");
    publish(b, "Pavillón doutro concello");

    Api.Response gl = api().get(publicList(a)).language("gl").send();
    Api.Response es = api().get(publicList(a)).language("es").send();

    assertThat(names(gl)).containsExactly("Abeiro", "Ágora");
    assertThat(names(es)).containsExactly("Abeiro", "Ágora");
    assertThat(names(api().get(publicList(b)).send())).containsExactly("Pavillón doutro concello");
    Api.Response unknown = api().get(publicList(uniqueSlug("nin"))).send();
    assertThat(unknown.status()).isEqualTo(404);
  }

  @Test
  void anonymousReadsThePublicDetailOfAVisibleFacilityOnly() {
    String slug = uniqueSlug("pud");
    createMunicipality(slug);
    JsonNode court = publish(slug, "Pista de pádel 2");
    JsonNode withdrawn = publish(slug, "Pista de baixa");
    status(slug, withdrawn, "WITHDRAWN");
    JsonNode draft = create(slug, "Sala en borrador");
    String other = uniqueSlug("puo");
    createMunicipality(other);

    Api.Response detail = api().get(publicList(slug) + "/" + court.get("id").asString()).send();

    assertThat(detail.status()).as(detail.toString()).isEqualTo(200);
    JsonNode body = detail.json();
    assertThat(body.get("name").asString()).isEqualTo("Pista de pádel 2");
    assertThat(body.get("address").get("latitude").isNumber()).isTrue();
    assertThat(body.get("address").has("ineCode")).isFalse();
    assertThat(body.get("images").get(0).get("kind").asString()).isEqualTo("DRAWING");
    assertThat(body.get("uses").get(0).get("name").asString()).isEqualTo("Pista completa");
    assertThat(body.get("uses").get(0).has("version")).isFalse();
    JsonNode week = body.get("openingHours");
    assertThat(week.size()).as("hoxe e os seis días seguintes").isEqualTo(7);
    int open = 0;
    for (JsonNode day : week) {
      if (!day.get("slots").isEmpty()) {
        open++;
        JsonNode slot = day.get("slots").get(0);
        assertThat(slot.get("startMinute").asInt()).isEqualTo(480);
        assertThat(slot.get("endMinute").asInt()).isEqualTo(1320);
        assertThat(slot.get("startsAt").asString()).endsWith("Z");
      }
    }
    assertThat(open).as("só abre os luns").isEqualTo(1);
    for (String internal :
        List.of("status", "missing", "details", "ridegaCode", "version", "customFields")) {
      assertThat(body.has(internal)).as(internal).isFalse();
    }
    for (JsonNode hidden : List.of(withdrawn, draft)) {
      Api.Response refused = api().get(publicList(slug) + "/" + hidden.get("id").asString()).send();
      assertThat(refused.status()).as(refused.toString()).isEqualTo(404);
      assertThat(refused.json().get("code").asString()).isEqualTo("facility.not-found");
    }
    Api.Response elsewhere = api().get(publicList(other) + "/" + court.get("id").asString()).send();
    assertThat(elsewhere.status()).as("doutro concello non se ve").isEqualTo(404);
  }

  /** Maqueta 54: os peches dos próximos sesenta días, só co público (datas, horas e motivo). */
  @Test
  void publicDetailListsTheNextClosures() {
    String slug = uniqueSlug("puc");
    createMunicipality(slug);
    JsonNode court = publish(slug, "Pista con peches");
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
    // Todos os días abertos, tamén os festivos: os peches non dependen de cando corre o test.
    String season = seasonId(slug, court);
    List<Map<String, Object>> week = new ArrayList<>();
    for (String day :
        List.of(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
            "HOLIDAYS")) {
      week.add(FacilityScheduleIT.day(day, 480, 1320));
    }
    Api.Response everyDay =
        api()
            .put(
                url(slug, court) + "/seasons/" + season,
                FacilityScheduleIT.withWeek(
                    FacilityScheduleIT.season("Todo o ano", "2026-01-01", "2030-12-31"), week))
            .ifMatch(1)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(everyDay.status()).as(everyDay.toString()).isEqualTo(200);
    Map<String, Object> closed = FacilityScheduleExceptionIT.closed(today.plusDays(5).toString());
    assertThat(
            api()
                .post(url(slug, court) + "/schedule-exceptions", closed)
                .token(TestTokens.manager(slug))
                .send()
                .status())
        .isEqualTo(201);
    for (int day : List.of(10, 11)) {
      Api.Response block =
          api()
              .post(
                  url(slug, court) + "/blocks",
                  FacilityBlockIT.block(today.plusDays(day).toString(), 0, 1440))
              .token(TestTokens.manager(slug))
              .send();
      assertThat(block.status()).as(block.toString()).isEqualTo(201);
    }
    Api.Response far =
        api()
            .post(
                url(slug, court) + "/blocks",
                FacilityBlockIT.block(today.plusDays(61).toString(), 600, 720))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(far.status()).as(far.toString()).isEqualTo(201);

    JsonNode body = api().get(publicList(slug) + "/" + court.get("id").asString()).send().json();

    List<String> closures = new ArrayList<>();
    for (JsonNode c : body.get("closures")) {
      assertThat(c.has("createdBy")).isFalse();
      closures.add(
          c.get("from").asString()
              + "/"
              + c.get("to").asString()
              + " "
              + c.get("reason").asString());
    }
    assertThat(closures)
        .as("o bloqueo a 61 días queda fóra; os dous seguidos van nunha liña")
        .containsExactly(
            today.plusDays(5) + "/" + today.plusDays(5) + " Fin de ano",
            today.plusDays(10) + "/" + today.plusDays(11) + " Mantemento do céspede");
  }

  String seasonId(String slug, JsonNode facility) {
    return api()
        .get(url(slug, facility) + "/schedule")
        .token(TestTokens.manager(slug))
        .send()
        .json()
        .get("seasons")
        .get(0)
        .get("id")
        .asString();
  }

  @Autowired FacilityQueries queries;

  // Pago e devolución por instalación (D2-11, revisión do 2026-11-03).

  @Test
  void paymentNotesOfAFacilityArePublicAndOnlyTheManagerSavesThem() {
    String slug = uniqueSlug("ppn");
    UUID municipalityId = id(createMunicipality(slug));
    JsonNode court = publish(slug, "Pista de pádel 2");
    String publicNotes = publicList(slug) + "/" + court.get("id").asString() + "/payment-notes";
    JsonNode empty = api().get(publicNotes).send().json();
    assertThat(empty.get("payment").isNull()).isTrue();
    assertThat(empty.get("refund").isNull()).isTrue();

    Map<String, Object> notes =
        Map.of("payment", Map.of("gl", "Na billeteira", "es", "En la taquilla"));
    Api.Response byStaff =
        api()
            .put(url(slug, court) + "/payment-notes", notes)
            .ifMatch(version(slug, court))
            .token(TestTokens.staff(slug))
            .send();
    assertThat(byStaff.status()).isEqualTo(403);
    Api.Response saved =
        api()
            .put(url(slug, court) + "/payment-notes", notes)
            .ifMatch(version(slug, court))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().get("paymentNotes").get("payment").get("es").asString())
        .isEqualTo("En la taquilla");
    assertThat(saved.json().get("paymentNotes").get("refund").isNull()).isTrue();
    Api.Response half =
        api()
            .put(
                url(slug, court) + "/payment-notes", Map.of("refund", Map.of("gl", "Só en galego")))
            .ifMatch(version(slug, court))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(half.status()).isEqualTo(400);
    assertThat(half.body()).contains("paymentNotes.refund.es");

    JsonNode read = api().get(publicNotes).send().json();
    assertThat(read.get("payment").get("gl").asString()).isEqualTo("Na billeteira");
    assertThat(
            TenantContext.runAs(
                new TenantRef(municipalityId, slug),
                () -> queries.paymentTexts(UUID.fromString(court.get("id").asString()))))
        .hasValueSatisfying(t -> assertThat(t.payment().es()).isEqualTo("En la taquilla"));
    assertThat(
            api()
                .get(publicList(slug) + "/" + UUID.randomUUID() + "/payment-notes")
                .send()
                .status())
        .isEqualTo(404);
  }

  // Dominio 2 (D2-02): normas da instalación, prezo e normas na ficha pública e visibleIds.

  @Test
  void houseRulesPriceAndRulesReachThePublicDetail() {
    String slug = uniqueSlug("pur");
    createMunicipality(slug);
    JsonNode court = publish(slug, "Campo de fútbol 1");
    Map<String, String> rules =
        Map.of("gl", "Calzado de herba artificial.", "es", "Calzado de hierba artificial.");
    Api.Response saved =
        api()
            .put(url(slug, court) + "/house-rules", rules)
            .ifMatch(version(slug, court))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().get("houseRules").get("es").asString())
        .isEqualTo("Calzado de hierba artificial.");
    Api.Response stale =
        api()
            .put(url(slug, court) + "/house-rules", rules)
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(stale.status()).isEqualTo(412);
    Api.Response half =
        api()
            .put(url(slug, court) + "/house-rules", Map.of("gl", "Só en galego"))
            .ifMatch(version(slug, court))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(half.status()).isEqualTo(422);
    assertThat(half.json().get("code").asString()).isEqualTo("facility.house-rules-invalid");
    Api.Response byStaff =
        api()
            .put(url(slug, court) + "/house-rules", rules)
            .ifMatch(version(slug, court))
            .token(TestTokens.staff(slug))
            .send();
    assertThat(byStaff.status()).isEqualTo(403);

    JsonNode use =
        api().get(url(slug, court) + "/uses").token(TestTokens.staff(slug)).send().json().get(0);
    Map<String, Object> parts = FacilityUseIT.parts("Fútbol 7", "Metade norte", "Metade sur");
    FacilityUseIT.withTerms(parts);
    Api.Response updated =
        api()
            .put(url(slug, court) + "/uses/" + use.get("id").asString(), parts)
            .ifMatch(use.get("version").asLong())
            .token(TestTokens.manager(slug))
            .send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);

    JsonNode detail = api().get(publicList(slug) + "/" + court.get("id").asString()).send().json();
    assertThat(detail.get("houseRules").get("gl").asString())
        .isEqualTo("Calzado de herba artificial.");
    assertThat(detail.get("bookable").asBoolean()).isTrue();
    JsonNode publicUse = detail.get("uses").get(0);
    assertThat(publicUse.get("id").asString()).isEqualTo(use.get("id").asString());
    assertThat(publicUse.get("parts").get(0).get("name").asString()).isEqualTo("Metade norte");
    assertThat(publicUse.get("parts").get(0).has("id")).isTrue();
    assertThat(publicUse.get("weekdays").asInt()).isEqualTo(31);
    assertThat(publicUse.get("price").get("amountCents").asInt()).isEqualTo(3000);
    assertThat(publicUse.get("price").get("per").asString()).isEqualTo("BOOKING");
    assertThat(publicUse.get("rules").get("cancelHours").asInt()).isEqualTo(24);

    status(slug, court, "TEMPORARILY_CLOSED");
    JsonNode closed = api().get(publicList(slug) + "/" + court.get("id").asString()).send().json();
    assertThat(closed.get("bookable").asBoolean()).isFalse();
  }

  @Test
  void visibleIdsLeaveOutDraftsWithdrawnAndFacilitiesWithoutOnlineBooking() {
    String slug = uniqueSlug("vis");
    UUID municipality = id(createMunicipality(slug));
    JsonNode b = publish(slug, "B pista");
    JsonNode a = publish(slug, "A pista");
    JsonNode closed = publish(slug, "C pista pechada");
    status(slug, closed, "TEMPORARILY_CLOSED");
    JsonNode withdrawn = publish(slug, "D pista de baixa");
    status(slug, withdrawn, "WITHDRAWN");
    JsonNode offline = publish(slug, "E pista sen reserva");
    Map<String, Object> details = FacilityDataIT.details(List.of(FacilityDataIT.catalog("padel")));
    details.put("onlineBooking", false);
    details.put("showFreeSlots", true);
    Api.Response changed =
        api()
            .put(url(slug, offline) + "/details", details)
            .ifMatch(version(slug, offline))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
    JsonNode draft = create(slug, "F sala en borrador");
    TenantRef tenant = new TenantRef(municipality, slug);
    UUID idA = UUID.fromString(a.get("id").asString());
    UUID idB = UUID.fromString(b.get("id").asString());
    UUID idC = UUID.fromString(closed.get("id").asString());

    assertThat(TenantContext.runAs(tenant, () -> queries.visibleIds(0, 10)))
        .containsExactly(idA, idB, idC);
    assertThat(TenantContext.runAs(tenant, () -> queries.visibleIds(1, 2))).containsExactly(idC);
    assertThat(TenantContext.runAs(tenant, () -> queries.visibleIds(2, 2))).isEmpty();
    List<UUID> asked =
        List.of(
            UUID.fromString(draft.get("id").asString()),
            idB,
            UUID.fromString(offline.get("id").asString()),
            UUID.fromString(withdrawn.get("id").asString()),
            idA);
    assertThat(TenantContext.runAs(tenant, () -> queries.visibleIds(asked)))
        .containsExactly(idB, idA);
  }
}
