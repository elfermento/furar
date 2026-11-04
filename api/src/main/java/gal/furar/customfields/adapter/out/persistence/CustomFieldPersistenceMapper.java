// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.DefinitionStatus;
import gal.furar.customfields.domain.FieldOption;
import gal.furar.customfields.domain.FieldRules;
import gal.furar.customfields.domain.FieldType;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.TextFormat;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.LocalizedText;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Dominio ↔ entidades JPA, a man. */
final class CustomFieldPersistenceMapper {

  private CustomFieldPersistenceMapper() {}

  static CustomFieldEntity newEntity(CustomFieldDefinition d) {
    var entity = new CustomFieldEntity(d.id().value(), d.code(), d.type().name(), d.createdAt());
    apply(entity, d);
    return entity;
  }

  /** Textos, regras, marca de dato persoal e estado; os usos e opcións van nas súas táboas. */
  static void apply(CustomFieldEntity e, CustomFieldDefinition d) {
    DefinitionSpec s = d.spec();
    FieldRules r = s.rules();
    e.applyTexts(text(s.label()), s.help() == null ? null : text(s.help()));
    e.applyRules(
        r.minLength(),
        r.maxLength(),
        r.min(),
        r.max(),
        r.decimals(),
        r.minDate(),
        r.maxDate(),
        r.format() == null ? null : r.format().name(),
        r.minItems(),
        r.maxItems());
    e.applyState(s.personalData(), d.status().name(), d.updatedAt());
  }

  static CustomFieldDefinition toDomain(
      CustomFieldEntity e,
      List<CustomFieldOptionEntity> options,
      List<CustomFieldUsageEntity> usages) {
    DefinitionSpec spec =
        new DefinitionSpec(
            text(e.label()),
            e.help() == null ? null : text(e.help()),
            new FieldRules(
                e.minLength(),
                e.maxLength(),
                e.minNumber(),
                e.maxNumber(),
                e.decimals(),
                e.minDate(),
                e.maxDate(),
                e.minChoices(),
                e.maxChoices(),
                e.textFormat() == null ? null : TextFormat.valueOf(e.textFormat())),
            options.stream()
                .sorted(Comparator.comparing(CustomFieldOptionEntity::code))
                .map(o -> new FieldOption(o.code(), text(o.label()), o.inactive()))
                .toList(),
            e.personalData(),
            usages.stream()
                .sorted(Comparator.comparing(CustomFieldUsageEntity::target))
                .map(
                    u ->
                        new FieldUsage(
                            new CustomFieldTarget(u.target()),
                            u.required(),
                            Visibility.valueOf(u.visibility())))
                .toList());
    return CustomFieldDefinition.rehydrate(
        new CustomFieldDefinitionId(e.getId()),
        e.code(),
        FieldType.valueOf(e.type()),
        spec,
        DefinitionStatus.valueOf(e.status()),
        e.version(),
        e.createdAt(),
        e.updatedAt());
  }

  static Map<String, String> text(LocalizedText t) {
    return Map.of("gl", t.gl(), "es", t.es());
  }

  static LocalizedText text(Map<String, String> m) {
    return new LocalizedText(m.get("gl"), m.get("es"));
  }
}
