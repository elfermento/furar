// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.AddressResponse;
import gal.furar.facility.adapter.in.web.dto.AmenityValue;
import gal.furar.facility.adapter.in.web.dto.FacilityDetailsResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityResponse;
import gal.furar.facility.adapter.in.web.dto.PhotoAltResponse;
import gal.furar.facility.adapter.in.web.dto.SportResponse;
import gal.furar.facility.adapter.in.web.dto.UsePartResponse;
import gal.furar.facility.adapter.in.web.dto.UsePeriodResponse;
import gal.furar.facility.adapter.in.web.dto.UsePriceResponse;
import gal.furar.facility.adapter.in.web.dto.UseResponse;
import gal.furar.facility.adapter.in.web.dto.UseRulesResponse;
import gal.furar.facility.adapter.in.web.portability.dto.ArchivedPartExportResponse;
import gal.furar.facility.adapter.in.web.portability.dto.BlockExportResponse;
import gal.furar.facility.adapter.in.web.portability.dto.FacilityExportResponse;
import gal.furar.facility.adapter.in.web.portability.dto.ImageExportResponse;
import gal.furar.facility.adapter.in.web.portability.dto.PhotoExportResponse;
import gal.furar.facility.adapter.in.web.schedule.ScheduleWebMapper;
import gal.furar.facility.adapter.in.web.schedule.dto.DayHoursResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleExceptionResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.SeasonResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.TimeRangeResponse;
import gal.furar.facility.application.port.in.DescriptionInput;
import gal.furar.facility.application.port.in.DetailsInput;
import gal.furar.facility.application.port.in.FacilityExport;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.in.RestoreFacilityCommand;
import gal.furar.facility.application.port.in.SportRefInput;
import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.UseCapPeriod;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.facility.domain.UsePrice;
import gal.furar.facility.domain.UsePriceKind;
import gal.furar.facility.domain.UseRules;
import gal.furar.facility.domain.UseSpec;
import gal.furar.facility.domain.Weekdays;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockSpec;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.SeasonSpec;
import gal.furar.facility.domain.schedule.TimeRange;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Fila da sección {@code facility} do ficheiro furar-export ↔ casos de uso, a man (ADR-0018). */
final class FacilityExportMapper {

  private FacilityExportMapper() {}

  static FacilityExportResponse toRow(FacilityExport export) {
    FacilityResponse f = FacilityWebMapper.toResponse(export.facility());
    return new FacilityExportResponse(
        f.id(),
        f.name(),
        f.status(),
        f.address(),
        f.published(),
        f.details(),
        f.description(),
        f.houseRules(),
        f.amenities(),
        null,
        images(export.facility()),
        f.missing(),
        f.version(),
        f.createdAt(),
        f.updatedAt(),
        export.uses().stream().map(UseWebMapper::toResponse).toList(),
        export.archivedParts().stream().map(FacilityExportMapper::archivedPart).toList(),
        export.seasons().stream().map(ScheduleWebMapper::toResponse).toList(),
        export.exceptions().stream().map(ScheduleWebMapper::toResponse).toList(),
        export.blocks().stream().map(FacilityExportMapper::block).toList());
  }

  private static List<ImageExportResponse> images(FacilityView view) {
    if (view.gallery() == null) {
      return List.of();
    }
    return view.gallery().stored().stream()
        .map(
            (ImageView image) ->
                switch (image.ref()) {
                  case ImageRef.OfDrawing d ->
                      new ImageExportResponse(d.code(), null, null, null, null, null);
                  case ImageRef.OfPhoto p ->
                      new ImageExportResponse(
                          null,
                          p.id().value(),
                          new PhotoAltResponse(image.alt().gl(), image.alt().es()),
                          image.width(),
                          image.height(),
                          image.createdAt());
                })
        .toList();
  }

  private static ArchivedPartExportResponse archivedPart(ArchivedUsePart p) {
    return new ArchivedPartExportResponse(
        p.id(), p.useId().value(), p.name(), p.position(), p.archivedAt());
  }

  private static BlockExportResponse block(Block b) {
    return new BlockExportResponse(
        b.id().value(),
        b.facilityId().value(),
        b.date(),
        b.interval().startMinute(),
        b.interval().endMinute(),
        b.start(),
        b.end(),
        b.reason(),
        b.createdBy(),
        b.version() == null ? 0 : b.version(),
        b.createdAt(),
        b.updatedAt());
  }

  /**
   * Unha fila anterior a D1-11b non trae datos (quedan os da alta); unha anterior a D1-18, listas.
   */
  static RestoreFacilityCommand toCommand(FacilityExportResponse row) {
    AddressResponse a = row.address();
    FacilityDetailsResponse d =
        row.details() == null
            ? new FacilityDetailsResponse(
                List.of(), null, null, null, null, true, false, null, Map.of())
            : row.details();
    return new RestoreFacilityCommand(
        row.id(),
        row.name(),
        row.status().name(),
        a == null ? null : a.street(),
        a == null ? null : a.streetNumber(),
        a == null ? null : a.postalCode(),
        a == null ? null : a.locality(),
        a == null ? null : a.ineCode(),
        a == null ? null : a.latitude(),
        a == null ? null : a.longitude(),
        row.published() == null || row.published(),
        new DetailsInput(
            sports(row).stream().map(s -> new SportRefInput(s.code(), s.customSportId())).toList(),
            d.type() == null ? null : FacilityType.valueOf(d.type().name()),
            d.ridegaCode(),
            d.phone(),
            d.email(),
            d.onlineBooking(),
            d.showFreeSlots(),
            d.covered()),
        row.description() == null
            ? null
            : new DescriptionInput(row.description().gl(), row.description().es()),
        row.houseRules() == null
            ? null
            : new DescriptionInput(row.houseRules().gl(), row.houseRules().es()),
        list(row.amenities()).stream()
            .map((AmenityValue v) -> Amenity.valueOf(v.name()))
            .collect(Collectors.toUnmodifiableSet()),
        row.createdAt(),
        row.updatedAt(),
        d.customFields(),
        images(row),
        list(row.uses()).stream().map(FacilityExportMapper::use).toList(),
        list(row.archivedParts()).stream()
            .map(
                p ->
                    new ArchivedUsePart(
                        p.id(), new UseId(p.useId()), p.name(), p.position(), p.archivedAt()))
            .toList(),
        list(row.seasons()).stream().map(FacilityExportMapper::season).toList(),
        list(row.exceptions()).stream().map(FacilityExportMapper::exception).toList(),
        list(row.blocks()).stream().map(FacilityExportMapper::block).toList());
  }

  static List<SportResponse> sports(FacilityExportResponse row) {
    return row.details() == null || row.details().sports() == null
        ? List.of()
        : row.details().sports();
  }

  /** As imaxes da fila; nun ficheiro anterior, as fotos coa portada primeiro. */
  private static List<RestoreFacilityCommand.RestoreImage> images(FacilityExportResponse row) {
    if (row.images() != null) {
      return row.images().stream()
          .map(
              i ->
                  new RestoreFacilityCommand.RestoreImage(
                      i.photoId(),
                      i.drawing(),
                      i.alt() == null ? null : i.alt().gl(),
                      i.alt() == null ? null : i.alt().es(),
                      i.width(),
                      i.height(),
                      i.createdAt()))
          .toList();
    }
    List<PhotoExportResponse> photos = list(row.photos());
    return java.util.stream.Stream.concat(
            photos.stream().filter(PhotoExportResponse::cover),
            photos.stream().filter(p -> !p.cover()))
        .map(
            p ->
                new RestoreFacilityCommand.RestoreImage(
                    p.id(),
                    null,
                    p.alt() == null ? null : p.alt().gl(),
                    p.alt() == null ? null : p.alt().es(),
                    p.width(),
                    p.height(),
                    p.createdAt()))
        .toList();
  }

  private static RestoreFacilityCommand.RestoreUse use(UseResponse u) {
    return new RestoreFacilityCommand.RestoreUse(
        u.id(),
        new UseSpec(
            u.name(),
            RentalMode.valueOf(u.rentalMode().name()),
            u.capacity(),
            u.slotMinutes(),
            UseAvailability.valueOf(u.availability().name()),
            u.blocksOthers(),
            list(u.parts()).stream()
                .map((UsePartResponse p) -> new UseSpec.PartSpec(p.id(), p.name()))
                .toList(),
            list(u.periods()).stream()
                .map((UsePeriodResponse p) -> new UsePeriod(p.from(), p.to()))
                .toList(),
            u.weekdays() == null ? Weekdays.ALL : u.weekdays(),
            importedPrice(u.price()),
            importedRules(u.rules())),
        u.archivedAt(),
        u.createdAt(),
        u.updatedAt());
  }

  /** Un ficheiro anterior ao dominio 2 non trae prezo: sen prezo. */
  private static UsePrice importedPrice(@Nullable UsePriceResponse p) {
    return p == null
        ? UsePrice.NONE
        : new UsePrice(UsePriceKind.valueOf(p.kind().name()), p.amountCents());
  }

  /** Un ficheiro anterior ao dominio 2 non trae normas: ningunha. */
  private static UseRules importedRules(@Nullable UseRulesResponse r) {
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

  private static RestoreFacilityCommand.RestoreSeason season(SeasonResponse s) {
    return new RestoreFacilityCommand.RestoreSeason(
        s.id(),
        new SeasonSpec(s.name(), s.startDate(), s.endDate()),
        list(s.week()).stream().map(FacilityExportMapper::day).toList(),
        s.createdAt(),
        s.updatedAt());
  }

  private static DayHoursSpec day(DayHoursResponse d) {
    return new DayHoursSpec(
        d.day() == null ? null : ScheduleDay.valueOf(d.day().name()),
        list(d.intervals()).stream()
            .map((TimeRangeResponse r) -> new TimeRange(r.startMinute(), r.endMinute()))
            .toList());
  }

  private static RestoreFacilityCommand.RestoreException exception(ScheduleExceptionResponse e) {
    return new RestoreFacilityCommand.RestoreException(
        e.id(),
        new ExceptionSpec(e.date(), e.open(), e.startMinute(), e.endMinute(), e.reason()),
        e.createdAt(),
        e.updatedAt());
  }

  private static RestoreFacilityCommand.RestoreBlock block(BlockExportResponse b) {
    return new RestoreFacilityCommand.RestoreBlock(
        b.id(),
        new BlockSpec(b.date(), b.startMinute(), b.endMinute(), b.reason()),
        b.createdBy(),
        b.createdAt(),
        b.updatedAt());
  }

  private static <T> List<T> list(@Nullable List<T> list) {
    return list == null ? List.of() : list;
  }
}
