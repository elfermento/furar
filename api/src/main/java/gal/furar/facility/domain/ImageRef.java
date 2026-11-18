// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Objects;

/** Unha imaxe da lista dunha instalación: unha foto da biblioteca do concello ou un debuxo. */
public sealed interface ImageRef {

  static ImageRef photo(PhotoId id) {
    return new OfPhoto(id);
  }

  static ImageRef drawing(String code) {
    return new OfDrawing(code);
  }

  /** Foto da biblioteca do concello. */
  record OfPhoto(PhotoId id) implements ImageRef {
    public OfPhoto {
      Objects.requireNonNull(id, "id");
    }
  }

  /** Debuxo de Furar, polo código. */
  record OfDrawing(String code) implements ImageRef {
    public OfDrawing {
      Objects.requireNonNull(code, "code");
    }
  }
}
