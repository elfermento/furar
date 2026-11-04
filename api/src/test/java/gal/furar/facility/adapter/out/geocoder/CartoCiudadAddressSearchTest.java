// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSearchError;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import gal.furar.shared.domain.DomainException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador de CartoCiudad con {@code MockRestServiceServer} e respostas reais gardadas en {@code
 * src/test/resources/geocoder/cartociudad} (2026-10-25, sen {@code geom}; IGN, CC BY 4.0): ningunha
 * petición sae á rede (ADR-0023).
 */
class CartoCiudadAddressSearchTest {

  static final String BASE = "https://cartociudad.test/geocoder/api/geocoder";
  static final SearchArea NARON = new SearchArea("Narón", "15054");

  MutableClock clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
  MockRestServiceServer server;
  CartoCiudadAddressSearch search;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        GeocoderConfig.restClient(java.net.http.HttpClient.newHttpClient(), BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    search =
        new CartoCiudadAddressSearch(builder.build(), JsonMapper.builder().build(), clock, null);
  }

  static String fixture(String name) {
    try (InputStream in =
        CartoCiudadAddressSearchTest.class.getResourceAsStream("/geocoder/cartociudad/" + name)) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void suggestionsAreStreetsAndPortalsOfTheMunicipalityWithReadableLabels() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("limit", "10"))
        .andExpect(queryParam("municipio_filter", "Nar%C3%B3n"))
        .andExpect(queryParam("q", "concepcion%20arenal"))
        .andExpect(header("User-Agent", "Furar"))
        .andRespond(withSuccess(fixture("candidates-naron.json"), MediaType.APPLICATION_JSON));

    List<AddressSuggestion> found = search.suggest("concepcion arenal", NARON);

    server.verify();
    // O portal de Ares (outro INE) e o topónimo de Ferrol quedan fóra.
    assertThat(found)
        .extracting(AddressSuggestion::label)
        .containsExactly(
            "Rua Concepcion Arenal, Narón",
            "Rua Concepcion Arenal 1, Narón",
            "Calle Concepcion Arenal 100, Narón");
    assertThat(found).allSatisfy(s -> assertThat(s.id()).startsWith("c."));
  }

  @Test
  void withoutIneCodeTheNameFilterIsEnough() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess(fixture("candidates-naron.json"), MediaType.APPLICATION_JSON));

    List<AddressSuggestion> found =
        search.suggest("concepcion arenal", new SearchArea("NARÓN", null));

    assertThat(found).hasSize(3);
  }

  @Test
  void theSameQueryIsAnsweredFromTheCacheFor24Hours() {
    server
        .expect(twice(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess(fixture("candidates-naron.json"), MediaType.APPLICATION_JSON));

    List<AddressSuggestion> first = search.suggest("concepcion arenal", NARON);
    List<AddressSuggestion> again = search.suggest("Concepcion Arenal", NARON);
    clock.advance(Duration.ofHours(23));
    search.suggest("concepcion arenal", NARON);
    // Un único pedido ata aquí: a segunda e a terceira consulta non saen.
    clock.advance(Duration.ofHours(1));
    search.suggest("concepcion arenal", NARON);

    server.verify();
    assertThat(again).isEqualTo(first);
  }

  @Test
  void anotherMunicipalityIsAnotherQuery() {
    server
        .expect(twice(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    search.suggest("rua real", NARON);
    search.suggest("rua real", new SearchArea("Ferrol", "15036"));

    server.verify();
  }

  @Test
  void aSuggestionResolvesWithFindAndFillsEveryField() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess(fixture("candidates-naron.json"), MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/find?")))
        .andExpect(queryParam("type", "portal"))
        .andExpect(queryParam("id", "12.15.G15_150540384713"))
        .andExpect(queryParam("tip_via", "CALLE"))
        .andExpect(queryParam("portal", "100"))
        .andRespond(withSuccess(fixture("find-portal.json"), MediaType.APPLICATION_JSON));

    String id = search.suggest("concepcion arenal", NARON).get(2).id();
    AddressMatch match = search.resolve(id).orElseThrow();
    // Segunda vez, da caché.
    assertThat(search.resolve(id)).contains(match);

    server.verify();
    assertThat(match.street()).isEqualTo("Calle Concepcion Arenal");
    assertThat(match.streetNumber()).isEqualTo("100");
    assertThat(match.postalCode()).isEqualTo("15570");
    assertThat(match.locality()).isEqualTo("Narón");
    assertThat(match.ineCode()).isEqualTo("15054");
    assertThat(match.latitude()).isEqualByComparingTo("43.493768");
    assertThat(match.longitude()).isEqualByComparingTo("-8.202904");
  }

  @Test
  void aStreetWithoutNumberResolvesToAPointOnTheStreet() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess(fixture("candidates-naron.json"), MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/find?")))
        .andExpect(queryParam("type", "callejero"))
        .andRespond(withSuccess(fixture("find-street.json"), MediaType.APPLICATION_JSON));

    String id = search.suggest("concepcion arenal", NARON).get(0).id();
    AddressMatch match = search.resolve(id).orElseThrow();

    assertThat(match.street()).isEqualTo("Rua Concepcion Arenal");
    assertThat(match.streetNumber()).isNull();
    assertThat(match.postalCode()).isEqualTo("15570");
    assertThat(match.latitude()).isEqualByComparingTo("43.493977");
  }

  @Test
  void anIdThatIsNotOursDoesNotGoOut() {
    assertThat(search.resolve("f.naron-1")).isEmpty();
    assertThat(search.resolve("c.not-base64!")).isEmpty();
    assertThat(search.resolve("c." + "A".repeat(700))).isEmpty();
    server.verify();
  }

  @Test
  void findWithoutContentIsNotFound() {
    String id =
        new CartoCiudadAddressSearch.Ref("portal", "x", "RUA", "1", "RUA X 1, Narón")
            .encode()
            .orElseThrow();
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/find?")))
        .andRespond(withNoContent());

    assertThat(search.resolve(id)).isEmpty();
  }

  @Test
  void reverseGivesTheNearestPortal() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/reverseGeocode?")))
        .andExpect(queryParam("lat", "43.510000"))
        .andExpect(queryParam("lon", "-8.191300"))
        .andRespond(withSuccess(fixture("reverse.json"), MediaType.APPLICATION_JSON));

    Optional<AddressMatch> match =
        search.reverse(new BigDecimal("43.51"), new BigDecimal("-8.1913"));

    assertThat(match).isPresent();
    assertThat(match.get().street()).isEqualTo("Rua Buxo");
    assertThat(match.get().streetNumber()).isEqualTo("16");
    assertThat(match.get().postalCode()).isEqualTo("15572");
    assertThat(match.get().locality()).isEqualTo("Narón");
    assertThat(match.get().ineCode()).isEqualTo("15054");
  }

  @Test
  void aTimeoutIsUnavailableAndIsNotCached() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> search.suggest("rua real", NARON))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(AddressSearchError.GEOCODER_UNAVAILABLE));
    assertThat(search.suggest("rua real", NARON)).isEmpty();
    server.verify();
  }

  @Test
  void aServerErrorOrAnUnreadableAnswerIsUnavailable() {
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withServerError());
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/candidates?")))
        .andRespond(withSuccess("<html>mantemento</html>", MediaType.TEXT_HTML));
    server
        .expect(once(), requestTo(Matchers.startsWith(BASE + "/reverseGeocode?")))
        .andRespond(withSuccess("[1, 2]", MediaType.APPLICATION_JSON));

    assertUnavailable(() -> search.suggest("rua real", NARON));
    assertUnavailable(() -> search.suggest("rua real", NARON));
    assertUnavailable(() -> search.reverse(BigDecimal.ONE, BigDecimal.ONE));
    server.verify();
  }

  @Test
  void aStreetWithSeveralPostalCodesLeavesThePostalCodeToTheManager() {
    AddressMatch match =
        CartoCiudadAddressSearch.match(
            JsonMapper.builder()
                .build()
                .readTree(
                    "{\"address\":\"ESPAÑA\",\"tip_via\":\"PLAZA\",\"postalCode\":\"15401 15403\","
                        + "\"muni\":\"Ferrol\",\"muniCode\":\"15036\",\"lat\":0.0,\"lng\":0.0}"));

    assertThat(match.street()).isEqualTo("Plaza España");
    assertThat(match.postalCode()).isNull();
    assertThat(match.locality()).isEqualTo("Ferrol");
    assertThat(match.latitude()).isNull();
    assertThat(match.longitude()).isNull();
  }

  private static void assertUnavailable(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(AddressSearchError.GEOCODER_UNAVAILABLE));
  }

  /** Reloxo que avanza a man. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
