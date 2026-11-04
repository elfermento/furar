// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gal.furar.shared.persistence.NativeSql;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.RowLevelSecurity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Condición de entrada de ADR-0006 (T11), feita regra: toda táboa que lea ou escriba unha clase
 * {@code @NativeSql(scope = MUNICIPALITY)} cumpre a regra de RLS de {@link RowLevelSecurity} no
 * esquema real despois de Flyway (a mesma función que usa {@code SchemaTenancyIT}). A8b é análise
 * de texto; isto é o que protexe aínda que A8b se equivoque (QA Q3). Non pasa sobre un conxunto
 * baleiro (N11): se ningunha clase nativa do concello le táboas, comproba todas as táboas do
 * concello do esquema.
 */
class NativeSqlRowLevelSecurityIT extends AbstractIT {

  @Test
  void everyTableOfMunicipalityNativeSqlHasRowLevelSecurity() {
    Set<String> tables = new TreeSet<>();
    for (JavaClass c :
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("gal.furar")) {
      NativeSql a =
          c.isAnnotatedWith(NativeSql.class) ? c.reflect().getAnnotation(NativeSql.class) : null;
      if (a != null && a.scope() == NativeSql.Scope.MUNICIPALITY) {
        NativeSqlScope.sqlLiterals(c).forEach(sql -> tables.addAll(NativeSqlScope.tables(sql)));
      }
    }
    if (tables.isEmpty()) {
      tables.addAll(RowLevelSecurity.municipalityTables(jdbc));
    }
    assertThat(tables).as("tables to check (never an empty set)").isNotEmpty();
    assertThat(problems(tables))
        .as("tables read by @NativeSql(MUNICIPALITY) without the RLS rule of D14")
        .isEmpty();
  }

  /**
   * Autoproba: a regra acepta unha táboa con RLS e detecta unha sen RLS ({@code municipality}, da
   * plataforma) e unha que non existe.
   */
  @Test
  void detectsTablesWithoutRowLevelSecurity() {
    assertThat(problems(Set.of("custom_field", "municipality", "no_such_table")))
        .containsExactly(
            "municipality: row level security not enabled",
            "no_such_table: row level security not enabled");
  }

  /** Autoproba: unha política que só mira que haxa concello ({@code is not null}) non vale. */
  @Test
  void policyThatOnlyChecksNotNullIsRejected() {
    assertThat(
            RowLevelSecurity.policyProblems(
                "t",
                List.of(
                    new RowLevelSecurity.Policy(
                        "open", "PERMISSIVE", "ALL", "(municipality_id IS NOT NULL)", ""))))
        .containsExactly(
            "t: permissive policy open does not filter by municipality",
            "t: no policy on municipality_id = furar.municipality_id for ALL");
  }

  private List<String> problems(Set<String> tables) {
    List<String> problems = new ArrayList<>();
    new TreeSet<>(tables).forEach(t -> problems.addAll(RowLevelSecurity.problems(jdbc, t)));
    return problems;
  }
}
