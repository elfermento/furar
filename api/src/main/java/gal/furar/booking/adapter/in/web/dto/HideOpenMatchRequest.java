// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** Ocultar a partida aberta (ou volver mostrala) desde o detalle da reserva. */
public record HideOpenMatchRequest(@NotNull Boolean hidden) {}
