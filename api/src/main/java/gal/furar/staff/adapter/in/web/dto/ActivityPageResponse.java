// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Unha páxina do rexistro de actividade, do máis novo ao máis vello (como moito 50).
 *
 * @param items os eventos
 * @param nextCursor o valor de {@code cursor} para a seguinte páxina; nulo se esta é a última
 */
public record ActivityPageResponse(
    List<ActivityEventResponse> items, @Nullable String nextCursor) {}
