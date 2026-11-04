// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.adapter.in.web.dto.CapPeriodValue;
import gal.furar.facility.adapter.in.web.dto.PriceKindValue;
import gal.furar.facility.adapter.in.web.dto.PricePerValue;
import gal.furar.facility.adapter.in.web.dto.RentalModeValue;
import gal.furar.facility.adapter.in.web.dto.SaveUseRequest;
import gal.furar.facility.adapter.in.web.dto.UseAvailabilityValue;
import gal.furar.facility.adapter.in.web.dto.UsePartResponse;
import gal.furar.facility.adapter.in.web.dto.UsePeriodResponse;
import gal.furar.facility.adapter.in.web.dto.UsePriceRequest;
import gal.furar.facility.adapter.in.web.dto.UsePriceResponse;
import gal.furar.facility.adapter.in.web.dto.UseResponse;
import gal.furar.facility.adapter.in.web.dto.UseRulesRequest;
import gal.furar.facility.adapter.in.web.dto.UseRulesResponse;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.UseCapPeriod;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.facility.domain.UsePrice;
import gal.furar.facility.domain.UsePriceKind;
import gal.furar.facility.domain.UseRules;
import gal.furar.facility.domain.UseSpec;
import gal.furar.facility.domain.Weekdays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** DTO ↔ dominio dos usos, a man. */
final class UseWebMapper {

  private UseWebMapper() {}

  static UseSpec spec(SaveUseRequest r) {
    return new UseSpec(
        r.name(),
        RentalMode.valueOf(r.rentalMode().name()),
        r.capacity(),
        r.slotMinutes(),
        UseAvailability.valueOf(r.availability().name()),
        Boolean.TRUE.equals(r.blocksOthers()),
        r.parts() == null
            ? List.of()
            : r.parts().stream().map(p -> new UseSpec.PartSpec(p.id(), p.name())).toList(),
        r.periods() == null
            ? List.of()
            : r.periods().stream().map(p -> new UsePeriod(p.from(), p.to())).toList(),
        r.weekdays() == null ? Weekdays.ALL : r.weekdays(),
        price(r.price()),
        rules(r.rules()));
  }

  static UsePrice price(@Nullable UsePriceRequest p) {
    return p == null
        ? UsePrice.NONE
        : new UsePrice(UsePriceKind.valueOf(p.kind().name()), p.amountCents());
  }

  static UseRules rules(@Nullable UseRulesRequest r) {
    return r == null
        ? UseRules.NONE
        : new UseRules(
            r.minAdvanceHours(),
            r.maxAdvanceDays(),
            r.maxSlots(),
            r.capCount(),
            r.capPeriod() == null ? null : UseCapPeriod.valueOf(r.capPeriod().name()),
            r.cancelHours());
  }

  static UsePriceResponse price(FacilityUse u) {
    return new UsePriceResponse(
        PriceKindValue.valueOf(u.price().kind().name()),
        u.price().amountCents(),
        u.rentalMode() == RentalMode.SEATS ? PricePerValue.PERSON : PricePerValue.BOOKING);
  }

  static UseRulesResponse rules(FacilityUse u) {
    UseRules r = u.rules();
    return new UseRulesResponse(
        r.minAdvanceHours(),
        r.maxAdvanceDays(),
        r.maxSlots(),
        r.capCount(),
        r.capPeriod() == null ? null : CapPeriodValue.valueOf(r.capPeriod().name()),
        r.cancelHours());
  }

  static List<BookingDecision> decisions(SaveUseRequest r) {
    return r.decisions() == null
        ? List.of()
        : r.decisions().stream().map(FacilityWebMapper::decision).toList();
  }

  static UseResponse toResponse(FacilityUse u) {
    return new UseResponse(
        u.id().value(),
        u.facilityId().value(),
        u.name(),
        RentalModeValue.valueOf(u.rentalMode().name()),
        u.capacity(),
        u.slotMinutes(),
        UseAvailabilityValue.valueOf(u.availability().name()),
        u.blocksOthers(),
        u.parts().stream().map(p -> new UsePartResponse(p.id(), p.name())).toList(),
        u.periods().stream().map(p -> new UsePeriodResponse(p.from(), p.to())).toList(),
        u.weekdays(),
        price(u),
        rules(u),
        u.archivedAt(),
        u.version() == null ? 0 : u.version(),
        u.createdAt(),
        u.updatedAt());
  }
}
