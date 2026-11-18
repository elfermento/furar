// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.domain;

import gal.furar.shared.domain.CustomFieldTarget; // @cf
import gal.furar.shared.domain.CustomFieldValues; // @cf
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @Name@ (agregado). Inmutable: as invariantes van no construtor e cada cambio é un método con
 * nome de negocio que devolve unha copia (ADR-0005, A13).
 */
public final class @Name@ {

  public static final int NAME_MAX = 100;
  /** Destino dos seus campos personalizados (ADR-0011). */ // @cf
  public static final CustomFieldTarget CUSTOM_FIELD_TARGET = new CustomFieldTarget("@TARGET@"); // @cf

  private final @Name@Id id;
  private final String name;
  private final CustomFieldValues customFields; // @cf
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private @Name@(
      @Name@Id id,
      String name,
      CustomFieldValues customFields, // @cf
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = checkName(name);
    this.customFields = Objects.requireNonNull(customFields, "customFields"); // @cf
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static @Name@ create(
      @Name@Id id,
      String name,
      CustomFieldValues customFields, // @cf
      Instant now) {
    return new @Name@(
        id,
        name,
        customFields, // @cf
        null,
        now,
        now);
  }

  /** Fila dun ficheiro {@code furar-export} (ADR-0018): conserva id e datas; aínda sen gardar. */
  public static @Name@ restore(
      @Name@Id id,
      String name,
      CustomFieldValues customFields, // @cf
      Instant createdAt,
      Instant updatedAt) {
    return new @Name@(
        id,
        name,
        customFields, // @cf
        null,
        createdAt,
        updatedAt);
  }

  public static @Name@ rehydrate(
      @Name@Id id,
      String name,
      CustomFieldValues customFields, // @cf
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new @Name@(
        id,
        name,
        customFields, // @cf
        version,
        createdAt,
        updatedAt);
  }

  /** Edición: mesma identidade e versión (o adaptador compróbaa con {@code If-Match}). */
  public @Name@ update(
      String newName,
      CustomFieldValues newCustomFields, // @cf
      Instant now) {
    return new @Name@(
        id,
        newName,
        newCustomFields, // @cf
        version,
        createdAt,
        now);
  }

  private static String checkName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > NAME_MAX) {
      throw new DomainException(@Name@Error.NAME_INVALID, Map.of("max", NAME_MAX));
    }
    return name.strip();
  }

  public @Name@Id id() {
    return id;
  }

  public String name() {
    return name;
  }

  // @cf{
  /** Valores gardados (todos); a lectura proxéctaos segundo quen le (CustomFieldProjector). */
  public CustomFieldValues customFields() {
    return customFields;
  }

  // @cf}
  public @Nullable Long version() {
    return version;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
