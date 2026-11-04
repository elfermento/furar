// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Os eventos bloqueados dun período, do máis vello ao máis novo.
 *
 * @param from o comezo exportado, incluído: o pedido ou, se era anterior, o que aínda se garda
 * @param to o fin exportado, excluído: o pedido ou, se era posterior, o último bloqueado
 * @param entries os eventos, coa identidade do persoal aínda que estea bloqueada
 */
public record BlockedActivityExport(Instant from, Instant to, List<ActivityEntry> entries) {

  public BlockedActivityExport {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    entries = List.copyOf(entries);
  }
}
