// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * API pública: almacén dos valores de campos personalizados en táboas tipadas (ADR-0011,
 * especificación, bloque 1b). O módulo dono garda cada valor validado ({@link
 * CustomFieldValidator}) con {@link #replace} na mesma transacción en que garda a súa entidade e
 * sobe a súa versión; as escrituras fallan sen transacción. Todo é do concello actual ({@code
 * TenantContext}).
 */
public interface CustomFieldValueStore {

  /**
   * Substitúe todos os valores da entidade nese destino polos indicados.
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.not-found} se o módulo dono di
   *     que a entidade non é do concello actual
   */
  void replace(CustomFieldTarget target, UUID entityId, CustomFieldValues values);

  /** Valores gardados dunha entidade, sen proxectar ({@link CustomFieldProjector}). */
  CustomFieldValues load(CustomFieldTarget target, UUID entityId);

  /**
   * Valores de varias entidades (listas, CSV): unha consulta por páxina, non unha por fila. As
   * entidades sen valores non aparecen no mapa.
   */
  Map<UUID, CustomFieldValues> loadAll(CustomFieldTarget target, Collection<UUID> entityIds);

  /** RGPD (acceso e portabilidade): só os valores dos campos marcados como dato persoal. */
  CustomFieldValues personalData(CustomFieldTarget target, UUID entityId);

  /** RGPD (anonimización): borra os valores dos campos marcados como dato persoal. */
  void erasePersonalData(CustomFieldTarget target, UUID entityId);

  /** RGPD (supresión) ou borrado da entidade: borra todos os seus valores nese destino. */
  void deleteAll(CustomFieldTarget target, UUID entityId);
}
