// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.PageResult;
import java.util.List;
import java.util.function.Function;

/** Páxina na API: {@code { items, page, size, totalItems }} (ADR-0009). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

  public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
    return new PageResponse<>(
        result.items().stream().map(mapper).toList(),
        result.page(),
        result.size(),
        result.totalItems());
  }
}
