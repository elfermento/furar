// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.LocalizedText;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Atributos editables dunha definición (todo menos {@code code} e {@code type}, que son
 * inmutables). Sen orde manual nin grupos: as listas van en orde alfabética (especificación, bloque
 * 1b).
 *
 * @param usages onde se usa o campo, cun obrigatorio e unha visibilidade por sitio
 */
public record DefinitionSpec(
    LocalizedText label,
    @Nullable LocalizedText help,
    FieldRules rules,
    List<FieldOption> options,
    boolean personalData,
    List<FieldUsage> usages) {

  public DefinitionSpec {
    Objects.requireNonNull(label, "label");
    rules = rules == null ? FieldRules.NONE : rules;
    options = options == null ? List.of() : List.copyOf(options);
    usages = usages == null ? List.of() : List.copyOf(usages);
  }
}
