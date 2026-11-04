// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import java.util.Set;

/**
 * Códigos que non pode usar un campo personalizado porque son campos núcleo da entidade (ADR-0011):
 * un concello non pode «sobrescribir» un campo do modelo. Aquí, os comúns a toda táboa; os propios
 * de cada entidade decláraos o seu módulo ({@code CustomFieldTargetProvider#coreFields}) e
 * compróbaos o caso de uso.
 */
public final class ReservedCodes {

  private static final Set<String> COMMON =
      Set.of(
          "id",
          "municipality_id",
          "custom_fields",
          "created_at",
          "updated_at",
          "version",
          "status");

  private ReservedCodes() {}

  public static boolean isReserved(String code) {
    return COMMON.contains(code);
  }
}
