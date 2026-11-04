// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.FacilityImageRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityImages;
import gal.furar.facility.domain.ImageRef;
import gal.furar.facility.domain.PhotoId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Listas de imaxes en memoria (un só concello). */
class InMemoryFacilityImageRepository implements FacilityImageRepository {

  final Map<FacilityId, FacilityImages> lists = new LinkedHashMap<>();

  @Override
  public FacilityImages load(FacilityId facility) {
    return lists.getOrDefault(facility, FacilityImages.NONE);
  }

  @Override
  public Map<FacilityId, FacilityImages> loadAll(Collection<FacilityId> facilities) {
    Map<FacilityId, FacilityImages> found = new LinkedHashMap<>();
    facilities.forEach(
        id -> {
          if (lists.containsKey(id)) {
            found.put(id, lists.get(id));
          }
        });
    return found;
  }

  @Override
  public void replace(FacilityId facility, FacilityImages images) {
    if (images.isEmpty()) {
      lists.remove(facility);
    } else {
      lists.put(facility, images);
    }
  }

  @Override
  public Map<PhotoId, List<FacilityId>> photoUsage() {
    Map<PhotoId, List<FacilityId>> usage = new LinkedHashMap<>();
    lists.forEach(
        (facility, images) ->
            images
                .photos()
                .forEach(p -> usage.computeIfAbsent(p, k -> new ArrayList<>()).add(facility)));
    return usage;
  }

  @Override
  public List<FacilityId> usingPhoto(PhotoId photo) {
    return photoUsage().getOrDefault(photo, List.of());
  }

  /** Coma o {@code on delete cascade} da base. */
  void forget(PhotoId photo) {
    lists.replaceAll(
        (id, images) ->
            new FacilityImages(
                images.images().stream().filter(i -> !i.equals(ImageRef.photo(photo))).toList()));
  }
}
