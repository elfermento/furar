// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * O que ten que ter unha instalación para deixar de estar en borrador (ADR-0022, punto 3). O
 * borrador calcúlase, non se garda: falta todo o que non estea cumprido.
 */
public enum Requirement {
  /** Polo menos un deporte. */
  SPORTS,
  /** Polo menos un uso activo. */
  USES,
  /** Unha tempada con algún tramo que remate hoxe ou despois (P8). */
  SCHEDULE;

  /** O que falta, na orde do {@code enum}, dados os requisitos cumpridos. */
  public static Set<Requirement> missing(Collection<Requirement> met) {
    EnumSet<Requirement> missing = EnumSet.allOf(Requirement.class);
    missing.removeAll(met);
    return Collections.unmodifiableSet(missing);
  }
}
