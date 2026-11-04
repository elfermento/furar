// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.application.port.in.DescriptionInput;
import gal.furar.facility.application.port.in.DetailsInput;
import gal.furar.facility.application.port.in.FacilityExport;
import gal.furar.facility.application.port.in.RestoreCustomSportCommand;
import gal.furar.facility.application.port.in.RestoreFacilityCommand;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreBlock;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreException;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreImage;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreSeason;
import gal.furar.facility.application.port.in.RestoreFacilityCommand.RestoreUse;
import gal.furar.facility.application.port.in.SportRefInput;
import gal.furar.facility.application.service.FacilityPortabilityService;
import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.ArchivedUsePart;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.UseError;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.facility.domain.UseSpec;
import gal.furar.facility.domain.schedule.BlockSpec;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.SeasonSpec;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.portability.PortabilityError;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Portabilidade do módulo (ADR-0018) con repositorios en memoria: sen Spring nin base. */
class FacilityPortabilityServiceTest {

  static final Instant CREATED = Instant.parse("2025-01-02T03:04:05Z");
  static final Instant UPDATED = Instant.parse("2025-06-07T08:09:10Z");

  final List<String> calls = new ArrayList<>();
  final InMemoryFacilityRepository repository = new InMemoryFacilityRepository();
  final InMemorySportRepository sports = new InMemorySportRepository();
  final InMemoryImages images = new InMemoryImages();
  final InMemoryPhotoRepository photos = images.photos;
  final InMemoryFacilityCustomFields customFields = new InMemoryFacilityCustomFields();
  final InMemoryFacilityUseRepository uses = new InMemoryFacilityUseRepository(calls);
  final InMemorySeasonRepository seasons = new InMemorySeasonRepository(calls);
  final InMemoryScheduleExceptionRepository exceptions =
      new InMemoryScheduleExceptionRepository(calls);
  final InMemoryBlockRepository blocks = new InMemoryBlockRepository(calls);
  final Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
  final FacilityPortabilityService service =
      new FacilityPortabilityService(
          repository,
          sports,
          photos,
          images.images,
          images.galleries,
          customFields,
          uses,
          seasons,
          exceptions,
          blocks,
          clock);
  TenantContext.Scope scope;

  @BeforeEach
  void enter() {
    scope = TenantContext.open(new TenantRef(UUID.randomUUID(), "naron"));
  }

  @AfterEach
  void leave() {
    scope.close();
  }

  /** A importación conserva id, estado, datos e datas, e os deportes do concello. */
  @Test
  void importKeepsIdsStatusAndDatesForTheExport() {
    UUID id = UUID.randomUUID();
    UUID rugby = UUID.randomUUID();
    service.importAll(
        List.of(new RestoreCustomSportCommand(rugby, "Rugby cinco", "Rugby cinco")),
        List.of(row(id, List.of(new SportRefInput(null, rugby)), List.of(), List.of())));
    List<FacilityExport> exported = service.exportAll();
    assertThat(exported).hasSize(1);
    Facility facility = exported.get(0).facility().value();
    assertThat(facility.id()).isEqualTo(new FacilityId(id));
    assertThat(facility.status()).isEqualTo(FacilityStatus.WITHDRAWN);
    assertThat(facility.createdAt()).isEqualTo(CREATED);
    assertThat(facility.details().showFreeSlots()).isTrue();
    assertThat(facility.amenities()).containsExactly(Amenity.LIGHTING);
    assertThat(exported.get(0).facility().sports())
        .extracting(sport -> sport.label().gl())
        .containsExactly("Pádel", "Rugby cinco");
  }

  /** Un deporte que xa entrou coa súa sección non se repite ao velo dentro dunha fila. */
  @Test
  void customSportsFromTheirSectionAreNotRepeated() {
    UUID unused = UUID.randomUUID();
    UUID used = UUID.randomUUID();
    service.importCustomSports(
        List.of(
            new RestoreCustomSportCommand(unused, "Bolos", "Bolos", CREATED),
            new RestoreCustomSportCommand(used, "Rugby cinco", "Rugby cinco", CREATED)));
    service.importAll(
        List.of(new RestoreCustomSportCommand(used, "Rugby cinco", "Rugby cinco")),
        List.of(row(UUID.randomUUID(), List.of(), List.of(), List.of())));
    assertThat(service.exportCustomSports())
        .extracting(s -> s.id().value())
        .containsExactlyInAnyOrder(unused, used);
    assertThat(service.exportCustomSports()).allMatch(s -> s.createdAt().equals(CREATED));
  }

  /** Usos (tamén arquivados) e partes arquivadas, fotos sen bytes, horario e bloqueos. */
  @Test
  void everythingHangingFromTheFacilityComesBack() {
    UUID id = UUID.randomUUID();
    UUID useId = UUID.randomUUID();
    UUID partA = UUID.randomUUID();
    UUID partB = UUID.randomUUID();
    UUID oldPart = UUID.randomUUID();
    UUID archivedUse = UUID.randomUUID();
    UUID photo = UUID.randomUUID();
    List<RestoreUse> useRows =
        List.of(
            new RestoreUse(
                useId,
                new UseSpec(
                    "Campo",
                    RentalMode.PARTS,
                    null,
                    60,
                    UseAvailability.ALWAYS,
                    false,
                    List.of(new UseSpec.PartSpec(partA, "A"), new UseSpec.PartSpec(partB, "B")),
                    List.of()),
                null,
                CREATED,
                UPDATED),
            new RestoreUse(
                archivedUse,
                new UseSpec(
                    "Torneo",
                    RentalMode.WHOLE,
                    null,
                    90,
                    UseAvailability.DATES,
                    true,
                    List.of(),
                    List.of(new UsePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)))),
                UPDATED,
                CREATED,
                UPDATED));
    RestoreFacilityCommand row =
        new RestoreFacilityCommand(
            id,
            "Completa",
            "ACTIVE",
            "Rúa",
            null,
            "15570",
            "Narón",
            "15054",
            new BigDecimal("43.5"),
            new BigDecimal("-8.2"),
            true,
            details(List.of()),
            null,
            null,
            Set.of(),
            CREATED,
            UPDATED,
            Map.of("zone", "north"),
            List.of(
                new RestoreImage(null, "pool", null, null, null, null, null),
                new RestoreImage(photo, null, "Campo", "Campo", 1600, 900, CREATED)),
            useRows,
            List.of(new ArchivedUsePart(oldPart, new UseId(useId), "Vella", 2, UPDATED)),
            List.of(
                new RestoreSeason(
                    UUID.randomUUID(),
                    new SeasonSpec("Inverno", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
                    List.of(
                        new DayHoursSpec(ScheduleDay.MONDAY, List.of(new TimeRange(480, 1320)))),
                    CREATED,
                    UPDATED)),
            List.of(
                new RestoreException(
                    UUID.randomUUID(),
                    new ExceptionSpec(LocalDate.of(2026, 3, 2), false, null, null, "Obras"),
                    CREATED,
                    UPDATED)),
            List.of(
                new RestoreBlock(
                    UUID.randomUUID(),
                    new BlockSpec(LocalDate.of(2026, 3, 9), 600, 720, "Limpeza"),
                    "manager-sub",
                    CREATED,
                    UPDATED)));

    service.importAll(List.of(), List.of(row));

    FacilityExport export = service.exportAll().getFirst();
    assertThat(export.uses()).extracting(FacilityUse::name).containsExactly("Campo", "Torneo");
    assertThat(export.uses().get(0).parts()).extracting(p -> p.id()).containsExactly(partA, partB);
    assertThat(export.uses().get(1).archivedAt()).isEqualTo(UPDATED);
    assertThat(export.uses().get(0).updatedAt()).isEqualTo(UPDATED);
    assertThat(export.archivedParts()).extracting(ArchivedUsePart::id).containsExactly(oldPart);
    assertThat(export.facility().gallery().stored())
        .extracting(i -> i.ref())
        .containsExactly(ImageRef.drawing("pool"), ImageRef.photo(new PhotoId(photo)));
    assertThat(export.facility().gallery().stored().get(1).createdAt()).isEqualTo(CREATED);
    assertThat(photos.content).as("sen bytes (P32)").isEmpty();
    assertThat(customFields.stored).containsKey(new FacilityId(id));
    assertThat(customFields.lastRaw).isEqualTo(Map.of("zone", "north"));
    assertThat(export.seasons().getFirst().week().hours(ScheduleDay.MONDAY))
        .containsExactly(new TimeRange(480, 1320));
    assertThat(export.seasons().getFirst().createdAt()).isEqualTo(CREATED);
    assertThat(export.exceptions().getFirst().reason()).isEqualTo("Obras");
    assertThat(export.blocks().getFirst().createdBy()).isEqualTo("manager-sub");
    assertThat(export.blocks().getFirst().start())
        .as("o período real calcúlase de novo en Europe/Madrid")
        .isEqualTo(Instant.parse("2026-03-09T09:00:00Z"));
  }

  /** As regras do dominio valen tamén na importación: un uso por partes cunha soa non entra. */
  @Test
  void anInvalidUseIsRejected() {
    RestoreUse onePart =
        new RestoreUse(
            UUID.randomUUID(),
            new UseSpec(
                "Campo",
                RentalMode.PARTS,
                null,
                60,
                UseAvailability.ALWAYS,
                false,
                List.of(new UseSpec.PartSpec(UUID.randomUUID(), "A")),
                List.of()),
            null,
            CREATED,
            UPDATED);
    assertThatThrownBy(
            () ->
                service.importAll(
                    List.of(),
                    List.of(row(UUID.randomUUID(), List.of(), List.of(onePart), List.of()))))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UseError.PARTS_REQUIRED));
  }

  /** Unha parte arquivada dun uso que non vén na fila: ficheiro incoherente. */
  @Test
  void anArchivedPartOfAnUnknownUseIsAnInvalidFile() {
    ArchivedUsePart orphan =
        new ArchivedUsePart(UUID.randomUUID(), new UseId(UUID.randomUUID()), "X", 0, UPDATED);
    assertThatThrownBy(
            () ->
                service.importAll(
                    List.of(),
                    List.of(row(UUID.randomUUID(), List.of(), List.of(), List.of(orphan)))))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PortabilityError.INVALID_FILE));
  }

  private static RestoreFacilityCommand row(
      UUID id,
      List<SportRefInput> extraSports,
      List<RestoreUse> useRows,
      List<ArchivedUsePart> archived) {
    List<SportRefInput> sportRefs = new ArrayList<>();
    sportRefs.add(new SportRefInput("padel", null));
    sportRefs.addAll(extraSports);
    return new RestoreFacilityCommand(
        id,
        "Importada " + id,
        "WITHDRAWN",
        "Rúa",
        null,
        "15570",
        "Narón",
        "15054",
        new BigDecimal("43.5"),
        new BigDecimal("-8.2"),
        true,
        details(sportRefs),
        new DescriptionInput("Pista", "Pista"),
        null,
        Set.of(Amenity.LIGHTING),
        CREATED,
        CREATED,
        Map.of(),
        List.of(),
        useRows,
        archived,
        List.of(),
        List.of(),
        List.of());
  }

  private static DetailsInput details(List<SportRefInput> sportRefs) {
    return new DetailsInput(
        sportRefs, FacilityType.PADEL_COURT, null, null, null, false, true, true);
  }
}
