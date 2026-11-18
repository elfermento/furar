// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import java.util.List;
import java.util.Objects;

/**
 * As imaxes dunha instalación: as gardadas (na orde da galería, a primeira é a portada) e o debuxo
 * suxerido, que é o que se mostra se non hai ningunha gardada (especificación, bloque 1, «Fotos»:
 * nunca sen imaxe).
 */
public record FacilityGallery(List<ImageView> stored, ImageView suggestion) {

  public FacilityGallery {
    stored = List.copyOf(stored);
    Objects.requireNonNull(suggestion, "suggestion");
  }

  /** O que se ve: as gardadas ou, se non hai, o suxerido. */
  public List<ImageView> shown() {
    return stored.isEmpty() ? List.of(suggestion) : stored;
  }

  public ImageView cover() {
    return shown().getFirst();
  }
}
