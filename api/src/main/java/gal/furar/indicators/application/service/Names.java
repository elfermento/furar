// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Orde alfabética en galego (sen distinguir acentos nin maiúsculas) das listas do panel. */
final class Names {

  private Names() {}

  static <T> Comparator<T> comparing(Function<T, String> name) {
    Collator collator = Collator.getInstance(Locale.forLanguageTag("gl"));
    collator.setStrength(Collator.SECONDARY);
    return Comparator.comparing(name, collator);
  }

  static <T> List<T> sorted(List<T> items, Function<T, String> name) {
    return items.stream().sorted(comparing(name)).toList();
  }
}
