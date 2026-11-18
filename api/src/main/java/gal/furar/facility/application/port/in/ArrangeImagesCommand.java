// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Gardar as imaxes da instalación: todas na nova orde (a primeira é a portada). Cada unha é unha
 * foto da biblioteca, co seu texto alternativo, ou un debuxo. Lista baleira = o debuxo suxerido.
 */
public record ArrangeImagesCommand(FacilityId id, long expectedVersion, List<ImageInput> images) {

  public ArrangeImagesCommand {
    images = images == null ? List.of() : List.copyOf(images);
  }

  /** Unha imaxe da lista: {@code photoId} (con texto alternativo) ou {@code drawing}. */
  public record ImageInput(
      @Nullable UUID photoId,
      @Nullable String drawing,
      @Nullable String altGl,
      @Nullable String altEs) {}
}
