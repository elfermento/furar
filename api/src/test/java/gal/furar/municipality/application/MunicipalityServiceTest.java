// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import gal.furar.customfields.CustomFieldProjector;
import gal.furar.customfields.CustomFieldValidator;
import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.customfields.ValidationContext;
import gal.furar.municipality.MunicipalityCreated;
import gal.furar.municipality.application.port.in.CreateMunicipalityCommand;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.in.ThemeData;
import gal.furar.municipality.application.port.in.UpdateMunicipalityCommand;
import gal.furar.municipality.application.service.MunicipalityService;
import gal.furar.municipality.domain.MunicipalityError;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LanguagePolicy;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.tenancy.TenancyError;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso do concello con dobres en memoria (sen Spring nin base). */
class MunicipalityServiceTest {

  final InMemoryMunicipalityRepository repository = new InMemoryMunicipalityRepository();
  final List<Object> events = new ArrayList<>();
  final List<AuditEvent> audit = new ArrayList<>();
  final List<ValidationContext> validations = new ArrayList<>();
  final List<Audience> projections = new ArrayList<>();
  Optional<Actor> actor = Optional.empty();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);

  final CustomFieldValidator validator =
      new CustomFieldValidator() {
        @Override
        public CustomFieldValues validate(
            CustomFieldTarget target,
            Map<String, ?> raw,
            CustomFieldValues previous,
            ValidationContext context) {
          assertThat(target).isEqualTo(CustomFieldTarget.MUNICIPALITY);
          validations.add(context);
          return CustomFieldValues.fromStorage(raw);
        }

        @Override
        public CustomFieldValues restore(CustomFieldTarget target, Map<String, ?> raw) {
          return CustomFieldValues.fromStorage(raw);
        }
      };
  final CustomFieldProjector projector =
      new CustomFieldProjector() {
        @Override
        public CustomFieldValues project(
            CustomFieldTarget target, CustomFieldValues stored, Audience viewer) {
          projections.add(viewer);
          return viewer == Audience.STAFF ? stored : CustomFieldValues.empty();
        }

        @Override
        public List<CustomFieldValues> projectAll(
            CustomFieldTarget target, List<CustomFieldValues> stored, Audience viewer) {
          return stored.stream().map(v -> project(target, v, viewer)).toList();
        }
      };

  /** Almacén de valores en memoria: o concello garda os seus propios (ADR-0011). */
  final Map<UUID, CustomFieldValues> stored = new HashMap<>();

  final CustomFieldValueStore store =
      new CustomFieldValueStore() {
        @Override
        public void replace(CustomFieldTarget target, UUID entityId, CustomFieldValues values) {
          stored.put(entityId, values);
        }

        @Override
        public CustomFieldValues load(CustomFieldTarget target, UUID entityId) {
          return stored.getOrDefault(entityId, CustomFieldValues.empty());
        }

        @Override
        public Map<UUID, CustomFieldValues> loadAll(
            CustomFieldTarget target, Collection<UUID> entityIds) {
          Map<UUID, CustomFieldValues> result = new HashMap<>();
          entityIds.forEach(id -> result.put(id, load(target, id)));
          return result;
        }

        @Override
        public CustomFieldValues personalData(CustomFieldTarget target, UUID entityId) {
          return CustomFieldValues.empty();
        }

        @Override
        public void erasePersonalData(CustomFieldTarget target, UUID entityId) {}

        @Override
        public void deleteAll(CustomFieldTarget target, UUID entityId) {
          stored.remove(entityId);
        }
      };
  final CurrentActor currentActor = () -> actor;

  MunicipalityService service;

  @BeforeEach
  void setUp() {
    service =
        new MunicipalityService(
            repository,
            validator,
            projector,
            store,
            currentActor,
            events::add,
            clock,
            LanguagePolicy.GALICIA,
            audit::add);
  }

  @Test
  void createsWithDefaultThemeAndPublishesEvent() {
    MunicipalityView view = service.create(command("naron", null));
    assertThat(view.municipality().version()).isZero();
    assertThat(view.municipality().theme().primary().value()).isEqualTo("#0b4f8a");
    assertThat(view.municipality().createdAt()).isEqualTo(clock.instant());
    assertThat(events)
        .containsExactly(new MunicipalityCreated(view.municipality().id().value(), "naron"));
  }

  @Test
  void createsWithCustomTheme() {
    MunicipalityView view =
        service.create(
            command(
                "ferrol", new ThemeData("#7a1f3d", "#ffffff", "#1f6b4a", "#fdfcf9", "#202020")));
    assertThat(view.municipality().theme().primary().value()).isEqualTo("#7a1f3d");
  }

  @Test
  void rejectsDuplicatedOrInvalidInput() {
    service.create(command("naron", null));
    assertError(() -> service.create(command("naron", null)), MunicipalityError.SLUG_TAKEN);
    assertError(() -> service.create(command("Bad Slug", null)), MunicipalityError.SLUG_INVALID);
    assertError(
        () ->
            service.create(
                new CreateMunicipalityCommand("lugo", "Lugo", List.of("en"), "gl", null, null)),
        MunicipalityError.LANGUAGES_INVALID);
    assertError(
        () ->
            service.create(
                new CreateMunicipalityCommand("lugo", "Lugo", List.of("gl"), "fr", null, null)),
        MunicipalityError.LANGUAGES_INVALID);
    assertError(
        () -> service.create(new CreateMunicipalityCommand("lugo", "Lugo", null, "gl", null, null)),
        MunicipalityError.LANGUAGES_INVALID);
  }

  /** AP-10: nunha instalación galega o galego é obrigatorio; outra política, outro idioma. */
  @Test
  void requiredLanguageComesFromTheInstallationPolicy() {
    assertError(
        () ->
            service.create(
                new CreateMunicipalityCommand("lugo", "Lugo", List.of("es"), "es", null, null)),
        MunicipalityError.LANGUAGE_REQUIRED);
    MunicipalityService anywhere =
        new MunicipalityService(
            repository,
            validator,
            projector,
            store,
            currentActor,
            events::add,
            clock,
            new LanguagePolicy(Set.of()),
            audit::add);
    assertThat(
            anywhere
                .create(
                    new CreateMunicipalityCommand("lugo", "Lugo", List.of("es"), "es", null, null))
                .municipality()
                .languages())
        .containsExactly(Language.ES);
  }

  @Test
  void getCurrentProjectsForTheAudience() {
    MunicipalityView created = service.create(command("naron", null));
    TenantRef naron = new TenantRef(created.municipality().id().value(), "naron");
    TenantContext.runAs(naron, () -> service.getCurrent());
    actor = Optional.of(new Actor("s", Set.of(new RoleGrant("naron", Role.STAFF))));
    TenantContext.runAs(naron, () -> service.getCurrent());
    actor = Optional.of(new Actor("r", Set.of(new RoleGrant("naron", Role.RESIDENT))));
    TenantContext.runAs(naron, () -> service.getCurrent());
    // SEC-03: ter sesión sen rol no concello (ou cun rol doutro) non fai titular.
    actor = Optional.of(new Actor("x", Set.of()));
    TenantContext.runAs(naron, () -> service.getCurrent());
    actor = Optional.of(new Actor("f", Set.of(new RoleGrant("ferrol", Role.RESIDENT))));
    TenantContext.runAs(naron, () -> service.getCurrent());
    assertThat(projections)
        .containsExactly(
            Audience.PUBLIC, Audience.STAFF, Audience.OWNER, Audience.PUBLIC, Audience.PUBLIC);
  }

  @Test
  void getCurrentNeedsAnExistingTenant() {
    assertError(
        () ->
            TenantContext.runAs(
                new TenantRef(java.util.UUID.randomUUID(), "x"), () -> service.getCurrent()),
        TenancyError.MUNICIPALITY_NOT_FOUND);
  }

  @Test
  void updateValidatesCustomFieldsAsStaffAndBumpsVersion() {
    MunicipalityView created = service.create(command("naron", null));
    TenantRef naron = new TenantRef(created.municipality().id().value(), "naron");
    actor = Optional.of(new Actor("m", Set.of(new RoleGrant("naron", Role.MANAGER))));
    MunicipalityView updated =
        TenantContext.runAs(
            naron, () -> service.update(update(0, Map.of("web", "https://naron.gal"))));
    assertThat(updated.municipality().version()).isEqualTo(1);
    assertThat(updated.municipality().name()).isEqualTo("Narón");
    assertThat(updated.visibleCustomFields().get("web"))
        .contains(new CustomFieldValue.Text("https://naron.gal"));
    assertThat(validations).extracting(ValidationContext::writer).containsExactly(Audience.STAFF);
    // Os valores van ao almacén de customfields, co concello como entidade.
    assertThat(stored.get(naron.id()).get("web"))
        .contains(new CustomFieldValue.Text("https://naron.gal"));

    assertError(
        () -> TenantContext.runAs(naron, () -> service.update(update(0, Map.of()))),
        CommonError.STALE_VERSION);
    MunicipalityView again = TenantContext.runAs(naron, () -> service.update(update(1, null)));
    assertThat(again.municipality().version()).isEqualTo(2);
  }

  /** ADR-0020: dar de alta e cambiar o concello quedan no rexistro, tamén cando fallan. */
  @Test
  void createAndUpdateAreAudited() {
    MunicipalityView created = service.create(command("naron", null));
    assertError(() -> service.create(command("naron", null)), MunicipalityError.SLUG_TAKEN);
    TenantRef naron = new TenantRef(created.municipality().id().value(), "naron");
    actor = Optional.of(new Actor("m", Set.of(new RoleGrant("naron", Role.MANAGER))));
    TenantContext.runAs(naron, () -> service.update(update(0, null)));
    assertError(
        () -> TenantContext.runAs(naron, () -> service.update(update(0, null))),
        CommonError.STALE_VERSION);
    assertThat(audit)
        .extracting(AuditEvent::action, AuditEvent::outcome, AuditEvent::municipalitySlug)
        .containsExactly(
            tuple("municipality.create", Outcome.SUCCESS, "naron"),
            tuple("municipality.create", Outcome.FAILURE, "naron"),
            tuple("municipality.update", Outcome.SUCCESS, "naron"),
            tuple("municipality.update", Outcome.FAILURE, "naron"));
    assertThat(audit.get(0).municipalityId()).isEqualTo(naron.id());
    assertThat(audit.get(1).municipalityId()).isNull();
    assertThat(audit.get(1).details()).containsEntry("code", "municipality.slug-taken");
    assertThat(audit.get(3).details()).containsEntry("code", "common.stale-version");
  }

  @Test
  void listValidatesPaging() {
    service.create(command("naron", null));
    service.create(command("ferrol", null));
    assertThat(service.list(0, 1).items()).hasSize(1);
    assertThat(service.list(0, 10).totalItems()).isEqualTo(2);
    assertError(() -> service.list(-1, 10), CommonError.INVALID_PARAMETER);
    assertError(() -> service.list(0, 0), CommonError.INVALID_PARAMETER);
    assertError(() -> service.list(0, 101), CommonError.INVALID_PARAMETER);
  }

  static CreateMunicipalityCommand command(String slug, ThemeData theme) {
    return new CreateMunicipalityCommand(
        slug, "Concello " + slug, List.of("gl", "es"), "gl", theme, null);
  }

  static UpdateMunicipalityCommand update(long version, Map<String, Object> customFields) {
    return new UpdateMunicipalityCommand(
        version,
        "Narón",
        List.of(Language.GL.code(), Language.ES.code()),
        "gl",
        new ThemeData("#0b4f8a", "#ffffff", "#b3541e", "#ffffff", "#1a1a1a"),
        customFields,
        null);
  }

  static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }
}
