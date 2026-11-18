// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.season.SeriesPlan;
import gal.furar.shared.domain.DomainException;

/**
 * 409 {@code booking.series-conflicts-changed} coa vista previa nova (ADR-0039, punto 8): baixo o
 * bloqueo, os días con choque xa non son os que viu o xestor. Non se escribiu nada.
 */
public class SeriesConflictsChangedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final transient SeriesPlan preview;

  public SeriesConflictsChangedException(SeriesPlan preview) {
    super(BookingError.SERIES_CONFLICTS_CHANGED);
    this.preview = preview;
  }

  public SeriesPlan preview() {
    return preview;
  }
}
