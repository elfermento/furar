// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O papel da persoa con sesión na partida.
 *
 * @param playerId a súa fila (para saír ou retirar a solicitude), se se uniu ou o pediu
 */
public record OpenMatchMeResponse(OpenMatchRoleValue role, @Nullable UUID playerId) {}
