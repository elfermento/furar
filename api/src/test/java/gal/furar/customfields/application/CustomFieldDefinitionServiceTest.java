// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import gal.furar.customfields.ValidationContext;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.service.CustomFieldDefinitionService;
import gal.furar.customfields.application.service.CustomFieldTargets;
import gal.furar.customfields.application.service.CustomFieldValuesService;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.CustomFieldError;
import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.Definitions;
import gal.furar.customfields.domain.FieldOption;
import gal.furar.customfields.domain.FieldRules;
import gal.furar.customfields.domain.FieldType;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldTargetProvider;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso de campos personalizados con repositorio en memoria. */
class CustomFieldDefinitionServiceTest {

  final InMemoryCustomFieldDefinitionRepository repository =
      new InMemoryCustomFieldDefinitionRepository();
  Optional<Actor> actor = Optional.empty();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
  final CustomFieldTargets targets =
      new CustomFieldTargets(List.of(MUNICIPALITY_TARGET, FACILITY_TARGET, BOOKING_TARGET));
  final List<AuditEvent> audit = new ArrayList<>();
  final CustomFieldDefinitionService service =
      new CustomFieldDefinitionService(repository, targets, () -> actor, audit::add, clock);

  /** Só o concello está rexistrado, como en produción hoxe (ADR-0005 ARQ3-2). */
  static final CustomFieldTargetProvider MUNICIPALITY_TARGET =
      new CustomFieldTargetProvider() {
        @Override
        public CustomFieldTarget target() {
          return CustomFieldTarget.MUNICIPALITY;
        }

        @Override
        public Set<String> coreFields() {
          return Set.of("slug", "name");
        }

        @Override
        public boolean ownedByCurrentMunicipality(UUID entityId) {
          return TenantContext.require().id().equals(entityId);
        }
      };

  /** Un segundo destino, para probar o cambio de sitio. */
  static final CustomFieldTarget FACILITY = new CustomFieldTarget("FACILITY");

  static final CustomFieldTargetProvider FACILITY_TARGET =
      new CustomFieldTargetProvider() {
        @Override
        public CustomFieldTarget target() {
          return FACILITY;
        }

        @Override
        public Set<String> coreFields() {
          return Set.of("address");
        }
      };

  /** A reserva: sen campos públicos. */
  static final CustomFieldTargetProvider BOOKING_TARGET =
      new CustomFieldTargetProvider() {
        @Override
        public CustomFieldTarget target() {
          return CustomFieldTarget.BOOKING;
        }

        @Override
        public Set<String> coreFields() {
          return Set.of("code");
        }

        @Override
        public boolean allowsPublic() {
          return false;
        }

        @Override
        public boolean ownedByItsCreator() {
          return true;
        }
      };

  final InMemoryCustomFieldValueRepository stored = new InMemoryCustomFieldValueRepository();
  final CustomFieldValuesService values = new CustomFieldValuesService(repository, stored, targets);
  final TenantRef naron = new TenantRef(UUID.randomUUID(), "naron");
  TenantContext.Scope scope;

  @BeforeEach
  void enter() {
    scope = TenantContext.open(naron);
  }

  @AfterEach
  void leave() {
    scope.close();
  }

  @Test
  void createListAndSchemaByAudience() {
    create("web", Visibility.PUBLIC);
    create("phone", Visibility.OWNER);
    create("note", Visibility.STAFF);
    assertThat(codes(service.listVisible(CustomFieldTarget.MUNICIPALITY))).containsExactly("web");
    actor = Optional.of(new Actor("r", Set.of(new RoleGrant("naron", Role.RESIDENT))));
    assertThat(codes(service.listVisible(CustomFieldTarget.MUNICIPALITY)))
        .containsExactlyInAnyOrder("phone", "web");
    actor = Optional.of(new Actor("s", Set.of(new RoleGrant("naron", Role.STAFF))));
    assertThat(codes(service.listVisible(CustomFieldTarget.MUNICIPALITY))).hasSize(3);
    actor = Optional.empty();
    assertThat(codes(service.listVisible(CustomFieldTarget.MUNICIPALITY))).containsExactly("web");
    assertThat(service.schema(CustomFieldTarget.MUNICIPALITY).get("properties").toString())
        .contains("web")
        .doesNotContain("note");
    assertThat(service.listAll(CustomFieldTarget.MUNICIPALITY)).hasSize(3);
    assertThat(service.listAll(FACILITY)).isEmpty();
    assertThat(service.listAll()).hasSize(3);
    // Un destino sen módulo que o rexistre é un erro de validación, non unha lista baleira.
    assertError(
        () -> service.listAll(new CustomFieldTarget("ORGANIZATION")),
        CommonError.VALIDATION_FAILED);
  }

  @Test
  void coreFieldsOfTheTargetAreReservedAndUnknownTargetsRejected() {
    assertError(() -> create("slug", Visibility.PUBLIC), CustomFieldError.CODE_RESERVED);
    assertError(() -> new CustomFieldTarget("facility"), CommonError.VALIDATION_FAILED);
  }

  @Test
  void duplicatedCodeAndLimit() {
    create("web", Visibility.PUBLIC);
    assertError(() -> create("web", Visibility.PUBLIC), CustomFieldError.CODE_TAKEN);
    for (int i = 1; i < CustomFieldDefinitionService.MAX_ACTIVE_DEFINITIONS; i++) {
      create("field_" + i, Visibility.PUBLIC);
    }
    assertError(() -> create("one_more", Visibility.PUBLIC), CustomFieldError.LIMIT_EXCEEDED);
    CustomFieldDefinition first = service.listAll(CustomFieldTarget.MUNICIPALITY).get(0);
    CustomFieldDefinition inactive = service.deactivate(first.id(), 0);
    create("one_more", Visibility.PUBLIC);
    assertError(
        () -> service.activate(inactive.id(), inactive.version()), CustomFieldError.LIMIT_EXCEEDED);
  }

  @Test
  void updateDeactivateActivateWithVersions() {
    CustomFieldDefinition created = create("web", Visibility.PUBLIC);
    CustomFieldDefinition updated =
        service.update(
            new UpdateCustomFieldDefinitionCommand(
                created.id(),
                0,
                Definitions.spec(true, FieldRules.NONE, List.of(), Visibility.STAFF)));
    assertThat(updated.version()).isEqualTo(1);
    assertThat(updated.usage(CustomFieldTarget.MUNICIPALITY).orElseThrow().required()).isTrue();
    assertError(
        () ->
            service.update(
                new UpdateCustomFieldDefinitionCommand(
                    created.id(),
                    0,
                    Definitions.spec(true, FieldRules.NONE, List.of(), Visibility.STAFF))),
        CommonError.STALE_VERSION);
    CustomFieldDefinition inactive = service.deactivate(created.id(), 1);
    assertThat(inactive.isActive()).isFalse();
    assertThat(service.deactivate(created.id(), 2).version()).as("idempotent").isEqualTo(2);
    assertThat(service.activate(created.id(), 2).isActive()).isTrue();
    assertThat(service.activate(created.id(), 3).version()).isEqualTo(3);
    assertThat(service.get(created.id()).version()).isEqualTo(3);
    assertError(
        () -> service.get(CustomFieldDefinitionId.newId()), CustomFieldError.DEFINITION_NOT_FOUND);
    assertError(
        () -> service.deactivate(CustomFieldDefinitionId.newId(), 0),
        CustomFieldError.DEFINITION_NOT_FOUND);
  }

  /**
   * ADR-0020: cada escritura queda no rexistro, co campo e o sitio no éxito e o código no fallo.
   */
  @Test
  void everyWriteIsAudited() {
    CustomFieldDefinition web = create("web", Visibility.PUBLIC);
    service.update(
        new UpdateCustomFieldDefinitionCommand(
            web.id(), 0, Definitions.spec(false, FieldRules.NONE, List.of(), Visibility.STAFF)));
    assertError(() -> service.deactivate(web.id(), 0), CommonError.STALE_VERSION);
    service.deactivate(web.id(), 1);
    service.activate(web.id(), 2);
    service.delete(web.id(), 3);
    assertThat(audit)
        .extracting(AuditEvent::action, AuditEvent::outcome)
        .containsExactly(
            tuple("customfield.create", Outcome.SUCCESS),
            tuple("customfield.update", Outcome.SUCCESS),
            tuple("customfield.deactivate", Outcome.FAILURE),
            tuple("customfield.deactivate", Outcome.SUCCESS),
            tuple("customfield.activate", Outcome.SUCCESS),
            tuple("customfield.delete", Outcome.SUCCESS));
    assertThat(audit.get(0).details())
        .containsEntry("fieldId", web.id().value().toString())
        .containsEntry("field", "web")
        .containsEntry("target", "MUNICIPALITY");
    assertThat(audit.get(2).details())
        .containsEntry("fieldId", web.id().value().toString())
        .containsEntry("code", "common.stale-version");
    assertThat(audit).allSatisfy(e -> assertThat(e.municipalityId()).isEqualTo(naron.id()));
  }

  /** ARQ-6: unha lista proxéctase cunha soa lectura das definicións, non unha por fila. */
  @Test
  void projectAllReadsTheDefinitionsOnce() {
    create("web", Visibility.PUBLIC);
    create("note", Visibility.STAFF);
    CustomFieldValues row =
        values.validate(
            CustomFieldTarget.MUNICIPALITY,
            Map.of("web", "https://naron.gal", "note", "interna"),
            CustomFieldValues.empty(),
            ValidationContext.of(Audience.STAFF));
    int[] reads = {0};
    var counting =
        new InMemoryCustomFieldDefinitionRepository() {
          @Override
          public List<CustomFieldDefinition> findByTarget(CustomFieldTarget target) {
            reads[0]++;
            return repository.findByTarget(target);
          }
        };
    List<CustomFieldValues> projected =
        new CustomFieldValuesService(counting, stored, targets)
            .projectAll(
                CustomFieldTarget.MUNICIPALITY,
                List.of(row, CustomFieldValues.empty(), row, row),
                Audience.PUBLIC);
    assertThat(reads[0]).isEqualTo(1);
    assertThat(projected).hasSize(4);
    assertThat(projected.get(0).asMap()).containsOnlyKeys("web");
    assertThat(projected.get(1).isEmpty()).isTrue();
    assertThat(
            new CustomFieldValuesService(counting, stored, targets)
                .projectAll(
                    CustomFieldTarget.MUNICIPALITY,
                    List.of(CustomFieldValues.empty()),
                    Audience.PUBLIC))
        .hasSize(1);
    assertThat(reads[0]).as("no read when every row is empty").isEqualTo(1);
  }

  @Test
  void valuesAreValidatedAndProjectedWithTheTenantDefinitions() {
    create("web", Visibility.PUBLIC);
    create("note", Visibility.STAFF);
    CustomFieldValues saved =
        values.validate(
            CustomFieldTarget.MUNICIPALITY,
            Map.of("web", "https://naron.gal", "note", "interna"),
            CustomFieldValues.empty(),
            ValidationContext.of(Audience.STAFF));
    assertThat(saved.asMap()).containsEntry("web", new CustomFieldValue.Text("https://naron.gal"));
    assertThat(values.project(CustomFieldTarget.MUNICIPALITY, saved, Audience.PUBLIC).asMap())
        .containsOnlyKeys("web");
    assertThat(
            values
                .project(CustomFieldTarget.MUNICIPALITY, CustomFieldValues.empty(), Audience.PUBLIC)
                .isEmpty())
        .isTrue();
    assertError(
        () ->
            values.validate(
                CustomFieldTarget.MUNICIPALITY,
                Map.of("note", "x"),
                CustomFieldValues.empty(),
                new ValidationContext(Audience.PUBLIC)),
        CommonError.VALIDATION_FAILED);

    // Outro concello non ve estas definicións.
    TenantContext.runAs(
        new TenantRef(UUID.randomUUID(), "ferrol"),
        () ->
            assertError(
                () ->
                    values.validate(
                        CustomFieldTarget.MUNICIPALITY,
                        Map.of("web", "https://x.gal"),
                        CustomFieldValues.empty(),
                        ValidationContext.of(Audience.STAFF)),
                CommonError.VALIDATION_FAILED));
  }

  /** Un campo, un sitio (revisión do 2026-11-04): o sitio cambia só mentres non hai valores. */
  @Test
  void oneFieldOnePlaceAndThePlaceIsLockedOnceItHasValues() {
    assertError(
        () ->
            service.create(
                new CreateCustomFieldDefinitionCommand(
                    "phone",
                    FieldType.TEXT,
                    Definitions.spec(
                        FieldRules.NONE,
                        List.of(),
                        false,
                        new FieldUsage(CustomFieldTarget.MUNICIPALITY, true, Visibility.STAFF),
                        new FieldUsage(FACILITY, false, Visibility.PUBLIC)))),
        CustomFieldError.SINGLE_USAGE);
    CustomFieldDefinition phone =
        service.create(
            new CreateCustomFieldDefinitionCommand(
                "phone",
                FieldType.TEXT,
                Definitions.spec(
                    FieldRules.NONE,
                    List.of(),
                    false,
                    new FieldUsage(CustomFieldTarget.MUNICIPALITY, true, Visibility.STAFF))));

    // Un código núcleo do destino novo tamén está reservado.
    assertError(
        () ->
            service.create(
                new CreateCustomFieldDefinitionCommand(
                    "address",
                    FieldType.TEXT,
                    Definitions.spec(
                        FieldRules.NONE,
                        List.of(),
                        false,
                        new FieldUsage(FACILITY, false, Visibility.STAFF)))),
        CustomFieldError.CODE_RESERVED);

    DefinitionSpec inFacility =
        Definitions.spec(
            FieldRules.NONE, List.of(), false, new FieldUsage(FACILITY, false, Visibility.PUBLIC));
    repository.withValues.add(phone.id());
    assertThatThrownBy(
            () -> service.update(new UpdateCustomFieldDefinitionCommand(phone.id(), 0, inFacility)))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(CustomFieldError.TARGET_HAS_VALUES);
              assertThat(e.errorCode().status()).isEqualTo(409);
            });
    repository.withValues.clear();
    CustomFieldDefinition moved =
        service.update(new UpdateCustomFieldDefinitionCommand(phone.id(), 0, inFacility));
    assertThat(moved.target()).isEqualTo(FACILITY);
    assertThat(codes(service.listVisible(FACILITY))).containsExactly("phone");
    assertThat(codes(service.listVisible(CustomFieldTarget.MUNICIPALITY))).isEmpty();
  }

  /** A reserva non ten campos públicos; titular ou só persoal, si. */
  @Test
  void bookingFieldsAreNeverPublic() {
    assertError(
        () ->
            service.create(
                new CreateCustomFieldDefinitionCommand(
                    "level",
                    FieldType.TEXT,
                    Definitions.spec(
                        FieldRules.NONE,
                        List.of(),
                        false,
                        new FieldUsage(CustomFieldTarget.BOOKING, false, Visibility.PUBLIC)))),
        CustomFieldError.VISIBILITY_NOT_ALLOWED);
    CustomFieldDefinition level =
        service.create(
            new CreateCustomFieldDefinitionCommand(
                "level",
                FieldType.TEXT,
                Definitions.spec(
                    FieldRules.NONE,
                    List.of(),
                    false,
                    new FieldUsage(CustomFieldTarget.BOOKING, true, Visibility.OWNER))));
    assertThat(level.target()).isEqualTo(CustomFieldTarget.BOOKING);
    // Sen sesión non o ve; quen vai reservar (sen rol no concello), si: será o titular.
    assertThat(service.listVisible(CustomFieldTarget.BOOKING)).isEmpty();
    actor = Optional.of(new Actor("r", Set.of()));
    assertThat(codes(service.listVisible(CustomFieldTarget.BOOKING))).containsExactly("level");
    assertThat(service.listVisible(CustomFieldTarget.MUNICIPALITY)).isEmpty();
    actor = Optional.empty();
    // Un campo do concello público non pode ir á reserva sen deixar de ser público.
    CustomFieldDefinition web = create("web", Visibility.PUBLIC);
    assertError(
        () ->
            service.update(
                new UpdateCustomFieldDefinitionCommand(
                    web.id(),
                    0,
                    Definitions.spec(
                        FieldRules.NONE,
                        List.of(),
                        false,
                        new FieldUsage(CustomFieldTarget.BOOKING, false, Visibility.PUBLIC)))),
        CustomFieldError.VISIBILITY_NOT_ALLOWED);
  }

  /** Eliminar: só sen valores; con eles, 409 e desactívase. */
  @Test
  void aFieldIsDeletedOnlyWithoutValues() {
    CustomFieldDefinition web = create("web", Visibility.PUBLIC);
    repository.withValues.add(web.id());
    assertThatThrownBy(() -> service.delete(web.id(), 0))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(CustomFieldError.HAS_VALUES);
              assertThat(e.errorCode().status()).isEqualTo(409);
            });
    assertThat(service.inUse(List.of(web)).get(web.id()).hasValues()).isTrue();
    assertError(() -> service.delete(web.id(), 7), CommonError.STALE_VERSION);
    repository.withValues.clear();
    assertThat(service.inUse(List.of(web)).get(web.id()).hasValues()).isFalse();
    service.delete(web.id(), 0);
    assertThat(service.listAll()).isEmpty();
    assertError(() -> service.delete(web.id(), 0), CustomFieldError.DEFINITION_NOT_FOUND);
  }

  /** Unha opción quitada da lista bórrase se non ten valores; con eles, 409: desactívase. */
  @Test
  void anOptionIsRemovedOnlyWithoutValues() {
    FieldOption north = new FieldOption("north", new LocalizedText("Norte", "Norte"), false);
    FieldOption south = new FieldOption("south", new LocalizedText("Sur", "Sur"), false);
    CustomFieldDefinition zone =
        service.create(
            new CreateCustomFieldDefinitionCommand(
                "zone",
                FieldType.SELECT,
                Definitions.spec(false, FieldRules.NONE, List.of(north, south), Visibility.STAFF)));
    repository.optionsWithValues.put(zone.id(), Set.of("south"));
    assertThat(service.inUse(List.of(zone)).get(zone.id()).optionsWithValues())
        .containsExactly("south");
    assertThatThrownBy(
            () ->
                service.update(
                    new UpdateCustomFieldDefinitionCommand(
                        zone.id(),
                        0,
                        Definitions.spec(
                            false, FieldRules.NONE, List.of(north), Visibility.STAFF))))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(CustomFieldError.OPTION_HAS_VALUES);
              assertThat(e.params()).containsEntry("option", "south");
            });
    CustomFieldDefinition withoutNorth =
        service.update(
            new UpdateCustomFieldDefinitionCommand(
                zone.id(),
                0,
                Definitions.spec(
                    false,
                    FieldRules.NONE,
                    List.of(
                        south.deactivate(),
                        new FieldOption("east", new LocalizedText("Leste", "Este"), false)),
                    Visibility.STAFF)));
    assertThat(withoutNorth.spec().options())
        .extracting(FieldOption::code)
        .containsExactlyInAnyOrder("south", "east");
    assertThat(withoutNorth.hasActiveOption("south")).isFalse();
  }

  /** O almacén: substitúe, le por lote, RGPD e só entidades do concello actual. */
  @Test
  void storeReplacesLoadsAndErasesPersonalData() {
    create("web", Visibility.PUBLIC);
    service.create(
        new CreateCustomFieldDefinitionCommand(
            "dpo_phone",
            FieldType.TEXT,
            Definitions.spec(
                FieldRules.NONE,
                List.of(),
                true,
                new FieldUsage(CustomFieldTarget.MUNICIPALITY, false, Visibility.STAFF))));
    CustomFieldValues validated =
        values.validate(
            CustomFieldTarget.MUNICIPALITY,
            Map.of("web", "https://naron.gal", "dpo_phone", "981000000"),
            CustomFieldValues.empty(),
            ValidationContext.of(Audience.STAFF));
    UUID self = naron.id();
    values.replace(CustomFieldTarget.MUNICIPALITY, self, validated);
    assertThat(values.load(CustomFieldTarget.MUNICIPALITY, self)).isEqualTo(validated);
    assertThat(values.loadAll(CustomFieldTarget.MUNICIPALITY, List.of(self, UUID.randomUUID())))
        .containsOnlyKeys(self);
    assertThat(values.loadAll(CustomFieldTarget.MUNICIPALITY, List.of())).isEmpty();
    assertThat(values.personalData(CustomFieldTarget.MUNICIPALITY, self).asMap())
        .containsOnlyKeys("dpo_phone");
    values.erasePersonalData(CustomFieldTarget.MUNICIPALITY, self);
    assertThat(values.load(CustomFieldTarget.MUNICIPALITY, self).asMap()).containsOnlyKeys("web");
    values.deleteAll(CustomFieldTarget.MUNICIPALITY, self);
    assertThat(values.load(CustomFieldTarget.MUNICIPALITY, self).isEmpty()).isTrue();
    // O concello só garda valores de si mesmo (entity_id sen FK, ADR-0011).
    assertError(
        () -> values.replace(CustomFieldTarget.MUNICIPALITY, UUID.randomUUID(), validated),
        CommonError.NOT_FOUND);
  }

  CustomFieldDefinition create(String code, Visibility visibility) {
    return service.create(
        new CreateCustomFieldDefinitionCommand(
            code, FieldType.TEXT, Definitions.spec(false, FieldRules.NONE, List.of(), visibility)));
  }

  static List<String> codes(List<CustomFieldDefinition> defs) {
    return defs.stream().map(CustomFieldDefinition::code).sorted().toList();
  }

  static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }
}
