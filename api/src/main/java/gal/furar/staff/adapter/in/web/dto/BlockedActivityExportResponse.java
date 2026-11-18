// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Os eventos bloqueados dun período, do máis vello ao máis novo, coa identidade do persoal aínda
 * que estea bloqueada.
 *
 * @param from o comezo exportado: o pedido ou, se era anterior, o que aínda se garda
 * @param to o fin exportado: o pedido ou, se era posterior, o último bloqueado
 * @param items os eventos
 */
public record BlockedActivityExportResponse(
    Instant from, Instant to, List<ActivityEventResponse> items) {}
