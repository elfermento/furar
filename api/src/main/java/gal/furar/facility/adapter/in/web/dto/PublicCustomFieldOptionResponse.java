// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

/** Unha opción dun campo de selección público: o código e o nome nos dous idiomas. */
public record PublicCustomFieldOptionResponse(String code, PublicTextResponse label) {}
