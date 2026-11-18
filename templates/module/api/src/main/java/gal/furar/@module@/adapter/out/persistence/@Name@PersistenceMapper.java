// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.out.persistence;

import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.domain.CustomFieldValues; // @cf

/** Dominio ↔ entidade JPA, a man. */
final class @Name@PersistenceMapper {

  private @Name@PersistenceMapper() {}

  static @Name@Entity newEntity(@Name@ @name@) {
    var entity = new @Name@Entity(@name@.id().value(), @name@.createdAt());
    apply(entity, @name@);
    return entity;
  }

  static void apply(@Name@Entity entity, @Name@ @name@) {
    entity.apply(@name@.name(), @name@.updatedAt());
  }

  static @Name@ toDomain(
      CustomFieldValues customFields, // @cf
      @Name@Entity e) {
    return @Name@.rehydrate(
        new @Name@Id(e.getId()),
        e.name(),
        customFields, // @cf
        e.version() == null ? 0 : e.version(),
        e.createdAt(),
        e.updatedAt());
  }
}
