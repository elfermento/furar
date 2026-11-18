// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.SeriesRequest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Unha solicitude de tempada cos nomes de agora (ADR-0039, puntos 10 e 11). */
public record SeriesRequestView(
    SeriesRequest request,
    String organizationName,
    String facilityName,
    String useName,
    @Nullable String partName) {

  public SeriesRequestView {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(organizationName, "organizationName");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
  }
}
