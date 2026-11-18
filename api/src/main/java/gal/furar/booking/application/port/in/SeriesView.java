// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.Series;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha tempada cos nomes de agora (ADR-0039, punto 11).
 *
 * @param sessions sesións confirmadas
 * @param cancelled sesións canceladas
 */
public record SeriesView(
    Series series,
    String organizationName,
    String facilityName,
    String useName,
    @Nullable String partName,
    int sessions,
    int cancelled) {

  public SeriesView {
    Objects.requireNonNull(series, "series");
    Objects.requireNonNull(organizationName, "organizationName");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
  }
}
