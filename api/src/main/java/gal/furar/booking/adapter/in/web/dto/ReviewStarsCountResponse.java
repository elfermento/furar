// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Cantas valoracións teñen esa nota no filtro (sen o filtro de nota). */
public record ReviewStarsCountResponse(int stars, long count) {}
