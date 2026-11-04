// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.ProgrammableBookingImpact;
import gal.furar.facility.application.port.in.ArrangeImagesCommand;
import gal.furar.facility.application.port.in.ArrangeImagesCommand.ImageInput;
import gal.furar.facility.application.port.in.ChangeFacilityStatusCommand;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.in.LibraryPhoto;
import gal.furar.facility.application.port.in.PublishFacilityCommand;
import gal.furar.facility.application.port.in.UploadPhotoCommand;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.application.service.FacilityImageService;
import gal.furar.facility.application.service.FacilityService;
import gal.furar.facility.application.service.PhotoLibraryService;
import gal.furar.facility.domain.DrawingSuggestion;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoUrlSignature;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.facility.domain.ProcessedPhoto;
import gal.furar.facility.domain.Requirement;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Biblioteca e sección Fotos con portos en memoria e un procesador falso: subidas pendentes, gardar
 * a lista coa versión, texto alternativo que vai coa foto, debuxo suxerido, caducidade das
 * pendentes, baixa e auditoría.
 */
class FacilityImageServiceTest {

  final InMemoryFacilityRepository facilities = new InMemoryFacilityRepository();
  final InMemoryImages store = new InMemoryImages();
  final List<AuditEvent> audit = new ArrayList<>();
  final List<byte[]> processed = new ArrayList<>();
  final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-26T10:00:00Z"));
  final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };
  final gal.furar.facility.application.port.out.PhotoProcessor processor =
      upload -> {
        processed.add(upload);
        PhotoFile file = new PhotoFile(upload, "image/jpeg");
        return new ProcessedPhoto(file, file, 1600, 1200);
      };

  /** Todos os requisitos cumpridos: as instalacións pódense publicar. */
  static final List<RequirementProbe> ALL =
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
          .toList();

  final FacilityService facilityService =
      new FacilityService(
          facilities,
          facilities,
          facilities,
          new ProgrammableBookingImpact(),
          ALL,
          new InMemorySportRepository(),
          store.galleries,
          new InMemoryFacilityCustomFields(),
          audit::add,
          e -> {},
          clock);
  final FacilityImageService service =
      new FacilityImageService(
          facilities,
          store.images,
          store.photos,
          store.drawings,
          code -> Optional.of(new byte[] {9, 9}),
          processor,
          facilityService,
          ALL,
          TransactionOperations.withoutTransaction(),
          audit::add,
          clock);
  final PhotoLibraryService library =
      new PhotoLibraryService(
          store.photos,
          store.images,
          facilities,
          store.drawings,
          processor,
          TransactionOperations.withoutTransaction(),
          audit::add,
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

  FacilityId facility(String name) {
    return facilityService.create(FacilityServiceTest.create(name)).value().id();
  }

  PhotoId upload() {
    return library.upload(new UploadPhotoCommand(new byte[] {1, 2, 3})).id();
  }

  static ImageInput photo(PhotoId id, String alt) {
    return new ImageInput(id.value(), null, alt, alt + " (es)");
  }

  static ImageInput drawing(String code) {
    return new ImageInput(null, code, null, null);
  }

  static String code(Throwable e) {
    return ((DomainException) e).errorCode().code();
  }

  @Test
  void withoutImagesTheSuggestedDrawingIsShown() {
    FacilityView view = facilityService.get(facility("Pavillón de Piñeiros"));
    assertThat(view.gallery().stored()).isEmpty();
    assertThat(view.gallery().shown())
        .extracting(ImageView::ref)
        .containsExactly(ImageRef.drawing(DrawingSuggestion.SPORTS_HALL));
    assertThat(view.gallery().suggestion().name()).isNotNull();
  }

  @Test
  void anUploadWaitsOutsideTheLibraryUntilAFacilityKeepsIt() {
    FacilityId id = facility("Campo");
    PhotoId photo = upload();
    assertThat(library.library()).isEmpty();

    FacilityView saved =
        service.arrange(
            new ArrangeImagesCommand(
                id, 0, List.of(photo(photo, "Bancada"), drawing(DrawingSuggestion.POOL))));

    assertThat(saved.value().version()).isEqualTo(1);
    assertThat(saved.gallery().shown())
        .extracting(ImageView::ref)
        .containsExactly(ImageRef.photo(photo), ImageRef.drawing(DrawingSuggestion.POOL));
    assertThat(saved.gallery().cover().alt().gl()).isEqualTo("Bancada");
    assertThat(library.library())
        .extracting(LibraryPhoto::usedIn)
        .containsExactly(List.of("Campo"));
    assertThat(audit)
        .extracting(AuditEvent::action)
        .contains("facility.photo.upload", "facility.images.arrange");
  }

  @Test
  void theAltTextGoesWithThePhotoInEveryFacility() {
    FacilityId campo = facility("Campo");
    FacilityId pista = facility("Pista");
    PhotoId photo = upload();
    service.arrange(new ArrangeImagesCommand(campo, 0, List.of(photo(photo, "Vella"))));
    service.arrange(new ArrangeImagesCommand(pista, 0, List.of(photo(photo, "Nova"))));

    assertThat(facilityService.get(campo).gallery().cover().alt().gl()).isEqualTo("Nova");
    assertThat(library.library().getFirst().usedIn()).containsExactly("Campo", "Pista");
  }

  @Test
  void removingEveryImageBringsBackTheSuggestion() {
    FacilityId id = facility("Piscina de verán");
    service.arrange(new ArrangeImagesCommand(id, 0, List.of(drawing(DrawingSuggestion.GYM))));
    FacilityView empty = service.arrange(new ArrangeImagesCommand(id, 1, List.of()));
    assertThat(empty.gallery().shown())
        .extracting(ImageView::ref)
        .containsExactly(ImageRef.drawing(DrawingSuggestion.POOL));
  }

  @Test
  void invalidListsSaveNothing() {
    FacilityId id = facility("Campo");
    PhotoId photo = upload();
    assertThatThrownBy(
            () ->
                service.arrange(
                    new ArrangeImagesCommand(id, 0, List.of(photo(photo, "A"), photo(photo, "A")))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.images-invalid"));
    assertThatThrownBy(
            () ->
                service.arrange(
                    new ArrangeImagesCommand(id, 0, List.of(photo(PhotoId.newId(), "A")))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.images-invalid"));
    assertThatThrownBy(
            () -> service.arrange(new ArrangeImagesCommand(id, 0, List.of(drawing("quidditch")))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.images-invalid"));
    assertThatThrownBy(
            () ->
                service.arrange(
                    new ArrangeImagesCommand(
                        id, 0, List.of(new ImageInput(photo.value(), "gym", "A", "A")))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.images-invalid"));
    assertThatThrownBy(
            () ->
                service.arrange(
                    new ArrangeImagesCommand(
                        id, 0, List.of(new ImageInput(photo.value(), null, "A", " ")))))
        .satisfies(
            e -> {
              assertThat(code(e)).isEqualTo("facility.photo-alt-invalid");
              assertThat(((DomainException) e).violations())
                  .extracting(v -> v.field())
                  .containsExactly("images[0].alt.es");
            });
    assertThatThrownBy(
            () -> service.arrange(new ArrangeImagesCommand(id, 7, List.of(photo(photo, "A")))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("common.stale-version"));
    assertThat(store.images.load(id).isEmpty()).isTrue();
    assertThat(store.photos.photos.get(photo).pending()).isTrue();
    assertThat(audit.getLast().outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
  }

  @Test
  void pendingUploadsCanBeDiscardedAndExpire() {
    FacilityId id = facility("Campo");
    PhotoId discarded = upload();
    library.discard(discarded);
    assertThat(store.photos.photos).doesNotContainKey(discarded);

    PhotoId kept = upload();
    service.arrange(new ArrangeImagesCommand(id, 0, List.of(photo(kept, "Campo"))));
    assertThatThrownBy(() -> library.discard(kept))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-not-pending"));
    assertThatThrownBy(() -> library.discard(PhotoId.newId()))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-not-found"));

    PhotoId forgotten = upload();
    now.set(now.get().plus(PhotoLibraryService.PENDING_FOR).plus(Duration.ofMinutes(1)));
    upload();
    assertThat(store.photos.photos).doesNotContainKey(forgotten).containsKey(kept);
  }

  /** Coa firma que dá a API: todas menos as que só usan instalacións de baixa. */
  @Test
  void photosOnlyUsedByWithdrawnFacilitiesAreHidden() {
    FacilityId id = facility("Campo");
    PhotoId photo = upload();
    assertThat(service.photo(photo, PhotoVariant.THUMB, signature(photo)).bytes())
        .as("pendente: vese para a vista previa")
        .containsExactly(1, 2, 3);
    service.arrange(new ArrangeImagesCommand(id, 0, List.of(photo(photo, "Campo"))));
    facilityService.changeStatus(
        new ChangeFacilityStatusCommand(id, 1, FacilityStatus.WITHDRAWN, List.of()));

    assertThatThrownBy(() -> service.photo(photo, PhotoVariant.THUMB, signature(photo)))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-not-found"));
    PhotoId unknown = PhotoId.newId();
    assertThatThrownBy(() -> service.photo(unknown, PhotoVariant.LARGE, signature(unknown)))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-not-found"));
  }

  /**
   * ADR-0024: sen firma (ou cunha doutra foto ou caducada) só se ven as fotos das instalacións que
   * están no público; as pendentes e as dos borradores, non.
   */
  @Test
  void withoutTheSignatureOnlyPhotosOfPublicFacilitiesAreServed() {
    FacilityId id = facility("Pista");
    PhotoId photo = upload();
    PhotoId other = upload();
    assertNotFound(photo, null);
    assertNotFound(photo, "non-e-unha-firma");
    assertNotFound(photo, signature(other));
    service.arrange(new ArrangeImagesCommand(id, 0, List.of(photo(photo, "Pista"))));
    assertNotFound(photo, null);

    facilityService.publish(new PublishFacilityCommand(id, 1));
    assertThat(service.photo(photo, PhotoVariant.LARGE, null).bytes()).containsExactly(1, 2, 3);
    assertNotFound(other, null);

    // Unha firma vale a súa semana e a seguinte; despois, só se é pública.
    String old = signature(other);
    now.set(now.get().plus(Duration.ofDays(15)));
    assertNotFound(other, old);
    assertThat(service.photo(other, PhotoVariant.LARGE, signature(other)).bytes())
        .containsExactly(1, 2, 3);
  }

  String signature(PhotoId photo) {
    return PhotoUrlSignature.sign(TenantContext.require().id(), photo.value(), now.get());
  }

  void assertNotFound(PhotoId photo, String signature) {
    assertThatThrownBy(() -> service.photo(photo, PhotoVariant.THUMB, signature))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-not-found"));
  }

  @Test
  void drawingsAreProcessedOnceAndListedAlphabetically() {
    assertThat(service.drawing("pool", PhotoVariant.LARGE).bytes()).containsExactly(9, 9);
    service.drawing("pool", PhotoVariant.THUMB);
    assertThat(processed).hasSize(1);
    assertThat(library.drawings(Language.GL))
        .extracting(d -> d.name().gl())
        .isSorted()
        .hasSize(DrawingSuggestion.CODES.size());
  }

  @Test
  void anUploadTooLargeIsRejectedBeforeDecoding() {
    assertThatThrownBy(
            () ->
                library.upload(new UploadPhotoCommand(new byte[PhotoLibraryService.MAX_BYTES + 1])))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-too-large"));
    assertThat(processed).isEmpty();
  }
}
