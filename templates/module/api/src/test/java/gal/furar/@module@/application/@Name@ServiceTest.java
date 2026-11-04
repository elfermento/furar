// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.customfields.CustomFieldProjector; // @cf
import gal.furar.customfields.CustomFieldValidator; // @cf
import gal.furar.customfields.ValidationContext; // @cf
import gal.furar.@module@.@Name@Created;
import gal.furar.@module@.application.port.in.Create@Name@Command;
import gal.furar.@module@.application.port.in.Restore@Name@Command;
import gal.furar.@module@.application.port.in.Update@Name@Command;
import gal.furar.@module@.application.port.in.@Name@View;
import gal.furar.@module@.application.service.@Name@Service;
import gal.furar.@module@.domain.@Name@Error;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.domain.Audience; // @cf
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget; // @cf
import gal.furar.shared.domain.CustomFieldValues; // @cf
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.CurrentActor; // @cf
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // @cf
import java.util.Optional; // @cf
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso con repositorio en memoria: sen Spring nin base de datos. */
class @Name@ServiceTest {

  final InMemory@Name@Repository repository = new InMemory@Name@Repository();
  final List<Object> events = new ArrayList<>();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
  // @cf{
  final List<Map<String, ?>> validated = new ArrayList<>();
  final CustomFieldValidator validator =
      new CustomFieldValidator() {
        @Override
        public CustomFieldValues validate(
            CustomFieldTarget target,
            Map<String, ?> raw,
            CustomFieldValues previous,
            ValidationContext context) {
          assertThat(target).isEqualTo(new CustomFieldTarget("@TARGET@"));
          validated.add(raw);
          return CustomFieldValues.fromStorage(raw);
        }

        @Override
        public CustomFieldValues restore(CustomFieldTarget target, Map<String, ?> raw) {
          validated.add(raw);
          return CustomFieldValues.fromStorage(raw);
        }
      };
  /** Dobre: un anónimo non ve ningún campo (o real decide pola visibilidade de cada un). */
  final CustomFieldProjector projector =
      new CustomFieldProjector() {
        @Override
        public CustomFieldValues project(
            CustomFieldTarget target, CustomFieldValues stored, Audience viewer) {
          return viewer == Audience.STAFF ? stored : CustomFieldValues.empty();
        }

        @Override
        public List<CustomFieldValues> projectAll(
            CustomFieldTarget target, List<CustomFieldValues> stored, Audience viewer) {
          return stored.stream().map(v -> project(target, v, viewer)).toList();
        }
      };
  final CurrentActor anonymous = Optional::empty;
  // @cf}
  final @Name@Service service =
      new @Name@Service(
          repository,
          validator, // @cf
          projector, // @cf
          anonymous, // @cf
          events::add,
          clock);
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

  /** Portabilidade (ADR-0018): a importación conserva id e datas, e a exportación devólveos. */
  @Test
  void importKeepsIdsAndDatesForTheExport() {
    UUID id = UUID.randomUUID();
    Instant created = Instant.parse("2025-01-02T03:04:05Z");
    service.importAll(
        List.of(
            new Restore@Name@Command(
                id,
                "Importado",
                Map.of(), // @cf
                created,
                created)));
    var exported = service.exportAll();
    assertThat(exported).hasSize(1);
    assertThat(exported.get(0).id()).isEqualTo(new @Name@Id(id));
    assertThat(exported.get(0).createdAt()).isEqualTo(created);
    assertThat(exported.get(0).name()).isEqualTo("Importado");
  }

  static Create@Name@Command create(String name) {
    return new Create@Name@Command(
        Map.of("code", "A1"), // @cf
        name);
  }

  @Test
  void createStoresAndPublishesAnEventWithTheMunicipality() {
    @Name@View created = service.create(create("Primeiro"));
    assertThat(service.get(created.value().id()).value().name()).isEqualTo("Primeiro");
    assertThat(events)
        .containsExactly(new @Name@Created(naron.id(), created.value().id().value()));
  }

  // @cf{
  @Test
  void customFieldsAreValidatedInTheUseCaseAndProjectedOnRead() {
    @Name@View created = service.create(create("Con campos"));
    assertThat(validated).containsExactly(Map.of("code", "A1"));
    assertThat(created.value().customFields().toJson()).containsEntry("code", "A1");
    assertThat(service.get(created.value().id()).visibleCustomFields().toJson()).isEmpty();
  }

  // @cf}
  @Test
  void updateNeedsTheCurrentVersion() {
    @Name@View created = service.create(create("Vello"));
    @Name@Id id = created.value().id();
    @Name@View updated =
        service.update(
            new Update@Name@Command(
                id,
                0,
                Map.of(), // @cf
                "Novo"));
    assertThat(updated.value().name()).isEqualTo("Novo");
    assertThat(updated.value().version()).isEqualTo(1);
    assertThatThrownBy(
            () ->
                service.update(
                    new Update@Name@Command(
                        id,
                        0,
                        Map.of(), // @cf
                        "Outro")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonError.STALE_VERSION));
  }

  @Test
  void unknownIdIsNotFound() {
    assertThatThrownBy(() -> service.get(@Name@Id.newId()))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(@Name@Error.NOT_FOUND));
  }

  @Test
  void listPagesAndRejectsBadSizes() {
    service.create(create("A"));
    service.create(create("B"));
    assertThat(service.list(0, 1).items()).hasSize(1);
    assertThat(service.list(0, 1).totalItems()).isEqualTo(2);
    assertThatThrownBy(() -> service.list(0, @Name@Service.MAX_PAGE_SIZE + 1))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CommonError.INVALID_PARAMETER));
  }

  @Test
  void anotherMunicipalitySeesNothing() {
    @Name@View created = service.create(create("Só en Narón"));
    try (TenantContext.Scope ignored =
        TenantContext.open(new TenantRef(UUID.randomUUID(), "ferrol"))) {
      assertThat(service.list(0, 20).items()).isEmpty();
      assertThatThrownBy(() -> service.get(created.value().id()))
          .isInstanceOf(DomainException.class);
    }
  }
}
