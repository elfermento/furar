// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.opendata.AbstractOpenDataIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A ficha pública leva os campos propios públicos e activos da instalación e as súas definicións
 * (ADR-0034, punto 16), lidos como anónimo: nin os do persoal nin os desactivados.
 */
class PublicFacilityCustomFieldsIT extends AbstractOpenDataIT {

  @Test
  void onlyPublicActiveFieldsAndTheirDefinitions() {
    String slug = uniqueSlug("pcf");
    UUID municipality = municipalityReady(slug);
    Published f = publish(slug, municipality, "Campo", true, false);
    createField(slug, facilityField("pavimento", "TEXT", "PUBLIC"));
    createField(slug, facilityField("iluminacion", "BOOLEAN", "PUBLIC"));
    createField(slug, facilityField("chave", "TEXT", "STAFF"));
    JsonNode old = createField(slug, facilityField("antigo", "TEXT", "PUBLIC"));
    fieldValues(
        f, Map.of("pavimento", "Herba", "iluminacion", true, "chave", "Armario", "antigo", "x"));
    Api.Response deactivated =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/custom-fields/" + id(old) + "/deactivate",
                Map.of())
            .ifMatch(old.get("version").asLong())
            .token(TestTokens.manager(slug))
            .send();
    assertThat(deactivated.status()).as(deactivated.toString()).isEqualTo(200);

    Api.Response detail =
        api().get("/api/v1/municipalities/" + slug + "/public/facilities/" + f.id()).send();
    assertThat(detail.status()).as(detail.toString()).isEqualTo(200);
    JsonNode body = detail.json();
    assertThat(body.get("customFields").propertyNames())
        .containsExactlyInAnyOrder("pavimento", "iluminacion");
    assertThat(body.get("customFields").get("iluminacion").asBoolean()).isTrue();
    assertThat(body.get("customFieldDefinitions").findValuesAsString("code"))
        .containsExactly("iluminacion", "pavimento");
    assertThat(body.get("customFieldDefinitions").get(0).get("type").asString())
        .isEqualTo("BOOLEAN");
    assertThat(body.toString()).doesNotContain("Armario").doesNotContain("chave");
  }
}
