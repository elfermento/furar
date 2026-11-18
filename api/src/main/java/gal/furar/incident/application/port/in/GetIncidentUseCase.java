// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import gal.furar.incident.domain.IncidentId;
import java.util.UUID;

/** O detalle dunha incidencia e os bytes das súas fotos, para o persoal. */
public interface GetIncidentUseCase {

  IncidentView get(IncidentId id);

  /**
   * @param large a grande (1600 px) ou a miniatura (480 px)
   */
  PhotoBytes photo(IncidentId id, UUID photoId, boolean large);
}
