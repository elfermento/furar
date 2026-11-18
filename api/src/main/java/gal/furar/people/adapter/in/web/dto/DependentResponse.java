// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

import java.util.UUID;

/**
 * Unha persoa a cargo da conta.
 *
 * @param name nome e apelidos
 * @param birthYear ano de nacemento
 * @param version a do {@code ETag}, para o {@code If-Match} de editar
 */
public record DependentResponse(UUID id, String name, int birthYear, long version) {}
