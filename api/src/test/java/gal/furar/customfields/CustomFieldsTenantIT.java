// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * D14/D18: as definicións de A non aparecen nin validan en B. As de A sementanse por JDBC para que
 * unha fuga falle nas asercións e non no {@code setUp} (probar-violacion.sh j).
 */
class CustomFieldsTenantIT extends AbstractIT {

  @Test
  void definitionsOfOneMunicipalityDoNotExistInAnother() {
    String a = uniqueSlug("cf-a");
    String b = uniqueSlug("cf-b");
    UUID municipalityA = id(createMunicipality(a));
    createMunicipality(b);
    insertField(municipalityA, "only_in_a", "PUBLIC");

    Api.Response listB =
        api()
            .get(
                "/api/v1/municipalities/"
                    + b
                    + "/custom-fields?target=MUNICIPALITY&includeInactive=true")
            .token(TestTokens.manager(b))
            .send();
    assertThat(listB.status()).isEqualTo(200);
    assertThat(listB.json().size()).isZero();

    Api.Response schemaB =
        api().get("/api/v1/municipalities/" + b + "/custom-fields/MUNICIPALITY/schema").send();
    assertThat(schemaB.json().get("properties").size()).isZero();

    Api.Response writeB =
        api()
            .put("/api/v1/municipalities/" + b, updateBody("B", Map.of("only_in_a", "x")))
            .token(TestTokens.manager(b))
            .ifMatch(0)
            .send();
    assertThat(writeB.status()).isEqualTo(400);
    assertThat(writeB.json().get("errors").get(0).get("code").asString())
        .isEqualTo("customfield.unknown-field");

    Api.Response writeA =
        api()
            .put("/api/v1/municipalities/" + a, updateBody("A", Map.of("only_in_a", "x")))
            .token(TestTokens.manager(a))
            .ifMatch(0)
            .send();
    assertThat(writeA.status()).isEqualTo(200);
    assertThat(writeA.json().get("customFields").get("only_in_a").asString()).isEqualTo("x");
  }

  @Test
  void sameCodeCanExistInTwoMunicipalities() {
    String a = uniqueSlug("cf-c");
    String b = uniqueSlug("cf-d");
    UUID municipalityA = id(createMunicipality(a));
    createMunicipality(b);
    insertField(municipalityA, "phone", "PUBLIC");
    Api.Response inB =
        api()
            .post("/api/v1/municipalities/" + b + "/custom-fields", textField("phone", "PUBLIC"))
            .token(TestTokens.manager(b))
            .send();
    assertThat(inB.status()).as("same code in another municipality: %s", inB).isEqualTo(201);
    Api.Response duplicate =
        api()
            .post("/api/v1/municipalities/" + a + "/custom-fields", textField("phone", "PUBLIC"))
            .token(TestTokens.manager(a))
            .send();
    assertThat(duplicate.status()).isEqualTo(409);
    assertThat(duplicate.json().get("code").asString()).isEqualTo("customfield.code-taken");
  }
}
