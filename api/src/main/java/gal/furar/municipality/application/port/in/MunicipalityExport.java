// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.domain.CustomFieldValues;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Todos os datos dun concello (ADR-0018): o agregado con todos os seus campos personalizados e as
 * filas de cada sección, na orde de importación.
 */
public record MunicipalityExport(
    Municipality municipality,
    CustomFieldValues customFields,
    Map<String, List<?>> sections,
    Instant exportedAt) {

  public MunicipalityExport {
    sections = Collections.unmodifiableMap(new LinkedHashMap<>(sections));
  }
}
