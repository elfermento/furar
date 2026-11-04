// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** {@code furar.today}: o día de Galicia (non o de UTC) e sempre por debaixo do configurado. */
class TodayPropertyTest {

  // 23:30 UTC do 31 de decembro: en Galicia xa é 1 de xaneiro.
  static final Clock NEW_YEAR = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);

  @Test
  void isTheDayInGalicia() {
    StandardEnvironment environment = new StandardEnvironment();
    new TodayProperty(NEW_YEAR).postProcessEnvironment(environment, new SpringApplication());
    assertThat(environment.getProperty("furar.today")).isEqualTo("2027-01-01");
    assertThat(environment.resolvePlaceholders("${furar.today}")).isEqualTo("2027-01-01");
  }

  @Test
  void aConfiguredValueWins() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("furar.today", "2030-05-05")));
    new TodayProperty(NEW_YEAR).postProcessEnvironment(environment, new SpringApplication());
    assertThat(environment.getProperty("furar.today")).isEqualTo("2030-05-05");
  }
}
