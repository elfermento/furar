// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.adapter.in.web.dto.AddressRequest;
import gal.furar.facility.adapter.in.web.dto.AddressResponse;
import gal.furar.facility.adapter.in.web.dto.AffectedBookingResponse;
import gal.furar.facility.adapter.in.web.dto.AmenityValue;
import gal.furar.facility.adapter.in.web.dto.ArrangeImagesRequest;
import gal.furar.facility.adapter.in.web.dto.BookingDecisionRequest;
import gal.furar.facility.adapter.in.web.dto.ChangeFacilityStatusRequest;
import gal.furar.facility.adapter.in.web.dto.CreateFacilityRequest;
import gal.furar.facility.adapter.in.web.dto.CreateSportRequest;
import gal.furar.facility.adapter.in.web.dto.FacilityDescriptionResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityDetailsResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityNoteResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityPaymentNotesResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityStatusValue;
import gal.furar.facility.adapter.in.web.dto.FacilitySummaryResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityTypeValue;
import gal.furar.facility.adapter.in.web.dto.HouseRulesResponse;
import gal.furar.facility.adapter.in.web.dto.RenameFacilityRequest;
import gal.furar.facility.adapter.in.web.dto.RequirementValue;
import gal.furar.facility.adapter.in.web.dto.SportLabelResponse;
import gal.furar.facility.adapter.in.web.dto.SportResponse;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityAmenitiesRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityDescriptionRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityDetailsRequest;
import gal.furar.facility.application.port.in.AddressInput;
import gal.furar.facility.application.port.in.ArrangeImagesCommand;
import gal.furar.facility.application.port.in.ChangeFacilityStatusCommand;
import gal.furar.facility.application.port.in.CreateFacilityCommand;
import gal.furar.facility.application.port.in.CreateSportCommand;
import gal.furar.facility.application.port.in.DescriptionInput;
import gal.furar.facility.application.port.in.DetailsInput;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.RenameFacilityCommand;
import gal.furar.facility.application.port.in.SportRefInput;
import gal.furar.facility.application.port.in.UpdateFacilityAddressCommand;
import gal.furar.facility.application.port.in.UpdateFacilityAmenitiesCommand;
import gal.furar.facility.application.port.in.UpdateFacilityDescriptionCommand;
import gal.furar.facility.application.port.in.UpdateFacilityDetailsCommand;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDescription;
import gal.furar.facility.domain.FacilityDetails;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityPaymentNotes;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.HouseRules;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.Sport;
import gal.furar.shared.domain.LocalizedText;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** DTO ↔ comandos e dominio, a man. */
final class FacilityWebMapper {

  private FacilityWebMapper() {}

  static CreateFacilityCommand toCommand(CreateFacilityRequest request) {
    return new CreateFacilityCommand(request.name(), address(request.address()));
  }

  static RenameFacilityCommand toCommand(FacilityId id, long version, RenameFacilityRequest r) {
    return new RenameFacilityCommand(id, version, r.name());
  }

  static ChangeFacilityStatusCommand toCommand(
      FacilityId id, long version, ChangeFacilityStatusRequest request) {
    List<BookingDecision> decisions =
        request.decisions() == null
            ? List.of()
            : request.decisions().stream().map(FacilityWebMapper::decision).toList();
    return new ChangeFacilityStatusCommand(
        id, version, FacilityStatus.valueOf(request.status().name()), decisions);
  }

  static UpdateFacilityDetailsCommand toCommand(
      FacilityId id, long version, UpdateFacilityDetailsRequest r) {
    return new UpdateFacilityDetailsCommand(
        id,
        version,
        new DetailsInput(
            r.sports().stream().map(s -> new SportRefInput(s.code(), s.customSportId())).toList(),
            r.type() == null ? null : FacilityType.valueOf(r.type().name()),
            r.ridegaCode(),
            r.phone(),
            r.email(),
            Boolean.TRUE.equals(r.onlineBooking()),
            Boolean.TRUE.equals(r.showFreeSlots()),
            r.covered()),
        r.customFields());
  }

  static UpdateFacilityDescriptionCommand toCommand(
      FacilityId id, long version, UpdateFacilityDescriptionRequest r) {
    return new UpdateFacilityDescriptionCommand(id, version, new DescriptionInput(r.gl(), r.es()));
  }

  static UpdateFacilityAddressCommand toCommand(FacilityId id, long version, AddressRequest r) {
    return new UpdateFacilityAddressCommand(id, version, address(r));
  }

  static UpdateFacilityAmenitiesCommand toCommand(
      FacilityId id, long version, UpdateFacilityAmenitiesRequest r) {
    return new UpdateFacilityAmenitiesCommand(
        id,
        version,
        r.amenities().stream()
            .map(a -> Amenity.valueOf(a.name()))
            .collect(Collectors.toUnmodifiableSet()));
  }

  static ArrangeImagesCommand toCommand(FacilityId id, long version, ArrangeImagesRequest r) {
    return new ArrangeImagesCommand(
        id,
        version,
        r.images().stream()
            .map(
                p ->
                    new ArrangeImagesCommand.ImageInput(
                        p.photoId(),
                        p.drawing(),
                        p.alt() == null ? null : p.alt().gl(),
                        p.alt() == null ? null : p.alt().es()))
            .toList());
  }

  static CreateSportCommand toCommand(CreateSportRequest r) {
    return new CreateSportCommand(r.gl(), r.es());
  }

  static SportResponse sport(Sport sport) {
    return new SportResponse(
        sport.ref().code(),
        sport.ref().customSportId() == null ? null : sport.ref().customSportId().value(),
        new SportLabelResponse(sport.label().gl(), sport.label().es()));
  }

  static @Nullable HouseRulesResponse houseRules(Facility f) {
    HouseRules rules = f.houseRules();
    return rules == null ? null : new HouseRulesResponse(rules.gl(), rules.es());
  }

  static FacilityPaymentNotesResponse paymentNotes(FacilityPaymentNotes notes) {
    return new FacilityPaymentNotesResponse(note(notes.payment()), note(notes.refund()));
  }

  private static @Nullable FacilityNoteResponse note(@Nullable LocalizedText t) {
    return t == null ? null : new FacilityNoteResponse(t.gl(), t.es());
  }

  static FacilityResponse toResponse(FacilityView view) {
    Facility f = view.value();
    FacilityDetails d = f.details();
    FacilityDescription description = f.description();
    return new FacilityResponse(
        f.id().value(),
        f.name(),
        status(f.status()),
        address(f.address()),
        f.published(),
        new FacilityDetailsResponse(
            view.sports().stream().map(FacilityWebMapper::sport).toList(),
            d.type() == null ? null : FacilityTypeValue.valueOf(d.type().name()),
            d.ridegaCode(),
            d.phone(),
            d.email(),
            d.onlineBooking(),
            d.showFreeSlots(),
            d.covered(),
            view.customFields().toJson()),
        description == null
            ? null
            : new FacilityDescriptionResponse(description.gl(), description.es()),
        houseRules(f),
        paymentNotes(f.paymentNotes()),
        f.amenities().stream().map(a -> AmenityValue.valueOf(a.name())).toList(),
        view.gallery() == null
            ? List.of()
            : view.gallery().shown().stream().map(ImageMapper::toResponse).toList(),
        view.gallery() == null ? null : ImageMapper.toResponse(view.gallery().suggestion()),
        missing(view.missing()),
        !view.booked(),
        f.version() == null ? 0 : f.version(),
        f.createdAt(),
        f.updatedAt());
  }

  static FacilitySummaryResponse toSummary(FacilityView view) {
    Facility f = view.value();
    return new FacilitySummaryResponse(
        f.id().value(),
        f.name(),
        status(f.status()),
        address(f.address()),
        f.published(),
        view.sports().stream().map(FacilityWebMapper::sport).toList(),
        view.gallery() == null ? null : ImageMapper.toResponse(view.gallery().cover()),
        missing(view.missing()),
        f.version() == null ? 0 : f.version());
  }

  static AffectedBookingResponse affected(AffectedBooking a) {
    return new AffectedBookingResponse(
        a.bookingId(),
        a.holderName(),
        a.hasEmail(),
        a.date(),
        a.startMinute(),
        a.endMinute(),
        a.useName());
  }

  static BookingDecision decision(BookingDecisionRequest r) {
    Set<BookingDecision.Channel> channels =
        r.channels() == null
            ? Set.of()
            : r.channels().stream()
                .map(c -> BookingDecision.Channel.valueOf(c.name()))
                .collect(Collectors.toUnmodifiableSet());
    return new BookingDecision(
        r.bookingId(), BookingDecision.Action.valueOf(r.action().name()), channels);
  }

  private static @Nullable AddressInput address(@Nullable AddressRequest r) {
    return r == null
        ? null
        : new AddressInput(
            r.street(),
            r.streetNumber(),
            r.postalCode(),
            r.locality(),
            r.ineCode(),
            r.latitude(),
            r.longitude());
  }

  static @Nullable AddressResponse address(@Nullable Address a) {
    if (a == null) {
      return null;
    }
    return new AddressResponse(
        a.street(),
        a.streetNumber(),
        a.postalCode(),
        a.locality(),
        a.ineCode(),
        a.latitude(),
        a.longitude());
  }

  private static FacilityStatusValue status(FacilityStatus status) {
    return FacilityStatusValue.valueOf(status.name());
  }

  /** Na orde do {@code enum} (deportes, usos, horario), como o aviso da ficha. */
  private static List<RequirementValue> missing(Set<Requirement> missing) {
    EnumSet<Requirement> ordered = EnumSet.noneOf(Requirement.class);
    ordered.addAll(missing);
    return ordered.stream().map(r -> RequirementValue.valueOf(r.name())).toList();
  }
}
