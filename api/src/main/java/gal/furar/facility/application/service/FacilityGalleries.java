// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.in.FacilityGallery;
import gal.furar.facility.application.port.in.ImageView;
import gal.furar.facility.application.port.out.DrawingRepository;
import gal.furar.facility.application.port.out.FacilityImageRepository;
import gal.furar.facility.application.port.out.PhotoRepository;
import gal.furar.facility.domain.Drawing;
import gal.furar.facility.domain.DrawingSuggestion;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolve as imaxes das instalacións para pintalas (especificación, bloque 1, «Fotos»): a lista
 * gardada coas fotos da biblioteca e os debuxos co seu texto, e o debuxo suxerido. Por lote: unha
 * consulta por táboa, nunca unha por instalación. Dentro da transacción de quen chama.
 */
@Component
public class FacilityGalleries {

  private final FacilityImageRepository images;
  private final PhotoRepository photos;
  private final DrawingRepository drawings;

  public FacilityGalleries(
      FacilityImageRepository images, PhotoRepository photos, DrawingRepository drawings) {
    this.images = images;
    this.photos = photos;
    this.drawings = drawings;
  }

  public FacilityGallery of(Facility facility) {
    return of(List.of(facility)).get(facility.id());
  }

  /** A galería de cada instalación pedida. */
  public Map<FacilityId, FacilityGallery> of(Collection<Facility> facilities) {
    if (facilities.isEmpty()) {
      return Map.of();
    }
    Map<FacilityId, FacilityImages> stored =
        images.loadAll(facilities.stream().map(Facility::id).toList());
    Set<PhotoId> photoIds = new HashSet<>();
    stored.values().forEach(list -> photoIds.addAll(list.photos()));
    Map<PhotoId, Photo> found = photoIds.isEmpty() ? Map.of() : photos.findAll(photoIds);
    Map<String, Drawing> catalog =
        drawings.findAll().stream().collect(Collectors.toMap(Drawing::code, Function.identity()));
    Map<FacilityId, FacilityGallery> result = new HashMap<>();
    for (Facility facility : facilities) {
      List<ImageView> list = new ArrayList<>();
      for (ImageRef ref : stored.getOrDefault(facility.id(), FacilityImages.NONE).images()) {
        ImageView view = view(ref, found, catalog);
        if (view != null) {
          list.add(view);
        }
      }
      Drawing suggested = catalog.get(DrawingSuggestion.suggest(facility));
      if (suggested == null) {
        suggested = Objects.requireNonNull(catalog.get(DrawingSuggestion.GENERIC), "generic");
      }
      result.put(facility.id(), new FacilityGallery(list, view(suggested)));
    }
    return result;
  }

  static ImageView view(Drawing drawing) {
    return new ImageView(
        ImageRef.drawing(drawing.code()),
        drawing.alt(),
        drawing.width(),
        drawing.height(),
        null,
        drawing.name());
  }

  static ImageView view(Photo photo) {
    return new ImageView(
        ImageRef.photo(photo.id()),
        Objects.requireNonNull(photo.alt(), "alt"),
        photo.width(),
        photo.height(),
        photo.createdAt(),
        null);
  }

  private static @Nullable ImageView view(
      ImageRef ref, Map<PhotoId, Photo> photos, Map<String, Drawing> drawings) {
    return switch (ref) {
      case ImageRef.OfPhoto p -> {
        Photo photo = photos.get(p.id());
        yield photo == null || photo.alt() == null ? null : view(photo);
      }
      case ImageRef.OfDrawing d -> {
        Drawing drawing = drawings.get(d.code());
        yield drawing == null ? null : view(drawing);
      }
    };
  }
}
