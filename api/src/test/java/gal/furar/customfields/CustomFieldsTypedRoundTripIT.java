// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * D18 (ADR-0011, especificación 1b): os 7 tipos gárdanse en táboas tipadas, cada un na columna do
 * seu tipo, e volven sen perda (decimais exactos, datas, Unicode); gardar outra vez substitúe os
 * valores e un valor quitado desaparece.
 */
class CustomFieldsTypedRoundTripIT extends AbstractIT {

  @Test
  void sevenTypesRoundTripWithoutLoss() {
    String slug = uniqueSlug("rt");
    UUID municipality = id(createMunicipality(slug));
    createField(slug, textField("short_text", "PUBLIC"));
    createField(slug, field("long_text", "LONG_TEXT", "PUBLIC", Map.of()));
    createField(slug, field("amount", "NUMBER", "PUBLIC", Map.of("rules", Map.of("decimals", 4))));
    createField(slug, field("opened_on", "DATE", "PUBLIC", Map.of()));
    createField(slug, field("has_defibrillator", "BOOLEAN", "PUBLIC", Map.of()));
    Map<String, Object> options =
        Map.of(
            "options",
            List.of(
                Map.of("code", "north", "label", Map.of("gl", "Norte", "es", "Norte")),
                Map.of("code", "south", "label", Map.of("gl", "Sur", "es", "Sur"))));
    createField(slug, field("zone", "SELECT", "PUBLIC", options));
    createField(slug, field("zones", "MULTI_SELECT", "PUBLIC", options));

    Map<String, Object> values =
        Map.of(
            "short_text", "Pavillón «O Alto» — ñ, ç, \"comiñas\"",
            "long_text", "liña 1\nliña 2\t✓",
            "amount", new java.math.BigDecimal("12345.6789"),
            "opened_on", "2026-02-28",
            "has_defibrillator", false,
            "zone", "north",
            "zones", List.of("south", "north"));
    Api.Response saved =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("Round", values))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    assertThat(saved.json().get("version").asLong()).isEqualTo(1);

    JsonNode read = api().get("/api/v1/municipalities/" + slug).send().json().get("customFields");
    assertThat(read.get("short_text").asString()).isEqualTo(values.get("short_text"));
    assertThat(read.get("long_text").asString()).isEqualTo(values.get("long_text"));
    assertThat(read.get("amount").decimalValue()).isEqualByComparingTo("12345.6789");
    assertThat(read.get("amount").toString()).isEqualTo("12345.6789");
    assertThat(read.get("opened_on").asString()).isEqualTo("2026-02-28");
    assertThat(read.get("has_defibrillator").isBoolean()).isTrue();
    assertThat(read.get("has_defibrillator").asBoolean()).isFalse();
    assertThat(read.get("zone").asString()).isEqualTo("north");
    // Sen orde manual (especificación 1b): as opcións marcadas volven en orde de código.
    assertThat(read.get("zones").toString()).isEqualTo("[\"north\",\"south\"]");

    // Cada valor na columna do seu tipo: a base garante o tipo.
    Map<String, String> columns = new java.util.TreeMap<>();
    jdbc.sql(
            """
            select f.code,
                   concat_ws('|', 't:' || v.text_value, 'n:' || v.number_value::text,
                             'd:' || v.date_value::text, 'b:' || v.bool_value::text,
                             'o:' || o.code) as stored
            from custom_field_value v
            join custom_field f on f.id = v.field_id
            left join custom_field_option o on o.id = v.option_id
            where v.entity_id = :m and v.target = 'MUNICIPALITY'
            """)
        .param("m", municipality)
        .query((rs, n) -> columns.put(rs.getString("code"), rs.getString("stored")))
        .list();
    assertThat(columns)
        .containsEntry("amount", "n:12345.6789")
        .containsEntry("opened_on", "d:2026-02-28")
        .containsEntry("has_defibrillator", "b:false")
        .containsEntry("zone", "o:north")
        .containsEntry("zones", "")
        .hasSize(7);
    Integer chosen =
        jdbc.sql(
                """
                select count(*) from custom_field_value_option c
                join custom_field_value v on v.id = c.value_id
                where v.entity_id = :m
                """)
            .param("m", municipality)
            .query(Integer.class)
            .single();
    assertThat(chosen).isEqualTo(2);

    // Gardar outra vez substitúe: o que non vén, desaparece; a versión sobe.
    Api.Response again =
        api()
            .put(
                "/api/v1/municipalities/" + slug,
                updateBody("Round", Map.of("zones", List.of("south"), "amount", 1)))
            .token(TestTokens.manager(slug))
            .ifMatch(1)
            .send();
    assertThat(again.status()).as(again.body()).isEqualTo(200);
    assertThat(again.json().get("version").asLong()).isEqualTo(2);
    JsonNode after = again.json().get("customFields");
    assertThat(after.propertyNames()).containsExactlyInAnyOrder("zones", "amount");
    assertThat(after.get("zones").toString()).isEqualTo("[\"south\"]");
    Integer rows =
        jdbc.sql("select count(*) from custom_field_value where entity_id = :m")
            .param("m", municipality)
            .query(Integer.class)
            .single();
    assertThat(rows).isEqualTo(2);
  }
}
