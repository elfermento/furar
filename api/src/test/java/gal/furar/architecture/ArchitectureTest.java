// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * D12 (ADR-0005): o código de produción cumpre A1–A21 e A8b; os tests cumpren A9 (sen @Disabled).
 */
class ArchitectureTest {

  static JavaClasses production;
  static JavaClasses withTests;

  @BeforeAll
  static void importClasses() {
    production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("gal.furar");
    withTests = new ClassFileImporter().importPackages("gal.furar");
  }

  @TestFactory
  Stream<DynamicTest> productionFollowsEveryRule() {
    Map<String, com.tngtech.archunit.lang.ArchRule> rules = new TreeMap<>(ArchitectureRules.ALL);
    rules.remove("A9t");
    return rules.entrySet().stream()
        .map(e -> DynamicTest.dynamicTest(e.getKey(), () -> e.getValue().check(production)));
  }

  @Test
  void testsAreNeverDisabled() {
    ArchitectureRules.A9_NO_DISABLED_TESTS.check(withTests);
  }
}
