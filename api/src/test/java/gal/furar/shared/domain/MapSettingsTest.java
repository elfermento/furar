// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class MapSettingsTest {

  private static final String IGN = "CC BY 4.0 scne.es";

  @Test
  void theMapIsOnByDefaultWithItsAttribution() {
    MapSettings map = MapSettings.parse("", " " + IGN + " ", "https://www.scne.es");
    assertThat(map.enabled()).isTrue();
    assertThat(map.attribution()).isEqualTo(IGN);
    assertThat(map.attributionUrl()).contains(URI.create("https://www.scne.es"));
    assertThat(MapSettings.parse(null, IGN, "").enabled()).isTrue();
    assertThat(MapSettings.parse(" ON ", IGN, null).attributionUrl()).isEmpty();
  }

  @Test
  void offTurnsTheMapOffWithoutNeedingAnAttribution() {
    MapSettings map = MapSettings.parse("off", "", "");
    assertThat(map.enabled()).isFalse();
    assertThat(MapSettings.parse("OFF", IGN, "https://www.scne.es").enabled()).isFalse();
  }

  @Test
  void aValueThatCannotBeHonouredStopsTheStart() {
    assertThatThrownBy(() -> MapSettings.parse("false", IGN, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FURAR_MAP");
    assertThatThrownBy(() -> MapSettings.parse("on", " ", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FURAR_MAP_ATTRIBUTION");
    for (String bad :
        new String[] {"javascript:alert(1)", "www.scne.es", "ftp://scne.es", "h ttp"}) {
      assertThatThrownBy(() -> MapSettings.parse("on", IGN, bad))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FURAR_MAP_ATTRIBUTION_URL");
    }
  }
}
