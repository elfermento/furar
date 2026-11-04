// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Eliminar unha instalación de punta a punta: co If-Match, só o xestor, borra o que é seu (usos,
 * tempadas, excepcións, bloqueos, lista de imaxes) e deixa a foto na biblioteca do concello. Se
 * tivo reservas, 409 e non se toca.
 */
class FacilityDeleteIT extends AbstractIT {

  @Autowired ProgrammableBookingImpact bookings;

  JsonNode create(String slug, String name) {
    Api.Response created =
        api()
            .post(FacilityIT.base(slug), Map.of("name", name, "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return created.json();
  }

  static String url(String slug, JsonNode facility) {
    return FacilityIT.base(slug) + "/" + facility.get("id").asString();
  }

  long version(String slug, JsonNode facility) {
    return api().get(url(slug, facility)).token(TestTokens.manager(slug)).send().etagVersion();
  }

  void created(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isIn(200, 201);
  }

  int rows(String table, UUID facility) {
    return jdbc.sql("select count(*) from " + table + " where facility_id = :id")
        .param("id", facility)
        .query(Integer.class)
        .single();
  }

  @Test
  void managerDeletesTheFacilityWithWhatIsItsOwnAndThePhotoStaysInTheLibrary() {
    String slug = uniqueSlug("del");
    createMunicipality(slug);
    JsonNode facility = create(slug, "Pista de pádel para eliminar");
    String token = TestTokens.manager(slug);
    created(
        api()
            .post(url(slug, facility) + "/uses", FacilityUseIT.use("Pista completa", "WHOLE"))
            .token(token)
            .send());
    created(
        api()
            .post(
                url(slug, facility) + "/seasons",
                FacilityScheduleIT.season("Todo o ano", "2026-01-01", "2030-12-31"))
            .token(token)
            .send());
    created(
        api()
            .post(
                url(slug, facility) + "/schedule-exceptions",
                FacilityScheduleExceptionIT.closed("2026-12-31"))
            .token(token)
            .send());
    created(
        api()
            .post(url(slug, facility) + "/blocks", FacilityBlockIT.block("2027-03-01", 480, 600))
            .token(token)
            .send());
    String photo = PublicFacilityIT.uploadPhoto(port, slug);
    created(
        api()
            .put(
                url(slug, facility) + "/images",
                Map.of("images", List.of(PublicFacilityIT.photoImage(photo))))
            .ifMatch(version(slug, facility))
            .token(token)
            .send());
    Api.Response read = api().get(url(slug, facility)).token(token).send();
    assertThat(read.json().get("deletable").asBoolean()).isTrue();
    UUID id = UUID.fromString(facility.get("id").asString());

    Api.Response stale = api().delete(url(slug, facility)).ifMatch(99).token(token).send();
    assertThat(stale.status()).as(stale.toString()).isEqualTo(412);
    Api.Response staff =
        api()
            .delete(url(slug, facility))
            .ifMatch(read.etagVersion())
            .token(TestTokens.staff(slug))
            .send();
    assertThat(staff.status()).as(staff.toString()).isEqualTo(403);

    Api.Response deleted =
        api().delete(url(slug, facility)).ifMatch(read.etagVersion()).token(token).send();

    assertThat(deleted.status()).as(deleted.toString()).isEqualTo(204);
    assertThat(api().get(url(slug, facility)).token(token).send().status()).isEqualTo(404);
    for (String table :
        List.of("facility_use", "season", "schedule_exception", "block", "facility_image")) {
      assertThat(rows(table, id)).as(table).isZero();
    }
    Api.Response library =
        api().get("/api/v1/municipalities/" + slug + "/photos").token(token).send();
    assertThat(library.status()).as(library.toString()).isEqualTo(200);
    assertThat(library.body()).contains(photo);
  }

  @Test
  void aFacilityWithBookingsIsNotDeleted() {
    String slug = uniqueSlug("delb");
    createMunicipality(slug);
    JsonNode facility = create(slug, "Campo con historial");
    bookings.book(UUID.fromString(facility.get("id").asString()));
    Api.Response read = api().get(url(slug, facility)).token(TestTokens.manager(slug)).send();
    assertThat(read.json().get("deletable").asBoolean()).isFalse();

    Api.Response refused =
        api()
            .delete(url(slug, facility))
            .ifMatch(read.etagVersion())
            .token(TestTokens.manager(slug))
            .send();

    assertThat(refused.status()).as(refused.toString()).isEqualTo(409);
    assertThat(refused.json().get("code").asString()).isEqualTo("facility.has-bookings");
    assertThat(api().get(url(slug, facility)).token(TestTokens.manager(slug)).send().status())
        .isEqualTo(200);
  }
}
