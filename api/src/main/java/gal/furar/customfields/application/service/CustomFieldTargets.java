// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.service;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldTargetProvider;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Destinos rexistrados polos módulos ({@link CustomFieldTargetProvider}, ADR-0005 ARQ3-2). Un
 * destino sen rexistrar é un erro de validación no campo {@code target}, igual que un formato mal.
 */
@Component
public class CustomFieldTargets {

  private final Map<CustomFieldTarget, CustomFieldTargetProvider> providers = new LinkedHashMap<>();

  public CustomFieldTargets(List<CustomFieldTargetProvider> providers) {
    for (CustomFieldTargetProvider p : providers) {
      if (this.providers.put(p.target(), p) != null) {
        throw new IllegalStateException("Duplicated custom field target " + p.target());
      }
    }
  }

  /** O destino, se está rexistrado; se non, {@code validation.failed} en {@code target}. */
  public CustomFieldTarget require(CustomFieldTarget target) {
    if (!providers.containsKey(target)) {
      throw DomainException.validation(
          List.of(FieldViolation.of("target", ValidationError.INVALID)));
    }
    return target;
  }

  /** Campos núcleo da entidade: un campo personalizado non pode usar ese código. */
  public Set<String> coreFields(CustomFieldTarget target) {
    return providers.get(require(target)).coreFields();
  }

  /** Se o módulo dono di que a entidade é do concello actual. */
  public boolean ownedByCurrentMunicipality(CustomFieldTarget target, java.util.UUID entityId) {
    return providers.get(require(target)).ownedByCurrentMunicipality(entityId);
  }

  /** Se o destino admite campos públicos (unha reserva, non: ADR-0011, revisión do 2026-11-04). */
  public boolean allowsPublic(CustomFieldTarget target) {
    return providers.get(require(target)).allowsPublic();
  }

  /** Se quen ten sesión é titular da entidade que crea (a súa reserva). */
  public boolean ownedByItsCreator(CustomFieldTarget target) {
    return providers.get(require(target)).ownedByItsCreator();
  }

  public Set<CustomFieldTarget> registered() {
    return Set.copyOf(providers.keySet());
  }
}
