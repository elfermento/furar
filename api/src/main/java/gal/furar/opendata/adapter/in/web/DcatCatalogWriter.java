// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import gal.furar.opendata.application.port.in.CatalogRecord;
import gal.furar.opendata.domain.Dataset;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.Nullable;

/**
 * A ficha de catálogo en RDF/XML segundo DCAT-AP-ES 1.0.0 (ADR-0034, punto 19), co {@code
 * XMLStreamWriter} do JDK: un {@code dcat:Catalog} cos tres {@code dcat:Dataset} e unha {@code
 * dcat:Distribution} por formato. O publicador vai co URI oficial do organismo polo seu DIR3.
 */
final class DcatCatalogWriter {

  static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  static final String DCAT = "http://www.w3.org/ns/dcat#";
  static final String DCT = "http://purl.org/dc/terms/";
  static final String FOAF = "http://xmlns.com/foaf/0.1/";
  static final String XSD = "http://www.w3.org/2001/XMLSchema#";
  static final String ORGANISM = "http://datos.gob.es/recurso/sector-publico/org/Organismo/";
  static final String THEME_TAXONOMY = "http://datos.gob.es/kos/sector-publico/sector";
  static final String THEME = "http://datos.gob.es/kos/sector-publico/sector/deporte";
  static final String LANGUAGE = "http://publications.europa.eu/resource/authority/language/";
  static final String LICENSE =
      "http://publications.europa.eu/resource/authority/licence/CC_BY_4_0";
  static final String FREQUENCY = "http://publications.europa.eu/resource/authority/frequency/";
  static final String FILE_TYPE = "http://publications.europa.eu/resource/authority/file-type/";
  static final String MEDIA_TYPE = "http://www.iana.org/assignments/media-types/";

  private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  /** Instante con segundos e desfase. */
  private static final DateTimeFormatter ISSUED =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

  private final String publicUrl;

  DcatCatalogWriter(String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  String write(CatalogRecord record) {
    StringWriter text = new StringWriter();
    try {
      XMLStreamWriter x = XMLOutputFactory.newFactory().createXMLStreamWriter(text);
      x.writeStartDocument("UTF-8", "1.0");
      x.writeStartElement("rdf", "RDF", RDF);
      x.writeNamespace("rdf", RDF);
      x.writeNamespace("dcat", DCAT);
      x.writeNamespace("dct", DCT);
      x.writeNamespace("foaf", FOAF);
      x.writeNamespace("xsd", XSD);

      String page = page(record);
      x.writeStartElement("dcat", "Catalog", DCAT);
      x.writeAttribute("rdf", RDF, "about", page);
      String who = record.publisherName();
      literal(x, DCT, "title", "es", "Datos abiertos deportivos: " + who);
      literal(x, DCT, "title", "gl", "Datos abertos deportivos: " + who);
      literal(
          x,
          DCT,
          "description",
          "es",
          "Instalaciones deportivas, cierres y ocupación pasada publicados por " + who + ".");
      literal(
          x,
          DCT,
          "description",
          "gl",
          "Instalacións deportivas, peches e ocupación pasada que publica " + who + ".");
      resource(x, DCT, "publisher", ORGANISM + record.dir3Code());
      typed(x, DCT, "issued", ISSUED.format(record.issued().atZone(ZONE)), "dateTime");
      LocalDate modified = latest(record.updatedOn());
      if (modified != null) {
        typed(x, DCT, "modified", modified.toString(), "date");
      }
      resource(x, FOAF, "homepage", page);
      resource(x, DCAT, "themeTaxonomy", THEME_TAXONOMY);
      resource(x, DCT, "language", LANGUAGE + "SPA");
      resource(x, DCT, "language", LANGUAGE + "GLG");
      resource(x, DCT, "license", LICENSE);
      for (Dataset dataset : Dataset.values()) {
        x.writeStartElement("dcat", "dataset", DCAT);
        dataset(x, record, dataset, page);
        x.writeEndElement();
      }
      x.writeEndElement();
      x.writeEndElement();
      x.writeEndDocument();
      x.close();
    } catch (XMLStreamException e) {
      throw new IllegalStateException(e);
    }
    return text.toString();
  }

  private void dataset(XMLStreamWriter x, CatalogRecord record, Dataset dataset, String page)
      throws XMLStreamException {
    String id = page + "?conxunto=" + dataset.fileName();
    x.writeStartElement("dcat", "Dataset", DCAT);
    x.writeAttribute("rdf", RDF, "about", id);
    literal(x, DCT, "identifier", null, id);
    Texts t = texts(dataset);
    literal(x, DCT, "title", "es", t.titleEs());
    literal(x, DCT, "title", "gl", t.titleGl());
    literal(x, DCT, "description", "es", t.descriptionEs() + FORMAT_ES);
    literal(x, DCT, "description", "gl", t.descriptionGl() + FORMAT_GL);
    resource(x, DCAT, "theme", THEME);
    for (String keyword : t.keywordsEs()) {
      literal(x, DCAT, "keyword", "es", keyword);
    }
    for (String keyword : t.keywordsGl()) {
      literal(x, DCAT, "keyword", "gl", keyword);
    }
    resource(x, DCT, "publisher", ORGANISM + record.dir3Code());
    typed(x, DCT, "issued", ISSUED.format(record.issued().atZone(ZONE)), "dateTime");
    LocalDate modified = record.updatedOn().get(dataset);
    if (modified != null) {
      typed(x, DCT, "modified", modified.toString(), "date");
    }
    resource(
        x,
        DCT,
        "accrualPeriodicity",
        FREQUENCY + (dataset == Dataset.OCCUPANCY ? "DAILY" : "IRREG"));
    resource(x, DCT, "license", LICENSE);
    if (dataset == Dataset.OCCUPANCY) {
      x.writeStartElement("dct", "temporal", DCT);
      x.writeStartElement("dct", "PeriodOfTime", DCT);
      typed(x, DCAT, "startDate", record.availableFrom().toString(), "date");
      typed(x, DCAT, "endDate", record.availableTo().toString(), "date");
      x.writeEndElement();
      x.writeEndElement();
    }
    for (String format : formats(dataset)) {
      x.writeStartElement("dcat", "distribution", DCAT);
      x.writeStartElement("dcat", "Distribution", DCAT);
      String url = file(record, dataset, format);
      x.writeAttribute("rdf", RDF, "about", url);
      literal(x, DCT, "title", "es", t.titleEs() + " (" + format.toUpperCase() + ")");
      literal(x, DCT, "title", "gl", t.titleGl() + " (" + format.toUpperCase() + ")");
      resource(x, DCAT, "accessURL", url);
      resource(x, DCAT, "downloadURL", url);
      resource(x, DCT, "format", FILE_TYPE + format.toUpperCase());
      resource(x, DCAT, "mediaType", MEDIA_TYPE + mediaType(format));
      resource(x, DCT, "license", LICENSE);
      x.writeEndElement();
      x.writeEndElement();
    }
    x.writeEndElement();
  }

  private static final String FORMAT_ES =
      " Listas separadas por «|»; decimales con punto; fechas y horas en ISO 8601, en la zona"
          + " Europe/Madrid. Nombres de columnas y códigos en gallego, iguales con lang=gl y"
          + " lang=es.";
  private static final String FORMAT_GL =
      " Listas separadas por «|»; decimais con punto; datas e horas en ISO 8601, na zona"
          + " Europe/Madrid. Nomes de columnas e códigos en galego, iguais con lang=gl e lang=es.";

  private record Texts(
      String titleEs,
      String titleGl,
      String descriptionEs,
      String descriptionGl,
      List<String> keywordsEs,
      List<String> keywordsGl) {}

  private static Texts texts(Dataset dataset) {
    return switch (dataset) {
      case FACILITIES ->
          new Texts(
              "Instalaciones deportivas",
              "Instalacións deportivas",
              "Las instalaciones deportivas que se ven en la web y lo que se puede reservar en cada"
                  + " una: dónde están, horarios, precios y condiciones. En CSV, una fila por uso.",
              "As instalacións deportivas que se ven na web e o que se pode reservar en cada unha:"
                  + " onde están, horarios, prezos e condicións. En CSV, unha fila por uso.",
              List.of("deporte", "instalaciones deportivas", "reservas"),
              List.of("deporte", "instalacións deportivas", "reservas"));
      case CLOSURES ->
          new Texts(
              "Cierres de instalaciones deportivas",
              "Peches das instalacións deportivas",
              "Los días en los que las instalaciones no abren: festivos y cierres de día entero,"
                  + " del 1 de enero del año anterior al 31 de diciembre del siguiente.",
              "Os días nos que as instalacións non abren: festivos e peches de día enteiro, do 1"
                  + " de xaneiro do ano anterior ao 31 de decembro do seguinte.",
              List.of("deporte", "festivos", "cierres"),
              List.of("deporte", "festivos", "peches"));
      case OCCUPANCY ->
          new Texts(
              "Ocupación de instalaciones deportivas",
              "Ocupación das instalacións deportivas",
              "La parte reservada de cada instalación que muestra en la web sus horas libres, por"
                  + " cuartos de hora en los que estaba abierta, de días pasados (últimos 12"
                  + " meses). Sin parámetros, el último mes entero; con desde y ata (AAAA-MM-DD,"
                  + " incluidos), como mucho 12 meses en un ayuntamiento y uno en el conjunto de"
                  + " todos.",
              "A parte reservada de cada instalación que mostra na web as súas horas libres, por"
                  + " cuartos de hora nos que estaba aberta, de días pasados (últimos 12 meses)."
                  + " Sen parámetros, o último mes enteiro; con desde e ata (AAAA-MM-DD,"
                  + " incluídos), como moito 12 meses nun concello e un no conxunto de todos.",
              List.of("deporte", "ocupación", "uso de instalaciones"),
              List.of("deporte", "ocupación", "uso das instalacións"));
    };
  }

  static List<String> formats(Dataset dataset) {
    return dataset == Dataset.FACILITIES
        ? List.of("csv", "json", "geojson")
        : List.of("csv", "json");
  }

  private static String mediaType(String format) {
    return switch (format) {
      case "json" -> "application/json";
      case "geojson" -> "application/geo+json";
      default -> "text/csv";
    };
  }

  private String page(CatalogRecord record) {
    return record.slug() == null
        ? publicUrl + "/datos-abertos"
        : publicUrl + "/" + record.slug() + "/datos-abertos";
  }

  private String file(CatalogRecord record, Dataset dataset, String format) {
    String base =
        record.slug() == null
            ? publicUrl + "/api/v1/catalog/open-data/"
            : publicUrl + "/api/v1/municipalities/" + record.slug() + "/open-data/";
    return base + OpenDataPaths.file(dataset) + "." + format + "?lang=gl";
  }

  private static @Nullable LocalDate latest(Map<Dataset, LocalDate> days) {
    return days.values().stream().max(LocalDate::compareTo).orElse(null);
  }

  private static void literal(
      XMLStreamWriter x, String ns, String name, @Nullable String lang, String value)
      throws XMLStreamException {
    x.writeStartElement(prefix(ns), name, ns);
    if (lang != null) {
      x.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "lang", lang);
    }
    x.writeCharacters(value);
    x.writeEndElement();
  }

  private static void typed(XMLStreamWriter x, String ns, String name, String value, String type)
      throws XMLStreamException {
    x.writeStartElement(prefix(ns), name, ns);
    x.writeAttribute("rdf", RDF, "datatype", XSD + type);
    x.writeCharacters(value);
    x.writeEndElement();
  }

  private static void resource(XMLStreamWriter x, String ns, String name, String uri)
      throws XMLStreamException {
    x.writeEmptyElement(prefix(ns), name, ns);
    x.writeAttribute("rdf", RDF, "resource", uri);
  }

  private static String prefix(String ns) {
    return switch (ns) {
      case DCAT -> "dcat";
      case DCT -> "dct";
      case FOAF -> "foaf";
      default -> "rdf";
    };
  }
}
