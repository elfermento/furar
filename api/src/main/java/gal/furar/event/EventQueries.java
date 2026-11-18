// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública síncrona do módulo para outros módulos (ADR-0005): lecturas dentro do concello
 * actual, con records propios da raíz e nunca o dominio (A19). Úsase cando outro módulo precisa un
 * dato **na mesma transacción** (p. ex. booking pide a capacidade dun espazo); para reaccionar a un
 * cambio, evento (`EventCreated`). Non comproba permisos: xa os comprobou o caso de uso que chama;
 * nunca se expón por HTTP.
 */
public interface EventQueries {

  Optional<EventSummary> find(UUID id);

  /** Os que existan, na orde pedida; os ids doutro concello ou inexistentes non aparecen. */
  List<EventSummary> findAll(List<UUID> ids);
}
