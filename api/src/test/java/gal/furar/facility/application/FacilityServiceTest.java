// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingDecision.Action;
import gal.furar.facility.BookingDecision.Channel;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityCreated;
import gal.furar.facility.ProgrammableBookingImpact;
import gal.furar.facility.application.port.in.AddressInput;
import gal.furar.facility.application.port.in.AffectedBookingsException;
import gal.furar.facility.application.port.in.ChangeFacilityStatusCommand;
import gal.furar.facility.application.port.in.CreateFacilityCommand;
import gal.furar.facility.application.port.in.DeleteFacilityCommand;
import gal.furar.facility.application.port.in.DescriptionInput;
import gal.furar.facility.application.port.in.DetailsInput;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.PublishFacilityCommand;
import gal.furar.facility.application.port.in.RenameFacilityCommand;
import gal.furar.facility.application.port.in.SportRefInput;
import gal.furar.facility.application.port.in.UpdateFacilityAddressCommand;
import gal.furar.facility.application.port.in.UpdateFacilityAmenitiesCommand;
import gal.furar.facility.application.port.in.UpdateFacilityDescriptionCommand;
import gal.furar.facility.application.port.in.UpdateFacilityDetailsCommand;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.service.FacilityService;
import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.FacilityDescription;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.Requirement;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso con repositorio en memoria e un dobre de BookingImpact: sen Spring nin base. */
class FacilityServiceTest {

  final InMemoryFacilityRepository repository = new InMemoryFacilityRepository();
  final InMemorySportRepository sports = new InMemorySportRepository();
  final ProgrammableBookingImpact impact = new ProgrammableBookingImpact();
  final List<Object> events = new ArrayList<>();
  final List<AuditEvent> audit = new ArrayList<>();
  final InMemoryFacilityCustomFields customFields = new InMemoryFacilityCustomFields();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
  final FacilityService service =
      new FacilityService(
          repository,
          repository,
          repository,
          impact,
          List.of(),
          sports,
          new InMemoryImages().galleries,
          customFields,
          audit::add,
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

  static AddressInput address() {
    return new AddressInput(
        "Rúa do Alto",
        "12",
        "15570",
        "Narón",
        "15054",
        new BigDecimal("43.5167"),
        new BigDecimal("-8.1833"));
  }

  static CreateFacilityCommand create(String name) {
    return new CreateFacilityCommand(name, address());
  }

  static AffectedBooking booking(UUID id) {
    return new AffectedBooking(
        id, "Uxía Pérez", true, LocalDate.of(2026, 11, 3), 480, 570, "Fútbol 11");
  }

  static void assertCode(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  @Test
  void createStoresActiveInDraftPublishesAndAudits() {
    FacilityView created = service.create(create("Campo de fútbol 1 de O Alto"));
    assertThat(created.value().status()).isEqualTo(FacilityStatus.ACTIVE);
    assertThat(created.missing())
        .containsExactlyInAnyOrder(Requirement.SPORTS, Requirement.USES, Requirement.SCHEDULE);
    assertThat(service.get(created.value().id()).value().name())
        .isEqualTo("Campo de fútbol 1 de O Alto");
    assertThat(events)
        .containsExactly(new FacilityCreated(naron.id(), created.value().id().value()));
    assertThat(audit)
        .singleElement()
        .satisfies(e -> assertThat(e.action()).isEqualTo("facility.create"));
    assertThat(audit.get(0).outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
  }

  @Test
  void createWithoutAddressIsAnUnpublishedDraft() {
    FacilityView created = service.create(new CreateFacilityCommand("Sen enderezo", null));
    assertThat(created.value().address()).isNull();
    assertThat(created.value().published()).isFalse();
    assertThat(audit)
        .singleElement()
        .satisfies(e -> assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS));
  }

  @Test
  void publishingNeedsTheAddressAndNothingMissing() {
    FacilityId bare = service.create(new CreateFacilityCommand("Sen enderezo", null)).value().id();
    assertCode(
        () -> service.publish(new PublishFacilityCommand(bare, 0)), FacilityError.NOT_PUBLISHABLE);
    FacilityId withAddress = service.create(create("Sen usos")).value().id();
    assertCode(
        () -> service.publish(new PublishFacilityCommand(withAddress, 0)),
        FacilityError.NOT_PUBLISHABLE);
    assertThat(audit.get(audit.size() - 1).action()).isEqualTo("facility.publish");
    assertThat(audit.get(audit.size() - 1).outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
  }

  @Test
  void publishingWithEverythingMakesItPublishedOnce() {
    RequirementProbe[] all =
        Arrays.stream(Requirement.values())
            .map(
                r ->
                    (RequirementProbe)
                        new RequirementProbe() {
                          @Override
                          public Requirement requirement() {
                            return r;
                          }

                          @Override
                          public Set<UUID> satisfiedBy(Collection<UUID> ids) {
                            return Set.copyOf(ids);
                          }
                        })
            .toArray(RequirementProbe[]::new);
    FacilityService complete =
        new FacilityService(
            repository,
            repository,
            repository,
            impact,
            List.of(all),
            sports,
            new InMemoryImages().galleries,
            customFields,
            audit::add,
            events::add,
            clock);
    FacilityId id = complete.create(create("Completa")).value().id();
    assertCode(
        () -> complete.publish(new PublishFacilityCommand(id, 7)), CommonError.STALE_VERSION);
    FacilityView published = complete.publish(new PublishFacilityCommand(id, 0));
    assertThat(published.value().published()).isTrue();
    long version = published.value().version();
    assertThat(complete.publish(new PublishFacilityCommand(id, version)).value().version())
        .isEqualTo(version);
    assertThat(audit.get(audit.size() - 1).action()).isEqualTo("facility.publish");
  }

  @Test
  void namesAreUniqueIgnoringCase() {
    service.create(create("Pabellón"));
    assertCode(() -> service.create(create("PABELLÓN")), FacilityError.NAME_TAKEN);
  }

  @Test
  void probesTakeRequirementsOutOfTheDraftInBatch() {
    List<Collection<UUID>> asked = new ArrayList<>();
    RequirementProbe probe =
        new RequirementProbe() {
          @Override
          public Requirement requirement() {
            return Requirement.SPORTS;
          }

          @Override
          public Set<UUID> satisfiedBy(Collection<UUID> ids) {
            asked.add(List.copyOf(ids));
            return ids.stream().limit(1).collect(Collectors.toSet());
          }
        };
    FacilityService withProbe =
        new FacilityService(
            repository,
            repository,
            repository,
            impact,
            List.of(probe),
            sports,
            new InMemoryImages().galleries,
            customFields,
            audit::add,
            events::add,
            clock);
    withProbe.create(create("A"));
    withProbe.create(create("B"));
    asked.clear();
    var page = withProbe.list(0, 20, Language.GL).items();
    assertThat(asked).hasSize(1);
    assertThat(page.get(0).missing()).containsExactly(Requirement.USES, Requirement.SCHEDULE);
    assertThat(page.get(1).missing())
        .containsExactly(Requirement.SPORTS, Requirement.USES, Requirement.SCHEDULE);
  }

  @Test
  void renameNeedsTheCurrentVersion() {
    FacilityId id = service.create(create("Vello")).value().id();
    FacilityView renamed = service.rename(new RenameFacilityCommand(id, 0, "Novo"));
    assertThat(renamed.value().name()).isEqualTo("Novo");
    assertThat(renamed.value().version()).isEqualTo(1);
    assertCode(
        () -> service.rename(new RenameFacilityCommand(id, 0, "Outro")), CommonError.STALE_VERSION);
  }

  @Test
  void statusChangeTakesTheLockFirst() {
    FacilityId id = service.create(create("Pista de pádel 1")).value().id();
    repository.calls.clear();
    FacilityView closed =
        service.changeStatus(
            new ChangeFacilityStatusCommand(id, 0, FacilityStatus.TEMPORARILY_CLOSED, List.of()));
    assertThat(closed.value().status()).isEqualTo(FacilityStatus.TEMPORARILY_CLOSED);
    assertThat(repository.calls).containsExactly("lock", "update");
  }

  @Test
  void unknownFacilityIsNotFoundOnStatusChange() {
    assertCode(
        () ->
            service.changeStatus(
                new ChangeFacilityStatusCommand(
                    FacilityId.newId(), 0, FacilityStatus.WITHDRAWN, List.of())),
        FacilityError.NOT_FOUND);
  }

  @Test
  void sameStatusIsANoOp() {
    FacilityId id = service.create(create("Pista")).value().id();
    FacilityView same =
        service.changeStatus(new ChangeFacilityStatusCommand(id, 0, FacilityStatus.ACTIVE, null));
    assertThat(same.value().version()).isZero();
  }

  @Test
  void affectedBookingsWithoutDecisionsAre409WithTheList() {
    FacilityId id = service.create(create("Pabellón de Piñeiros")).value().id();
    UUID b1 = UUID.randomUUID();
    impact.affect(id.value(), List.of(booking(b1)));
    assertThatThrownBy(
            () ->
                service.changeStatus(
                    new ChangeFacilityStatusCommand(id, 0, FacilityStatus.WITHDRAWN, List.of())))
        .isInstanceOfSatisfying(
            AffectedBookingsException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(FacilityError.AFFECTED_BOOKINGS);
              assertThat(e.affected()).extracting(AffectedBooking::bookingId).containsExactly(b1);
            });
    assertThat(service.get(id).value().status()).isEqualTo(FacilityStatus.ACTIVE);
    assertThat(impact.appliedTo(id.value())).isEmpty();
  }

  @Test
  void decisionsForEveryAffectedBookingApplyTheChange() {
    FacilityId id = service.create(create("Pabellón")).value().id();
    UUID b1 = UUID.randomUUID();
    UUID b2 = UUID.randomUUID();
    impact.affect(id.value(), List.of(booking(b1), booking(b2)));
    List<BookingDecision> decisions =
        List.of(
            new BookingDecision(b1, Action.CANCEL, Set.of(Channel.EMAIL)),
            new BookingDecision(b2, Action.KEEP, Set.of()));
    FacilityView closed =
        service.changeStatus(
            new ChangeFacilityStatusCommand(id, 0, FacilityStatus.TEMPORARILY_CLOSED, decisions));
    assertThat(closed.value().status()).isEqualTo(FacilityStatus.TEMPORARILY_CLOSED);
    assertThat(impact.appliedTo(id.value()))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.change()).isEqualTo(new FacilityChange.Closure(id.value()));
              assertThat(a.decisions()).isEqualTo(decisions);
            });
  }

  @Test
  void cancellingWithoutChannelsOrKeepingWithChannelsIs422() {
    FacilityId id = service.create(create("Pabellón")).value().id();
    UUID b1 = UUID.randomUUID();
    impact.affect(id.value(), List.of(booking(b1)));
    for (BookingDecision wrong :
        List.of(
            new BookingDecision(b1, Action.CANCEL, Set.of()),
            new BookingDecision(b1, Action.KEEP, Set.of(Channel.EMAIL)))) {
      assertCode(
          () ->
              service.changeStatus(
                  new ChangeFacilityStatusCommand(id, 0, FacilityStatus.WITHDRAWN, List.of(wrong))),
          FacilityError.DECISION_INVALID);
    }
  }

  @Test
  void decisionsForOtherBookingsAre409AgainWithTheNewList() {
    FacilityId id = service.create(create("Pabellón")).value().id();
    UUID old = UUID.randomUUID();
    UUID now = UUID.randomUUID();
    impact.affect(id.value(), List.of(booking(now)));
    assertThatThrownBy(
            () ->
                service.changeStatus(
                    new ChangeFacilityStatusCommand(
                        id,
                        0,
                        FacilityStatus.WITHDRAWN,
                        List.of(new BookingDecision(old, Action.KEEP, Set.of())))))
        .isInstanceOfSatisfying(
            AffectedBookingsException.class,
            e ->
                assertThat(e.affected())
                    .extracting(AffectedBooking::bookingId)
                    .containsExactly(now));
  }

  @Test
  void reactivatingDoesNotAskForAffectedBookings() {
    FacilityId id = service.create(create("Pabellón")).value().id();
    service.changeStatus(
        new ChangeFacilityStatusCommand(id, 0, FacilityStatus.TEMPORARILY_CLOSED, List.of()));
    impact.affect(id.value(), List.of(booking(UUID.randomUUID())));
    FacilityView active =
        service.changeStatus(new ChangeFacilityStatusCommand(id, 1, FacilityStatus.ACTIVE, null));
    assertThat(active.value().status()).isEqualTo(FacilityStatus.ACTIVE);
  }

  @Test
  void unknownIdIsNotFound() {
    assertCode(() -> service.get(FacilityId.newId()), FacilityError.NOT_FOUND);
  }

  @Test
  void listIsAlphabeticalAndRejectsBadSizes() {
    service.create(create("Pista de pádel 2"));
    service.create(create("Ágora"));
    service.create(create("Campo"));
    assertThat(service.list(0, 20, Language.GL).items())
        .extracting(v -> v.value().name())
        .containsExactly("Ágora", "Campo", "Pista de pádel 2");
    assertThat(service.list(0, 1, Language.GL).totalItems()).isEqualTo(3);
    assertCode(
        () -> service.list(0, FacilityService.MAX_PAGE_SIZE + 1, Language.GL),
        CommonError.INVALID_PARAMETER);
  }

  @Test
  void anotherMunicipalitySeesNothing() {
    FacilityView created = service.create(create("Só en Narón"));
    try (TenantContext.Scope ignored =
        TenantContext.open(new TenantRef(UUID.randomUUID(), "ferrol"))) {
      assertThat(service.list(0, 20, Language.GL).items()).isEmpty();
      assertThatThrownBy(() -> service.get(created.value().id()))
          .isInstanceOf(DomainException.class);
    }
  }

  @Test
  void sectionsReplaceTheirPartAndNeedTheCurrentVersion() {
    FacilityView created = service.create(create("Pavillón"));
    FacilityId id = created.value().id();
    FacilityView details =
        service.updateDetails(
            new UpdateFacilityDetailsCommand(
                id,
                0,
                new DetailsInput(
                    List.of(new SportRefInput("handball", null)),
                    FacilityType.SPORTS_HALL,
                    " 15-054-0012 ",
                    "981 38 21 00",
                    "deportes@naron.es",
                    true,
                    false,
                    true)));
    assertThat(details.value().version()).isEqualTo(1);
    assertThat(details.value().details().ridegaCode()).isEqualTo("15-054-0012");
    assertThat(details.sports()).extracting(s -> s.label().es()).containsExactly("Balonmano");

    FacilityView described =
        service.updateDescription(
            new UpdateFacilityDescriptionCommand(id, 1, new DescriptionInput("Ola", "Hola")));
    assertThat(described.value().description()).isEqualTo(new FacilityDescription("Ola", "Hola"));
    assertThat(described.value().details().sports()).hasSize(1);

    FacilityView equipped =
        service.updateAmenities(
            new UpdateFacilityAmenitiesCommand(
                id, 2, Set.of(Amenity.SHOWERS, Amenity.CHANGING_ROOMS)));
    assertThat(equipped.value().amenities())
        .containsExactly(Amenity.CHANGING_ROOMS, Amenity.SHOWERS);

    FacilityView moved = service.updateAddress(new UpdateFacilityAddressCommand(id, 3, address()));
    assertThat(moved.value().version()).isEqualTo(4);
    assertThat(moved.value().description()).isNotNull();

    assertCode(
        () ->
            service.updateDescription(
                new UpdateFacilityDescriptionCommand(id, 0, new DescriptionInput("", ""))),
        CommonError.STALE_VERSION);
    FacilityView cleared =
        service.updateDescription(
            new UpdateFacilityDescriptionCommand(id, 4, new DescriptionInput(" ", null)));
    assertThat(cleared.value().description()).isNull();
    assertThat(audit)
        .extracting(AuditEvent::action)
        .contains(
            "facility.details", "facility.description", "facility.amenities", "facility.address");
  }

  @Test
  void detailsPassTheCustomFieldsToTheValidatorAndReplaceThemOnlyWhenTheCardIsValid() {
    FacilityId id = service.create(create("Pista de atletismo")).value().id();
    DetailsInput card = new DetailsInput(List.of(), null, null, null, null, true, false, null);

    FacilityView saved =
        service.updateDetails(new UpdateFacilityDetailsCommand(id, 0, card, Map.of("lanes", 8)));
    assertThat(customFields.lastRaw).isEqualTo(Map.of("lanes", 8));
    assertThat(customFields.stored).containsKey(id);
    assertThat(saved.value().version()).isEqualTo(1);

    // Rexeitados polo validador: nin a tarxeta nin os valores cambian.
    customFields.stored.clear();
    customFields.rejection = new DomainException(CommonError.VALIDATION_FAILED);
    assertCode(
        () ->
            service.updateDetails(
                new UpdateFacilityDetailsCommand(id, 1, card, Map.of("lanes", "x"))),
        CommonError.VALIDATION_FAILED);
    assertThat(customFields.stored).isEmpty();
    assertThat(service.get(id).value().version()).isEqualTo(1);
    assertThat(audit)
        .filteredOn(e -> e.action().equals("facility.details"))
        .extracting(AuditEvent::outcome)
        .containsExactly(AuditEvent.Outcome.SUCCESS, AuditEvent.Outcome.FAILURE);
  }

  @Test
  void detailsRejectBookingOnlineWithFreeSlotsAndHalfSportRefs() {
    FacilityId id = service.create(create("Piscina")).value().id();
    assertCode(
        () ->
            service.updateDetails(
                new UpdateFacilityDetailsCommand(
                    id, 0, new DetailsInput(List.of(), null, null, null, null, true, true, null))),
        FacilityError.BOOKING_MODE_INVALID);
    assertCode(
        () ->
            service.updateDetails(
                new UpdateFacilityDetailsCommand(
                    id,
                    0,
                    new DetailsInput(
                        List.of(new SportRefInput("padel", UUID.randomUUID())),
                        null,
                        null,
                        null,
                        null,
                        true,
                        false,
                        null))),
        FacilityError.DETAILS_INVALID);
    assertCode(
        () ->
            service.updateDescription(
                new UpdateFacilityDescriptionCommand(id, 0, new DescriptionInput("Só gl", null))),
        FacilityError.DESCRIPTION_INVALID);
  }

  @Test
  void deleteRemovesTheFacilityAndItsCustomValuesUnderTheLock() {
    FacilityView created = service.create(create("Pista de pádel 3"));
    FacilityId id = created.value().id();
    customFields.stored.put(id, CustomFieldValues.empty());
    repository.calls.clear();
    audit.clear();

    service.delete(new DeleteFacilityCommand(id, created.value().version()));

    assertThat(repository.findById(id)).isEmpty();
    assertThat(customFields.stored).doesNotContainKey(id);
    assertThat(repository.calls).containsExactly("lock", "remove");
    assertThat(audit)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo("facility.delete");
              assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
            });
  }

  @Test
  void deleteWithAnOldVersionIsStale() {
    FacilityView created = service.create(create("Pista de pádel 4"));
    assertCode(
        () -> service.delete(new DeleteFacilityCommand(created.value().id(), 99)),
        CommonError.STALE_VERSION);
    assertThat(repository.findById(created.value().id())).isPresent();
  }

  @Test
  void aFacilityThatHadBookingsIsNotDeletedNorDeletable() {
    FacilityView created = service.create(create("Pavillón con historial"));
    impact.book(created.value().id().value());
    assertThat(service.get(created.value().id()).booked()).isTrue();
    assertCode(
        () ->
            service.delete(
                new DeleteFacilityCommand(created.value().id(), created.value().version())),
        FacilityError.HAS_BOOKINGS);
    assertThat(repository.findById(created.value().id())).isPresent();
  }

  @Test
  void deletingAnUnknownFacilityIsNotFound() {
    assertCode(
        () -> service.delete(new DeleteFacilityCommand(new FacilityId(UUID.randomUUID()), 0)),
        FacilityError.NOT_FOUND);
  }
}
