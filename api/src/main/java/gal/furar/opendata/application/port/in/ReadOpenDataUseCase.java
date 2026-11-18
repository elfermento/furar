// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import gal.furar.opendata.domain.OccupancyRange;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Os datos abertos, sen sesión (ADR-0034): do concello actual ou de todos os publicados que o teñen
 * aceso. Nun concello co interruptor apagado, todo responde 404 {@code opendata.not-published}.
 */
public interface ReadOpenDataUseCase {

  OpenDataOverview overview(Scope scope);

  FacilitiesFile facilities(Scope scope);

  ClosuresFile closures(Scope scope);

  /**
   * Valida as datas da ocupación (e, nun concello, que publica).
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.invalid-parameter}
   */
  OccupancyRange occupancyRange(Scope scope, @Nullable String from, @Nullable String to);

  /**
   * A pegada da ocupación dun concello antes de ler as filas (o {@code ETag}): o último día
   * calculado, cantas filas, que instalacións e as datas.
   */
  String occupancyTag(OccupancyRange range);

  /**
   * Escribe a ocupación por tramos. Como moito {@code furar.opendata.occupancy-downloads} á vez.
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.rate-limited} se xa hai tantas
   */
  void occupancy(Scope scope, OccupancyRange range, OccupancySink sink);

  /** A ficha de catálogo; baleira sen DIR3 (ou, na de todos, sen o publicador do despregue). */
  Optional<CatalogRecord> catalog(Scope scope);
}
