// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.List;
import java.util.function.Function;

/** Unha páxina de resultados. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

  public PageResult {
    items = List.copyOf(items);
  }

  public <R> PageResult<R> map(Function<T, R> mapper) {
    return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems);
  }
}
