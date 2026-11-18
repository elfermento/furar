// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.booking.SeriesNotice;
import gal.furar.booking.SeriesRequestNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.shared.domain.Language;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Escribe os correos das tempadas á entidade (ADR-0039, punto 12), no idioma do concello. */
public interface SeriesMailComposer {

  /**
   * «Tempada asignada» (ou «Solicitude aprobada»), ou «Tempada rematada» con {@code endedFrom}.
   *
   * @param endedFrom desde cando se rematou; nulo no de creada
   * @param cancelled sesións canceladas ao rematala
   */
  OutgoingMail series(
      SeriesNotice notice,
      @Nullable LocalDate endedFrom,
      int cancelled,
      Language language,
      MunicipalityMailContext municipality);

  /** «Solicitude de tempada non aprobada», coa mensaxe do concello tal cal. */
  OutgoingMail rejected(
      SeriesRequestNotice notice, Language language, MunicipalityMailContext municipality);
}
