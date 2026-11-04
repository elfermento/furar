// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.Map;

/**
 * API pública: valida os campos personalizados que chegan nunha escritura contra as definicións do
 * concello actual. Chámao o caso de uso do módulo dono, nunca só a web (ADR-0011).
 */
public interface CustomFieldValidator {

  /**
   * @param target entidade
   * @param raw valores tal como chegan (JSON: texto, número, booleano, lista)
   * @param previous valores gardados ata agora ({@link CustomFieldValues#empty()} ao crear)
   * @param context quen escribe e atributos para as condicións
   * @return valores validados; os de campos desactivados ou invisibles para quen escribe
   *     consérvanse
   * @throws gal.furar.shared.domain.DomainException {@code validation.failed} con {@code errors[]}
   *     por campo, ou {@code customfield.limit-exceeded}
   */
  CustomFieldValues validate(
      CustomFieldTarget target,
      Map<String, ?> raw,
      CustomFieldValues previous,
      ValidationContext context);

  /**
   * Valores dun ficheiro {@code furar-export} (ADR-0018): cada clave co seu tipo e definición
   * (activa ou desactivada), sen volver aplicar obrigatoriedade nin regras que poden ter cambiado.
   *
   * @throws gal.furar.shared.domain.DomainException {@code validation.failed} con {@code errors[]}
   */
  CustomFieldValues restore(CustomFieldTarget target, Map<String, ?> raw);
}
