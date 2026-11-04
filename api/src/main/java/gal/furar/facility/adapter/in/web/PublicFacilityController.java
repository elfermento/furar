// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.AmenityValue;
import gal.furar.facility.adapter.in.web.dto.ClosureHolidayResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityDescriptionResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityPaymentNotesResponse;
import gal.furar.facility.adapter.in.web.dto.PublicClosureResponse;
import gal.furar.facility.adapter.in.web.dto.PublicFacilityDetailResponse;
import gal.furar.facility.adapter.in.web.dto.PublicFacilityResponse;
import gal.furar.facility.adapter.in.web.dto.PublicLocationResponse;
import gal.furar.facility.adapter.in.web.dto.PublicOpeningDayResponse;
import gal.furar.facility.adapter.in.web.dto.PublicOpeningSlotResponse;
import gal.furar.facility.adapter.in.web.dto.PublicUseResponse;
import gal.furar.facility.adapter.in.web.dto.RentalModeValue;
import gal.furar.facility.adapter.in.web.dto.UseAvailabilityValue;
import gal.furar.facility.adapter.in.web.dto.UsePartResponse;
import gal.furar.facility.adapter.in.web.dto.UsePeriodResponse;
import gal.furar.facility.application.port.in.GetPublicFacilityUseCase;
import gal.furar.facility.application.port.in.ListPublicFacilitiesUseCase;
import gal.furar.facility.application.port.in.PublicFacility;
import gal.furar.facility.application.port.in.PublicFacilityDetail;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDescription;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.schedule.Closure;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vista da veciñanza, sen sesión: as instalacións publicadas (nin borrador nin de baixa) co que
 * pinta a portada. Vai baixo {@code /public} para non mesturala coa lectura de xestión.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/public/facilities")
@Tag(name = "facilities", description = "Instalacións")
class PublicFacilityController {

  private final ListPublicFacilitiesUseCase facilities;
  private final GetPublicFacilityUseCase detail;

  PublicFacilityController(
      ListPublicFacilitiesUseCase facilities, GetPublicFacilityUseCase detail) {
    this.facilities = facilities;
    this.detail = detail;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listPublicFacilities",
      summary =
          "Instalacións que ve a veciñanza (nin borrador nin de baixa), en orde alfabética segundo"
              + " Accept-Language, con deportes e imaxe de portada")
  List<PublicFacilityResponse> list(@PathVariable String slug, HttpServletRequest request) {
    return facilities.listPublic(Locales.language(request)).stream()
        .map(f -> toResponse(slug, f))
        .toList();
  }

  @GetMapping("/{id}")
  @PublicEndpoint
  @Operation(
      operationId = "getPublicFacility",
      summary =
          "Ficha pública dunha instalación que ve a veciñanza: imaxes, descrición, usos, servizos,"
              + " contacto, horario de hoxe e os seis días seguintes e peches dos próximos sesenta;"
              + " 404 se non se ve")
  PublicFacilityDetailResponse get(@PathVariable String slug, @PathVariable UUID id) {
    return toResponse(detail.getPublic(new FacilityId(id)));
  }

  @GetMapping("/{id}/payment-notes")
  @PublicEndpoint
  @Operation(
      operationId = "getFacilityPaymentNotes",
      summary =
          "Pago e devolución desta instalación (só se usan se o concello escolle «Distinto por"
              + " instalación»); tamén de baixa; 404 se non existe")
  FacilityPaymentNotesResponse paymentNotes(@PathVariable String slug, @PathVariable UUID id) {
    return FacilityWebMapper.paymentNotes(detail.paymentNotes(new FacilityId(id)));
  }

  static PublicFacilityDetailResponse toResponse(PublicFacilityDetail d) {
    Facility f = d.value();
    FacilityDescription description = f.description();
    return new PublicFacilityDetailResponse(
        f.id().value(),
        f.name(),
        location(f),
        description == null
            ? null
            : new FacilityDescriptionResponse(description.gl(), description.es()),
        FacilityWebMapper.houseRules(f),
        f.status() == FacilityStatus.TEMPORARILY_CLOSED,
        f.status() == FacilityStatus.ACTIVE && f.details().onlineBooking(),
        d.images().stream().map(ImageMapper::toPublicResponse).toList(),
        d.uses().stream().map(PublicFacilityController::use).toList(),
        f.amenities().stream().map(x -> AmenityValue.valueOf(x.name())).toList(),
        f.details().phone(),
        f.details().email(),
        d.week().stream()
            .map(
                day ->
                    new PublicOpeningDayResponse(
                        day.date(),
                        day.slots().stream()
                            .map(
                                s ->
                                    new PublicOpeningSlotResponse(
                                        s.startMinute(), s.endMinute(), s.start(), s.end()))
                            .toList()))
            .toList(),
        d.closures().stream().map(PublicFacilityController::closure).toList());
  }

  private static PublicClosureResponse closure(Closure c) {
    return new PublicClosureResponse(
        c.from(),
        c.to(),
        c.hours() == null ? null : c.hours().startMinute(),
        c.hours() == null ? null : c.hours().endMinute(),
        c.reason(),
        c.holiday() == null
            ? null
            : new ClosureHolidayResponse(c.holiday().nameGl(), c.holiday().nameEs()));
  }

  private static PublicUseResponse use(FacilityUse u) {
    return new PublicUseResponse(
        u.id().value(),
        u.name(),
        RentalModeValue.valueOf(u.rentalMode().name()),
        u.parts().stream().map(p -> new UsePartResponse(p.id(), p.name())).toList(),
        u.capacity(),
        u.slotMinutes(),
        UseAvailabilityValue.valueOf(u.availability().name()),
        u.periods().stream().map(p -> new UsePeriodResponse(p.from(), p.to())).toList(),
        u.weekdays(),
        UseWebMapper.price(u),
        UseWebMapper.rules(u));
  }

  static PublicFacilityResponse toResponse(String slug, PublicFacility facility) {
    return new PublicFacilityResponse(
        facility.value().id().value(),
        facility.value().name(),
        location(facility.value()),
        facility.sports().stream().map(FacilityWebMapper::sport).toList(),
        facility.value().status() == FacilityStatus.TEMPORARILY_CLOSED,
        facility.value().details().covered(),
        ImageMapper.toPublicResponse(facility.cover()));
  }

  /** Só se ven as publicadas, que sempre teñen enderezo (o punto pode faltar). */
  private static PublicLocationResponse location(Facility f) {
    Address a = Objects.requireNonNull(f.address(), "published facility without address");
    return new PublicLocationResponse(
        a.street(), a.streetNumber(), a.postalCode(), a.locality(), a.latitude(), a.longitude());
  }
}
