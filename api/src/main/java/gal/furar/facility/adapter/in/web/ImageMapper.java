// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.DrawingNameResponse;
import gal.furar.facility.adapter.in.web.dto.DrawingResponse;
import gal.furar.facility.adapter.in.web.dto.ImageKindValue;
import gal.furar.facility.adapter.in.web.dto.ImageResponse;
import gal.furar.facility.adapter.in.web.dto.LibraryPhotoResponse;
import gal.furar.facility.adapter.in.web.dto.PhotoAltResponse;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.in.LibraryPhoto;
import gal.furar.facility.domain.Drawing;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoUrlSignature;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Imaxes ↔ DTO, coas rutas dos bytes ({@code …/photos/{id}/large}, {@code …/drawings/{code}/thumb})
 * no concello da petición. As fotos da xestión levan a firma ({@code ?s=…}, {@link
 * PhotoUrlSignature}) para verse aínda que a instalación non estea no público; as das respostas
 * públicas van sen ela.
 */
final class ImageMapper {

  private ImageMapper() {}

  static String base() {
    return "/api/v1/municipalities/"
        + Objects.requireNonNull(TenantContext.require().slug(), "slug");
  }

  /** Ruta asinada dos bytes dunha foto. */
  static String photoUrl(String photoId, String variant) {
    return photoUrl(photoId, variant, true);
  }

  static String photoUrl(String photoId, String variant, boolean signed) {
    String url = base() + "/photos/" + photoId + "/" + variant;
    if (!signed) {
      return url;
    }
    String signature =
        PhotoUrlSignature.sign(
            TenantContext.require().id(), UUID.fromString(photoId), Instant.now());
    return url + "?" + PhotoUrlSignature.PARAM + "=" + signature;
  }

  static String drawingUrl(String code, String variant) {
    return base() + "/drawings/" + code + "/" + variant;
  }

  /** Para a xestión: as fotos con firma. */
  static ImageResponse toResponse(ImageView view) {
    return toResponse(view, true);
  }

  /** Para as respostas públicas: só fotos que xa se ven no público, sen firma. */
  static ImageResponse toPublicResponse(ImageView view) {
    return toResponse(view, false);
  }

  private static ImageResponse toResponse(ImageView view, boolean signed) {
    boolean photo = view.ref() instanceof ImageRef.OfPhoto;
    String id =
        switch (view.ref()) {
          case ImageRef.OfPhoto p -> p.id().value().toString();
          case ImageRef.OfDrawing d -> d.code();
        };
    return new ImageResponse(
        photo ? ImageKindValue.PHOTO : ImageKindValue.DRAWING,
        id,
        alt(view.alt()),
        view.width(),
        view.height(),
        photo ? photoUrl(id, "large", signed) : drawingUrl(id, "large"),
        photo ? photoUrl(id, "thumb", signed) : drawingUrl(id, "thumb"),
        view.createdAt(),
        name(view.name()));
  }

  static LibraryPhotoResponse toResponse(LibraryPhoto library) {
    return toResponse(library.photo(), library.usedIn());
  }

  static LibraryPhotoResponse toResponse(Photo photo, java.util.List<String> usedIn) {
    String id = photo.id().value().toString();
    return new LibraryPhotoResponse(
        photo.id().value(),
        photo.alt() == null ? null : alt(photo.alt()),
        photo.width(),
        photo.height(),
        photo.createdAt(),
        photoUrl(id, "large"),
        photoUrl(id, "thumb"),
        photo.pending(),
        usedIn);
  }

  static DrawingResponse toResponse(Drawing drawing) {
    return new DrawingResponse(
        drawing.code(),
        new DrawingNameResponse(drawing.name().gl(), drawing.name().es()),
        alt(drawing.alt()),
        drawing.width(),
        drawing.height(),
        drawingUrl(drawing.code(), "large"),
        drawingUrl(drawing.code(), "thumb"));
  }

  static PhotoAltResponse alt(LocalizedText alt) {
    return new PhotoAltResponse(alt.gl(), alt.es());
  }

  private static @Nullable DrawingNameResponse name(@Nullable LocalizedText name) {
    return name == null ? null : new DrawingNameResponse(name.gl(), name.es());
  }
}
