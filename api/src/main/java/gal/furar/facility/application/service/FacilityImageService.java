// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.FacilityPermission;
import gal.furar.facility.application.port.in.ArrangeImagesCommand;
import gal.furar.facility.application.port.in.ArrangeImagesCommand.ImageInput;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.GetFacilityUseCase;
import gal.furar.facility.application.port.in.GetImageContentUseCase;
import gal.furar.facility.application.port.in.ManageFacilityImagesUseCase;
import gal.furar.facility.application.port.out.DrawingFiles;
import gal.furar.facility.application.port.out.DrawingRepository;
import gal.furar.facility.application.port.out.FacilityImageRepository;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.PhotoProcessor;
import gal.furar.facility.application.port.out.PhotoRepository;
import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoUrlSignature;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.facility.domain.ProcessedPhoto;
import gal.furar.facility.domain.Requirement;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sección Fotos (especificación, bloque 1, «Fotos»): garda a lista de imaxes da instalación nun só
 * paso co {@code If-Match} da instalación. As fotos novas xa se subiron á biblioteca (pendentes) e
 * aquí pasan a ela co seu texto alternativo; o texto vai coa foto e cámbiase en todas as
 * instalacións que a usan. As imaxes non cambian a reservabilidade: non toman {@code FacilityLock}.
 * Tamén serve os bytes, sen sesión.
 */
@Service
public class FacilityImageService implements ManageFacilityImagesUseCase, GetImageContentUseCase {

  static final String ARRANGE = "facility.images.arrange";

  private final FacilityRepository facilities;
  private final FacilityImageRepository images;
  private final PhotoRepository photos;
  private final DrawingRepository drawings;
  private final DrawingFiles drawingFiles;
  private final PhotoProcessor processor;
  private final GetFacilityUseCase views;
  private final TransactionOperations transaction;
  private final FacilityDrafts drafts;
  private final AuditLog audit;
  private final Clock clock;

  /** Os debuxos reencodados (grande e miniatura): son poucos e non cambian mentres corre a API. */
  private final Map<String, ProcessedPhoto> drawingCache = new ConcurrentHashMap<>();

  /** Os {@link RequirementProbe} poden faltar: entón ningunha foto é pública sen firma. */
  @Autowired
  public FacilityImageService(
      FacilityRepository facilities,
      FacilityImageRepository images,
      PhotoRepository photos,
      DrawingRepository drawings,
      DrawingFiles drawingFiles,
      PhotoProcessor processor,
      GetFacilityUseCase views,
      ObjectProvider<RequirementProbe> probes,
      TransactionOperations transaction,
      AuditLog audit,
      Clock clock) {
    this(
        facilities,
        images,
        photos,
        drawings,
        drawingFiles,
        processor,
        views,
        probes.orderedStream().toList(),
        transaction,
        audit,
        clock);
  }

  public FacilityImageService(
      FacilityRepository facilities,
      FacilityImageRepository images,
      PhotoRepository photos,
      DrawingRepository drawings,
      DrawingFiles drawingFiles,
      PhotoProcessor processor,
      GetFacilityUseCase views,
      List<RequirementProbe> probes,
      TransactionOperations transaction,
      AuditLog audit,
      Clock clock) {
    this.drafts = new FacilityDrafts(probes);
    this.facilities = facilities;
    this.images = images;
    this.photos = photos;
    this.drawings = drawings;
    this.drawingFiles = drawingFiles;
    this.processor = processor;
    this.views = views;
    this.transaction = transaction;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public FacilityView arrange(ArrangeImagesCommand command) {
    TenantRef tenant = TenantContext.require();
    try {
      FacilityView view = doArrange(command);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", command.id().value().toString());
      details.put("images", command.images().size());
      audit.record(new AuditEvent(ARRANGE, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return view;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", command.id().value().toString());
      details.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(ARRANGE, Outcome.FAILURE, tenant.id(), tenant.slug(), details));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }

  private FacilityView doArrange(ArrangeImagesCommand command) {
    List<ImageRef> refs = new ArrayList<>();
    Map<PhotoId, LocalizedText> alts = new LinkedHashMap<>();
    for (int i = 0; i < command.images().size(); i++) {
      ImageInput input = command.images().get(i);
      if (input == null || (input.photoId() == null) == (input.drawing() == null)) {
        throw new DomainException(FacilityError.IMAGES_INVALID);
      }
      if (input.photoId() != null) {
        PhotoId id = new PhotoId(input.photoId());
        refs.add(ImageRef.photo(id));
        alts.put(id, Photo.alt("images[" + i + "].alt", input.altGl(), input.altEs()));
      } else {
        refs.add(ImageRef.drawing(Objects.requireNonNull(input.drawing())));
      }
    }
    FacilityImages next = new FacilityImages(refs);
    transaction.executeWithoutResult(
        status -> {
          Facility current =
              facilities
                  .findById(command.id())
                  .orElseThrow(() -> new DomainException(FacilityError.NOT_FOUND));
          if (current.version() == null || current.version() != command.expectedVersion()) {
            throw new DomainException(CommonError.STALE_VERSION);
          }
          Map<PhotoId, Photo> found = photos.findAll(alts.keySet());
          if (found.size() != alts.size()) {
            throw new DomainException(FacilityError.IMAGES_INVALID);
          }
          for (ImageRef ref : next.images()) {
            if (ref instanceof ImageRef.OfDrawing d && drawings.find(d.code()).isEmpty()) {
              throw new DomainException(FacilityError.IMAGES_INVALID);
            }
          }
          List<Photo> changed = new ArrayList<>();
          alts.forEach(
              (id, alt) -> {
                Photo photo = found.get(id);
                if (photo.pending() || !alt.equals(photo.alt())) {
                  changed.add(photo.keep(alt));
                }
              });
          photos.update(changed);
          images.replace(command.id(), next);
          photos.purgeExpired(clock.instant());
          facilities.update(current.touch(clock.instant()));
        });
    return views.get(command.id());
  }

  /**
   * Sen sesión (ADR-0024, «Bytes públicos»). Coa firma que a API puxo na ruta ({@link
   * PhotoUrlSignature}): calquera foto do concello salvo as que só usan instalacións de baixa (plan
   * do dominio 1, §2.2), tamén as pendentes e as dos borradores. Sen ela: só as que usa algunha
   * instalación que se ve no público (non de baixa, publicada e sen nada que lle falte).
   */
  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public PhotoFile photo(PhotoId photo, PhotoVariant variant, @Nullable String signature) {
    List<FacilityId> users = images.usingPhoto(photo);
    List<Facility> using = users.isEmpty() ? List.of() : facilities.findAllById(users);
    boolean signed =
        PhotoUrlSignature.verify(
            TenantContext.require().id(), photo.value(), signature, clock.instant());
    boolean shown =
        signed
            ? using.isEmpty()
                || using.stream().anyMatch(f -> f.status() != FacilityStatus.WITHDRAWN)
            : anyPublic(using);
    if (!shown) {
      throw new DomainException(FacilityError.PHOTO_NOT_FOUND);
    }
    return photos
        .content(photo, variant)
        .orElseThrow(() -> new DomainException(FacilityError.PHOTO_NOT_FOUND));
  }

  /** Se algunha se ve no público: como o listado público, cos requisitos por lote. */
  private boolean anyPublic(List<Facility> using) {
    List<UUID> candidates =
        using.stream()
            .filter(f -> f.status() != FacilityStatus.WITHDRAWN)
            .filter(Facility::published)
            .map(f -> f.id().value())
            .toList();
    if (candidates.isEmpty()) {
      return false;
    }
    Map<UUID, Set<Requirement>> missing = drafts.missing(candidates);
    return candidates.stream().anyMatch(id -> missing.get(id).isEmpty());
  }

  /** Sen sesión: os debuxos son da plataforma. Reencódanse unha vez, como unha subida. */
  @Override
  @PublicUseCase
  public PhotoFile drawing(String code, PhotoVariant variant) {
    ProcessedPhoto processed = drawingCache.get(code);
    if (processed == null) {
      byte[] bytes =
          drawingFiles
              .read(code)
              .orElseThrow(() -> new DomainException(FacilityError.PHOTO_NOT_FOUND));
      processed = processor.process(bytes);
      drawingCache.putIfAbsent(code, processed);
    }
    return processed.file(variant);
  }
}
