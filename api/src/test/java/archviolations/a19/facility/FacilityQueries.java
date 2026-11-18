// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a19.facility;

import archviolations.a19.facility.domain.Space;
import java.util.UUID;

/** A19: a API pública devolve un tipo do dominio en vez dun record da raíz. */
public interface FacilityQueries {
  Space space(UUID id);
}
