// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.GeocoderSettings.HttpProxy;
import gal.furar.shared.domain.GeocoderSettings.Provider;
import org.junit.jupiter.api.Test;

class GeocoderSettingsTest {

  @Test
  void providerIsCartociudadByDefaultAndFixedOnRequest() {
    assertThat(Provider.parse(null)).isEqualTo(Provider.CARTOCIUDAD);
    assertThat(Provider.parse("")).isEqualTo(Provider.CARTOCIUDAD);
    assertThat(Provider.parse(" CartoCiudad ")).isEqualTo(Provider.CARTOCIUDAD);
    assertThat(Provider.parse("fixed")).isEqualTo(Provider.FIXED);
    assertThatThrownBy(() -> Provider.parse("nominatim"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FURAR_GEOCODER");
  }

  @Test
  void proxyIsHostAndPort() {
    assertThat(HttpProxy.parse("")).isEmpty();
    assertThat(HttpProxy.parse(null)).isEmpty();
    assertThat(HttpProxy.parse("http://proxy.concello.local:3128"))
        .contains(new HttpProxy("proxy.concello.local", 3128));
    assertThat(HttpProxy.parse("proxy:8080/")).contains(new HttpProxy("proxy", 8080));
  }

  @Test
  void aProxyThatCannotBeHonouredStopsTheStart() {
    for (String bad :
        new String[] {
          "https://proxy:3128",
          "http://user:secret@proxy:3128",
          "http://proxy",
          "http://proxy:3128/x",
          "socks5://proxy:1080",
          "http://:3128"
        }) {
      assertThatThrownBy(() -> HttpProxy.parse(bad))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FURAR_HTTP_PROXY");
    }
  }
}
