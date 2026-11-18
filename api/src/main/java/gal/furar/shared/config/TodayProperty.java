// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * A data de hoxe en Galicia como propiedade {@value #PROPERTY} (por exemplo {@code 2026-10-28}),
 * para o placeholder {@code demo_day} de Flyway ({@code application.yaml}). As sementes de demo
 * calculan sobre {@code current_date} e Flyway só volve aplicar unha repetible se cambia o seu
 * checksum, que nas repetibles leva os placeholders xa substituídos: cun placeholder que cambia
 * cada día, a primeira migración de cada día volve aplicalas e a demo non envellece.
 *
 * <p>Vai ao final das fontes de propiedades: calquera valor configurado (un test, o contorno) gaña.
 * Corre en todo arrinque, tamén no modo {@code migrate} (só autoconfiguración de Flyway).
 */
public class TodayProperty implements EnvironmentPostProcessor {

  static final String PROPERTY = "furar.today";
  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private final Clock clock;

  public TodayProperty() {
    this(Clock.system(ZONE));
  }

  TodayProperty(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                PROPERTY, Map.of(PROPERTY, LocalDate.now(clock.withZone(ZONE)).toString())));
  }
}
