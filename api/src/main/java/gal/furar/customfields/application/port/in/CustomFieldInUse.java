// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import java.util.Set;

/**
 * Se un campo e as súas opcións teñen valores gardados: con eles non se eliminan nin o campo cambia
 * de sitio, só se desactivan (ADR-0011, revisión do 2026-11-04). O editor pregúntao para abrir
 * directamente o diálogo axeitado; a API compróbao igual ao gardar.
 *
 * @param hasValues algunha entidade ten valor neste campo
 * @param optionsWithValues códigos das opcións escollidas nalgún valor
 */
public record CustomFieldInUse(boolean hasValues, Set<String> optionsWithValues) {

  public CustomFieldInUse {
    optionsWithValues = Set.copyOf(optionsWithValues);
  }
}
