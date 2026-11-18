// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Sen {@code FURAR_JOIN_REQUEST_EMAIL}, «¿Es un concello?» non existe (ADR-0042): o endpoint é 404
 * e a web sabe que non hai función. Contexto propio (outra propiedade).
 */
@TestPropertySource(properties = "furar.join-request.email=")
class MunicipalityJoinRequestDisabledIT extends AbstractIT {

  @Autowired RecordingMailSender mails;

  @Test
  void withoutTheFeatureTheEndpointIsNotFoundAndNothingIsSent() {
    int before = mails.sent().size();
    Api.Response response =
        api()
            .post(
                "/api/v1/municipality-join-requests",
                MunicipalityJoinRequestIT.body("Concello sen función"))
            .header("X-Forwarded-For", "192.0.2.10")
            .send();
    assertThat(response.status()).as(response.toString()).isEqualTo(404);
    assertThat(response.json().get("code").asString()).isEqualTo("common.not-found");
    assertThat(mails.sent()).hasSize(before);
  }

  @Test
  void theWebKnowsThereIsNoFeature() {
    Api.Response settings = api().get("/api/v1/deployment-settings").send();
    assertThat(settings.status()).isEqualTo(200);
    assertThat(settings.json().get("joinRequests").asBoolean()).isFalse();
  }
}
