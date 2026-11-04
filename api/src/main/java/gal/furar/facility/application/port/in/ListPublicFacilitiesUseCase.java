// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.shared.domain.Language;
import java.util.List;

/**
 * Instalacións que ve a veciñanza, sen sesión (especificación, bloque 1): as que non están en
 * borrador (ADR-0022, punto 3) nin de baixa. As pechadas temporalmente vense (P16 do plan do
 * dominio 1). En orde alfabética no idioma de quen mira.
 */
public interface ListPublicFacilitiesUseCase {

  List<PublicFacility> listPublic(Language language);
}
