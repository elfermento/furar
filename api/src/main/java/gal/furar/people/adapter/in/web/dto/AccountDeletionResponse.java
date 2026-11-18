// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

import java.util.List;

/**
 * Se se pode eliminar a conta da sesión (ADR-0043).
 *
 * @param allowed sen ningún impedimento
 * @param blockers o que hai que cancelar antes, por tipo e concello
 */
public record AccountDeletionResponse(
    boolean allowed, List<AccountDeletionBlockerResponse> blockers) {}
