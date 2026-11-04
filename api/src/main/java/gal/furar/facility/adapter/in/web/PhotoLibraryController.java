// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.DrawingResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityTypeValue;
import gal.furar.facility.adapter.in.web.dto.ImageResponse;
import gal.furar.facility.adapter.in.web.dto.LibraryPhotoResponse;
import gal.furar.facility.adapter.in.web.dto.UploadLibraryPhotoRequest;
import gal.furar.facility.application.port.in.GetImageContentUseCase;
import gal.furar.facility.application.port.in.PhotoLibraryUseCase;
import gal.furar.facility.application.port.in.UploadPhotoCommand;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDetails;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoUrlSignature;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biblioteca de imaxes do concello (especificación, bloque 1, «Fotos»): as súas fotos e os debuxos
 * de Furar. A subida é {@code multipart/form-data} e só aquí se ve un {@code MultipartFile} (A11):
 * o caso de uso recibe bytes. Os límites son os de {@code spring.servlet.multipart.*} (8 MB o
 * ficheiro, 9 MB a petición; o 413 sae como {@code facility.photo-too-large}, {@link
 * FacilityProblemHandler}). Os bytes sérvense sen sesión: as fotos con caché para sempre (o id non
 * cambia de bytes) e os debuxos un día.
 */
@RestController
@Tag(name = "facilities", description = "Instalacións")
class PhotoLibraryController {

  /** O id dunha foto non cambia de bytes: un ano e {@code immutable}. */
  static final CacheControl FOREVER =
      CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

  /** Unha ruta asinada non vai ás cachés compartidas: é dunha resposta da xestión. */
  static final CacheControl FOREVER_PRIVATE =
      CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

  /** Un debuxo pode redebuxarse nunha versión nova de Furar. */
  static final CacheControl ONE_DAY = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

  private final PhotoLibraryUseCase library;
  private final GetImageContentUseCase content;

  PhotoLibraryController(PhotoLibraryUseCase library, GetImageContentUseCase content) {
    this.library = library;
    this.content = content;
  }

  @GetMapping("/api/v1/municipalities/{slug}/photos")
  @Operation(
      operationId = "listLibraryPhotos",
      summary =
          "Fotos da biblioteca do concello, da máis nova á máis vella, cos nomes das instalacións"
              + " que as usan (sen as subidas pendentes)")
  List<LibraryPhotoResponse> list(@PathVariable String slug) {
    return library.library().stream().map(ImageMapper::toResponse).toList();
  }

  @PostMapping(
      value = "/api/v1/municipalities/{slug}/photos",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      operationId = "uploadLibraryPhoto",
      summary =
          "Subir unha foto á biblioteca: JPEG ou PNG ata 8 MB e 40 Mpx. Queda pendente (fóra da"
              + " biblioteca) ata que se garda nunha instalación; se non, bórrase nun día")
  @ApiResponse(responseCode = "201", description = "Subida", useReturnTypeSchema = true)
  ResponseEntity<LibraryPhotoResponse> upload(
      @PathVariable String slug, @Valid @ModelAttribute UploadLibraryPhotoRequest form) {
    byte[] bytes;
    try {
      bytes = form.file().getBytes();
    } catch (IOException e) {
      throw new DomainException(FacilityError.PHOTO_UNREADABLE);
    }
    Photo photo = library.upload(new UploadPhotoCommand(bytes));
    LibraryPhotoResponse body = ImageMapper.toResponse(photo, List.of());
    return ResponseEntity.created(URI.create(body.large())).body(body);
  }

  @DeleteMapping("/api/v1/municipalities/{slug}/photos/{photoId}")
  @Operation(
      operationId = "discardLibraryPhoto",
      summary =
          "Descartar unha subida pendente (cancelar). Unha foto xa gardada non se descarta (409)")
  @ApiResponse(responseCode = "204", description = "Descartada")
  ResponseEntity<Void> discard(@PathVariable String slug, @PathVariable UUID photoId) {
    library.discard(new PhotoId(photoId));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/municipalities/{slug}/photos/{photoId}/{variant}")
  @PublicEndpoint
  @Operation(
      operationId = "getLibraryPhoto",
      summary =
          "Bytes dunha foto (large: 1600 px; thumb: 480 px), sen sesión e con caché immutable. Sen"
              + " firma só as das instalacións que se ven no público; coa firma da ruta que deu a"
              + " API, tamén as dos borradores e as pendentes. Nunca as que só usan instalacións de"
              + " baixa")
  @ApiResponse(
      responseCode = "200",
      description = "A imaxe, sen EXIF",
      content =
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")))
  ResponseEntity<byte[]> photo(
      @PathVariable String slug,
      @PathVariable UUID photoId,
      @PathVariable @Parameter(schema = @Schema(allowableValues = {"large", "thumb"}))
          String variant,
      @Parameter(description = "Firma que a API pon na ruta das fotos que dá na xestión")
          @RequestParam(name = PhotoUrlSignature.PARAM, required = false)
          @Nullable String s) {
    PhotoFile file = content.photo(new PhotoId(photoId), variant(variant), s);
    return ResponseEntity.ok()
        .cacheControl(s == null ? FOREVER : FOREVER_PRIVATE)
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(file.bytes());
  }

  @GetMapping("/api/v1/municipalities/{slug}/drawings")
  @Operation(
      operationId = "listDrawings",
      summary = "Debuxos de Furar (CC0), en orde alfabética segundo Accept-Language")
  List<DrawingResponse> drawings(@PathVariable String slug, HttpServletRequest request) {
    return library.drawings(Locales.language(request)).stream()
        .map(ImageMapper::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/municipalities/{slug}/drawings/suggestion")
  @Operation(
      operationId = "suggestDrawing",
      summary =
          "Debuxo suxerido para uns datos aínda sen gardar (nome, deportes do catálogo, tipo e se"
              + " é cuberta), coas mesmas regras ca o suxerido da ficha")
  ImageResponse suggestion(
      @PathVariable String slug,
      @RequestParam(required = false) @Size(max = Facility.NAME_MAX) @Nullable String name,
      @RequestParam(name = "sport", required = false) @Size(max = FacilityDetails.SPORTS_MAX)
          @Nullable List<String> sports,
      @RequestParam(required = false) @Nullable FacilityTypeValue type,
      @RequestParam(required = false) @Nullable Boolean covered) {
    return ImageMapper.toResponse(
        library.suggestedDrawing(
            name,
            sports == null ? List.of() : sports,
            type == null ? null : FacilityType.valueOf(type.name()),
            covered));
  }

  @GetMapping("/api/v1/municipalities/{slug}/drawings/{code}/{variant}")
  @PublicEndpoint
  @Operation(
      operationId = "getDrawing",
      summary = "Bytes dun debuxo de Furar (large: 1200 px; thumb: 480 px), sen sesión")
  @ApiResponse(
      responseCode = "200",
      description = "A imaxe",
      content =
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")))
  ResponseEntity<byte[]> drawing(
      @PathVariable String slug,
      @PathVariable String code,
      @PathVariable @Parameter(schema = @Schema(allowableValues = {"large", "thumb"}))
          String variant) {
    PhotoFile file = content.drawing(code, variant(variant));
    return ResponseEntity.ok()
        .cacheControl(ONE_DAY)
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(file.bytes());
  }

  private static PhotoVariant variant(String variant) {
    return switch (variant) {
      case "large" -> PhotoVariant.LARGE;
      case "thumb" -> PhotoVariant.THUMB;
      default -> throw new DomainException(FacilityError.PHOTO_NOT_FOUND);
    };
  }
}
