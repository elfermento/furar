// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

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
 * Servizo de deportes do concello (plan do dominio 2, D2-02, P8): contacto e notas de pago e
 * devolución, públicas para ler e do xestor para gardar co {@code If-Match} do concello; sen nota,
 * o texto de Furar; {@link MunicipalityQueries#mailContext} co que levarán os correos.
 */
class MunicipalitySportsServiceIT extends AbstractIT {

  @Autowired MunicipalityQueries queries;

  static String url(String slug, String section) {
    return "/api/v1/municipalities/" + slug + "/" + section;
  }

  @Test
  void contactIsPublicAndOnlyTheManagerSavesItWithIfMatch() {
    String slug = uniqueSlug("dep");
    createMunicipality(slug);
    Api.Response empty = api().get(url(slug, "sports-contact")).send();
    assertThat(empty.status()).as(empty.toString()).isEqualTo(200);
    assertThat(empty.json().get("email").isNull()).isTrue();
    long version = empty.etagVersion();

    Map<String, Object> contact =
        Map.of(
            "name", "Servizo Municipal de Deportes",
            "address", "Rúa Buxo, 16 · 15572 Narón",
            "phone", "981 38 21 00",
            "email", "deportes@naron.example");
    Api.Response byStaff =
        api()
            .put(url(slug, "sports-contact"), contact)
            .ifMatch(version)
            .token(TestTokens.staff(slug))
            .send();
    assertThat(byStaff.status()).isEqualTo(403);
    Api.Response saved =
        api()
            .put(url(slug, "sports-contact"), contact)
            .ifMatch(version)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.etagVersion()).isEqualTo(version + 1);
    Api.Response stale =
        api()
            .put(url(slug, "sports-contact"), contact)
            .ifMatch(version)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(stale.status()).isEqualTo(412);

    JsonNode read = api().get(url(slug, "sports-contact")).send().json();
    assertThat(read.get("name").asString()).isEqualTo("Servizo Municipal de Deportes");
    assertThat(read.get("address").asString()).isEqualTo("Rúa Buxo, 16 · 15572 Narón");
    assertThat(read.get("phone").asString()).isEqualTo("981 38 21 00");
    assertThat(read.get("email").asString()).isEqualTo("deportes@naron.example");

    Api.Response bad =
        api()
            .put(url(slug, "sports-contact"), Map.of("email", "sen-arroba"))
            .ifMatch(version + 1)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(bad.status()).isEqualTo(400);
    assertThat(bad.body()).contains("sportsContact.email");

    // ADR-0020: o alta e cada escritura do concello, tamén a fallida, quedan no rexistro.
    List<Map<String, Object>> rows =
        jdbc.sql(
                "select action, outcome from audit_event where municipality_slug = :s"
                    + " order by occurred_at")
            .param("s", slug)
            .query()
            .listOfRows();
    assertThat(rows)
        .extracting(r -> r.get("action") + " " + r.get("outcome"))
        .contains(
            "municipality.create SUCCESS",
            "municipality.sports-contact SUCCESS",
            "municipality.sports-contact FAILURE");
  }

  @Test
  void notesFallBackToTheFurarTextsAndReachTheMailContext() {
    String slug = uniqueSlug("pag");
    UUID municipality = id(createMunicipality(slug));
    JsonNode defaults = api().get(url(slug, "payment-notes")).send().json();
    assertThat(defaults.get("payment").get("gl").asString()).isEqualTo("No mostrador, ao chegar");
    assertThat(defaults.get("payment").get("es").asString())
        .isEqualTo("En el mostrador, al llegar");
    assertThat(defaults.get("paymentDefault").asBoolean()).isTrue();
    assertThat(defaults.get("refund").get("gl").asString())
        .isEqualTo("O concello dirache como cha devolve");
    assertThat(defaults.get("refundDefault").asBoolean()).isTrue();
    long version = api().get(url(slug, "payment-notes")).send().etagVersion();

    Map<String, Object> notes =
        Map.of(
            "payment",
            Map.of("gl", "Con tarxeta no mostrador", "es", "Con tarjeta en el mostrador"));
    Api.Response saved =
        api()
            .put(url(slug, "payment-notes"), notes)
            .ifMatch(version)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().get("payment").get("gl").asString())
        .isEqualTo("Con tarxeta no mostrador");
    assertThat(saved.json().get("paymentDefault").asBoolean()).isFalse();
    assertThat(saved.json().get("refundDefault").asBoolean()).isTrue();

    Api.Response half =
        api()
            .put(url(slug, "payment-notes"), Map.of("refund", Map.of("gl", "Só galego")))
            .ifMatch(version + 1)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(half.status()).isEqualTo(400);
    assertThat(half.body()).contains("paymentNotes.refund.es");

    MunicipalityMailContext mail = queries.mailContext(municipality).orElseThrow();
    assertThat(mail.slug()).isEqualTo(slug);
    assertThat(mail.languages()).contains("gl");
    assertThat(mail.primaryColor()).startsWith("#");
    assertThat(mail.paymentNote().es()).isEqualTo("Con tarjeta en el mostrador");
    assertThat(mail.refundNote().gl()).isEqualTo("O concello dirache como cha devolve");
    assertThat(mail.contact().email()).isNull();
    assertThat(queries.mailContext(UUID.randomUUID())).isEmpty();
  }

  @Test
  void byFacilityTheMunicipalityTextsAreKeptButTheMailUsesFurarOnes() {
    String slug = uniqueSlug("pfa");
    UUID municipality = id(createMunicipality(slug));
    long version = api().get(url(slug, "payment-notes")).send().etagVersion();
    Map<String, Object> notes =
        Map.of(
            "scope",
            "FACILITY",
            "payment",
            Map.of("gl", "Con tarxeta no mostrador", "es", "Con tarjeta en el mostrador"));
    Api.Response saved =
        api()
            .put(url(slug, "payment-notes"), notes)
            .ifMatch(version)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().get("scope").asString()).isEqualTo("FACILITY");
    assertThat(saved.json().get("payment").get("gl").asString())
        .isEqualTo("Con tarxeta no mostrador");
    assertThat(saved.json().get("furarPayment").get("gl").asString())
        .isEqualTo("No mostrador, ao chegar");

    // Sen «scope», queda o que había.
    Api.Response kept =
        api()
            .put(url(slug, "payment-notes"), Map.of())
            .ifMatch(version + 1)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(kept.json().get("scope").asString()).isEqualTo("FACILITY");
    Api.Response bad =
        api()
            .put(url(slug, "payment-notes"), Map.of("scope", "OUTRO"))
            .ifMatch(version + 2)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(bad.status()).isEqualTo(400);

    MunicipalityMailContext mail = queries.mailContext(municipality).orElseThrow();
    assertThat(mail.paymentNotesByFacility()).isTrue();
    assertThat(mail.paymentNote().gl()).isEqualTo("No mostrador, ao chegar");
    assertThat(mail.privacyPublished()).isTrue();
  }
}
