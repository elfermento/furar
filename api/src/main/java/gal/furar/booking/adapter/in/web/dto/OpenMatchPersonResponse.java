// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;

/** Unha persoa que está dentro: só a ven as persoas da partida. */
public record OpenMatchPersonResponse(UUID id, String name) {}
