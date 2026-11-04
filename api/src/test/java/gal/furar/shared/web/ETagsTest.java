// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ETagsTest {

  @Test
  void roundTrip() {
    assertThat(ETags.of(3)).isEqualTo("\"3\"");
    assertThat(ETags.parseIfMatch("\"3\"")).isEqualTo(3);
    assertThat(ETags.parseIfMatch("W/\"4\"")).isEqualTo(4);
    assertThat(ETags.parseIfMatch(" 5 ")).isEqualTo(5);
  }

  @Test
  void missingIsPreconditionRequiredAndGarbageIsStale() {
    assertThatThrownBy(() -> ETags.parseIfMatch(null))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonError.PRECONDITION_REQUIRED));
    assertThatThrownBy(() -> ETags.parseIfMatch("*"))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonError.STALE_VERSION));
  }

  @Test
  void slugFromPath() {
    assertThat(TenantFilter.slugOf(request("/api/v1/municipalities/naron"))).contains("naron");
    assertThat(TenantFilter.slugOf(request("/api/v1/municipalities/naron/theme")))
        .contains("naron");
    assertThat(TenantFilter.slugOf(request("/api/v1/municipalities"))).isEmpty();
    assertThat(TenantFilter.slugOf(request("/api/v1/municipalities/"))).isEmpty();
    assertThat(TenantFilter.slugOf(request("/other"))).isEmpty();
  }

  @Test
  void languageFromAcceptLanguage() {
    MockHttpServletRequest es = request("/");
    es.addHeader("Accept-Language", "en-GB,es;q=0.8");
    es.setPreferredLocales(java.util.List.of(java.util.Locale.UK, java.util.Locale.of("es")));
    assertThat(Locales.language(es).code()).isEqualTo("es");
    assertThat(Locales.language(request("/")).code()).isEqualTo("gl");
    MockHttpServletRequest en = request("/");
    en.addHeader("Accept-Language", "en");
    en.setPreferredLocales(java.util.List.of(java.util.Locale.ENGLISH));
    assertThat(Locales.language(en).code()).isEqualTo("gl");
  }

  private static MockHttpServletRequest request(String uri) {
    return new MockHttpServletRequest("GET", uri);
  }
}
