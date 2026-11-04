// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import gal.furar.facility.application.port.out.AddressSearch;
import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Buscador sen rede ({@code FURAR_GEOCODER=fixed}, ADR-0023): uns poucos enderezos reais de Narón e
 * Ferrol (rueiro de CartoCiudad, outubro de 2026) e os das instalacións de demo da comarca de Verín
 * (OpenStreetMap, outubro de 2026; algúns sen número). Para tests, e2e e despregues sen saída a
 * Internet. Busca por todas as palabras, sen maiúsculas nin acentos; o punto do mapa dá o enderezo
 * gardado máis próximo a menos de 1 km.
 */
public class FixedAddressSearch implements AddressSearch {

  static final String ID_PREFIX = "f.";
  private static final double MAX_DISTANCE_KM = 1.0;
  private static final double EARTH_KM = 6371.0;

  private record Entry(String key, AddressMatch match) {

    String id() {
      return ID_PREFIX + key;
    }

    String label() {
      String number = match.streetNumber() == null ? "" : " " + match.streetNumber();
      return match.street() + number + ", " + match.locality();
    }
  }

  private static final List<Entry> ENTRIES =
      List.of(
          entry(
              "naron-1",
              "Rúa Concepción Arenal",
              "100",
              "15570",
              "Narón",
              "15054",
              "43.493768",
              "-8.202904"),
          entry(
              "naron-2",
              "Estrada de Castela",
              "100",
              "15570",
              "Narón",
              "15054",
              "43.497415",
              "-8.200025"),
          entry(
              "naron-3",
              "Estrada da Gándara",
              "108",
              "15570",
              "Narón",
              "15054",
              "43.491445",
              "-8.199826"),
          entry("naron-4", "Rúa Buxo", "16", "15572", "Narón", "15054", "43.510072", "-8.191273"),
          entry("ferrol-1", "Rúa Real", "10", "15402", "Ferrol", "15036", "43.485765", "-8.229473"),
          entry(
              "ferrol-2",
              "Praza de España",
              "10",
              "15403",
              "Ferrol",
              "15036",
              "43.486943",
              "-8.228466"),
          entry(
              "ferrol-3",
              "Avenida de Esteiro",
              "10",
              "15403",
              "Ferrol",
              "15036",
              "43.482787",
              "-8.226911"),
          entry(
              "verin-1",
              "Rúa Irmáns Moreno",
              "13",
              "32600",
              "Verín",
              "32085",
              "41.938260",
              "-7.434630"),
          entry(
              "verin-2",
              "Rúa Irmáns Moreno",
              null,
              "32600",
              "Verín",
              "32085",
              "41.938530",
              "-7.434010"),
          entry(
              "verin-3",
              "Rúa Antonio Fernández Pérez",
              null,
              "32600",
              "Verín",
              "32085",
              "41.933430",
              "-7.442570"),
          entry(
              "verin-4",
              "Avenida de Portugal",
              null,
              "32600",
              "Verín",
              "32085",
              "41.932770",
              "-7.440470"),
          entry(
              "verin-5",
              "Avenida de Laza",
              null,
              "32600",
              "Verín",
              "32085",
              "41.953140",
              "-7.431650"),
          entry(
              "vilardevos-1",
              "Estrada OU-310",
              null,
              "32616",
              "Vilardevós",
              "32091",
              "41.906220",
              "-7.307210"),
          entry(
              "rios-1",
              "Estrada de Romariz",
              "78",
              "32611",
              "A Telleira",
              "32071",
              "41.976060",
              "-7.286290"),
          entry(
              "castrelo-1",
              "Rúa Castrelo do Val",
              "10A",
              "32625",
              "Castrelo do Val",
              "32021",
              "41.990710",
              "-7.427040"),
          entry(
              "castrelo-2",
              "O Terrón",
              null,
              "32625",
              "Castrelo do Val",
              "32021",
              "41.990350",
              "-7.426390"),
          entry(
              "castrelo-3",
              "Estrada de Gondulfes",
              null,
              "32625",
              "Castrelo do Val",
              "32021",
              "41.990470",
              "-7.412910"),
          entry(
              "cualedro-1",
              "Rúa do Toural da Feira",
              "3",
              "32689",
              "Cualedro",
              "32028",
              "41.984260",
              "-7.592860"),
          entry("laza-1", "O Toural", null, "32620", "Laza", "32039", "42.063200", "-7.462850"),
          entry(
              "laza-2",
              "Rúa Canella Vales",
              null,
              "32620",
              "Laza",
              "32039",
              "42.062730",
              "-7.462580"),
          entry(
              "monterrei-1",
              "Rúa Fontiñas",
              null,
              "32618",
              "Albarellos",
              "32050",
              "41.950580",
              "-7.495120"),
          entry(
              "monterrei-2", "Vilaza", null, "32618", "Vilaza", "32050", "41.932070", "-7.488330"),
          entry(
              "oimbra-1",
              "Avenida do Carregal",
              "3",
              "32613",
              "Oímbra",
              "32053",
              "41.885470",
              "-7.476630"),
          entry(
              "oimbra-2",
              "Camiño da Portaxe",
              null,
              "32613",
              "Oímbra",
              "32053",
              "41.885360",
              "-7.466200"),
          entry(
              "oimbra-3",
              "Estrada de Oímbra a Videferre",
              null,
              "32613",
              "Oímbra",
              "32053",
              "41.885460",
              "-7.475950"));

  private static Entry entry(
      String key,
      String street,
      @Nullable String number,
      String postalCode,
      String locality,
      String ine,
      String lat,
      String lon) {
    return new Entry(
        key,
        new AddressMatch(
            street, number, postalCode, locality, ine, new BigDecimal(lat), new BigDecimal(lon)));
  }

  @Override
  public List<AddressSuggestion> suggest(String query, SearchArea area) {
    List<String> words = Arrays.asList(SearchArea.fold(query).split(" "));
    return ENTRIES.stream()
        .filter(e -> area.contains(e.match().ineCode(), e.match().locality()))
        .filter(e -> words.stream().allMatch(SearchArea.fold(e.label())::contains))
        .limit(AddressSearch.LIMIT)
        .map(e -> new AddressSuggestion(e.id(), e.label()))
        .toList();
  }

  @Override
  public Optional<AddressMatch> resolve(String id) {
    return ENTRIES.stream().filter(e -> e.id().equals(id)).map(Entry::match).findFirst();
  }

  @Override
  public Optional<AddressMatch> reverse(BigDecimal latitude, BigDecimal longitude) {
    Entry nearest = null;
    double best = Double.MAX_VALUE;
    for (Entry e : ENTRIES) {
      double d = distanceKm(latitude, longitude, e.match().latitude(), e.match().longitude());
      if (d < best) {
        best = d;
        nearest = e;
      }
    }
    return nearest == null || best > MAX_DISTANCE_KM
        ? Optional.empty()
        : Optional.of(nearest.match());
  }

  private static double distanceKm(
      BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
    double p1 = Math.toRadians(lat1.doubleValue());
    double p2 = Math.toRadians(lat2.doubleValue());
    double dp = p2 - p1;
    double dl = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
    double a =
        Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return 2 * EARTH_KM * Math.asin(Math.sqrt(a));
  }
}
