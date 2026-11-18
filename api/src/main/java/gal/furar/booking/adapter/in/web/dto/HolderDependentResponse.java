// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;

/**
 * Unha persoa a cargo da conta dun correo, para o «Para quen» do mostrador.
 *
 * @param name nome e apelidos
 * @param birthYear ano de nacemento
 */
public record HolderDependentResponse(UUID id, String name, int birthYear) {}
