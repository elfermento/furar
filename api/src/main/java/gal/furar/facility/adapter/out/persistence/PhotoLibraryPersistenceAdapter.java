// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.PhotoRepository;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.facility.domain.ProcessedPhoto;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Biblioteca de fotos con JPA, sempre dentro dun concello (A7b). Borrar unha foto borra na base os
 * seus bytes e as súas imaxes nas instalacións ({@code on delete cascade}).
 */
@Component
class PhotoLibraryPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements PhotoRepository {

  private final LibraryPhotoJpaRepository photos;
  private final LibraryPhotoContentJpaRepository contents;

  PhotoLibraryPersistenceAdapter(
      LibraryPhotoJpaRepository photos, LibraryPhotoContentJpaRepository contents) {
    this.photos = photos;
    this.contents = contents;
  }

  @Override
  public List<Photo> library() {
    tenant();
    return photos.findByPendingUntilIsNullOrderByCreatedAtDescIdDesc().stream()
        .map(PhotoLibraryPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public Map<PhotoId, Photo> findAll(Collection<PhotoId> ids) {
    tenant();
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<PhotoId, Photo> found = new HashMap<>();
    photos
        .findAllById(ids.stream().map(PhotoId::value).toList())
        .forEach(row -> found.put(new PhotoId(row.getId()), toDomain(row)));
    return found;
  }

  @Override
  public void add(Photo photo, ProcessedPhoto content) {
    tenant();
    photos.saveAndFlush(entity(photo));
    List<LibraryPhotoContentEntity> bytes = new ArrayList<>();
    for (PhotoVariant variant : PhotoVariant.values()) {
      bytes.add(
          new LibraryPhotoContentEntity(
              photo.id().value(), variant, content.file(variant).bytes()));
    }
    contents.saveAllAndFlush(bytes);
  }

  @Override
  public void update(Collection<Photo> changed) {
    tenant();
    if (changed.isEmpty()) {
      return;
    }
    Map<java.util.UUID, Photo> byId = new HashMap<>();
    changed.forEach(p -> byId.put(p.id().value(), p));
    List<LibraryPhotoEntity> rows = photos.findAllById(byId.keySet());
    for (LibraryPhotoEntity row : rows) {
      Photo photo = byId.get(row.getId());
      row.describe(alt(photo.alt()), photo.pendingUntil());
    }
    photos.saveAllAndFlush(rows);
  }

  @Override
  public void restore(Photo photo) {
    tenant();
    photos.saveAndFlush(entity(photo));
  }

  @Override
  public void delete(PhotoId id) {
    tenant();
    photos.findById(id.value()).ifPresent(photos::delete);
    photos.flush();
  }

  @Override
  public int purgeExpired(Instant now) {
    tenant();
    List<LibraryPhotoEntity> expired = photos.findByPendingUntilBefore(now);
    photos.deleteAll(expired);
    photos.flush();
    return expired.size();
  }

  @Override
  public Optional<PhotoFile> content(PhotoId photo, PhotoVariant variant) {
    tenant();
    return photos
        .findById(photo.value())
        .flatMap(
            row ->
                contents
                    .findById(new LibraryPhotoContentEntity.Key(photo.value(), variant))
                    .map(c -> new PhotoFile(c.bytes(), row.contentType())));
  }

  private static LibraryPhotoEntity entity(Photo photo) {
    LibraryPhotoEntity row =
        new LibraryPhotoEntity(
            photo.id().value(),
            photo.width(),
            photo.height(),
            photo.contentType(),
            photo.createdAt());
    row.describe(alt(photo.alt()), photo.pendingUntil());
    return row;
  }

  private static Photo toDomain(LibraryPhotoEntity row) {
    Map<String, String> alt = row.alt();
    return new Photo(
        new PhotoId(row.getId()),
        alt == null ? null : new LocalizedText(alt.get("gl"), alt.get("es")),
        row.width(),
        row.height(),
        row.contentType(),
        row.createdAt(),
        row.pendingUntil());
  }

  private static @Nullable Map<String, String> alt(@Nullable LocalizedText alt) {
    if (alt == null) {
      return null;
    }
    Map<String, String> json = new LinkedHashMap<>();
    json.put("gl", alt.gl());
    json.put("es", alt.es());
    return json;
  }
}
