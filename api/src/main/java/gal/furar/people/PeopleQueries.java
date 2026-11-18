// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública síncrona do módulo para outros módulos (ADR-0005; ADR-0031, punto 3), con records
 * propios da raíz e nunca o dominio (A19). Non comproba permisos: xa os comprobou o caso de uso que
 * chama; nunca se expón por HTTP. Quen chama dá sempre o {@code sub} da conta: o do token ou, no
 * mostrador, o da conta do correo que escribe o persoal.
 */
public interface PeopleQueries {

  /** As persoas a cargo activas da conta, por nome. */
  List<DependentSummary> dependents(String subject);

  /**
   * As persoas a cargo activas da conta con estes ids. As doutra conta, as desactivadas e as que
   * non existen non aparecen.
   */
  List<DependentSummary> dependents(String subject, Collection<UUID> ids);

  /** O {@code sub} da conta que entrou con ese correo verificado, se a hai. */
  Optional<String> accountByEmail(String email);
}
