// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * As tempadas das entidades (ADR-0039, puntos 8 e 11; permiso {@code booking.organizations}).
 * Crear, rematar e o pago quedan no rexistro de actividade.
 */
public interface ManageSeriesUseCase {

  /** O que crearía: as sesións libres e os días que non se crean. Non escribe nin bloquea. */
  SeriesPlan preview(SeriesCommand command);

  /**
   * Crea a tempada e as súas sesións libres nunha transacción, baixo o bloqueo da instalación e
   * despois o da entidade.
   *
   * @throws SeriesConflictsChangedException se os días con choque xa non son {@code skip}
   */
  SeriesCreated create(CreateSeriesCommand command);

  /** As activas ou as rematadas, coa entidade e os nomes de agora. */
  List<SeriesView> list(boolean ended);

  /** 404 {@code booking.series-not-found}. */
  SeriesView get(UUID id);

  /**
   * Cancela as sesións confirmadas desde ese día (en lote, un só correo á entidade) e garda desde
   * cando. Repetilo non cancela nada máis. 422 {@code booking.series-end-invalid}.
   */
  SeriesView end(UUID id, LocalDate from);

  /** O pago da tempada (If-Match). */
  SeriesView payment(UUID id, long expectedVersion, Series.PaymentStatus status);
}
