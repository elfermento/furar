// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.PublicCustomFields;
import gal.furar.facility.PublicFacilityData;
import gal.furar.opendata.application.port.in.ClosuresFile;
import gal.furar.opendata.application.port.in.FacilitiesFile;
import gal.furar.opendata.application.port.in.OpenDataPublisher;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os ficheiros de instalacións e peches (ADR-0034, puntos 14 a 17): columnas e orde, unha fila por
 * uso, punto decimal, listas con «|», códigos iguais en gl e es e campos propios.
 */
class OpenDataWriterTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final UUID FACILITY = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final UUID EMPTY = UUID.fromString("00000000-0000-0000-0000-000000000002");
  static final UUID USE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  static final UUID PARTS = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

  static final OpenDataPublisher NARON =
      new OpenDataPublisher(
          UUID.randomUUID(), "naron", "Narón", Instant.parse("2026-11-11T00:00:00Z"), null, null);

  static FacilitiesFile file() {
    Map<Integer, List<PublicFacilityData.Hours>> week = new LinkedHashMap<>();
    for (int d = 1; d <= 8; d++) {
      week.put(
          d,
          d >= 7
              ? List.of()
              : List.of(
                  new PublicFacilityData.Hours(540, 840), new PublicFacilityData.Hours(960, 1320)));
    }
    Map<String, Object> custom = new LinkedHashMap<>();
    custom.put("pavimento", "artificial");
    custom.put("aforo_bancada", new BigDecimal("300.5"));
    custom.put("iluminacion", true);
    custom.put("modalidades", List.of("f11", "f7"));
    PublicFacilityData.Item full =
        new PublicFacilityData.Item(
            FACILITY,
            "Pavillón municipal de O Alto",
            new PublicFacilityData.Text("Texto", "Texto es"),
            "Rúa do Alto",
            "12",
            "05570",
            "Narón",
            new BigDecimal("43.5091234"),
            new BigDecimal("-8.1"),
            "981000000",
            null,
            List.of(
                new PublicFacilityData.SportItem(
                    "padel", new PublicFacilityData.Text("Pádel", "Pádel")),
                new PublicFacilityData.SportItem(
                    null, new PublicFacilityData.Text("Bolos celtas", "Bolos celtas"))),
            null,
            List.of("CHANGING_ROOMS", "STEP_FREE_ACCESS"),
            false,
            true,
            false,
            week,
            null,
            custom,
            List.of(
                new PublicFacilityData.Use(
                    USE,
                    "Pista enteira",
                    "WHOLE",
                    90,
                    List.of(),
                    null,
                    31,
                    List.of(),
                    "PAID",
                    600,
                    null,
                    7,
                    2,
                    3,
                    "WEEK",
                    24),
                new PublicFacilityData.Use(
                    PARTS,
                    "Media pista",
                    "PARTS",
                    60,
                    List.of("Metade A", "Metade|B"),
                    null,
                    96,
                    List.of(
                        new PublicFacilityData.Period(
                            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31))),
                    "NONE",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));
    PublicFacilityData.Item bare =
        new PublicFacilityData.Item(
            EMPTY,
            "Campo sen usos",
            null,
            "Rúa B",
            null,
            "15570",
            "Narón",
            null,
            null,
            null,
            null,
            List.of(),
            true,
            List.of(),
            true,
            false,
            false,
            null,
            null,
            Map.of(),
            List.of());
    List<PublicCustomFields.Definition> defs =
        List.of(
            new PublicCustomFields.Definition(
                "pavimento", new LocalizedText("Pavimento", "Pavimento"), "SELECT", List.of()),
            new PublicCustomFields.Definition(
                "aforo_bancada", new LocalizedText("Aforo", "Aforo"), "NUMBER", List.of()),
            new PublicCustomFields.Definition(
                "iluminacion", new LocalizedText("Luz", "Luz"), "BOOLEAN", List.of()),
            new PublicCustomFields.Definition(
                "modalidades", new LocalizedText("Modos", "Modos"), "MULTI_SELECT", List.of()));
    return new FacilitiesFile(
        List.of(new FacilitiesFile.Part(NARON, new PublicFacilityData(List.of(full, bare), defs))),
        LocalDate.of(2026, 11, 10));
  }

  static OpenDataWriter writer(Language language, boolean all) {
    return new OpenDataWriter(JSON, "https://reservas.example.org/", language, all);
  }

  @Test
  void csvHasBomSemicolonsCrlfAndTheFixedColumnsInOrder() {
    String csv = writer(Language.GL, false).facilitiesCsv(file());
    assertThat(csv).startsWith("﻿id;nome;descricion;enderezo;rua;numero;codigo_postal;");
    String[] lines = csv.substring(1).split("\r\n");
    assertThat(lines[0])
        .endsWith(
            "cancelar_sen_custo_horas;campo_aforo_bancada;campo_iluminacion;campo_modalidades;"
                + "campo_pavimento");
    // Unha fila por uso e a instalación sen usos nunha fila.
    assertThat(lines).hasSize(4);
    String whole = lines[1];
    assertThat(whole)
        .contains(";43.509123;-8.100000;")
        .contains(";05570;")
        .contains("Rúa do Alto, 12, 05570 Narón")
        .contains(";Pádel|Bolos celtas;padel;")
        .contains(";changing_rooms|step_free_access;non;si;")
        .contains("lu 09:00-14:00,16:00-22:00|ma")
        .contains("do pechado|fe pechado")
        .contains(";enteira;90;;;lu|ma|me|xo|ve;;6.00;franxa;;7;2;3;semana;24;")
        .contains("https://reservas.example.org/naron/instalacions/" + FACILITY)
        .endsWith(";300.5;si;f11|f7;artificial");
    assertThat(lines[2])
        .contains(";partes;60;Metade A|Metade\\|B;;")
        .contains(";2026-07-01/2026-08-31;;;");
    assertThat(lines[3]).startsWith(EMPTY + ";Campo sen usos;;").contains(";si;;si;non;;");
    assertThat(csv).doesNotContain("ridega").doesNotContain("FIELD");
  }

  @Test
  void codesAndColumnNamesDoNotChangeWithTheLanguage() {
    String gl = writer(Language.GL, false).facilitiesCsv(file());
    String es = writer(Language.ES, false).facilitiesCsv(file());
    assertThat(gl.split("\r\n")[0]).isEqualTo(es.split("\r\n")[0]);
    assertThat(es).contains(";enteira;").contains(";Texto es;").contains(";si;");
  }

  @Test
  void jsonHasTheEnvelopeTypesAndUsesInside() {
    JsonNode root = JSON.readTree(writer(Language.GL, false).facilitiesJson(file()));
    assertThat(root.get("conxunto").asString()).isEqualTo("instalacions");
    assertThat(root.get("concello").asString()).isEqualTo("naron");
    assertThat(root.get("licenza").asString()).isEqualTo("CC BY 4.0");
    assertThat(root.get("atribucion").asString()).isEqualTo("Concello de Narón");
    assertThat(root.get("zona_horaria").asString()).isEqualTo("Europe/Madrid");
    assertThat(root.get("campos").get(0).get("codigo").asString()).isEqualTo("aforo_bancada");
    JsonNode first = root.get("datos").get(0);
    assertThat(first.get("latitude").isNumber()).isTrue();
    assertThat(first.get("cuberta").isNull()).isTrue();
    assertThat(first.get("usos")).hasSize(2);
    assertThat(first.get("usos").get(0).get("prezo_euros").decimalValue())
        .isEqualByComparingTo("6.00");
    assertThat(first.get("usos").get(1).get("prezo_euros").isNull()).isTrue();
    assertThat(first.get("horario").get("do")).isEmpty();
    assertThat(first.get("campos").get("modalidades")).hasSize(2);
    assertThat(root.get("datos").get(1).get("usos")).isEmpty();
  }

  @Test
  void geoJsonPointsAreLonLatAndPropertiesMatchTheJson() {
    JsonNode root = JSON.readTree(writer(Language.GL, false).facilitiesGeoJson(file()));
    assertThat(root.get("type").asString()).isEqualTo("FeatureCollection");
    JsonNode feature = root.get("features").get(0);
    assertThat(feature.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(-8.1);
    assertThat(feature.get("properties").has("latitude")).isFalse();
    assertThat(root.get("features").get(1).get("geometry").isNull()).isTrue();
  }

  @Test
  void allHasTheMunicipalityColumnAndNoCustomColumns() {
    String csv = writer(Language.GL, true).facilitiesCsv(file());
    assertThat(csv.split("\r\n")[0]).startsWith("﻿concello;id;").doesNotContain("campo_");
    JsonNode root = JSON.readTree(writer(Language.GL, true).facilitiesJson(file()));
    assertThat(root.get("concello").asString()).isEqualTo("todos");
    assertThat(root.get("concellos").get(0).get("campos")).hasSize(4);
    assertThat(root.get("datos").get(0).get("concello").asString()).isEqualTo("naron");
  }

  @Test
  void closuresTranslateHolidaysOnly() {
    ClosuresFile file =
        new ClosuresFile(
            List.of(
                new ClosuresFile.Part(
                    NARON,
                    List.of(
                        new ClosuresFile.Row(
                            LocalDate.of(2026, 12, 25), true, null, "Nadal", "Navidad"),
                        new ClosuresFile.Row(
                            LocalDate.of(2026, 12, 26),
                            false,
                            FACILITY,
                            "Mantemento",
                            "Mantemento"),
                        new ClosuresFile.Row(
                            LocalDate.of(2026, 1, 2), false, FACILITY, null, null)))),
            null);
    String gl = writer(Language.GL, false).closuresCsv(file);
    assertThat(gl.split("\r\n"))
        .containsExactly(
            "﻿data;tipo;instalacion;motivo",
            "2026-12-25;festivo;;Nadal",
            "2026-12-26;peche;" + FACILITY + ";Mantemento",
            "2026-01-02;peche;" + FACILITY + ";");
    assertThat(writer(Language.ES, false).closuresCsv(file)).contains(";Navidad");
    JsonNode json = JSON.readTree(writer(Language.ES, false).closuresJson(file));
    assertThat(json.get("datos").get(0).get("instalacion").isNull()).isTrue();
    assertThat(json.get("actualizado").isNull()).isTrue();
  }
}
