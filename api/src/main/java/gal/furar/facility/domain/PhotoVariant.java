// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

/** Tamaños gardados de cada foto (plan do dominio 1, §1.2.9). */
public enum PhotoVariant {
  /** Ata 1600 px no lado longo: a foto grande da ficha. */
  LARGE(1600),
  /** Ata 480 px no lado longo: tarxetas e miniaturas. */
  THUMB(480);

  private final int maxSide;

  PhotoVariant(int maxSide) {
    this.maxSide = maxSide;
  }

  public int maxSide() {
    return maxSide;
  }
}
