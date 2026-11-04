// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import gal.furar.facility.application.port.out.AddressSearch;
import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSearchError;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import gal.furar.shared.domain.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Buscador de enderezos con CartoCiudad (IGN, CC BY 4.0) polo servidor (ADR-0023): {@code
 * candidates} para as suxestións, {@code find} para resolver unha e {@code reverseGeocode} para un
 * punto do mapa. Sen reintentos; os tempos límite van no {@code RestClient} ({@link
 * GeocoderConfig}). Calquera fallo (rede, tempo, 5xx, resposta que non se entende) é {@code
 * facility.geocoder-unavailable}. As respostas boas gárdanse 24 h nunha caché LRU en memoria.
 *
 * <p>Suxestións: só vías ({@code callejero}) e portais ({@code portal}), filtradas polo concello
 * ({@code municipio_filter} co nome e, se o hai, o código INE na resposta).
 */
public class CartoCiudadAddressSearch implements AddressSearch, AutoCloseable {

  /** Servizo público do IGN. */
  public static final String BASE_URL = "https://www.cartociudad.es/geocoder/api/geocoder";

  static final String ID_PREFIX = "c.";
  static final int CACHE_ENTRIES = 2_000;
  static final Duration CACHE_TTL = Duration.ofHours(24);
  private static final int MAX_ID = 600;
  private static final char SEPARATOR = '\u001f';
  private static final Set<String> SUGGESTED_TYPES = Set.of("callejero", "portal");
  private static final Pattern POSTAL_CODE = Pattern.compile("^[0-9]{5}$");
  private static final Pattern INE = Pattern.compile("^[0-9]{5}$");
  private static final Logger LOG = LoggerFactory.getLogger(CartoCiudadAddressSearch.class);

  private final RestClient http;
  private final JsonMapper json;
  private final @Nullable AutoCloseable resources;
  private final ExpiringLruCache<String, List<AddressSuggestion>> suggestions;
  private final ExpiringLruCache<String, Optional<AddressMatch>> matches;

  /**
   * @param http cliente con URL base, tempos límite e proxy xa postos
   * @param resources o que pechar ao parar (o {@code HttpClient}); nulo se non hai
   */
  public CartoCiudadAddressSearch(
      RestClient http, JsonMapper json, Clock clock, @Nullable AutoCloseable resources) {
    this.http = http;
    this.json = json;
    this.resources = resources;
    this.suggestions = new ExpiringLruCache<>(CACHE_ENTRIES, CACHE_TTL, clock);
    this.matches = new ExpiringLruCache<>(CACHE_ENTRIES, CACHE_TTL, clock);
  }

  @Override
  public List<AddressSuggestion> suggest(String query, SearchArea area) {
    String key =
        "s"
            + SEPARATOR
            + area.municipalityName()
            + SEPARATOR
            + area.ineCode()
            + SEPARATOR
            + query.toLowerCase(Locale.ROOT);
    return suggestions.get(key, () -> candidates(query, area));
  }

  @Override
  public Optional<AddressMatch> resolve(String id) {
    Optional<Ref> ref = Ref.decode(id);
    if (ref.isEmpty()) {
      return Optional.empty();
    }
    return matches.get("f" + SEPARATOR + id, () -> find(ref.get()));
  }

  @Override
  public Optional<AddressMatch> reverse(BigDecimal latitude, BigDecimal longitude) {
    String lat = latitude.setScale(6, RoundingMode.HALF_UP).toPlainString();
    String lon = longitude.setScale(6, RoundingMode.HALF_UP).toPlainString();
    return matches.get(
        "r" + SEPARATOR + lat + SEPARATOR + lon,
        () ->
            single(
                get(
                    b ->
                        b.path("/reverseGeocode")
                            .queryParam("lon", "{lon}")
                            .queryParam("lat", "{lat}")
                            .build(Map.of("lon", lon, "lat", lat)))));
  }

  @Override
  public void close() throws Exception {
    if (resources != null) {
      resources.close();
    }
  }

  private List<AddressSuggestion> candidates(String query, SearchArea area) {
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("q", query);
    vars.put("limit", AddressSearch.LIMIT);
    String municipality = area.municipalityName();
    JsonNode body =
        get(
            b -> {
              b.path("/candidates").queryParam("q", "{q}").queryParam("limit", "{limit}");
              if (municipality != null) {
                b.queryParam("municipio_filter", "{municipality}");
                vars.put("municipality", municipality);
              }
              return b.build(vars);
            });
    if (body == null) {
      return List.of();
    }
    if (!body.isArray()) {
      throw unavailable(null);
    }
    List<AddressSuggestion> out = new ArrayList<>();
    for (JsonNode candidate : body) {
      String type = text(candidate, "type");
      String address = text(candidate, "address");
      String cartoId = text(candidate, "id");
      if (type == null
          || address == null
          || cartoId == null
          || !SUGGESTED_TYPES.contains(type)
          || !area.contains(text(candidate, "muniCode"), text(candidate, "muni"))) {
        continue;
      }
      Ref ref =
          new Ref(
              type, cartoId, text(candidate, "tip_via"), text(candidate, "portalNumber"), address);
      Optional<String> id = ref.encode();
      if (id.isPresent()) {
        out.add(new AddressSuggestion(id.get(), StreetText.pretty(address)));
      }
      if (out.size() == AddressSearch.LIMIT) {
        break;
      }
    }
    return List.copyOf(out);
  }

  private Optional<AddressMatch> find(Ref ref) {
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("q", ref.address());
    vars.put("type", ref.type());
    vars.put("id", ref.cartoId());
    JsonNode body =
        get(
            b -> {
              b.path("/find")
                  .queryParam("q", "{q}")
                  .queryParam("type", "{type}")
                  .queryParam("id", "{id}");
              if (ref.streetType() != null) {
                b.queryParam("tip_via", "{tipVia}");
                vars.put("tipVia", ref.streetType());
              }
              if (ref.portal() != null) {
                b.queryParam("portal", "{portal}");
                vars.put("portal", ref.portal());
              }
              return b.build(vars);
            });
    return single(body);
  }

  private Optional<AddressMatch> single(@Nullable JsonNode body) {
    if (body == null || body.isEmpty()) {
      return Optional.empty();
    }
    if (!body.isObject()) {
      throw unavailable(null);
    }
    return Optional.of(match(body));
  }

  /** Unha resposta de {@code find} ou {@code reverseGeocode} → enderezo para o formulario. */
  static AddressMatch match(JsonNode node) {
    String streetName = text(node, "address");
    String streetType = text(node, "tip_via");
    String street = null;
    if (streetName != null) {
      String full =
          streetType == null || streetName.toUpperCase(Locale.ROOT).startsWith(streetType + " ")
              ? streetName
              : streetType + " " + streetName;
      street = StreetText.pretty(full);
    }
    String number = null;
    if (!Boolean.TRUE.equals(bool(node, "noNumber"))) {
      String portal = text(node, "portalNumber");
      if (portal != null && !"0".equals(portal)) {
        String extension = text(node, "extension");
        number = extension == null ? portal : portal + " " + extension;
      }
    }
    String postalCode = text(node, "postalCode");
    if (postalCode != null && !POSTAL_CODE.matcher(postalCode).matches()) {
      postalCode = null; // unha vía longa pode ter varios («15401 15403»): escolle o xestor
    }
    String locality = Optional.ofNullable(text(node, "poblacion")).orElse(text(node, "muni"));
    String ine = text(node, "muniCode");
    if (ine != null && !INE.matcher(ine).matches()) {
      ine = null;
    }
    BigDecimal lat = number(node, "lat");
    BigDecimal lng = number(node, "lng");
    if (lat != null && lng != null && lat.signum() == 0 && lng.signum() == 0) {
      lat = null; // sen punto
      lng = null;
    }
    return new AddressMatch(street, number, postalCode, locality, ine, lat, lng);
  }

  private @Nullable JsonNode get(Function<UriBuilder, URI> uri) {
    byte[] bytes;
    try {
      bytes = http.get().uri(uri).retrieve().body(byte[].class);
    } catch (RestClientException e) {
      throw unavailable(e);
    }
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    try {
      return json.readTree(new String(bytes, StandardCharsets.UTF_8));
    } catch (JacksonException e) {
      throw unavailable(e);
    }
  }

  private static DomainException unavailable(@Nullable Exception cause) {
    LOG.warn(
        "CartoCiudad unavailable: {}",
        cause == null ? "unexpected response" : cause.getClass().getSimpleName());
    DomainException error = new DomainException(AddressSearchError.GEOCODER_UNAVAILABLE);
    if (cause != null) {
      error.initCause(cause);
    }
    return error;
  }

  private static @Nullable String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return null;
    }
    String text = value.asString().strip();
    return text.isEmpty() ? null : text;
  }

  private static @Nullable Boolean bool(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || !value.isBoolean() ? null : value.asBoolean();
  }

  private static @Nullable BigDecimal number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || !value.isNumber() ? null : value.decimalValue();
  }

  /**
   * O que pide {@code find} para resolver unha suxestión, levado no id para non depender da caché
   * (un reinicio entre suxerir e escoller non rompe nada).
   */
  record Ref(
      String type,
      String cartoId,
      @Nullable String streetType,
      @Nullable String portal,
      String address) {

    Optional<String> encode() {
      String raw =
          String.join(
              String.valueOf(SEPARATOR),
              type,
              cartoId,
              streetType == null ? "" : streetType,
              portal == null ? "" : portal,
              address);
      String id =
          ID_PREFIX
              + Base64.getUrlEncoder()
                  .withoutPadding()
                  .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
      return id.length() > MAX_ID ? Optional.empty() : Optional.of(id);
    }

    static Optional<Ref> decode(String id) {
      if (id == null || id.length() > MAX_ID || !id.startsWith(ID_PREFIX)) {
        return Optional.empty();
      }
      String raw;
      try {
        raw =
            new String(
                Base64.getUrlDecoder().decode(id.substring(ID_PREFIX.length())),
                StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
      String[] parts = raw.split(String.valueOf(SEPARATOR), -1);
      if (parts.length != 5
          || !SUGGESTED_TYPES.contains(parts[0])
          || parts[1].isBlank()
          || parts[4].isBlank()) {
        return Optional.empty();
      }
      return Optional.of(
          new Ref(
              parts[0],
              parts[1],
              parts[2].isEmpty() ? null : parts[2],
              parts[3].isEmpty() ? null : parts[3],
              parts[4]));
    }
  }
}
