// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.domain;

/** Os tres conxuntos de datos abertos (ADR-0034), co nome do seu ficheiro en galego. */
public enum Dataset {
  FACILITIES("instalacions"),
  CLOSURES("peches"),
  OCCUPANCY("ocupacion");

  private final String fileName;

  Dataset(String fileName) {
    this.fileName = fileName;
  }

  /** O nome do conxunto nos ficheiros e no envoltorio do JSON. */
  public String fileName() {
    return fileName;
  }
}
