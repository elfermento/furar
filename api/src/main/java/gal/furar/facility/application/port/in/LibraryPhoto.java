// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Photo;
import java.util.List;
import java.util.Objects;

/** Unha foto da biblioteca do concello e os nomes das instalacións que a usan (alfabética). */
public record LibraryPhoto(Photo photo, List<String> usedIn) {

  public LibraryPhoto {
    Objects.requireNonNull(photo, "photo");
    usedIn = List.copyOf(usedIn);
  }
}
