// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * A nota dunha instalación (ADR-0037, punto 5).
 *
 * @param reviews cantas valoracións, tamén as de comentario oculto
 * @param average a nota media, sen redondear; nula sen valoracións
 */
public record ReviewSummaryResponse(long reviews, @Nullable Double average) {}
