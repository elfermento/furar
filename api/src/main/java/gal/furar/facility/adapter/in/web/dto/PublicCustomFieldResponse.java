// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.util.List;

/**
 * Un campo personalizado público da instalación (ADR-0034, punto 16): o código, o nome nos dous
 * idiomas, o tipo e, nos de selección, as opcións na orde da definición.
 */
public record PublicCustomFieldResponse(
    String code,
    PublicTextResponse label,
    CustomFieldTypeValue type,
    List<PublicCustomFieldOptionResponse> options) {}
