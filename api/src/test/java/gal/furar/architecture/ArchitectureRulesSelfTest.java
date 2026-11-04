// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.modulith.core.ApplicationModules;

/**
 * D12 (ADR-0005): cada regra falla ante a súa violación e a mensaxe nomea o id. Aplica as mesmas
 * constantes de {@link ArchitectureRules} e {@link ModularityRules} a exemplos que as violan
 * ({@code src/test/java/archviolations}), un por regra.
 */
class ArchitectureRulesSelfTest {

  static final ImportOption EVERYTHING = location -> true;

  @TestFactory
  Stream<DynamicTest> everyRuleFailsOnItsViolation() {
    List<DynamicTest> tests = new ArrayList<>();
    ArchitectureRules.ALL.forEach(
        (id, rule) ->
            tests.add(
                DynamicTest.dynamicTest(
                    id,
                    () -> {
                      JavaClasses violation = violation(id);
                      assertThat(violation).as("fixture for " + id).isNotEmpty();
                      String ruleId = id.equals("A9t") ? "A9" : id;
                      assertThatThrownBy(() -> rule.check(violation))
                          .isInstanceOf(AssertionError.class)
                          .hasMessageContaining(ruleId + " ");
                    })));
    return tests.stream();
  }

  @Test
  void everyRuleHasAFixture() {
    assertThat(new TreeSet<>(ArchitectureRules.ALL.keySet()))
        .containsAll(
            List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A7b", "A8", "A8b", "A9", "A10"))
        .containsAll(List.of("A11", "A12", "A13", "A14", "A15", "A16", "A17", "A18"))
        .containsAll(List.of("A19", "A20", "A21", "A22"));
  }

  /** ARQ3-4: a infraestrutura de plataforma de shared tampouco se usa desde a web dun módulo. */
  @Test
  void a20ClosesSharedPlatformClasses() {
    assertThatThrownBy(
            () -> ArchitectureRules.A20_SHARED_INTERNALS_STAY_INTERNAL.check(violation("A20")))
        .hasMessageContaining("PlatformLeakController depends on gal.furar.shared.web.TenantFilter")
        .hasMessageContaining(
            "PlatformLeakController depends on gal.furar.shared.adapter.out.persistence")
        .hasMessageContaining(
            "PlatformLeakController depends on gal.furar.shared.tenancy.TenantIdentifierResolver");
  }

  /** ADR-0025: @PersonUseCase vale como regra de acceso; dúas á vez, non. */
  @Test
  void a15AcceptsPersonUseCasesAndRejectsTwoRules() {
    assertThatThrownBy(
            () -> ArchitectureRules.A15_USE_CASES_DECLARE_PERMISSION.check(violation("A15")))
        .hasMessageContaining("OpenService.doIt() has no permission")
        .hasMessageContaining("DoubleRuleService.doIt() declares more than one access rule")
        .hasMessageNotContaining("PersonService");
  }

  /** QA Q13: cada anotación con SQL de Hibernate falla, en campo ou en clase. */
  @Test
  void a22RejectsSqlAnnotations() {
    assertThatThrownBy(() -> ArchitectureRules.A22_NO_SQL_IN_ANNOTATIONS.check(violation("A22")))
        .hasMessageContaining("org.hibernate.annotations.Formula")
        .hasMessageContaining("org.hibernate.annotations.SQLRestriction")
        .hasMessageContaining("org.hibernate.annotations.ColumnTransformer")
        .hasMessageContaining("org.hibernate.annotations.Subselect");
  }

  /** QA Q12: comas no FROM e nomes con esquema xa non esquivan a lista de táboas. */
  @Test
  void a8bRejectsCommaJoinsAndSchemaQualifiedTables() {
    assertThatThrownBy(() -> ArchitectureRules.A8B_NATIVE_SQL_IS_SCOPED.check(violation("A8b")))
        .hasMessageContaining("CommaJoinSql has a comma in FROM/USING; use an explicit JOIN")
        .hasMessageContaining(
            "SchemaQualifiedSql is GLOBAL but reads tables outside TenancyAllowlist:"
                + " [custom_field]")
        .hasMessageContaining("SchemaQualifiedSql has a schema-qualified table name");
  }

  @Test
  void a8bReportsEveryBrokenPromise() {
    assertThatThrownBy(() -> ArchitectureRules.A8B_NATIVE_SQL_IS_SCOPED.check(violation("A8b")))
        .hasMessageContaining("UnprovenTenantSql is MUNICIPALITY but")
        .hasMessageContaining("UnprovenTenantSqlTenantIT does not exist")
        .hasMessageContaining("SQL without municipality_id = :param")
        .hasMessageContaining("without municipality_id in the column list): insert into seat_slot")
        .hasMessageContaining("top-level OR that can bypass municipality_id")
        .hasMessageContaining("GlobalLeakSql is GLOBAL without a reason")
        .hasMessageContaining("outside TenancyAllowlist: [custom_field]")
        .hasMessageContaining("SQL without municipality_id = :param")
        .hasMessageContaining("): select count(*) from seat_slot")
        .hasMessageContaining(
            "CommentedGlobalSql is GLOBAL but reads tables outside TenancyAllowlist:"
                + " [custom_field]")
        .hasMessageContaining("CommentedGlobalSql has SQL that A8b cannot parse")
        .hasMessageContaining("SplitSql builds the SQL of sql(...) at runtime in any()")
        .hasMessageContaining("RawJdbcInNativeSql uses java.sql.Statement.executeQuery in count()")
        .hasMessageContaining("RawJdbcInNativeSql uses javax.sql.DataSource.getConnection");
  }

  /** QA Q3: un {@code or} entre parénteses e un TenantIT baleiro xa non pasan. */
  @Test
  void a8bRejectsNestedPredicatesAndEmptyTenantIts() {
    assertThatThrownBy(() -> ArchitectureRules.A8B_NATIVE_SQL_IS_SCOPED.check(violation("A8b")))
        .hasMessageContaining(
            "ParenOrSql is MUNICIPALITY but its municipality_id predicate is only inside"
                + " parentheses, after NOT or outside the top-level WHERE: select count(*) from seat_slot"
                + " where (municipality_id")
        .hasMessageContaining(
            "outside the top-level WHERE: select count(*) from seat_slot where not")
        .hasMessageContaining("ParenOrSql is MUNICIPALITY but has a top-level UNION")
        .hasMessageContaining("ParenOrSql is MUNICIPALITY but has a top-level CASE")
        .hasMessageContaining("ParenOrSqlTenantIT does not extend AbstractTenantIT");
  }

  /** QA Q11: o predicado na lista do SELECT e un TenantIT que non chama á clase xa non pasan. */
  @Test
  void a8bRejectsPredicatesOutsideWhereAndHollowTenantIts() {
    assertThatThrownBy(() -> ArchitectureRules.A8B_NATIVE_SQL_IS_SCOPED.check(violation("A8b")))
        .hasMessageContaining(
            "SelectListSql is MUNICIPALITY but its municipality_id predicate is only inside"
                + " parentheses, after NOT or outside the top-level WHERE: select municipality_id")
        .hasMessageContaining("SelectListSqlTenantIT.visibleTo does not call");
    assertThat(
            NativeSqlScope.hasTopLevelPredicate(
                "select a from t where b = 1 and t.municipality_id = :m"))
        .isTrue();
    assertThat(
            NativeSqlScope.hasTopLevelPredicate("select a from t join u on u.municipality_id = :m"))
        .isFalse();
    assertThat(
            NativeSqlScope.hasTopLevelPredicate(
                "select a from t where b = 1 group by a having municipality_id = :m"))
        .isFalse();
  }

  /** QN1: as táboas lense por nome, non só despois de from/join (TABLE, SQL dinámico). */
  @Test
  void a8bSeesTablesWhereverTheyAreNamed() {
    assertThatThrownBy(() -> ArchitectureRules.A8B_NATIVE_SQL_IS_SCOPED.check(violation("A8b")))
        .hasMessageContaining(
            "TableStatementSql is GLOBAL but reads tables outside TenancyAllowlist:"
                + " [custom_field]");
    assertThat(NativeSqlScope.tables("table custom_field")).contains("custom_field");
    assertThat(NativeSqlScope.tables("select count(*) from (table custom_field) t"))
        .contains("custom_field");
    assertThat(
            NativeSqlScope.tables(
                "select query_to_xml('select * from custom_field', true, false, '')"))
        .contains("custom_field");
    assertThat(NativeSqlScope.schemaRelations())
        .contains("municipality", "custom_field", "audit_event");
  }

  /** QN2: @NativeQuery e @Query(nativeQuery = true) fallan en calquera clase. */
  @Test
  void nativeQueryAnnotationsFailEverywhere() {
    assertThatThrownBy(() -> ArchitectureRules.A22_NO_SQL_IN_ANNOTATIONS.check(violation("A22")))
        .hasMessageContaining("org.springframework.data.jpa.repository.NativeQuery");
    assertThatThrownBy(() -> ArchitectureRules.A8_NATIVE_SQL_IS_MARKED.check(violation("A8")))
        .hasMessageContaining("NativeQueryInNativeSql.allCodes()");
  }

  /** QA2: A8 pecha todo acceso ao SQL fóra de @NativeSql, non só as clases de Spring. */
  @Test
  void a8ReportsEveryRawSqlPath() {
    assertThatThrownBy(() -> ArchitectureRules.A8_NATIVE_SQL_IS_MARKED.check(violation("A8")))
        .hasMessageContaining("RawSqlAdapter")
        .hasMessageContaining("RawJdbcReader")
        .hasMessageContaining("javax.sql.DataSource")
        .hasMessageContaining("java.sql.Statement")
        .hasMessageContaining("HibernateWorkReader")
        .hasMessageContaining("createNativeQuery");
  }

  @Test
  void a8bAcceptsScopedSqlWithItsTenantIt() {
    JavaClasses compliant =
        new ClassFileImporter().withImportOption(EVERYTHING).importPackages("archviolations.a8bok");
    ArchitectureRules.A8B_NATIVE_SQL_IS_SCOPED.check(compliant);
  }

  @Test
  void m1CyclesBetweenModulesFail() {
    ApplicationModules modules = ApplicationModules.of("archviolations.m1", EVERYTHING);
    assertThatThrownBy(() -> ModularityRules.verifyModules(modules))
        .hasMessageContaining("M1 ")
        .hasMessageContaining("Cycle");
  }

  @Test
  void m2InternalTypesOfOtherModulesFail() {
    ApplicationModules modules = ApplicationModules.of("archviolations.m2", EVERYTHING);
    assertThatThrownBy(() -> ModularityRules.verifyModules(modules))
        .hasMessageContaining("M2 ")
        .hasMessageContaining("Secret");
  }

  @Test
  void m3ModulesWithoutExplicitDependenciesFail() {
    ApplicationModules modules = ApplicationModules.of("archviolations.m3", EVERYTHING);
    assertThatThrownBy(() -> ModularityRules.verifyExplicitDependencies(modules))
        .hasMessageContaining("M3")
        .hasMessageContaining("archviolations.m3.epsilon");
  }

  static JavaClasses violation(String id) {
    String pkg =
        id.equals("A14") ? "gal.furar.zzarch" : "archviolations." + id.toLowerCase(Locale.ROOT);
    return new ClassFileImporter().withImportOption(EVERYTHING).importPackages(pkg);
  }
}
