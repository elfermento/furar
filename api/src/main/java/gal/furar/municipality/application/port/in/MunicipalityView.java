// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.domain.CustomFieldValues;

/** Concello tal como o ve quen pregunta: o agregado e só os campos personalizados que pode ver. */
public record MunicipalityView(Municipality municipality, CustomFieldValues visibleCustomFields) {}
