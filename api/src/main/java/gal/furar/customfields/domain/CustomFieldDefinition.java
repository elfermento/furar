// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LocalizedText;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Definición dun campo personalizado dun concello (agregado). {@code code} e {@code type} non
 * cambian nunca. Úsase nun só destino ({@link FieldUsage}) co seu obrigatorio e visibilidade: un
 * campo, un sitio (ADR-0011, revisión do 2026-11-04). O campo e as opcións desactívanse e
 * actívanse; elimínanse só se aínda non teñen valores (compróbao o caso de uso).
 */
public final class CustomFieldDefinition {

  public static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,39}$");
  public static final int MAX_OPTIONS = 100;

  private final CustomFieldDefinitionId id;
  private final String code;
  private final FieldType type;
  private final DefinitionSpec spec;
  private final DefinitionStatus status;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private CustomFieldDefinition(
      CustomFieldDefinitionId id,
      String code,
      FieldType type,
      DefinitionSpec spec,
      DefinitionStatus status,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.type = Objects.requireNonNull(type, "type");
    this.spec = Objects.requireNonNull(spec, "spec");
    this.status = Objects.requireNonNull(status, "status");
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** Nova definición activa. */
  public static CustomFieldDefinition create(
      CustomFieldDefinitionId id, String code, FieldType type, DefinitionSpec spec, Instant now) {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new DomainException(
          CustomFieldError.CODE_INVALID, Map.of("code", String.valueOf(code)));
    }
    if (ReservedCodes.isReserved(code)) {
      throw new DomainException(CustomFieldError.CODE_RESERVED, Map.of("code", code));
    }
    checkSpec(type, spec);
    return new CustomFieldDefinition(id, code, type, spec, DefinitionStatus.ACTIVE, null, now, now);
  }

  /**
   * Definición que chega dun ficheiro {@code furar-export} (ADR-0018): conserva id, estado e datas,
   * pero pasa as mesmas comprobacións ca unha nova. Aínda sen gardar (versión nula).
   */
  public static CustomFieldDefinition restore(
      CustomFieldDefinitionId id,
      String code,
      FieldType type,
      DefinitionSpec spec,
      DefinitionStatus status,
      Instant createdAt,
      Instant updatedAt) {
    CustomFieldDefinition checked = create(id, code, type, spec, createdAt);
    return new CustomFieldDefinition(
        checked.id, code, type, spec, status, null, createdAt, updatedAt);
  }

  /** Reconstrúe unha definición gardada. */
  public static CustomFieldDefinition rehydrate(
      CustomFieldDefinitionId id,
      String code,
      FieldType type,
      DefinitionSpec spec,
      DefinitionStatus status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new CustomFieldDefinition(id, code, type, spec, status, version, createdAt, updatedAt);
  }

  /**
   * Cambia os atributos editables. As opcións pódense engadir, desactivar, cambiar de etiqueta e
   * quitar; quitar unha con valores compróbao o caso de uso ({@link #removedOptions}). O destino
   * pódese cambiar mentres o campo non teña valores ({@link #targetChanged}).
   */
  public CustomFieldDefinition update(DefinitionSpec newSpec, Instant now) {
    checkSpec(type, newSpec);
    return new CustomFieldDefinition(id, code, type, newSpec, status, version, createdAt, now);
  }

  /** Se a nova especificación leva o campo a outro destino. */
  public boolean targetChanged(DefinitionSpec newSpec) {
    return newSpec.usages().stream().noneMatch(u -> u.target().equals(target()));
  }

  /** Códigos das opcións gardadas que xa non están na nova especificación. */
  public Set<String> removedOptions(DefinitionSpec newSpec) {
    Set<String> removed = new HashSet<>();
    spec.options().forEach(o -> removed.add(o.code()));
    newSpec.options().forEach(o -> removed.remove(o.code()));
    return removed;
  }

  public CustomFieldDefinition deactivate(Instant now) {
    return withStatus(DefinitionStatus.INACTIVE, now);
  }

  public CustomFieldDefinition activate(Instant now) {
    return withStatus(DefinitionStatus.ACTIVE, now);
  }

  private CustomFieldDefinition withStatus(DefinitionStatus newStatus, Instant now) {
    if (newStatus == status) {
      return this;
    }
    return new CustomFieldDefinition(id, code, type, spec, newStatus, version, createdAt, now);
  }

  private static void checkSpec(FieldType type, DefinitionSpec spec) {
    // RGPD (ADR-0011): un dato persoal nunca se publica a anónimos, en ningún sitio.
    if (spec.personalData()
        && spec.usages().stream().anyMatch(u -> u.visibility() == Visibility.PUBLIC)) {
      throw new DomainException(CustomFieldError.PERSONAL_DATA_PUBLIC);
    }
    invalidIf(spec.usages().isEmpty(), "usages-required");
    // Un campo, un sitio (revisión do 2026-11-04): para usalo noutro, defínese outro campo.
    if (spec.usages().size() > 1) {
      throw new DomainException(
          CustomFieldError.SINGLE_USAGE, Map.of("usages", spec.usages().size()));
    }
    List<FieldOption> options = spec.options();
    if (type.hasOptions()) {
      long active = options.stream().filter(o -> !o.inactive()).count();
      invalidIf(active == 0, "options-required");
      invalidIf(options.size() > MAX_OPTIONS, "too-many-options");
      Map<String, FieldOption> unique = new LinkedHashMap<>();
      options.forEach(o -> unique.put(o.code(), o));
      invalidIf(unique.size() != options.size(), "option-code-duplicated");
      spec.rules().checkFor(type, (int) active);
    } else {
      invalidIf(!options.isEmpty(), "options-not-allowed");
      spec.rules().checkFor(type, 0);
    }
    if (spec.help() != null) {
      invalidIf(spec.help().gl().isBlank() || spec.help().es().isBlank(), "help-incomplete");
    }
  }

  private static void invalidIf(boolean condition, String reason) {
    if (condition) {
      throw new DomainException(CustomFieldError.INVALID_DEFINITION, Map.of("reason", reason));
    }
  }

  public boolean isActive() {
    return status == DefinitionStatus.ACTIVE;
  }

  /** Opción activa co código indicado. */
  public boolean hasActiveOption(String optionCode) {
    return spec.options().stream().anyMatch(o -> !o.inactive() && o.code().equals(optionCode));
  }

  public List<String> activeOptionCodes() {
    return spec.options().stream().filter(o -> !o.inactive()).map(FieldOption::code).toList();
  }

  /** O único destino do campo (un campo, un sitio). */
  public CustomFieldTarget target() {
    return spec.usages().getFirst().target();
  }

  /** O uso do campo nese destino, se o ten. */
  public Optional<FieldUsage> usage(CustomFieldTarget target) {
    return spec.usages().stream().filter(u -> u.target().equals(target)).findFirst();
  }

  public boolean usedIn(CustomFieldTarget target) {
    return usage(target).isPresent();
  }

  public Set<CustomFieldTarget> targets() {
    Set<CustomFieldTarget> targets = new HashSet<>();
    spec.usages().forEach(u -> targets.add(u.target()));
    return targets;
  }

  public boolean personalData() {
    return spec.personalData();
  }

  public CustomFieldDefinitionId id() {
    return id;
  }

  public String code() {
    return code;
  }

  public FieldType type() {
    return type;
  }

  public DefinitionSpec spec() {
    return spec;
  }

  public LocalizedText label() {
    return spec.label();
  }

  public FieldRules rules() {
    return spec.rules();
  }

  public DefinitionStatus status() {
    return status;
  }

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
