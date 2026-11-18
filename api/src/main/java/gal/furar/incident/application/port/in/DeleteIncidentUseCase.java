// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import gal.furar.incident.domain.IncidentId;
import java.util.UUID;

/**
 * A xestión elimina unha incidencia enteira (coas fotos e os envíos de correo) ou só unha foto (por
 * exemplo, se sae unha persoa). As incidencias non caducan soas: quedan ata que o concello as
 * elimina (ADR-0036).
 */
public interface DeleteIncidentUseCase {

  void delete(IncidentId id);

  /** Quita unha foto e devolve a incidencia como queda. */
  IncidentView deletePhoto(IncidentId id, UUID photoId);
}
