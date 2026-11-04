// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.portability.MunicipalityDataSection;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenancyAllowlist;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Disabled;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Regras de arquitectura de ADR-0005 como constantes. As mesmas constantes aplícanse ao código
 * ({@link ArchitectureTest}) e a exemplos que as violan ({@link ArchitectureRulesSelfTest}): unha
 * regra que non falla ante a súa violación non conta.
 */
public final class ArchitectureRules {

  private ArchitectureRules() {}

  public static final ArchRule A1_DOMAIN_IS_PURE =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "org.hibernate..",
              "tools.jackson..",
              "com.fasterxml..",
              "lombok..",
              "..application..",
              "..adapter..")
          .because("A1 o dominio é Java puro: sen Spring, JPA, Jackson nin capas exteriores");

  public static final ArchRule A2_APPLICATION_HAS_NO_WEB_OR_PERSISTENCE =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..adapter..",
              "org.springframework.web..",
              "org.springframework.data..",
              "jakarta.servlet..",
              "jakarta.persistence..",
              "org.hibernate..",
              "tools.jackson..",
              "com.fasterxml..")
          .because("A2 a aplicación non coñece web, persistencia nin JSON (portos e adaptadores)");

  public static final ArchRule A3_ADAPTERS_DO_NOT_TALK =
      CompositeArchRule.of(
              noClasses()
                  .that()
                  .resideInAPackage("..adapter.in..")
                  .should()
                  .dependOnClassesThat()
                  .resideInAPackage("..adapter.out..")
                  .because("A3 os adaptadores de entrada e saída non dependen entre si"))
          .and(
              noClasses()
                  .that()
                  .resideInAPackage("..adapter.out..")
                  .should()
                  .dependOnClassesThat()
                  .resideInAPackage("..adapter.in..")
                  .because("A3 os adaptadores de entrada e saída non dependen entre si"));

  public static final ArchRule A4_WEB_USES_ONLY_INPUT_PORTS =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in.web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application.service..", "..port.out..")
          .because("A4 o controlador só usa portos de entrada (casos de uso)");

  public static final ArchRule A5_CONTROLLERS_SPEAK_DTO =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .and()
          .areMetaAnnotatedWith(RequestMapping.class)
          .should(notExpose("..domain..", "..persistence.."))
          .because("A5 a API nunca recibe nin devolve dominio nin entidades: só DTO");

  public static final ArchRule A6_JPA_ONLY_IN_PERSISTENCE =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .or()
          .areAnnotatedWith(Embeddable.class)
          .or()
          .areAssignableTo(Repository.class)
          .should()
          .resideInAPackage("..adapter.out.persistence..")
          .because("A6 entidades e repositorios JPA só no adaptador de persistencia");

  public static final ArchRule A7_ENTITIES_HAVE_TENANT =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .and(
              not(
                  describe(
                      "in TenancyAllowlist", c -> TenancyAllowlist.ENTITIES.contains(c.getName()))))
          .should(haveTenantIdField())
          .because("A7 toda entidade leva @TenantId (ADR-0006)");

  public static final ArchRule A7B_TENANT_ADAPTERS_EXTEND_BASE =
      classes()
          .that()
          .haveSimpleNameEndingWith("PersistenceAdapter")
          .and(
              describe(
                  "in a package with a @TenantId entity", ArchitectureRules::nextToTenantEntity))
          .should()
          .beAssignableTo(TenantScopedPersistenceAdapter.class)
          .because(
              "A7b o adaptador dunha entidade con @TenantId herda de TenantScopedPersistenceAdapter");

  /** Métodos de JPA/Hibernate que executan SQL sen filtro de concello. */
  static final Set<String> NATIVE_SQL_METHODS =
      Set.of(
          "createNativeQuery",
          "createNativeMutationQuery",
          "createNativeStoredProcedureQuery",
          "createStoredProcedureQuery",
          "createStoredProcedureCall",
          "doWork",
          "doReturningWork");

  /**
   * A8 (ADR-0005, ADR-0006): todo camiño ao SQL sen pasar por Hibernate está pechado fóra das
   * clases {@code @NativeSql} do adaptador de persistencia. Non é unha lista de clases de Spring
   * (QA2: un {@code DataSource.getConnection().createStatement()} esquivábaa): prohíbe depender de
   * calquera tipo de {@code java.sql}, {@code javax.sql} e {@code org.springframework.jdbc}, chamar
   * ao SQL nativo de JPA ou Hibernate ({@link #NATIVE_SQL_METHODS}) e {@code @Query(nativeQuery =
   * true)}. Dentro de {@code @NativeSql}, A8b só admite {@code JdbcClient.sql} e {@code
   * EntityManager.createNativeQuery} cun literal.
   */
  public static final ArchRule A8_NATIVE_SQL_IS_MARKED =
      CompositeArchRule.of(
              noClasses()
                  .that(not(nativeSqlAdapter()))
                  .should()
                  .dependOnClassesThat(rawSqlApi())
                  .orShould()
                  .callMethodWhere(
                      describe(
                          "native SQL of JPA or Hibernate " + NATIVE_SQL_METHODS,
                          call ->
                              NATIVE_SQL_METHODS.contains(call.getName())
                                  && call.getTargetOwner()
                                      .getPackageName()
                                      .matches("(jakarta\\.persistence|org\\.hibernate)(\\..*)?")))
                  .because("A8 SQL nativo só en clases @NativeSql do adaptador de persistencia"))
          .and(
              noMethods()
                  .that(describe("@Query(nativeQuery = true)", ArchitectureRules::isNativeQuery))
                  .should()
                  .beDeclaredInClassesThat(describe("any class", c -> true))
                  .allowEmptyShould(true)
                  .because(
                      "A8 sen @Query(nativeQuery = true): A8b non le o SQL das anotacións (QN2)"));

  /** Calquera API de acceso directo á base: JDBC, DataSource e o JDBC de Spring. */
  static DescribedPredicate<JavaClass> rawSqlApi() {
    return describe(
        "raw SQL API (java.sql, javax.sql, org.springframework.jdbc)",
        c ->
            c.getPackageName()
                .matches("(java\\.sql|javax\\.sql|org\\.springframework\\.jdbc)(\\..*)?"));
  }

  /**
   * A8b (ADR-0006): o SQL nativo declara o seu alcance e cúmpreo. Le os literais SQL do bytecode da
   * clase: {@code MUNICIPALITY} esixe {@code municipality_id = :param} en cada sentenza e un {@code
   * <Clase>TenantIT} que use a clase; {@code GLOBAL} esixe motivo e só táboas de {@link
   * TenancyAllowlist#TABLES}.
   */
  public static final ArchRule A8B_NATIVE_SQL_IS_SCOPED =
      classes()
          .that()
          .areAnnotatedWith(NativeSql.class)
          .should(NativeSqlScope.isRespected())
          .allowEmptyShould(true)
          .because("A8b o SQL nativo declara o alcance e cúmpreo (ADR-0006)");

  public static final ArchRule A9_FORBIDDEN_APIS =
      CompositeArchRule.of(
              noClasses()
                  .should()
                  .dependOnClassesThat()
                  .belongToAnyOf(
                      java.sql.Timestamp.class,
                      java.sql.Date.class,
                      java.util.Date.class,
                      Calendar.class)
                  .orShould()
                  .dependOnClassesThat()
                  .resideInAPackage("lombok..")
                  .orShould()
                  .dependOnClassesThat()
                  .belongToAnyOf(Lazy.class)
                  .orShould()
                  .callMethod(Throwable.class, "printStackTrace")
                  .because("A9 sen datas antigas, Lombok, @Lazy nin printStackTrace"))
          .and(
              GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because(
                  "A9 sen @Autowired en campos"))
          .and(
              GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
                  "A9 sen System.out nin System.err"));

  /** A9 tamén nos tests: ningún test desactivado. */
  public static final ArchRule A9_NO_DISABLED_TESTS =
      CompositeArchRule.of(
              noClasses()
                  .should()
                  .beAnnotatedWith(Disabled.class)
                  .because("A9 non se desactivan tests (@Disabled)"))
          .and(
              noMethods()
                  .should()
                  .beAnnotatedWith(Disabled.class)
                  .because("A9 non se desactivan tests (@Disabled)"));

  public static final ArchRule A10_NO_LOCAL_DATE_TIME_IN_API_OR_DB =
      noFields()
          .that()
          .areDeclaredInClassesThat(
              resideInAPackage("..dto..")
                  .or(describe("@Entity", c -> c.isAnnotatedWith(Entity.class))))
          .should()
          .haveRawType(LocalDateTime.class)
          .allowEmptyShould(true)
          .because("A10 horas en Instant (UTC), nunca LocalDateTime en DTO nin entidades");

  public static final ArchRule A11_MULTIPART_ONLY_IN_WEB =
      noClasses()
          .that()
          .resideOutsideOfPackage("..adapter.in.web..")
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(MultipartFile.class)
          .because("A11 MultipartFile só no adaptador web");

  public static final ArchRule A12_NAMING =
      CompositeArchRule.of(
              classes()
                  .that()
                  .haveSimpleNameEndingWith("Controller")
                  .should()
                  .resideInAPackage("..adapter.in.web..")
                  .because("A12 *Controller en adapter.in.web"))
          .and(
              classes()
                  .that()
                  .haveSimpleNameEndingWith("UseCase")
                  .and()
                  .areNotAnnotations()
                  .should()
                  .beInterfaces()
                  .andShould()
                  .resideInAPackage("..application.port.in..")
                  .because("A12 *UseCase é unha interface en port.in"))
          .and(
              classes()
                  .that()
                  .haveSimpleNameEndingWith("Service")
                  .and()
                  .areNotInterfaces()
                  .should()
                  .resideInAPackage("..application.service..")
                  .because("A12 *Service en application.service"))
          .and(
              classes()
                  .that()
                  .haveSimpleNameEndingWith("Entity")
                  .should()
                  .resideInAPackage("..adapter.out.persistence..")
                  .because("A12 *Entity en adapter.out.persistence"))
          .and(
              classes()
                  .that()
                  .resideInAPackage("..adapter.in.web.dto..")
                  .and(describe("records", JavaClass::isRecord))
                  .should()
                  .haveSimpleNameEndingWith("Request")
                  .orShould()
                  .haveSimpleNameEndingWith("Response")
                  .because("A12 os DTO rematan en Request ou Response"));

  public static final ArchRule A13_DOMAIN_IS_IMMUTABLE =
      CompositeArchRule.of(
              fields()
                  .that()
                  .areDeclaredInClassesThat()
                  .resideInAPackage("..domain..")
                  .and()
                  .areNotStatic()
                  .should()
                  .beFinal()
                  .because("A13 o dominio é inmutable: campos final ou records"))
          .and(
              noMethods()
                  .that()
                  .areDeclaredInClassesThat()
                  .resideInAPackage("..domain..")
                  .and()
                  .arePublic()
                  .should()
                  .haveNameMatching("set[A-Z].*")
                  .because("A13 sen setters públicos no dominio"));

  public static final ArchRule A14_NO_PACKAGE_CYCLES =
      slices()
          .matching("gal.furar.(*).(**)")
          .should()
          .beFreeOfCycles()
          .because("A14 sen ciclos entre paquetes");

  public static final ArchRule A15_USE_CASES_DECLARE_PERMISSION =
      methods()
          .that()
          .arePublic()
          .and()
          .areNotStatic()
          .and()
          .areDeclaredInClassesThat(
              describe(
                  "*Service implementing a *UseCase",
                  c ->
                      c.getSimpleName().endsWith("Service")
                          && c.getAllRawInterfaces().stream()
                              .anyMatch(i -> i.getSimpleName().endsWith("UseCase"))))
          .should(
              new ArchCondition<JavaMethod>(
                  "be annotated with exactly one of @RequiresPermission, @PublicUseCase or"
                      + " @PersonUseCase") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  long declared =
                      java.util.stream.Stream.of(
                              RequiresPermission.class, PublicUseCase.class, PersonUseCase.class)
                          .filter(method::isAnnotatedWith)
                          .count();
                  events.add(
                      new SimpleConditionEvent(
                          method,
                          declared == 1,
                          method.getFullName()
                              + (declared == 0
                                  ? " has no permission"
                                  : " declares more than one access rule")));
                }
              })
          .because("A15 cada caso de uso declara o seu permiso (ADR-0007, ADR-0025)");

  public static final ArchRule A16_CUSTOM_FIELD_VALUES_ONLY_FROM_VALIDATOR =
      CompositeArchRule.of(
              noClasses()
                  .that()
                  .resideOutsideOfPackage("gal.furar.customfields..")
                  .should()
                  .callMethod(CustomFieldValues.class, "trusted", Map.class)
                  .because("A16 CustomFieldValues.trusted só desde customfields (ADR-0011)"))
          .and(
              noClasses()
                  .that()
                  .resideOutsideOfPackage("..adapter.out.persistence..")
                  .should()
                  .callMethod(CustomFieldValues.class, "fromStorage", Map.class)
                  .because("A16 CustomFieldValues.fromStorage só desde a persistencia (ADR-0011)"));

  public static final ArchRule A17_PLATFORM_ONLY_FROM_PLATFORM_TASKS =
      noClasses()
          .that()
          .areNotAnnotatedWith(PlatformTask.class)
          .should()
          .callMethod(TenantContext.class, "runAsPlatform", Supplier.class)
          .because("A17 TenantContext.runAsPlatform só desde clases @PlatformTask");

  public static final ArchRule A18_VERSIONED_ENTITIES_ARE_PERSISTABLE =
      classes()
          .that(
              describe(
                  "have a @Version field",
                  c -> c.getAllFields().stream().anyMatch(f -> f.isAnnotatedWith(Version.class))))
          .should()
          .implement(Persistable.class)
          .allowEmptyShould(true)
          .because("A18 as entidades con @Version implementan Persistable (ADR-0010)");

  /**
   * A19 (ARQ-3): a API pública dun módulo (o seu paquete raíz) só expón tipos propios da raíz,
   * {@code shared.domain} ou Java: nunca dominio, aplicación nin adaptadores. Outro módulo depende
   * dun contrato estable ({@code XxxQueries} + records), non do modelo interno.
   */
  public static final ArchRule A19_MODULE_API_EXPOSES_NO_INTERNALS =
      methods()
          .that()
          .arePublic()
          .and()
          .areDeclaredInClassesThat(moduleRoot())
          .should(notExpose("..domain..", "..application..", "..adapter.."))
          .allowEmptyShould(true)
          .because("A19 a API pública dun módulo só expón records da raíz e shared.domain");

  /**
   * A20 (ARQ-8): {@code shared} é OPEN, pero a súa configuración é interna: ningún módulo depende
   * de {@code shared..config..}, e {@code shared.web} só se usa desde os adaptadores web.
   */
  public static final ArchRule A20_SHARED_INTERNALS_STAY_INTERNAL =
      classes()
          .that()
          .resideOutsideOfPackage("gal.furar.shared..")
          .should(notUseSharedInternals())
          .because("A20 a configuración de shared e shared.web non son API dos módulos");

  /**
   * A21 (ADR-0018, AP2-01): todo módulo con táboas do concello (entidades con {@code @TenantId})
   * publica a súa sección de portabilidade, para que o ficheiro {@code furar-export} leve todos os
   * datos do concello. A plantilla de módulo xa a trae ({@code @Name@DataSection}).
   */
  public static final ArchRule A21_TENANT_DATA_IS_PORTABLE =
      classes()
          .that(describe("@Entity with @TenantId", ArchitectureRules::isTenantEntity))
          .should(
              new ArchCondition<JavaClass>("have a MunicipalityDataSection in its module") {
                @Override
                public void check(JavaClass entity, ConditionEvents events) {
                  String root = moduleRoot(entity.getPackageName());
                  boolean found =
                      rootPackage(entity, root).getClassesInPackageTree().stream()
                          .anyMatch(c -> c.isAssignableTo(MunicipalityDataSection.class));
                  events.add(
                      new SimpleConditionEvent(
                          entity,
                          found,
                          entity.getName()
                              + " is tenant data but "
                              + root
                              + " has no MunicipalityDataSection"));
                }
              })
          .allowEmptyShould(true)
          .because("A21 os datos do concello son portables: cada módulo publica a súa sección");

  /**
   * Anotacións de persistencia permitidas (QA Q13, QN2). A22 é unha lista pechada: calquera outra
   * anotación de {@code org.hibernate.annotations}, {@code jakarta.persistence} ou {@code
   * org.springframework.data.jpa.repository} queda prohibida, porque moitas levan SQL propio
   * ({@code @Formula}, {@code @Subselect}, {@code @SQLRestriction}, {@code @NativeQuery}, {@code
   * DialectOverride.*}, {@code @Procedure}...) que {@code @TenantId} non filtra e A8b non le. Unha
   * lista de prohibidas quedaba atrás en cada versión nova de Hibernate ou Spring Data. Engadir
   * unha aquí xustifícase no PR: só as que non levan SQL. {@code @Query} admítese porque A8 rexeita
   * {@code nativeQuery = true} en todo o código.
   */
  static final Set<String> ALLOWED_PERSISTENCE_ANNOTATIONS =
      Set.of(
          "jakarta.persistence.Entity",
          "jakarta.persistence.Table",
          "jakarta.persistence.Id",
          "jakarta.persistence.Column",
          "jakarta.persistence.Version",
          "jakarta.persistence.Embeddable",
          "jakarta.persistence.Embedded",
          "jakarta.persistence.EmbeddedId",
          "jakarta.persistence.IdClass",
          "jakarta.persistence.Enumerated",
          "jakarta.persistence.Convert",
          "jakarta.persistence.Converter",
          "jakarta.persistence.Transient",
          "jakarta.persistence.MappedSuperclass",
          "jakarta.persistence.Index",
          "jakarta.persistence.UniqueConstraint",
          "jakarta.persistence.JoinColumn",
          "jakarta.persistence.JoinColumns",
          "jakarta.persistence.ManyToOne",
          "jakarta.persistence.OneToMany",
          "jakarta.persistence.OneToOne",
          "jakarta.persistence.ElementCollection",
          "jakarta.persistence.CollectionTable",
          "jakarta.persistence.GeneratedValue",
          "jakarta.persistence.Basic",
          "jakarta.persistence.PrePersist",
          "jakarta.persistence.PreUpdate",
          "jakarta.persistence.PostLoad",
          "org.hibernate.annotations.TenantId",
          "org.hibernate.annotations.JdbcTypeCode",
          "org.hibernate.annotations.CreationTimestamp",
          "org.hibernate.annotations.UpdateTimestamp",
          "org.hibernate.annotations.Immutable",
          "org.hibernate.annotations.NaturalId",
          "org.springframework.data.jpa.repository.Query",
          "org.springframework.data.jpa.repository.Modifying",
          "org.springframework.data.jpa.repository.EntityGraph",
          "org.springframework.data.jpa.repository.Lock");

  private static final String PERSISTENCE_ANNOTATION_PACKAGES =
      "(org\\.hibernate\\.annotations|jakarta\\.persistence|org\\.springframework\\.data\\.jpa"
          + "\\.repository)(\\..*)?";

  /**
   * A22 (QA Q13, QN2, T13): ningunha anotación de persistencia fóra de {@link
   * #ALLOWED_PERSISTENCE_ANNOTATIONS}.
   */
  public static final ArchRule A22_NO_SQL_IN_ANNOTATIONS =
      noClasses()
          .should()
          .dependOnClassesThat(
              describe(
                  "persistence annotations outside ALLOWED_PERSISTENCE_ANNOTATIONS (they may carry"
                      + " SQL)",
                  c ->
                      c.isAnnotation()
                          && c.getPackageName().matches(PERSISTENCE_ANNOTATION_PACKAGES)
                          && !ALLOWED_PERSISTENCE_ANNOTATIONS.contains(c.getName())))
          .because(
              "A22 sen SQL en anotacións (@Formula, @Subselect, @SQLRestriction, @NativeQuery...):"
                  + " @TenantId non o filtra");

  /** Todas as regras, por id, para a autoproba. */
  public static final Map<String, ArchRule> ALL =
      Map.ofEntries(
          Map.entry("A1", A1_DOMAIN_IS_PURE),
          Map.entry("A2", A2_APPLICATION_HAS_NO_WEB_OR_PERSISTENCE),
          Map.entry("A3", A3_ADAPTERS_DO_NOT_TALK),
          Map.entry("A4", A4_WEB_USES_ONLY_INPUT_PORTS),
          Map.entry("A5", A5_CONTROLLERS_SPEAK_DTO),
          Map.entry("A6", A6_JPA_ONLY_IN_PERSISTENCE),
          Map.entry("A7", A7_ENTITIES_HAVE_TENANT),
          Map.entry("A7b", A7B_TENANT_ADAPTERS_EXTEND_BASE),
          Map.entry("A8", A8_NATIVE_SQL_IS_MARKED),
          Map.entry("A8b", A8B_NATIVE_SQL_IS_SCOPED),
          Map.entry("A9", A9_FORBIDDEN_APIS),
          Map.entry("A9t", A9_NO_DISABLED_TESTS),
          Map.entry("A10", A10_NO_LOCAL_DATE_TIME_IN_API_OR_DB),
          Map.entry("A11", A11_MULTIPART_ONLY_IN_WEB),
          Map.entry("A12", A12_NAMING),
          Map.entry("A13", A13_DOMAIN_IS_IMMUTABLE),
          Map.entry("A14", A14_NO_PACKAGE_CYCLES),
          Map.entry("A15", A15_USE_CASES_DECLARE_PERMISSION),
          Map.entry("A16", A16_CUSTOM_FIELD_VALUES_ONLY_FROM_VALIDATOR),
          Map.entry("A17", A17_PLATFORM_ONLY_FROM_PLATFORM_TASKS),
          Map.entry("A18", A18_VERSIONED_ENTITIES_ARE_PERSISTABLE),
          Map.entry("A19", A19_MODULE_API_EXPOSES_NO_INTERNALS),
          Map.entry("A20", A20_SHARED_INTERNALS_STAY_INTERNAL),
          Map.entry("A21", A21_TENANT_DATA_IS_PORTABLE),
          Map.entry("A22", A22_NO_SQL_IN_ANNOTATIONS));

  // --- Axudas ---

  /**
   * O tipo compartido {@code shared.domain} (p. ex. {@code CustomFieldTarget}) é vocabulario común.
   */
  private static final Set<String> SHARED_VOCABULARY = Set.of("gal.furar.shared.domain");

  private static ArchCondition<JavaMethod> notExpose(String... packages) {
    DescribedPredicate<JavaClass> forbidden = resideInAnyPackage(packages);
    return new ArchCondition<>("not accept or return types from " + String.join(", ", packages)) {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        java.util.List<JavaType> types = new java.util.ArrayList<>(method.getParameterTypes());
        types.add(method.getReturnType());
        for (JavaType type : types) {
          for (JavaClass raw : type.getAllInvolvedRawTypes()) {
            if (forbidden.test(raw) && !SHARED_VOCABULARY.contains(raw.getPackageName())) {
              events.add(
                  SimpleConditionEvent.violated(
                      method, method.getFullName() + " exposes " + raw.getName()));
            }
          }
        }
      }
    };
  }

  /**
   * Paquete raíz dun módulo: {@code <base>.<modulo>} sen subpaquete {@code domain}, {@code
   * application} nin {@code adapter}; fóra de {@code shared} e do paquete base.
   */
  private static DescribedPredicate<JavaClass> moduleRoot() {
    return describe(
        "public types in a module root package",
        c -> {
          String pkg = c.getPackageName();
          return c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)
              && !pkg.equals("gal.furar")
              && !pkg.startsWith("gal.furar.shared")
              && !pkg.startsWith("gal.furar.architecture")
              && !pkg.startsWith("gal.furar.testsupport")
              && !pkg.matches(".*\\.(domain|application|adapter)(\\..*)?");
        });
  }

  /**
   * Infraestrutura de plataforma dentro de {@code shared} (ARQ3-4): filtros, manexadores,
   * configuración de Hibernate e adaptadores propios. Ningún módulo a usa, nin desde a web.
   */
  private static final Set<String> SHARED_PLATFORM_CLASSES =
      Set.of(
          "gal.furar.shared.web.TenantFilter",
          "gal.furar.shared.web.RequestIdFilter",
          "gal.furar.shared.web.ProblemExceptionHandler",
          "gal.furar.shared.web.ErrorEndpoint",
          "gal.furar.shared.web.WebConfig",
          "gal.furar.shared.web.OpenApiConfig",
          "gal.furar.shared.tenancy.TenantListenerInterceptor",
          "gal.furar.shared.tenancy.TenancyConfig",
          "gal.furar.shared.tenancy.TenantIdentifierResolver");

  private static ArchCondition<JavaClass> notUseSharedInternals() {
    return new ArchCondition<>(
        "not depend on gal.furar.shared..config.., gal.furar.shared.adapter.., shared platform"
            + " classes, nor on gal.furar.shared.web.. outside ..adapter.in.web..") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        boolean web = item.getPackageName().contains(".adapter.in.web");
        item.getDirectDependenciesFromSelf().stream()
            .map(d -> d.getTargetClass())
            .filter(
                t ->
                    t.getPackageName().matches("gal\\.furar\\.shared(\\..*)?\\.config(\\..*)?")
                        || t.getPackageName().startsWith("gal.furar.shared.adapter")
                        || SHARED_PLATFORM_CLASSES.contains(t.getName())
                        || (!web && t.getPackageName().startsWith("gal.furar.shared.web")))
            .map(JavaClass::getName)
            .distinct()
            .forEach(
                t ->
                    events.add(
                        SimpleConditionEvent.violated(item, item.getName() + " depends on " + t)));
      }
    };
  }

  private static ArchCondition<JavaClass> haveTenantIdField() {
    return new ArchCondition<>("have a field annotated with @TenantId") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        boolean ok = item.getAllFields().stream().anyMatch(f -> f.isAnnotatedWith(TenantId.class));
        events.add(new SimpleConditionEvent(item, ok, item.getName() + " has no @TenantId field"));
      }
    };
  }

  private static boolean isTenantEntity(JavaClass c) {
    return c.isAnnotatedWith(Entity.class)
        && c.getAllFields().stream().anyMatch(f -> f.isAnnotatedWith(TenantId.class));
  }

  /** Paquete raíz do módulo: o que vai antes de {@code .adapter}, {@code .domain}… */
  private static String moduleRoot(String packageName) {
    for (String layer : List.of(".adapter", ".application", ".domain")) {
      int at = packageName.indexOf(layer);
      if (at > 0) {
        return packageName.substring(0, at);
      }
    }
    return packageName;
  }

  private static JavaPackage rootPackage(JavaClass c, String root) {
    var pkg = c.getPackage();
    while (!pkg.getName().equals(root) && pkg.getParent().isPresent()) {
      pkg = pkg.getParent().get();
    }
    return pkg;
  }

  private static boolean nextToTenantEntity(JavaClass adapter) {
    return adapter.getPackage().getClasses().stream().anyMatch(ArchitectureRules::isTenantEntity)
        || adapter.getDirectDependenciesFromSelf().stream()
            .anyMatch(d -> isTenantEntity(d.getTargetClass()));
  }

  private static DescribedPredicate<JavaClass> nativeSqlAdapter() {
    return describe(
        "@NativeSql in ..adapter.out.persistence..",
        c ->
            c.isAnnotatedWith(NativeSql.class)
                && c.getPackageName().contains(".adapter.out.persistence"));
  }

  private static boolean isNativeQuery(JavaMethod method) {
    return method
        .tryGetAnnotationOfType(Query.class.getName())
        .map(JavaAnnotation::getProperties)
        .map(p -> Boolean.TRUE.equals(p.get("nativeQuery")))
        .orElse(false);
  }
}
