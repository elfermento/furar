// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Un módulo que admite campos personalizados nunha entidade rexistra así o seu destino (ADR-0005
 * ARQ3-2, ADR-0011): un bean por destino, no paquete do módulo. {@code customfields} só acepta
 * definicións para destinos rexistrados.
 */
public interface CustomFieldTargetProvider {

  CustomFieldTarget target();

  /** Nomes dos campos núcleo da entidade: un campo personalizado non pode usar ese código. */
  Set<String> coreFields();

  /**
   * Se a entidade é do concello actual. O almacén de valores pregúntao antes de gardar ({@code
   * entity_id} non ten FK, ADR-0011). Por defecto, si: as filas de valores levan o concello actual
   * (RLS) e o módulo dono só garda entidades súas; un módulo que poida comprobalo sen consulta
   * (como o concello) debe facelo.
   */
  default boolean ownedByCurrentMunicipality(UUID entityId) {
    return true;
  }

  /**
   * Se un campo deste destino pode ser público (visible sen sesión). Unha reserva é dunha persoa:
   * os seus campos ven só ela e o persoal (ADR-0011, revisión do 2026-11-04).
   */
  default boolean allowsPublic() {
    return true;
  }

  /**
   * Se calquera persoa con sesión é titular da entidade que vai crear (unha reserva é de quen a
   * fai): na lista de campos para pintar o formulario ve os de titular. Por defecto, non: o titular
   * do concello é quen ten un rol nel.
   */
  default boolean ownedByItsCreator() {
    return false;
  }
}
