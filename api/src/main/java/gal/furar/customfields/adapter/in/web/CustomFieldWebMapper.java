// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web;

import gal.furar.customfields.adapter.in.web.dto.CustomFieldDefinitionRequest;
import gal.furar.customfields.adapter.in.web.dto.CustomFieldDefinitionResponse;
import gal.furar.customfields.adapter.in.web.dto.FieldOptionRequest;
import gal.furar.customfields.adapter.in.web.dto.FieldOptionResponse;
import gal.furar.customfields.adapter.in.web.dto.FieldRulesRequest;
import gal.furar.customfields.adapter.in.web.dto.FieldRulesResponse;
import gal.furar.customfields.adapter.in.web.dto.FieldUsageRequest;
import gal.furar.customfields.adapter.in.web.dto.FieldUsageResponse;
import gal.furar.customfields.adapter.in.web.dto.LocalizedTextRequest;
import gal.furar.customfields.adapter.in.web.dto.LocalizedTextResponse;
import gal.furar.customfields.adapter.in.web.dto.UpdateCustomFieldDefinitionRequest;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.CustomFieldInUse;
import gal.furar.customfields.application.port.in.RestoreCustomFieldDefinitionCommand;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.DefinitionStatus;
import gal.furar.customfields.domain.FieldOption;
import gal.furar.customfields.domain.FieldRules;
import gal.furar.customfields.domain.FieldType;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.TextFormat;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.web.Locales;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** DTO ↔ comandos e dominio de campos personalizados. */
final class CustomFieldWebMapper {

  private CustomFieldWebMapper() {}

  static CreateCustomFieldDefinitionCommand toCommand(CustomFieldDefinitionRequest r) {
    return new CreateCustomFieldDefinitionCommand(
        r.code(),
        FieldType.valueOf(r.type()),
        spec(r.label(), r.help(), r.rules(), r.options(), r.personalData(), r.usages()));
  }

  static DefinitionSpec toSpec(UpdateCustomFieldDefinitionRequest r) {
    return spec(r.label(), r.help(), r.rules(), r.options(), r.personalData(), r.usages());
  }

  private static DefinitionSpec spec(
      LocalizedTextRequest label,
      LocalizedTextRequest help,
      FieldRulesRequest rules,
      List<FieldOptionRequest> options,
      Boolean personalData,
      List<FieldUsageRequest> usages) {
    return new DefinitionSpec(
        text(label),
        help == null ? null : text(help),
        rules == null ? FieldRules.NONE : rules(rules),
        options == null
            ? List.of()
            : options.stream()
                .map(
                    o ->
                        new FieldOption(
                            o.code(), text(o.label()), Boolean.TRUE.equals(o.inactive())))
                .toList(),
        Boolean.TRUE.equals(personalData),
        usages == null
            ? List.of()
            : usages.stream()
                .map(
                    u ->
                        new FieldUsage(
                            new CustomFieldTarget(u.target()),
                            Boolean.TRUE.equals(u.required()),
                            Visibility.valueOf(u.visibility())))
                .toList());
  }

  private static LocalizedText text(LocalizedTextRequest t) {
    return new LocalizedText(t.gl(), t.es());
  }

  private static FieldRules rules(FieldRulesRequest r) {
    return new FieldRules(
        r.minLength(),
        r.maxLength(),
        r.min(),
        r.max(),
        r.decimals(),
        r.minDate(),
        r.maxDate(),
        r.minItems(),
        r.maxItems(),
        r.format() == null ? null : TextFormat.valueOf(r.format()));
  }

  /**
   * Orde alfabética no idioma de quen mira (especificación, bloque 1b): polo nome e, se coincide,
   * polo código.
   */
  static List<CustomFieldDefinition> sorted(List<CustomFieldDefinition> list, Language language) {
    Collator collator = collator(language);
    return list.stream()
        .sorted(
            Comparator.comparing((CustomFieldDefinition d) -> d.label().in(language), collator)
                .thenComparing(CustomFieldDefinition::code))
        .toList();
  }

  /** Sen saber se ten valores (exportación). */
  static CustomFieldDefinitionResponse toResponse(CustomFieldDefinition d, Language language) {
    return toResponse(d, language, u -> true, null);
  }

  /** Para o xestor: co uso e cos valores gardados no campo e nas opcións. */
  static CustomFieldDefinitionResponse toResponse(
      CustomFieldDefinition d, Language language, @Nullable CustomFieldInUse inUse) {
    return toResponse(d, language, u -> true, inUse);
  }

  /**
   * Só cos usos que cumpren {@code usages} (na lista pública dun destino, o dese destino); {@code
   * inUse} nulo deixa {@code hasValues} nulo.
   */
  static CustomFieldDefinitionResponse toResponse(
      CustomFieldDefinition d,
      Language language,
      Predicate<FieldUsage> usages,
      @Nullable CustomFieldInUse inUse) {
    DefinitionSpec s = d.spec();
    FieldRules r = s.rules();
    Collator collator = collator(language);
    return new CustomFieldDefinitionResponse(
        d.id().value(),
        d.code(),
        d.type().name(),
        text(s.label()),
        s.help() == null ? null : text(s.help()),
        new FieldRulesResponse(
            r.minLength(),
            r.maxLength(),
            r.min(),
            r.max(),
            r.decimals(),
            r.minDate(),
            r.maxDate(),
            r.minItems(),
            r.maxItems(),
            r.format() == null ? null : r.format().name()),
        s.options().stream()
            .sorted(
                Comparator.comparing((FieldOption o) -> o.label().in(language), collator)
                    .thenComparing(FieldOption::code))
            .map(
                o ->
                    new FieldOptionResponse(
                        o.code(),
                        text(o.label()),
                        o.inactive(),
                        inUse == null ? null : inUse.optionsWithValues().contains(o.code())))
            .toList(),
        s.personalData(),
        s.usages().stream()
            .filter(usages)
            .map(
                u -> new FieldUsageResponse(u.target().code(), u.required(), u.visibility().name()))
            .toList(),
        d.status().name(),
        inUse == null ? null : inUse.hasValues(),
        d.version() == null ? 0 : d.version(),
        d.createdAt(),
        d.updatedAt());
  }

  /** Fila da sección {@code customfields} dun ficheiro {@code furar-export} (ADR-0018). */
  static RestoreCustomFieldDefinitionCommand toRestoreCommand(CustomFieldDefinitionResponse r) {
    FieldRulesResponse rules = r.rules();
    DefinitionSpec spec =
        new DefinitionSpec(
            text(r.label()),
            r.help() == null ? null : text(r.help()),
            rules == null
                ? FieldRules.NONE
                : new FieldRules(
                    rules.minLength(),
                    rules.maxLength(),
                    rules.min(),
                    rules.max(),
                    rules.decimals(),
                    rules.minDate(),
                    rules.maxDate(),
                    rules.minItems(),
                    rules.maxItems(),
                    rules.format() == null ? null : TextFormat.valueOf(rules.format())),
            r.options() == null
                ? List.of()
                : r.options().stream()
                    .map(o -> new FieldOption(o.code(), text(o.label()), o.inactive()))
                    .toList(),
            r.personalData(),
            Objects.requireNonNull(r.usages(), "usages").stream()
                .map(
                    u ->
                        new FieldUsage(
                            new CustomFieldTarget(Objects.requireNonNull(u.target(), "target")),
                            u.required(),
                            Visibility.valueOf(u.visibility())))
                .toList());
    return new RestoreCustomFieldDefinitionCommand(
        Objects.requireNonNull(r.id(), "id"),
        r.code(),
        FieldType.valueOf(r.type()),
        spec,
        DefinitionStatus.valueOf(r.status()),
        Objects.requireNonNull(r.createdAt(), "createdAt"),
        Objects.requireNonNull(r.updatedAt(), "updatedAt"));
  }

  private static Collator collator(Language language) {
    Collator collator = Collator.getInstance(Locales.locale(language));
    collator.setStrength(Collator.SECONDARY);
    return collator;
  }

  private static LocalizedText text(LocalizedTextResponse t) {
    return new LocalizedText(t.gl(), t.es());
  }

  private static LocalizedTextResponse text(LocalizedText t) {
    return new LocalizedTextResponse(t.gl(), t.es());
  }
}
