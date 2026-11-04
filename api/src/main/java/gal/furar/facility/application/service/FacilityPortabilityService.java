// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.in.ExportFacilitiesUseCase;
import gal.furar.facility.application.port.in.FacilityExport;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.ImportFacilitiesUseCase;
import gal.furar.facility.application.port.in.RestoreCustomSportCommand;
import gal.furar.facility.application.port.in.RestoreFacilityCommand;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreBlock;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreException;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreImage;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreSeason;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreUse;
import gal.furar.facility.application.port.out.FacilityCustomFields;
import gal.furar.facility.application.port.out.FacilityImageRepository;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.FacilityUseRepository;
import gal.furar.facility.application.port.out.PhotoRepository;
import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.application.port.out.schedule.BlockRepository;
import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.CustomSport;
import gal.furar.facility.domain.CustomSportId;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.FacilityPaymentNotes;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.HouseRules;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.WeekHours;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.portability.PortabilityError;
import gal.furar.shared.portability.PortabilityPermission;
import gal.furar.shared.security.RequiresPermission;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portabilidade do módulo (ADR-0018): exporta e importa as instalacións con todo o que colga delas
 * e os deportes do concello. A importación corre na transacción da do concello, que é novo: non hai
 * reservas nin outras escrituras, así que non toma o bloqueo da instalación nin pregunta polas
 * reservas afectadas. Cada fila pasa as regras do dominio (as mesmas ca unha alta) e a base garante
 * o resto (solapes, nomes únicos); a auditoría é a da importación do concello.
 */
@Service
public class FacilityPortabilityService
    implements ExportFacilitiesUseCase, ImportFacilitiesUseCase {

  /** O reencodado das fotos sempre dá JPEG; o ficheiro non leva o tipo. */
  static final String PHOTO_TYPE = "image/jpeg";

  private final FacilityRepository facilities;
  private final SportRepository sports;
  private final PhotoRepository photos;
  private final FacilityImageRepository images;
  private final FacilityGalleries galleries;
  private final FacilityCustomFields customFields;
  private final FacilityUseRepository uses;
  private final SeasonRepository seasons;
  private final ScheduleExceptionRepository exceptions;
  private final BlockRepository blocks;
  private final Clock clock;

  public FacilityPortabilityService(
      FacilityRepository facilities,
      SportRepository sports,
      PhotoRepository photos,
      FacilityImageRepository images,
      FacilityGalleries galleries,
      FacilityCustomFields customFields,
      FacilityUseRepository uses,
      SeasonRepository seasons,
      ScheduleExceptionRepository exceptions,
      BlockRepository blocks,
      Clock clock) {
    this.facilities = facilities;
    this.sports = sports;
    this.photos = photos;
    this.images = images;
    this.galleries = galleries;
    this.customFields = customFields;
    this.uses = uses;
    this.seasons = seasons;
    this.exceptions = exceptions;
    this.blocks = blocks;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(PortabilityPermission.Code.EXPORT)
  public List<FacilityExport> exportAll() {
    return facilities.findAll().stream().map(this::export).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(PortabilityPermission.Code.EXPORT)
  public List<CustomSport> exportCustomSports() {
    return sports.findAllCustom();
  }

  private FacilityExport export(Facility f) {
    FacilityId id = f.id();
    return new FacilityExport(
        new FacilityView(
            f,
            Set.of(),
            sports.findAll(f.details().sports()),
            galleries.of(f),
            customFields.stored(id)),
        uses.findAll(id),
        uses.findArchivedParts(id),
        seasons.findByFacility(id),
        exceptions.findByFacility(id),
        blocks.findByFacility(id));
  }

  @Override
  @Transactional
  @RequiresPermission(PortabilityPermission.Code.IMPORT)
  public void importCustomSports(List<RestoreCustomSportCommand> customSports) {
    insertCustomSports(customSports);
  }

  @Override
  @Transactional
  @RequiresPermission(PortabilityPermission.Code.IMPORT)
  public void importAll(
      List<RestoreCustomSportCommand> customSports, List<RestoreFacilityCommand> rows) {
    insertCustomSports(customSports);
    Set<PhotoId> restoredPhotos = new HashSet<>();
    for (RestoreFacilityCommand row : rows) {
      restore(row, restoredPhotos);
    }
  }

  /** Os que xa están (entraron coa súa sección) non se repiten. */
  private void insertCustomSports(List<RestoreCustomSportCommand> customSports) {
    Set<UUID> known = new HashSet<>();
    sports.findAllCustom().forEach(s -> known.add(s.id().value()));
    for (RestoreCustomSportCommand sport : customSports) {
      if (known.add(sport.id())) {
        sports.insert(
            new CustomSport(
                new CustomSportId(sport.id()),
                new LocalizedText(sport.gl(), sport.es()),
                sport.createdAt() == null ? clock.instant() : sport.createdAt()));
      }
    }
  }

  /** Sen vía, sen enderezo (borrador); o resto o comproba {@link Address}. */
  private static @Nullable Address address(RestoreFacilityCommand row) {
    String street = row.street();
    if (street == null) {
      return null;
    }
    return new Address(
        street,
        row.streetNumber(),
        Objects.requireNonNullElse(row.postalCode(), ""),
        Objects.requireNonNullElse(row.locality(), ""),
        Objects.requireNonNullElse(row.ineCode(), ""),
        row.latitude(),
        row.longitude());
  }

  private void restore(RestoreFacilityCommand row, Set<PhotoId> restoredPhotos) {
    FacilityId id = new FacilityId(row.id());
    facilities.insert(
        Facility.restore(
            id,
            row.name(),
            FacilityStatus.valueOf(row.status()),
            address(row),
            row.published(),
            FacilityService.details(row.details()),
            row.description() == null ? null : FacilityService.description(row.description()),
            row.houseRules() == null
                ? null
                : HouseRules.of(row.houseRules().gl(), row.houseRules().es()),
            FacilityPaymentNotes.NONE,
            row.amenities(),
            row.createdAt(),
            row.updatedAt()));
    if (!row.customFields().isEmpty()) {
      CustomFieldValues values = customFields.restore(row.customFields());
      customFields.replace(id, values);
    }
    if (!row.images().isEmpty()) {
      images.replace(id, images(row.images(), restoredPhotos));
    }
    restoreUses(id, row.uses(), row.archivedParts());
    for (RestoreSeason s : row.seasons()) {
      Season checked = Season.create(new SeasonId(s.id()), id, s.spec(), null, s.createdAt());
      seasons.insert(
          Season.rehydrate(
              checked.id(),
              id,
              checked.name(),
              checked.startDate(),
              checked.endDate(),
              WeekHours.of(s.week()),
              null,
              s.createdAt(),
              s.updatedAt()));
    }
    for (RestoreException e : row.exceptions()) {
      ScheduleException checked =
          ScheduleException.create(new ScheduleExceptionId(e.id()), id, e.spec(), e.createdAt());
      exceptions.insert(
          ScheduleException.rehydrate(
              checked.id(),
              id,
              checked.date(),
              checked.interval(),
              checked.reason(),
              null,
              e.createdAt(),
              e.updatedAt()));
    }
    for (RestoreBlock b : row.blocks()) {
      Block checked = Block.create(new BlockId(b.id()), id, b.spec(), b.createdBy(), b.createdAt());
      blocks.insert(
          Block.rehydrate(
              checked.id(),
              id,
              checked.date(),
              checked.interval(),
              checked.start(),
              checked.end(),
              checked.reason(),
              b.createdBy(),
              null,
              b.createdAt(),
              b.updatedAt()));
    }
  }

  /** Os usos e despois as partes arquivadas, que só poden ser dun uso desta instalación. */
  private void restoreUses(
      FacilityId id, List<RestoreUse> rows, List<ArchivedUsePart> archivedParts) {
    Set<UseId> restored = new HashSet<>();
    for (RestoreUse u : rows) {
      FacilityUse use =
          FacilityUse.restore(
              new UseId(u.id()), id, u.spec(), u.archivedAt(), u.createdAt(), u.updatedAt());
      uses.insert(use);
      restored.add(use.id());
    }
    for (ArchivedUsePart part : archivedParts) {
      if (!restored.contains(part.useId())) {
        // Unha parte arquivada dun uso que non está nesta fila: o ficheiro non é coherente.
        throw new DomainException(
            PortabilityError.INVALID_FILE,
            Map.of("section", "facility", "reason", "archived-part"));
      }
    }
    if (!archivedParts.isEmpty()) {
      uses.insertArchivedParts(archivedParts);
    }
  }

  /**
   * A lista de imaxes da fila. Cada foto entra na biblioteca do concello unha vez (pode usala máis
   * dunha instalación), co seu metadato e sen bytes (P32).
   */
  private FacilityImages images(List<RestoreImage> rows, Set<PhotoId> restoredPhotos) {
    List<ImageRef> list = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      RestoreImage image = rows.get(i);
      if (image.drawing() != null) {
        list.add(ImageRef.drawing(image.drawing()));
        continue;
      }
      if (image.photoId() == null || image.width() == null || image.height() == null) {
        throw new DomainException(
            PortabilityError.INVALID_FILE, Map.of("section", "facility", "reason", "image"));
      }
      PhotoId photoId = new PhotoId(image.photoId());
      if (restoredPhotos.add(photoId)) {
        photos.restore(
            new Photo(
                photoId,
                Photo.alt("images[" + i + "].alt", image.altGl(), image.altEs()),
                image.width(),
                image.height(),
                PHOTO_TYPE,
                image.createdAt() == null ? clock.instant() : image.createdAt(),
                null));
      }
      list.add(ImageRef.photo(photoId));
    }
    return new FacilityImages(list);
  }
}
