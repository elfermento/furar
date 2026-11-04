// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.Map;

/**
 * Porto de saída: os campos propios da instalación (destino {@code FACILITY}), que valida e garda
 * {@code customfields} (ADR-0011). Sempre co concello actual e con quen fai a petición como
 * escritor ou lector.
 */
public interface FacilityCustomFields {

  /**
   * Valida os valores tal como chegan contra as definicións e os gardados ata agora.
   *
   * @throws gal.furar.shared.domain.DomainException {@code validation.failed} con {@code errors[]}
   *     en {@code customFields.<código>}, ou {@code customfield.limit-exceeded}
   */
  CustomFieldValues validate(FacilityId id, Map<String, ?> raw);

  /** Substitúe os valores; na mesma transacción en que a instalación sobe de versión. */
  void replace(FacilityId id, CustomFieldValues values);

  /** Todos os valores gardados, tamén os de persoal (portabilidade, ADR-0018). */
  CustomFieldValues stored(FacilityId id);

  /**
   * Valores dun ficheiro furar-export: cada código coa súa definición e tipo, sen volver aplicar
   * obrigatoriedade nin regras (ADR-0018).
   */
  CustomFieldValues restore(Map<String, ?> raw);

  /** Os valores gardados que pode ver quen le. */
  CustomFieldValues visible(FacilityId id);

  /** Borra todos os valores da instalación (eliminala). */
  void removeAll(FacilityId id);
}
