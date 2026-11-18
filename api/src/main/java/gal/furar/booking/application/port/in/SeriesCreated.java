// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.Objects;

/**
 * A tempada creada.
 *
 * @param created falso se xa se creara antes coa mesma clave e a mesma entrada
 */
public record SeriesCreated(SeriesView view, boolean created) {

  public SeriesCreated {
    Objects.requireNonNull(view, "view");
  }
}
