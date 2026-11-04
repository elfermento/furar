// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

/**
 * Modo {@code migrate} da imaxe da API (SEC2-03, ADR-0015): aplica as migracións como {@code
 * furar_owner} e sae. En Compose é un servizo dun só uso do que depende a API ({@code
 * service_completed_successfully}), así que o contrasinal do dono do esquema nunca está no proceso
 * que atende peticións.
 *
 * <p>O contexto só ten a autoconfiguración de Flyway (nin web, nin JPA, nin seguridade, nin escaneo
 * de compoñentes). Le a mesma configuración ca a API (perfís, {@code spring.flyway.*},
 * localizacións de demo) e conecta co URL de {@code spring.datasource}.
 */
public final class FurarMigration {

  /** Argumento da imaxe: {@code java … JarLauncher migrate}. */
  public static final String COMMAND = "migrate";

  /** Propiedade que ve {@code ProdProfileGuard}: neste modo só se comproba o dono. */
  public static final String MODE_PROPERTY = "furar.mode";

  private FurarMigration() {}

  /** Migra e devolve o código de saída do proceso (0 = migracións aplicadas e validadas). */
  public static int run(String... args) {
    var application =
        new SpringApplicationBuilder(FlywayAutoConfiguration.class)
            .web(WebApplicationType.NONE)
            .properties("spring.flyway.url=${spring.datasource.url}")
            .build();
    String[] overrides = {
      "--" + MODE_PROPERTY + "=" + COMMAND,
      // Gaña a application-prod.yaml, onde a API ten Flyway desactivado.
      "--spring.flyway.enabled=true"
    };
    return SpringApplication.exit(application.run(concat(overrides, args)));
  }

  private static String[] concat(String[] first, String[] second) {
    String[] result = new String[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
