// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web;

import gal.furar.opendata.domain.Dataset;

/** O nome de cada conxunto nas rutas (en inglés, coma o resto da API). */
final class OpenDataPaths {

  private OpenDataPaths() {}

  static String file(Dataset dataset) {
    return switch (dataset) {
      case FACILITIES -> "facilities";
      case CLOSURES -> "closures";
      case OCCUPANCY -> "occupancy";
    };
  }
}
