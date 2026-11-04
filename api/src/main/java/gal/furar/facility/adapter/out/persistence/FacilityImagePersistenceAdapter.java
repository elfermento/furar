// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.FacilityImageRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.PhotoId;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Listas de imaxes das instalacións con JPA, sempre dentro dun concello (A7b). Gardar borra as
 * filas da instalación e fai {@code flush} antes de inserir as novas (Hibernate executa os INSERT
 * antes cós DELETE, ADR-0022 punto 11), así os índices únicos non ven a mesma imaxe dúas veces.
 */
@Component
class FacilityImagePersistenceAdapter extends TenantScopedPersistenceAdapter
    implements FacilityImageRepository {

  private final FacilityImageJpaRepository rows;

  FacilityImagePersistenceAdapter(FacilityImageJpaRepository rows) {
    this.rows = rows;
  }

  @Override
  public FacilityImages load(FacilityId facility) {
    return loadAll(List.of(facility)).getOrDefault(facility, FacilityImages.NONE);
  }

  @Override
  public Map<FacilityId, FacilityImages> loadAll(Collection<FacilityId> facilities) {
    tenant();
    if (facilities.isEmpty()) {
      return Map.of();
    }
    Map<FacilityId, List<ImageRef>> lists = new LinkedHashMap<>();
    rows.findByFacilityIdInOrderByPositionAsc(facilities.stream().map(FacilityId::value).toList())
        .forEach(
            row ->
                lists
                    .computeIfAbsent(new FacilityId(row.facilityId()), k -> new ArrayList<>())
                    .add(toDomain(row)));
    Map<FacilityId, FacilityImages> result = new LinkedHashMap<>();
    lists.forEach((id, list) -> result.put(id, new FacilityImages(list)));
    return result;
  }

  @Override
  public void replace(FacilityId facility, FacilityImages images) {
    tenant();
    rows.deleteAll(rows.findByFacilityIdInOrderByPositionAsc(List.of(facility.value())));
    rows.flush();
    List<FacilityImageEntity> next = new ArrayList<>();
    int position = 0;
    for (ImageRef image : images.images()) {
      UUID photo = image instanceof ImageRef.OfPhoto p ? p.id().value() : null;
      String drawing = image instanceof ImageRef.OfDrawing d ? d.code() : null;
      next.add(new FacilityImageEntity(Ids.newV7(), facility.value(), position++, photo, drawing));
    }
    rows.saveAllAndFlush(next);
  }

  @Override
  public Map<PhotoId, List<FacilityId>> photoUsage() {
    tenant();
    Map<PhotoId, List<FacilityId>> usage = new LinkedHashMap<>();
    for (FacilityImageEntity row : rows.findByPhotoIdIsNotNull()) {
      usage
          .computeIfAbsent(
              new PhotoId(Objects.requireNonNull(row.photoId())), k -> new ArrayList<>())
          .add(new FacilityId(row.facilityId()));
    }
    return usage;
  }

  @Override
  public List<FacilityId> usingPhoto(PhotoId photo) {
    tenant();
    return rows.findByPhotoId(photo.value()).stream()
        .map(row -> new FacilityId(row.facilityId()))
        .toList();
  }

  private static ImageRef toDomain(FacilityImageEntity row) {
    UUID photo = row.photoId();
    return photo != null
        ? ImageRef.photo(new PhotoId(photo))
        : ImageRef.drawing(Objects.requireNonNull(row.drawingCode()));
  }
}
