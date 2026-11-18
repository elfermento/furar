// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

/**
 * Suxestión do buscador de enderezos.
 *
 * @param id identificador opaco para {@code GET /address-search/{id}}
 * @param label texto para a lista («Rúa Real 10, Ferrol»)
 */
public record AddressSuggestionResponse(String id, String label) {}
