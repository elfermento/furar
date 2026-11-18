// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.shared.audit.AuditCursor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Unha páxina do rexistro, do máis novo ao máis vello.
 *
 * @param entries os eventos
 * @param next onde segue; nulo se é a última
 */
public record ActivityPage(List<ActivityEntry> entries, @Nullable AuditCursor next) {

  public ActivityPage {
    entries = List.copyOf(entries);
  }
}
