// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.PhotoRepository;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.facility.domain.ProcessedPhoto;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Biblioteca de fotos en memoria (un só concello). Ao borrar, quita as imaxes coma a base. */
class InMemoryPhotoRepository implements PhotoRepository {

  final Map<PhotoId, Photo> photos = new LinkedHashMap<>();
  final Map<PhotoId, ProcessedPhoto> content = new HashMap<>();
  InMemoryFacilityImageRepository images;

  @Override
  public List<Photo> library() {
    return photos.values().stream()
        .filter(p -> !p.pending())
        .sorted(Comparator.comparing(Photo::createdAt).reversed())
        .toList();
  }

  @Override
  public Map<PhotoId, Photo> findAll(Collection<PhotoId> ids) {
    Map<PhotoId, Photo> found = new HashMap<>();
    ids.forEach(
        id -> {
          if (photos.containsKey(id)) {
            found.put(id, photos.get(id));
          }
        });
    return found;
  }

  @Override
  public void add(Photo photo, ProcessedPhoto bytes) {
    photos.put(photo.id(), photo);
    content.put(photo.id(), bytes);
  }

  @Override
  public void update(Collection<Photo> changed) {
    changed.forEach(p -> photos.put(p.id(), p));
  }

  @Override
  public void restore(Photo photo) {
    photos.put(photo.id(), photo);
  }

  @Override
  public void delete(PhotoId id) {
    photos.remove(id);
    content.remove(id);
    if (images != null) {
      images.forget(id);
    }
  }

  @Override
  public int purgeExpired(Instant now) {
    List<PhotoId> expired =
        photos.values().stream()
            .filter(p -> p.pendingUntil() != null && p.pendingUntil().isBefore(now))
            .map(Photo::id)
            .toList();
    expired.forEach(this::delete);
    return expired.size();
  }

  @Override
  public Optional<PhotoFile> content(PhotoId photo, PhotoVariant variant) {
    return Optional.ofNullable(content.get(photo)).map(p -> p.file(variant));
  }
}
