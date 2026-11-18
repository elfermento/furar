// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

/** Tipos de campo personalizado (ADR-0011). */
public enum FieldType {
  TEXT,
  LONG_TEXT,
  NUMBER,
  DATE,
  BOOLEAN,
  SELECT,
  MULTI_SELECT;

  public boolean hasOptions() {
    return this == SELECT || this == MULTI_SELECT;
  }

  public boolean isText() {
    return this == TEXT || this == LONG_TEXT;
  }
}
