// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.municipality.domain.Municipality;

/**
 * Textos propios das páxinas legais do concello actual (ADR-0017 punto 8). A lectura é pública (a
 * web pinta a páxina no modo «Texto propio»); a escritura, do xestor, coa versión do concello
 * ({@code If-Match}). Devolven o concello enteiro para a versión.
 */
public interface LegalTextsUseCase {

  Municipality legalTexts();

  Municipality updateLegalTexts(UpdateLegalTextsCommand command);
}
