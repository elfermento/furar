// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import java.util.List;
import java.util.Set;

/**
 * API pública síncrona do persoal para outros módulos (ADR-0005, ARQ-3; plan do dominio 4, §2.2):
 * lecturas dentro do concello actual, con records propios da raíz e nunca o dominio (A19). Non
 * comproba permisos: xa os comprobou o caso de uso que chama; nunca se expón por HTTP.
 */
public interface StaffQueries {

  /**
   * As pertenzas destas persoas ({@code sub}) no concello actual, tamén as desactivadas, das máis
   * novas ás máis vellas: unha persoa pode ter varias (desactivada e volta a invitar). Poñen nome e
   * correo a quen aparece no rexistro de actividade.
   */
  List<StaffMemberSummary> members(Set<String> subjects);
}
