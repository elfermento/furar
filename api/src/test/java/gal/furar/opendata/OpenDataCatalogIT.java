// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Datos abertos de toda a instalación (ADR-0034, puntos 8 e 11): os concellos publicados co
 * interruptor aceso, concello a concello coa súa RLS, cada fila co seu concello; como moito un mes
 * na ocupación.
 */
class OpenDataCatalogIT extends AbstractOpenDataIT {

  static final String ALL = "/api/v1/catalog/open-data";

  @Test
  void everyPublishedMunicipalityWithItsRowsAndOnlyOneMonthOfOccupancy() {
    String a = uniqueSlug("oda");
    String b = uniqueSlug("odb");
    String off = uniqueSlug("odc");
    UUID ma = municipalityReady(a);
    UUID mb = municipalityReady(b);
    UUID mc = municipalityReady(off);
    Published pa = publish(a, ma, "Pista A", true, false);
    Published pb = publish(b, mb, "Pista B", false, true);
    Published pc = publish(off, mc, "Pista C", true, false);
    jdbc.sql("update municipality set open_data_since = null where id = :id")
        .param("id", mc)
        .update();
    LocalDate yesterday = today().minusDays(1);
    occupy(pa, yesterday, 600, 60);
    occupy(pb, yesterday, 660, 60);
    refresh.refresh();

    JsonNode summary = api().get(ALL).send().json();
    assertThat(summary.get("occupancy").get("maxMonths").asInt()).isEqualTo(1);
    assertThat(summary.get("municipalities").asInt()).isGreaterThanOrEqualTo(2);

    Api.Response csv = api().get(ALL + "/facilities.csv").send();
    assertThat(csv.status()).as(csv.toString()).isEqualTo(200);
    assertThat(csv.header("Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(csv.header("Content-Disposition")).contains("todos-instalacions.csv");
    assertThat(csv.body())
        .startsWith("﻿concello;id;")
        .contains(a + ";" + pa.id())
        .contains(b + ";" + pb.id())
        .doesNotContain(pc.id().toString())
        .doesNotContain("campo_");

    Api.Response json = api().get(ALL + "/facilities.json").send();
    assertThat(json.json().get("concello").asString()).isEqualTo("todos");

    // A lectura en tramos, concello a concello, ve as filas de cada un (non 0).
    Api.Response occupancy =
        api().get(ALL + "/occupancy.csv?desde=" + yesterday + "&ata=" + yesterday).send();
    assertThat(occupancy.status()).as(occupancy.toString()).isEqualTo(200);
    assertThat(occupancy.body())
        .contains(a + ";" + pa.id())
        .contains(b + ";" + pb.id())
        .doesNotContain(pc.id().toString());
    assertThat(occupancy.header("ETag")).isNull();

    assertThat(
            api()
                .get(ALL + "/occupancy.csv?desde=" + today().minusMonths(2) + "&ata=" + yesterday)
                .send()
                .status())
        .isEqualTo(400);
    // Sen o publicador do despregue, sen ficha de todos.
    assertThat(api().get(ALL + "/dcat.rdf").send().status()).isEqualTo(404);
  }
}
