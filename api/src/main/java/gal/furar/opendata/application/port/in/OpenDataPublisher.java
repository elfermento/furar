// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PlaceName;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un concello que publica datos abertos.
 *
 * @param since desde cando publica ({@code dct:issued}; o corte dos motivos dos peches)
 * @param legalEntityName a entidade responsable dos datos legais, se a ten
 * @param dir3Code o código DIR3, se o ten (sen el non hai ficha de catálogo)
 */
public record OpenDataPublisher(
    UUID id,
    String slug,
    String name,
    Instant since,
    @Nullable String legalEntityName,
    @Nullable String dir3Code) {

  /** A quen se cita: a entidade responsable ou, sen ela, «Concello de ‹nome›». */
  public String attribution(Language language) {
    if (legalEntityName != null && !legalEntityName.isBlank()) {
      return legalEntityName;
    }
    return (language == Language.ES ? "Ayuntamiento " : "Concello ") + PlaceName.of(name, language);
  }
}
