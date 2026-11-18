// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import gal.furar.facility.PublicCustomFields;
import gal.furar.facility.PublicFacilityData;
import gal.furar.opendata.application.port.in.ClosuresFile;
import gal.furar.opendata.application.port.in.FacilitiesFile;
import gal.furar.opendata.application.port.in.OpenDataPublisher;
import gal.furar.opendata.domain.Dataset;
import gal.furar.shared.domain.Language;
import gal.furar.shared.web.CsvCells;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os ficheiros dos conxuntos «Instalacións» e «Peches» (ADR-0034, puntos 14 a 17): CSV, JSON e
 * GeoJSON, cos nomes de columnas e os códigos en galego e sen acentos, iguais en {@code gl} e en
 * {@code es}. Só cambian co idioma a descrición, as normas, os deportes, os nomes dos festivos e as
 * etiquetas dos campos personalizados. Constrúese en memoria: son pequenos (decenas de kB).
 */
final class OpenDataWriter {

  static final String LICENSE = "CC BY 4.0";

  static final String ZONE = "Europe/Madrid";

  /** Os días da semana na orde da máscara (bit 0 = luns) e, ao final, os festivos. */
  static final List<String> DAYS = List.of("lu", "ma", "me", "xo", "ve", "sa", "do", "fe");

  static final List<String> FACILITY_COLUMNS =
      List.of(
          "id",
          "nome",
          "descricion",
          "enderezo",
          "rua",
          "numero",
          "codigo_postal",
          "localidade",
          "latitude",
          "lonxitude",
          "telefono",
          "correo",
          "url",
          "deportes",
          "deportes_codigos",
          "cuberta",
          "servizos",
          "pechada_temporalmente",
          "reserva_internet",
          "horario",
          "normas");

  static final List<String> USE_COLUMNS =
      List.of(
          "uso_id",
          "uso",
          "modo_aluguer",
          "franxa_minutos",
          "partes",
          "prazas",
          "dias",
          "datas",
          "prezo_euros",
          "prezo_por",
          "antelacion_minima_horas",
          "antelacion_maxima_dias",
          "franxas_maximas",
          "limite_por_persoa",
          "limite_periodo",
          "cancelar_sen_custo_horas");

  static final List<String> CLOSURE_COLUMNS = List.of("data", "tipo", "instalacion", "motivo");

  private final JsonMapper json;
  private final String publicUrl;
  private final Language language;
  private final boolean all;

  OpenDataWriter(JsonMapper json, String publicUrl, Language language, boolean all) {
    this.json = json;
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    this.language = language;
    this.all = all;
  }

  // ---------------------------------------------------------------------------------------
  // Instalacións

  String facilitiesCsv(FacilitiesFile file) {
    List<PublicCustomFields.Definition> fields =
        all || file.parts().isEmpty() ? List.of() : sorted(file.parts().getFirst().data());
    List<String> header = new ArrayList<>();
    if (all) {
      header.add("concello");
    }
    header.addAll(FACILITY_COLUMNS);
    header.addAll(USE_COLUMNS);
    fields.forEach(f -> header.add("campo_" + f.code()));
    StringBuilder out = new StringBuilder(CsvCells.BOM);
    line(out, header);
    for (FacilitiesFile.Part part : file.parts()) {
      for (PublicFacilityData.Item f : part.data().facilities()) {
        List<String> facility = facilityCells(part.publisher(), f);
        List<String> custom = new ArrayList<>();
        fields.forEach(d -> custom.add(customCell(d, f.customFields().get(d.code()))));
        List<List<String>> uses =
            f.uses().isEmpty()
                ? List.of(USE_COLUMNS.stream().map(c -> "").toList())
                : f.uses().stream().map(this::useCells).toList();
        for (List<String> use : uses) {
          List<String> row = new ArrayList<>();
          if (all) {
            row.add(CsvCells.text(part.publisher().slug()));
          }
          row.addAll(facility);
          row.addAll(use);
          row.addAll(custom);
          line(out, row);
        }
      }
    }
    return out.toString();
  }

  String facilitiesJson(FacilitiesFile file) {
    Map<String, Object> root = envelope(Dataset.FACILITIES, file.parts(), file.updatedOn());
    fieldsPerMunicipality(root, file);
    if (!all) {
      root.put(
          "campos",
          file.parts().isEmpty() ? List.of() : definitions(file.parts().getFirst().data()));
    }
    List<Object> data = new ArrayList<>();
    for (FacilitiesFile.Part part : file.parts()) {
      part.data().facilities().forEach(f -> data.add(facilityObject(part.publisher(), f, true)));
    }
    root.put("datos", data);
    return json.writeValueAsString(root);
  }

  String facilitiesGeoJson(FacilitiesFile file) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "FeatureCollection");
    Map<String, Object> envelope = envelope(Dataset.FACILITIES, file.parts(), file.updatedOn());
    fieldsPerMunicipality(envelope, file);
    envelope.forEach(root::put);
    if (!all) {
      root.put(
          "campos",
          file.parts().isEmpty() ? List.of() : definitions(file.parts().getFirst().data()));
    }
    List<Object> features = new ArrayList<>();
    for (FacilitiesFile.Part part : file.parts()) {
      for (PublicFacilityData.Item f : part.data().facilities()) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", f.id().toString());
        feature.put(
            "geometry",
            f.latitude() == null || f.longitude() == null
                ? null
                : Map.of(
                    "type",
                    "Point",
                    "coordinates",
                    List.of(scale(f.longitude(), 6), scale(f.latitude(), 6))));
        feature.put("properties", facilityObject(part.publisher(), f, false));
        features.add(feature);
      }
    }
    root.put("features", features);
    return json.writeValueAsString(root);
  }

  private List<String> facilityCells(OpenDataPublisher publisher, PublicFacilityData.Item f) {
    return List.of(
        f.id().toString(),
        CsvCells.text(f.name()),
        CsvCells.text(text(f.description())),
        CsvCells.text(address(f)),
        CsvCells.text(f.street()),
        CsvCells.text(f.streetNumber()),
        CsvCells.text(f.postalCode()),
        CsvCells.text(f.locality()),
        CsvCells.decimal(f.latitude(), 6),
        CsvCells.decimal(f.longitude(), 6),
        CsvCells.text(f.phone()),
        CsvCells.text(f.email()),
        CsvCells.text(url(publisher, f)),
        CsvCells.list(sports(f)),
        CsvCells.list(sportCodes(f)),
        f.covered() == null ? "" : yesNo(f.covered()),
        CsvCells.list(amenities(f)),
        yesNo(f.temporarilyClosed()),
        yesNo(f.onlineBooking()),
        CsvCells.text(weekCell(f.week())),
        CsvCells.text(text(f.houseRules())));
  }

  private List<String> useCells(PublicFacilityData.Use u) {
    BigDecimal price = price(u);
    return List.of(
        u.id().toString(),
        CsvCells.text(u.name()),
        mode(u.rentalMode()),
        Integer.toString(u.slotMinutes()),
        "PARTS".equals(u.rentalMode()) ? CsvCells.list(u.parts()) : "",
        number("SEATS".equals(u.rentalMode()) ? u.capacity() : null),
        CsvCells.list(days(u.weekdays())),
        CsvCells.list(u.periods().stream().map(p -> p.from() + "/" + p.to()).toList()),
        CsvCells.decimal(price, 2),
        price == null ? "" : per(u),
        number(u.minAdvanceHours()),
        number(u.maxAdvanceDays()),
        number(u.maxSlots()),
        number(u.capCount()),
        u.capPeriod() == null ? "" : period(u.capPeriod()),
        number(u.cancelHours()));
  }

  private Map<String, Object> facilityObject(
      OpenDataPublisher publisher, PublicFacilityData.Item f, boolean coordinates) {
    Map<String, Object> o = new LinkedHashMap<>();
    if (all) {
      o.put("concello", publisher.slug());
    }
    o.put("id", f.id().toString());
    o.put("nome", f.name());
    o.put("descricion", text(f.description()));
    o.put("enderezo", address(f));
    o.put("rua", f.street());
    o.put("numero", f.streetNumber());
    o.put("codigo_postal", f.postalCode());
    o.put("localidade", f.locality());
    if (coordinates) {
      o.put("latitude", scale(f.latitude(), 6));
      o.put("lonxitude", scale(f.longitude(), 6));
    }
    o.put("telefono", f.phone());
    o.put("correo", f.email());
    o.put("url", url(publisher, f));
    o.put("deportes", sports(f));
    o.put("deportes_codigos", sportCodes(f));
    o.put("cuberta", f.covered());
    o.put("servizos", amenities(f));
    o.put("pechada_temporalmente", f.temporarilyClosed());
    o.put("reserva_internet", f.onlineBooking());
    o.put("horario", weekObject(f.week()));
    o.put("normas", text(f.houseRules()));
    o.put("campos", f.customFields());
    o.put("usos", f.uses().stream().map(this::useObject).toList());
    return o;
  }

  private Map<String, Object> useObject(PublicFacilityData.Use u) {
    BigDecimal price = price(u);
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("uso_id", u.id().toString());
    o.put("uso", u.name());
    o.put("modo_aluguer", mode(u.rentalMode()));
    o.put("franxa_minutos", u.slotMinutes());
    o.put("partes", "PARTS".equals(u.rentalMode()) ? u.parts() : List.of());
    o.put("prazas", "SEATS".equals(u.rentalMode()) ? u.capacity() : null);
    o.put("dias", days(u.weekdays()));
    o.put(
        "datas",
        u.periods().isEmpty()
            ? null
            : u.periods().stream()
                .map(p -> Map.of("desde", p.from().toString(), "ata", p.to().toString()))
                .toList());
    o.put("prezo_euros", price);
    o.put("prezo_por", price == null ? null : per(u));
    o.put("antelacion_minima_horas", u.minAdvanceHours());
    o.put("antelacion_maxima_dias", u.maxAdvanceDays());
    o.put("franxas_maximas", u.maxSlots());
    o.put("limite_por_persoa", u.capCount());
    o.put("limite_periodo", u.capPeriod() == null ? null : period(u.capPeriod()));
    o.put("cancelar_sen_custo_horas", u.cancelHours());
    return o;
  }

  // ---------------------------------------------------------------------------------------
  // Peches

  String closuresCsv(ClosuresFile file) {
    StringBuilder out = new StringBuilder(CsvCells.BOM);
    List<String> header = new ArrayList<>();
    if (all) {
      header.add("concello");
    }
    header.addAll(CLOSURE_COLUMNS);
    line(out, header);
    for (ClosuresFile.Part part : file.parts()) {
      for (ClosuresFile.Row r : part.rows()) {
        List<String> row = new ArrayList<>();
        if (all) {
          row.add(CsvCells.text(part.publisher().slug()));
        }
        row.add(r.date().toString());
        row.add(r.holiday() ? "festivo" : "peche");
        row.add(r.facilityId() == null ? "" : r.facilityId().toString());
        row.add(CsvCells.text(reason(r)));
        line(out, row);
      }
    }
    return out.toString();
  }

  String closuresJson(ClosuresFile file) {
    Map<String, Object> root = envelope(Dataset.CLOSURES, file.parts(), file.updatedOn());
    List<Object> data = new ArrayList<>();
    for (ClosuresFile.Part part : file.parts()) {
      for (ClosuresFile.Row r : part.rows()) {
        Map<String, Object> o = new LinkedHashMap<>();
        if (all) {
          o.put("concello", part.publisher().slug());
        }
        o.put("data", r.date().toString());
        o.put("tipo", r.holiday() ? "festivo" : "peche");
        o.put("instalacion", r.facilityId() == null ? null : r.facilityId().toString());
        o.put("motivo", reason(r));
        data.add(o);
      }
    }
    root.put("datos", data);
    return json.writeValueAsString(root);
  }

  private @Nullable String reason(ClosuresFile.Row r) {
    return language == Language.ES ? r.reasonEs() : r.reasonGl();
  }

  // ---------------------------------------------------------------------------------------
  // Común

  /**
   * O envoltorio común (punto 16): conxunto, concello, licenza, atribución, actualizado e zona
   * horaria; no de todos, ademais, a lista de concellos.
   */
  Map<String, Object> envelope(
      Dataset dataset, Collection<?> parts, @Nullable LocalDate updatedOn) {
    List<OpenDataPublisher> publishers =
        parts.stream()
            .map(
                p ->
                    p instanceof FacilitiesFile.Part f
                        ? f.publisher()
                        : ((ClosuresFile.Part) p).publisher())
            .toList();
    return envelopeOf(dataset, publishers, updatedOn);
  }

  Map<String, Object> envelopeOf(
      Dataset dataset, List<OpenDataPublisher> publishers, @Nullable LocalDate updatedOn) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("conxunto", dataset.fileName());
    root.put("concello", all || publishers.isEmpty() ? "todos" : publishers.getFirst().slug());
    root.put("licenza", LICENSE);
    root.put(
        "atribucion",
        all || publishers.isEmpty()
            ? (language == Language.ES
                ? "El ayuntamiento de origen de cada fila"
                : "O concello de orixe de cada fila")
            : publishers.getFirst().attribution(language));
    root.put("actualizado", updatedOn == null ? null : updatedOn.toString());
    root.put("zona_horaria", ZONE);
    if (all) {
      root.put(
          "concellos",
          publishers.stream()
              .map(
                  p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("concello", p.slug());
                    m.put("nome", p.name());
                    m.put("atribucion", p.attribution(language));
                    return m;
                  })
              .toList());
    }
    return root;
  }

  /** No de todos, as definicións dos campos van dentro de cada concello do envoltorio. */
  void fieldsPerMunicipality(Map<String, Object> root, FacilitiesFile file) {
    if (!all) {
      return;
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> municipalities = (List<Map<String, Object>>) root.get("concellos");
    for (int i = 0; i < municipalities.size() && i < file.parts().size(); i++) {
      municipalities.get(i).put("campos", definitions(file.parts().get(i).data()));
    }
  }

  private List<Map<String, Object>> definitions(PublicFacilityData data) {
    return sorted(data).stream()
        .map(
            d -> {
              Map<String, Object> o = new LinkedHashMap<>();
              o.put("codigo", d.code());
              o.put("nome", d.label().in(language));
              o.put("tipo", type(d.type()));
              o.put(
                  "opcions",
                  d.options().stream()
                      .map(op -> Map.of("codigo", op.code(), "nome", op.label().in(language)))
                      .toList());
              return o;
            })
        .toList();
  }

  private static List<PublicCustomFields.Definition> sorted(PublicFacilityData data) {
    return data.customFields().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
  }

  /** O valor dun campo propio nunha cela (punto 16). */
  static String customCell(PublicCustomFields.Definition d, @Nullable Object value) {
    if (value == null) {
      return "";
    }
    return switch (value) {
      case Boolean b -> b ? "si" : "non";
      case BigDecimal n -> n.toPlainString();
      case Number n -> new BigDecimal(n.toString()).toPlainString();
      case Collection<?> list -> CsvCells.list(list.stream().map(String::valueOf).toList());
      default -> CsvCells.text(String.valueOf(value));
    };
  }

  /** O tipo dun campo, en galego e sen acentos. */
  static String type(String type) {
    return switch (type) {
      case "LONG_TEXT" -> "texto_longo";
      case "NUMBER" -> "numero";
      case "DATE" -> "data";
      case "BOOLEAN" -> "si_non";
      case "SELECT" -> "seleccion";
      case "MULTI_SELECT" -> "seleccion_multiple";
      default -> "texto";
    };
  }

  private String url(OpenDataPublisher publisher, PublicFacilityData.Item f) {
    return publicUrl + "/" + publisher.slug() + "/instalacions/" + f.id();
  }

  private @Nullable String text(PublicFacilityData.@Nullable Text t) {
    if (t == null) {
      return null;
    }
    String value = language == Language.ES ? t.es() : t.gl();
    return value == null || value.isBlank() ? t.gl() : value;
  }

  private List<String> sports(PublicFacilityData.Item f) {
    return f.sports().stream().map(s -> text(s.label())).toList();
  }

  private static List<String> sportCodes(PublicFacilityData.Item f) {
    return f.sports().stream()
        .map(PublicFacilityData.SportItem::code)
        .filter(c -> c != null && !c.isBlank())
        .map(String::valueOf)
        .toList();
  }

  private static List<String> amenities(PublicFacilityData.Item f) {
    return f.amenities().stream().map(a -> a.toLowerCase(java.util.Locale.ROOT)).toList();
  }

  /** «Rúa do Alto, 12, 15570 Narón» (o número, se o hai). */
  static String address(PublicFacilityData.Item f) {
    String street =
        f.streetNumber() == null || f.streetNumber().isBlank()
            ? f.street()
            : f.street() + ", " + f.streetNumber();
    return street + ", " + f.postalCode() + " " + f.locality();
  }

  /** «lu 09:00-14:00,16:00-22:00|…|do pechado|fe pechado»; baleiro sen tempada. */
  static @Nullable String weekCell(@Nullable Map<Integer, List<PublicFacilityData.Hours>> week) {
    if (week == null) {
      return null;
    }
    List<String> days = new ArrayList<>();
    for (int i = 0; i < DAYS.size(); i++) {
      List<PublicFacilityData.Hours> hours = week.getOrDefault(i + 1, List.of());
      days.add(
          DAYS.get(i)
              + " "
              + (hours.isEmpty()
                  ? "pechado"
                  : hours.stream()
                      .map(h -> time(h.startMinute()) + "-" + time(h.endMinute()))
                      .collect(Collectors.joining(","))));
    }
    return String.join("|", days);
  }

  static @Nullable Map<String, Object> weekObject(
      @Nullable Map<Integer, List<PublicFacilityData.Hours>> week) {
    if (week == null) {
      return null;
    }
    Map<String, Object> o = new LinkedHashMap<>();
    for (int i = 0; i < DAYS.size(); i++) {
      o.put(
          DAYS.get(i),
          week.getOrDefault(i + 1, List.of()).stream()
              .map(h -> Map.of("de", time(h.startMinute()), "a", time(h.endMinute())))
              .toList());
    }
    return o;
  }

  static String time(int minute) {
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
  }

  static List<String> days(int mask) {
    List<String> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      if ((mask & (1 << i)) != 0) {
        days.add(DAYS.get(i));
      }
    }
    return days;
  }

  static String mode(String rentalMode) {
    return switch (rentalMode) {
      case "PARTS" -> "partes";
      case "SEATS" -> "prazas";
      default -> "enteira";
    };
  }

  static String period(String capPeriod) {
    return switch (capPeriod) {
      case "WEEK" -> "semana";
      case "MONTH" -> "mes";
      default -> "dia";
    };
  }

  private static String per(PublicFacilityData.Use u) {
    return "SEATS".equals(u.rentalMode()) ? "persoa" : "franxa";
  }

  /** Euros con dous decimais: de pago, o importe; gratis, 0.00; sen prezo posto, nada. */
  static @Nullable BigDecimal price(PublicFacilityData.Use u) {
    return switch (u.priceKind()) {
      case "PAID" ->
          u.priceCents() == null ? null : BigDecimal.valueOf(u.priceCents()).movePointLeft(2);
      case "FREE" -> new BigDecimal("0.00");
      default -> null;
    };
  }

  private static @Nullable BigDecimal scale(@Nullable BigDecimal value, int scale) {
    return value == null ? null : value.setScale(scale, java.math.RoundingMode.HALF_UP);
  }

  private static String number(@Nullable Integer value) {
    return value == null ? "" : value.toString();
  }

  private static String yesNo(boolean value) {
    return value ? "si" : "non";
  }

  static void line(StringBuilder out, List<String> cells) {
    out.append(String.join(String.valueOf(CsvCells.SEPARATOR), cells)).append(CsvCells.LINE_END);
  }
}
