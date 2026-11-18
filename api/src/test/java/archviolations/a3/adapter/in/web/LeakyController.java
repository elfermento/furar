// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a3.adapter.in.web;

import archviolations.a3.adapter.out.persistence.Row;

public class LeakyController {
  public String read(Row row) {
    return row.value();
  }
}
