// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/**
 * Regras de módulo de ADR-0005, aplicadas igual ao código e aos exemplos da autoproba: M1 sen
 * ciclos e M2 só a API pública (Spring Modulith {@code verify()}); M3 dependencias explícitas.
 */
public final class ModularityRules {

  private ModularityRules() {}

  /**
   * M1 e M2: {@code ApplicationModules.verify()}. Cada infracción sae co seu id (M1 ciclo, M2
   * dependencia non permitida ou tipo interno) para que o fallo diga que regra de ADR-0005 rompe.
   */
  public static void verifyModules(ApplicationModules modules) {
    Violations violations = modules.detectViolations();
    if (violations.hasViolations()) {
      throw new AssertionError(
          "ADR-0005 module rules violated:\n"
              + violations.getMessages().stream()
                  .map(m -> "- " + ruleOf(m) + " " + m)
                  .collect(Collectors.joining("\n")));
    }
  }

  private static String ruleOf(String message) {
    return message.toLowerCase(Locale.ROOT).contains("cycle")
        ? "M1 (no cycles between modules):"
        : "M2 (only the public API of allowed modules):";
  }

  /** M3: todo módulo non aberto declara {@code allowedDependencies} explícitas. */
  public static void verifyExplicitDependencies(ApplicationModules modules) {
    List<String> missing = new ArrayList<>();
    modules.forEach(
        module -> {
          ApplicationModule annotation = annotationOf(module.getBasePackage().getName());
          boolean open = annotation != null && annotation.type() == ApplicationModule.Type.OPEN;
          if (!open && (annotation == null || annotation.allowedDependencies().length == 0)) {
            missing.add(module.getBasePackage().getName());
          }
        });
    assertThat(missing).as("M3 modules without explicit allowedDependencies").isEmpty();
  }

  static ApplicationModule annotationOf(String basePackage) {
    try {
      return Class.forName(basePackage + ".package-info").getAnnotation(ApplicationModule.class);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
