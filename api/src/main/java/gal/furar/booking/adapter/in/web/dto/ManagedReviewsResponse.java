// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;

/**
 * Unha páxina da táboa «Valoracións» dos indicadores.
 *
 * @param totalItems as valoracións do filtro
 * @param withComment desas, as que teñen comentario
 * @param byStars as cinco notas, da 5 á 1, con cantas ten cada unha sen o filtro de nota
 */
public record ManagedReviewsResponse(
    List<ManagedReviewResponse> items,
    int page,
    int size,
    long totalItems,
    long withComment,
    List<ReviewStarsCountResponse> byStars) {}
