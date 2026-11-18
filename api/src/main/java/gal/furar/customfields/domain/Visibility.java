// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.Audience;

/** Quen ve un campo: calquera, o titular e o persoal, ou só o persoal. */
public enum Visibility {
  PUBLIC,
  OWNER,
  STAFF;

  public boolean visibleTo(Audience audience) {
    return switch (this) {
      case PUBLIC -> true;
      case OWNER -> audience == Audience.OWNER || audience == Audience.STAFF;
      case STAFF -> audience == Audience.STAFF;
    };
  }
}
