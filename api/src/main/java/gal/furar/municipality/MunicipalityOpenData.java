// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A publicación dos datos abertos dun concello para {@code opendata} (ADR-0034; A19: só tipos de
 * Java).
 *
 * @param since desde cando publica; nulo = apagado
 * @param slug o slug do concello
 * @param name o nome do concello
 * @param legalEntityName a entidade responsable dos datos legais, se a ten (a atribución)
 * @param dir3Code o código DIR3, se o ten (sen el non hai ficha de catálogo)
 */
public record MunicipalityOpenData(
    @Nullable Instant since,
    String slug,
    String name,
    @Nullable String legalEntityName,
    @Nullable String dir3Code) {

  public boolean enabled() {
    return since != null;
  }
}
