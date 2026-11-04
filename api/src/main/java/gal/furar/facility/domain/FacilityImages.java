// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * As imaxes dunha instalación, na orde da galería (especificación, bloque 1, «Fotos»): fotos da
 * biblioteca do concello e debuxos de Furar. **A primeira é a portada.** Inmutable. Invariantes:
 * como moito {@value #MAX} e sen a mesma imaxe dúas veces. Baleira = a instalación mostra o debuxo
 * suxerido ({@link DrawingSuggestion}), así nunca queda sen imaxe. Gárdase coa versión da
 * instalación ({@code If-Match}).
 */
public record FacilityImages(List<ImageRef> images) {

  public static final int MAX = 12;
  public static final FacilityImages NONE = new FacilityImages(List.of());

  public FacilityImages {
    images = List.copyOf(images);
    if (images.size() > MAX) {
      throw new DomainException(FacilityError.PHOTO_LIMIT, Map.of("max", MAX));
    }
    Set<ImageRef> seen = new HashSet<>();
    for (ImageRef image : images) {
      if (!seen.add(image)) {
        throw new DomainException(FacilityError.IMAGES_INVALID);
      }
    }
  }

  public boolean isEmpty() {
    return images.isEmpty();
  }

  /** A portada, se a lista non está baleira. */
  public Optional<ImageRef> cover() {
    return images.stream().findFirst();
  }

  /** As fotos da biblioteca que usa, na orde da lista. */
  public List<PhotoId> photos() {
    return images.stream()
        .filter(ImageRef.OfPhoto.class::isInstance)
        .map(i -> ((ImageRef.OfPhoto) i).id())
        .toList();
  }
}
