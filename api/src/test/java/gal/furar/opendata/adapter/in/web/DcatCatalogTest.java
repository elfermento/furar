// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.opendata.application.port.in.CatalogRecord;
import gal.furar.opendata.domain.Dataset;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/** A ficha DCAT-AP-ES (ADR-0034, punto 19): propiedades obrigatorias e URI fixados. */
class DcatCatalogTest {

  static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

  static String write(String slug) {
    return new DcatCatalogWriter("https://reservas.example.org")
        .write(
            new CatalogRecord(
                slug,
                "Concello de Narón",
                "L01150548",
                Instant.parse("2026-11-11T09:00:00Z"),
                Map.of(Dataset.OCCUPANCY, LocalDate.of(2026, 11, 11)),
                LocalDate.of(2025, 11, 11),
                LocalDate.of(2026, 11, 10)));
  }

  @Test
  void isValidXmlWithTheProfileUris() throws Exception {
    String rdf = write("naron");
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document doc =
        factory
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(rdf.getBytes(StandardCharsets.UTF_8)));
    assertThat(doc.getElementsByTagNameNS("http://www.w3.org/ns/dcat#", "Catalog").getLength())
        .isEqualTo(1);
    assertThat(doc.getElementsByTagNameNS("http://www.w3.org/ns/dcat#", "Dataset").getLength())
        .isEqualTo(3);
    assertThat(doc.getElementsByTagNameNS("http://www.w3.org/ns/dcat#", "Distribution").getLength())
        .isEqualTo(7);
    assertThat(rdf)
        .contains("http://datos.gob.es/recurso/sector-publico/org/Organismo/L01150548")
        .contains("http://datos.gob.es/kos/sector-publico/sector/deporte")
        .contains("http://datos.gob.es/kos/sector-publico/sector\"")
        .contains("http://publications.europa.eu/resource/authority/language/SPA")
        .contains("http://publications.europa.eu/resource/authority/language/GLG")
        .contains("http://publications.europa.eu/resource/authority/licence/CC_BY_4_0")
        .contains("http://publications.europa.eu/resource/authority/frequency/DAILY")
        .contains("http://publications.europa.eu/resource/authority/frequency/IRREG")
        .contains("http://publications.europa.eu/resource/authority/file-type/GEOJSON")
        .contains("http://www.iana.org/assignments/media-types/application/geo+json")
        .contains("xml:lang=\"es\"")
        .contains("xml:lang=\"gl\"")
        .contains("2026-11-11T10:00:00+01:00")
        .contains("https://reservas.example.org/naron/datos-abertos")
        .contains(
            "https://reservas.example.org/api/v1/municipalities/naron/open-data/occupancy.csv?lang=gl");
  }

  @Test
  void allPointsToTheCatalogRoutes() {
    assertThat(write(null))
        .contains("https://reservas.example.org/datos-abertos")
        .contains("https://reservas.example.org/api/v1/catalog/open-data/facilities.geojson");
  }
}
