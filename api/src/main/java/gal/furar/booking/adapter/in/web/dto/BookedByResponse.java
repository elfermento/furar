// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * Quen do persoal fixo a reserva no nome da persoa («Reservada por»).
 *
 * @param name o nome da súa conta ou o correo; nulo se xa está anonimizada
 * @param role o seu rol de agora
 */
public record BookedByResponse(@Nullable String name, StaffRoleValue role) {}
