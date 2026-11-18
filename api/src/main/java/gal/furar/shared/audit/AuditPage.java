// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Unha páxina do rexistro, do máis novo ao máis vello.
 *
 * @param records os eventos da páxina
 * @param next onde segue a seguinte; nulo se esta é a última
 */
public record AuditPage(List<AuditRecord> records, @Nullable AuditCursor next) {

  public AuditPage {
    records = List.copyOf(records);
  }
}
