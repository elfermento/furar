// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.SeriesRequest;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As solicitudes de tempada das entidades (ADR-0039, punto 11; permiso {@code
 * booking.organizations}).
 */
public interface ManageSeriesRequestsUseCase {

  /** As dese estado, da máis vella. */
  List<SeriesRequestView> list(SeriesRequest.Status status);

  /** Cantas hai pendentes: o número da pestana. */
  int pendingCount();

  /** 404 {@code booking.series-request-not-found}. */
  SeriesRequestView get(UUID id);

  /**
   * Aproba creando a tempada cos datos (axustables) do xestor: coma {@link
   * ManageSeriesUseCase#create}; a solicitude bloquéase despois da instalación e da entidade. Xa
   * decidida, 409 {@code booking.series-request-decided}.
   */
  SeriesCreated approve(UUID id, CreateSeriesCommand command);

  /** Rexeita coa mensaxe para a entidade (por correo). */
  SeriesRequestView reject(UUID id, @Nullable String message);
}
