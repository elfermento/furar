// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.FacilityPermission;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.in.LibraryPhoto;
import gal.furar.facility.application.port.in.PhotoLibraryUseCase;
import gal.furar.facility.application.port.in.UploadPhotoCommand;
import gal.furar.facility.application.port.out.DrawingRepository;
import gal.furar.facility.application.port.out.FacilityImageRepository;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.PhotoProcessor;
import gal.furar.facility.application.port.out.PhotoRepository;
import gal.furar.facility.domain.Drawing;
import gal.furar.facility.domain.DrawingSuggestion;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.ProcessedPhoto;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Biblioteca de imaxes (especificación, bloque 1, «Fotos»). Subir reencoda a imaxe **fóra** da
 * transacción (non se ten unha conexión aberta mentres se decodifica) e despois, nunha transacción
 * curta, garda a foto **pendente**: non entra na biblioteca ata que o «Gardar» dunha instalación a
 * usa, así cancelar o modal non deixa nada. As pendentes que ninguén garda bórranse pasadas {@link
 * #PENDING_FOR} (ao subir ou gardar outra). Subir e descartar quedan no rexistro de actividade.
 */
@Service
public class PhotoLibraryService implements PhotoLibraryUseCase {

  static final String UPLOAD = "facility.photo.upload";
  static final String DISCARD = "facility.photo.discard";

  /**
   * O mesmo límite ca {@code spring.servlet.multipart.max-file-size}, por se chega por outro lado.
   */
  public static final int MAX_BYTES = 8 * 1024 * 1024;

  /** Canto agarda unha subida a que a garde unha instalación. */
  public static final Duration PENDING_FOR = Duration.ofHours(24);

  private final PhotoRepository photos;
  private final FacilityImageRepository images;
  private final FacilityRepository facilities;
  private final DrawingRepository drawings;
  private final PhotoProcessor processor;
  private final TransactionOperations transaction;
  private final AuditLog audit;
  private final Clock clock;

  public PhotoLibraryService(
      PhotoRepository photos,
      FacilityImageRepository images,
      FacilityRepository facilities,
      DrawingRepository drawings,
      PhotoProcessor processor,
      TransactionOperations transaction,
      AuditLog audit,
      Clock clock) {
    this.photos = photos;
    this.images = images;
    this.facilities = facilities;
    this.drawings = drawings;
    this.processor = processor;
    this.transaction = transaction;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public Photo upload(UploadPhotoCommand command) {
    return audited(
        UPLOAD,
        null,
        () -> {
          if (command.content().length > MAX_BYTES) {
            throw new DomainException(FacilityError.PHOTO_TOO_LARGE, Map.of("max", "8 MB"));
          }
          ProcessedPhoto image = processor.process(command.content());
          Instant now = clock.instant();
          Photo photo =
              Photo.uploaded(
                  PhotoId.newId(),
                  image.width(),
                  image.height(),
                  image.large().contentType(),
                  now,
                  now.plus(PENDING_FOR));
          transaction.executeWithoutResult(
              status -> {
                photos.purgeExpired(now);
                photos.add(photo, image);
              });
          return photo;
        });
  }

  @Override
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public void discard(PhotoId id) {
    audited(
        DISCARD,
        id,
        () ->
            transaction.execute(
                status -> {
                  Photo photo =
                      photos.findAll(List.of(id)).values().stream()
                          .findFirst()
                          .orElseThrow(() -> new DomainException(FacilityError.PHOTO_NOT_FOUND));
                  if (!photo.pending()) {
                    throw new DomainException(FacilityError.PHOTO_NOT_PENDING);
                  }
                  photos.delete(id);
                  return photo;
                }));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public List<LibraryPhoto> library() {
    List<Photo> all = photos.library();
    Map<PhotoId, List<FacilityId>> usage = images.photoUsage();
    Set<FacilityId> used = new HashSet<>();
    usage.values().forEach(used::addAll);
    Map<FacilityId, String> names =
        facilities.findAllById(List.copyOf(used)).stream()
            .collect(Collectors.toMap(Facility::id, Facility::name));
    Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
    return all.stream()
        .map(
            p ->
                new LibraryPhoto(
                    p,
                    usage.getOrDefault(p.id(), List.of()).stream()
                        .map(names::get)
                        .filter(Objects::nonNull)
                        .sorted(collator)
                        .toList()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public List<Drawing> drawings(Language language) {
    Collator collator = Collator.getInstance(Locale.forLanguageTag(language.code()));
    Function<Drawing, String> name = d -> d.name().in(language);
    return drawings.findAll().stream().sorted(Comparator.comparing(name, collator)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public ImageView suggestedDrawing(
      @Nullable String name,
      List<String> sports,
      @Nullable FacilityType type,
      @Nullable Boolean covered) {
    List<SportRef> refs =
        sports.stream().filter(code -> !code.isBlank()).map(SportRef::catalog).toList();
    String code = DrawingSuggestion.suggest(name, refs, type, covered);
    Map<String, Drawing> catalog =
        drawings.findAll().stream().collect(Collectors.toMap(Drawing::code, Function.identity()));
    Drawing drawing = catalog.get(code);
    if (drawing == null) {
      drawing = Objects.requireNonNull(catalog.get(DrawingSuggestion.GENERIC), "generic");
    }
    return FacilityGalleries.view(drawing);
  }

  private <T> T audited(String action, @Nullable PhotoId id, Supplier<T> work) {
    TenantRef tenant = TenantContext.require();
    try {
      T result = work.get();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("photoId", (id != null ? id : ((Photo) result).id()).value().toString());
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return result;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      if (id != null) {
        details.put("photoId", id.value().toString());
      }
      details.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(action, Outcome.FAILURE, tenant.id(), tenant.slug(), details));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }
}
