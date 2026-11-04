// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.domain.DrawingSuggestion;
import gal.furar.facility.domain.PhotoUrlSignature;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Biblioteca de imaxes e sección Fotos (D1-13; especificación, bloque 1, «Fotos») de punta a punta:
 * multipart real por Tomcat, límites de Spring, ImageIO e PostgreSQL. A imaxe xigante vai primeiro
 * (nota de D1-13 no plan): rexéitase pola cabeceira sen que a API reserve memoria para
 * decodificala.
 */
class FacilityImageIT extends AbstractIT {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final String BOUNDARY = "furar-photo-boundary";

  record Raw(int status, byte[] body, HttpResponse<byte[]> response) {
    JsonNode json() {
      return Api.JSON.readTree(new String(body, StandardCharsets.UTF_8));
    }

    String code() {
      return json().get("code").asString();
    }

    String header(String name) {
      return response.headers().firstValue(name).orElse(null);
    }

    long etag() {
      return Long.parseLong(header("ETag").replace("\"", ""));
    }

    @Override
    public String toString() {
      return status + " " + new String(body, StandardCharsets.UTF_8);
    }
  }

  JsonNode facility(String slug) {
    Api.Response response =
        api()
            .post(
                FacilityIT.base(slug),
                Map.of("name", "Campo " + UUID.randomUUID(), "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }

  static String library(String slug) {
    return "/api/v1/municipalities/" + slug + "/photos";
  }

  static String images(String slug, JsonNode facility) {
    return FacilityIT.base(slug) + "/" + facility.get("id").asString() + "/images";
  }

  Raw upload(String slug, byte[] file, String type) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"foto\"\r\n"
                + "Content-Type: "
                + type
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.writeBytes(file);
    body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return send(
        HttpRequest.newBuilder(uri(library(slug)))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .header("Authorization", "Bearer " + TestTokens.manager(slug))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build());
  }

  /** A ruta dos bytes coa firma que pon a API (a mesma chave: a API corre nesta JVM). */
  String signed(String slug, String photo, String variant) {
    UUID municipality =
        jdbc.sql("select id from municipality where slug = :s")
            .param("s", slug)
            .query(UUID.class)
            .single();
    return library(slug)
        + "/"
        + photo
        + "/"
        + variant
        + "?"
        + PhotoUrlSignature.PARAM
        + "="
        + PhotoUrlSignature.sign(municipality, UUID.fromString(photo), Instant.now());
  }

  Raw bytes(String path) {
    return send(HttpRequest.newBuilder(uri(path)).GET().build());
  }

  String uploaded(String slug) {
    Raw created = upload(slug, TestImages.halves(32, 24), "image/jpeg");
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return created.json().get("id").asString();
  }

  static Map<String, Object> photo(String id, String alt) {
    return Map.of("photoId", id, "alt", Map.of("gl", alt, "es", alt + " (es)"));
  }

  static Map<String, Object> drawing(String code) {
    return Map.of("drawing", code);
  }

  Api.Response arrange(String slug, JsonNode facility, long version, List<?> list) {
    return api()
        .put(images(slug, facility), Map.of("images", list))
        .ifMatch(version)
        .token(TestTokens.manager(slug))
        .send();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static Raw send(HttpRequest request) {
    try {
      HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Raw(response.statusCode(), response.body(), response);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void aGiantPngIsRejectedWithoutAllocatingItsPixels() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    byte[] giant = TestImages.giantPng(20_000, 20_000);
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long before = threads.getTotalThreadAllocatedBytes();

    Raw rejected = upload(slug, giant, "image/png");

    long allocated = threads.getTotalThreadAllocatedBytes() - before;
    assertThat(rejected.status()).as(rejected.toString()).isEqualTo(422);
    assertThat(rejected.code()).isEqualTo("facility.photo-too-many-pixels");
    assertThat(allocated).as("bytes allocated by the whole JVM").isLessThan(100L * 1024 * 1024);
  }

  @Test
  void aJpegWithGpsComesOutWithoutExifAndUprightAndWaitsOutsideTheLibrary() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    byte[] upload = TestImages.withExif(TestImages.halves(400, 200), 6);

    Raw created = upload(slug, upload, "image/jpeg");

    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    JsonNode photo = created.json();
    assertThat(photo.get("pending").asBoolean()).isTrue();
    assertThat(photo.get("width").asInt()).isEqualTo(200);
    assertThat(photo.get("height").asInt()).isEqualTo(400);
    assertThat(created.header("Location"))
        .contains("/photos/" + photo.get("id").asString() + "/large?s=")
        .isEqualTo(photo.get("large").asString());
    // ADR-0024: pendente, só coa firma da ruta que deu a API.
    assertThat(bytes(library(slug) + "/" + photo.get("id").asString() + "/large").status())
        .isEqualTo(404);

    for (String variant : List.of("large", "thumb")) {
      Raw image = bytes(photo.get(variant).asString());
      assertThat(image.status()).as(variant).isEqualTo(200);
      assertThat(image.header("Content-Type")).isEqualTo("image/jpeg");
      assertThat(image.header("Cache-Control")).isEqualTo("max-age=31536000, private, immutable");
      assertThat(TestImages.contains(image.body(), "Exif".getBytes(StandardCharsets.US_ASCII)))
          .isFalse();
      assertThat(TestImages.contains(image.body(), TestImages.GPS_MARK)).isFalse();
      BufferedImage decoded = TestImages.decode(image.body());
      assertThat(decoded.getHeight()).isGreaterThan(decoded.getWidth());
      assertThat(TestImages.isRed(decoded.getRGB(decoded.getWidth() / 2, 5))).isTrue();
    }
    Api.Response list = api().get(library(slug)).token(TestTokens.staff(slug)).send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    assertThat(list.json().size()).as("pendente: fóra da biblioteca").isZero();
  }

  @Test
  void pngIsAcceptedAndGifIsNot() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);

    Raw png = upload(slug, TestImages.png(640, 480), "image/png");
    assertThat(png.status()).as(png.toString()).isEqualTo(201);
    assertThat(png.json().get("width").asInt()).isEqualTo(640);

    Raw gif = upload(slug, TestImages.gif(20, 20), "image/gif");
    assertThat(gif.status()).as(gif.toString()).isEqualTo(415);
    assertThat(gif.code()).isEqualTo("facility.photo-unsupported-type");
    Raw disguised = upload(slug, TestImages.gif(20, 20), "image/jpeg");
    assertThat(disguised.status()).isEqualTo(415);
  }

  @Test
  void nineMegabytesAre413FromSpring() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    byte[] big = new byte[9 * 1024 * 1024];
    System.arraycopy(TestImages.halves(10, 10), 0, big, 0, 3);

    Raw rejected = upload(slug, big, "image/jpeg");

    assertThat(rejected.status()).as(rejected.toString()).isEqualTo(413);
    assertThat(rejected.code()).isEqualTo("facility.photo-too-large");
    assertThat(rejected.header("Content-Type")).startsWith("application/problem+json");
  }

  @Test
  void withoutImagesTheSuggestedDrawingIsTheCover() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    JsonNode facility =
        api()
            .post(
                FacilityIT.base(slug),
                Map.of("name", "Piscina " + UUID.randomUUID(), "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send()
            .json();

    JsonNode shown = facility.get("images");
    assertThat(shown.size()).isEqualTo(1);
    assertThat(shown.get(0).get("kind").asString()).isEqualTo("DRAWING");
    assertThat(shown.get(0).get("id").asString()).isEqualTo("pool");
    assertThat(shown.get(0).get("thumb").asString())
        .isEqualTo("/api/v1/municipalities/" + slug + "/drawings/pool/thumb");
    assertThat(facility.get("suggestedImage").get("id").asString()).isEqualTo("pool");
  }

  /** Fotos e debuxos na orde nova nun só gardado; o texto vai coa foto; baleira = suxerida. */
  @Test
  void photosAndDrawingsAreSavedInOneGoAndTheFirstIsTheCover() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    JsonNode campo = facility(slug);
    JsonNode pista = facility(slug);
    String a = uploaded(slug);
    String b = uploaded(slug);

    Api.Response saved =
        arrange(
            slug,
            campo,
            0,
            List.of(photo(b, "Bancada"), drawing("football_field"), photo(a, "Céspede")));

    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.etagVersion()).isEqualTo(1);
    JsonNode shown = saved.json().get("images");
    assertThat(shown.size()).isEqualTo(3);
    assertThat(shown.get(0).get("id").asString()).isEqualTo(b);
    assertThat(shown.get(0).get("kind").asString()).isEqualTo("PHOTO");
    assertThat(shown.get(0).get("uploadedAt").isNull()).isFalse();
    assertThat(shown.get(1).get("kind").asString()).isEqualTo("DRAWING");
    assertThat(shown.get(1).get("name").get("gl").asString()).isEqualTo("Campo de fútbol");
    assertThat(shown.get(2).get("alt").get("es").asString()).isEqualTo("Céspede (es)");

    Api.Response reused = arrange(slug, pista, 0, List.of(photo(a, "Céspede novo")));
    assertThat(reused.status()).as(reused.toString()).isEqualTo(200);
    JsonNode again =
        api()
            .get(FacilityIT.base(slug) + "/" + id(campo))
            .token(TestTokens.staff(slug))
            .send()
            .json();
    assertThat(again.get("images").get(2).get("alt").get("gl").asString())
        .as("o texto alternativo vai coa foto")
        .isEqualTo("Céspede novo");

    JsonNode list = api().get(library(slug)).token(TestTokens.manager(slug)).send().json();
    assertThat(list.size()).isEqualTo(2);
    JsonNode shared = list.get(0).get("id").asString().equals(a) ? list.get(0) : list.get(1);
    assertThat(shared.get("usedIn").size()).isEqualTo(2);
    assertThat(shared.get("pending").asBoolean()).isFalse();

    Api.Response emptied = arrange(slug, campo, 1, List.of());
    assertThat(emptied.status()).as(emptied.toString()).isEqualTo(200);
    assertThat(emptied.json().get("images").get(0).get("kind").asString()).isEqualTo("DRAWING");
    assertThat(bytes(signed(slug, b, "large")).status())
        .as("quitar non a borra da biblioteca")
        .isEqualTo(200);
  }

  @Test
  void invalidListsAreRejected() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    JsonNode facility = facility(slug);
    String a = uploaded(slug);

    Api.Response alt =
        arrange(slug, facility, 0, List.of(Map.of("photoId", a, "alt", Map.of("gl", "Só gl"))));
    assertThat(alt.status()).as(alt.toString()).isEqualTo(422);
    assertThat(alt.json().get("code").asString()).isEqualTo("facility.photo-alt-invalid");
    assertThat(alt.json().get("errors").get(0).get("field").asString())
        .isEqualTo("images[0].alt.es");

    Api.Response twice = arrange(slug, facility, 0, List.of(photo(a, "A"), photo(a, "A")));
    assertThat(twice.json().get("code").asString()).isEqualTo("facility.images-invalid");
    Api.Response unknown = arrange(slug, facility, 0, List.of(drawing("quidditch")));
    assertThat(unknown.status()).isEqualTo(422);
    assertThat(unknown.json().get("code").asString()).isEqualTo("facility.images-invalid");

    List<Map<String, Object>> thirteen = new ArrayList<>();
    for (String code :
        List.of(
            "pool",
            "gym",
            "jetty",
            "fronton",
            "velodrome",
            "generic",
            "sports_hall",
            "outdoor_court",
            "tennis_court",
            "padel_court",
            "rugby_field",
            "squash_court",
            "table_tennis")) {
      thirteen.add(drawing(code));
    }
    Api.Response limit = arrange(slug, facility, 0, thirteen);
    assertThat(limit.status()).as(limit.toString()).isEqualTo(422);
    Api.Response twelve = arrange(slug, facility, 0, thirteen.subList(0, 12));
    assertThat(twelve.status()).as(twelve.toString()).isEqualTo(200);
    Api.Response stale = arrange(slug, facility, 0, List.of());
    assertThat(stale.status()).isEqualTo(412);
    assertThat(
            jdbc.sql("select count(*) from facility_image where facility_id = :id")
                .param("id", id(facility))
                .query(Long.class)
                .single())
        .isEqualTo(12);
  }

  @Test
  void pendingUploadsAreDiscardedButKeptPhotosAreNot() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    JsonNode facility = facility(slug);
    String pending = uploaded(slug);
    String kept = uploaded(slug);
    arrange(slug, facility, 0, List.of(photo(kept, "Campo")));

    Api.Response discarded =
        api().delete(library(slug) + "/" + pending).token(TestTokens.manager(slug)).send();
    assertThat(discarded.status()).as(discarded.toString()).isEqualTo(204);
    assertThat(bytes(signed(slug, pending, "thumb")).status()).isEqualTo(404);
    assertThat(
            jdbc.sql("select count(*) from library_photo_content where photo_id = :id")
                .param("id", UUID.fromString(pending))
                .query(Long.class)
                .single())
        .isZero();
    Api.Response refused =
        api().delete(library(slug) + "/" + kept).token(TestTokens.manager(slug)).send();
    assertThat(refused.status()).isEqualTo(409);
    assertThat(refused.json().get("code").asString()).isEqualTo("facility.photo-not-pending");
    Api.Response staff =
        api().delete(library(slug) + "/" + kept).token(TestTokens.staff(slug)).send();
    assertThat(staff.status()).isEqualTo(403);
  }

  @Test
  void otherMunicipalitiesAndWithdrawnFacilitiesShowNothing() {
    String slug = uniqueSlug("fot");
    String other = uniqueSlug("fot");
    createMunicipality(slug);
    createMunicipality(other);
    JsonNode facility = facility(slug);
    JsonNode foreign = facility(other);
    String photo = uploaded(slug);
    arrange(slug, facility, 0, List.of(photo(photo, "Campo")));
    assertThat(bytes(signed(slug, photo, "thumb")).status()).isEqualTo(200);
    assertThat(bytes(library(slug) + "/" + photo + "/thumb").status())
        .as("sen firma: a instalación non está no público")
        .isEqualTo(404);

    Raw elsewhere = bytes(signed(other, photo, "thumb"));
    assertThat(elsewhere.status()).as(elsewhere.toString()).isEqualTo(404);
    Api.Response stolen = arrange(other, foreign, 0, List.of(photo(photo, "Alleo")));
    assertThat(stolen.status()).as(stolen.toString()).isEqualTo(422);
    assertThat(stolen.json().get("code").asString()).isEqualTo("facility.images-invalid");
    assertThat(api().get(library(other)).token(TestTokens.manager(other)).send().json().size())
        .isZero();
    assertThat(bytes(library(slug) + "/" + photo + "/huge").status()).isEqualTo(404);

    Api.Response withdrawn =
        api()
            .put(
                FacilityIT.base(slug) + "/" + id(facility) + "/status",
                Map.of("status", "WITHDRAWN"))
            .ifMatch(1)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(withdrawn.status()).as(withdrawn.toString()).isEqualTo(200);
    assertThat(bytes(signed(slug, photo, "thumb")).status()).isEqualTo(404);
  }

  @Test
  void drawingsAreAReadOnlyPlatformCatalogWithTheirFiles() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    List<String> codes =
        appJdbc.sql("select code from drawing order by code").query(String.class).list();
    assertThat(codes).containsExactlyInAnyOrderElementsOf(DrawingSuggestion.CODES);
    for (String code : codes) {
      assertThat(getClass().getClassLoader().getResource("drawings/" + code + ".jpg"))
          .as(code)
          .isNotNull();
    }
    for (String write :
        List.of(
            "insert into drawing (code, name, alt, width, height) values ('x', '{\"gl\":\"x\",\"es\":\"x\"}', '{\"gl\":\"x\",\"es\":\"x\"}', 1, 1)",
            "update drawing set width = 2",
            "delete from drawing")) {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> appJdbc.sql(write).update())
          .as(write)
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    Api.Response list =
        api()
            .get("/api/v1/municipalities/" + slug + "/drawings")
            .language("es")
            .token(TestTokens.staff(slug))
            .send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    assertThat(list.json().size()).isEqualTo(codes.size());
    assertThat(list.json().get(0).get("name").get("es").asString()).isEqualTo("Campo de fútbol");

    Raw thumb = bytes("/api/v1/municipalities/" + slug + "/drawings/sports_hall/thumb");
    assertThat(thumb.status()).as(thumb.toString()).isEqualTo(200);
    assertThat(thumb.header("Cache-Control")).isEqualTo("max-age=86400, public");
    BufferedImage decoded = TestImages.decode(thumb.body());
    assertThat(decoded.getWidth()).isEqualTo(480);
    assertThat(bytes("/api/v1/municipalities/" + slug + "/drawings/quidditch/thumb").status())
        .isEqualTo(404);
    assertThat(
            bytes("/api/v1/municipalities/" + slug + "/drawings/..%2Fapplication/thumb").status())
        .isIn(400, 404);
  }

  /** O modo edición pide o suxerido cos deportes aínda sen gardar: mesmas regras ca a ficha. */
  @Test
  void suggestedDrawingForUnsavedDataFollowsTheSameRules() {
    String slug = uniqueSlug("fot");
    createMunicipality(slug);
    String base = "/api/v1/municipalities/" + slug + "/drawings/suggestion";
    Function<String, Api.Response> suggest =
        query -> api().get(base + query).token(TestTokens.staff(slug)).send();

    Api.Response hall =
        suggest.apply("?name=Instalaci%C3%B3n&sport=futsal&sport=basketball&covered=true");
    assertThat(hall.status()).as(hall.toString()).isEqualTo(200);
    assertThat(hall.json().get("kind").asString()).isEqualTo("DRAWING");
    assertThat(hall.json().get("id").asString()).isEqualTo("sports_hall");
    assertThat(hall.json().get("thumb").asString())
        .isEqualTo("/api/v1/municipalities/" + slug + "/drawings/sports_hall/thumb");
    assertThat(suggest.apply("?sport=futsal&sport=basketball").json().get("id").asString())
        .isEqualTo("outdoor_court");
    assertThat(suggest.apply("?name=Piscina&sport=padel").json().get("id").asString())
        .isEqualTo("pool");
    assertThat(suggest.apply("?type=PADEL_COURT").json().get("id").asString())
        .isEqualTo("padel_court");
    assertThat(suggest.apply("?sport=quidditch").json().get("id").asString()).isEqualTo("generic");
    assertThat(suggest.apply("").json().get("id").asString()).isEqualTo("generic");
    assertThat(suggest.apply("?type=NOPE").status()).isEqualTo(400);
    assertThat(api().get(base).send().status()).isEqualTo(401);
  }

  @Test
  void writesAreAudited() {
    String slug = uniqueSlug("fot");
    JsonNode municipality = createMunicipality(slug);
    JsonNode facility = facility(slug);
    String photo = uploaded(slug);
    upload(slug, TestImages.gif(4, 4), "image/gif");
    arrange(slug, facility, 0, List.of(photo(photo, "Campo")));

    List<String> outcomes =
        jdbc.sql(
                """
                select action || ' ' || outcome from audit_event
                where municipality_id = :m and action like 'facility.%'
                  and action <> 'facility.create'
                order by occurred_at
                """)
            .param("m", id(municipality))
            .query(String.class)
            .list();
    assertThat(outcomes)
        .containsExactly(
            "facility.photo.upload SUCCESS",
            "facility.photo.upload FAILURE",
            "facility.images.arrange SUCCESS");
  }
}
