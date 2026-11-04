// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Como se publica cada páxina legal do concello (ADR-0017 punto 8): a que xera Furar cos datos do
 * concello, un texto propio do concello, a páxina que o concello ten noutro sitio ou ningunha (o pé
 * non a liga; a obriga legal de publicala é do concello).
 */
public enum LegalPageMode {
  /** A páxina de Furar, cos datos legais do concello. */
  FURAR,
  /** O texto propio do concello ({@link LegalTexts}), co estilo de Furar. */
  TEXT,
  /** A páxina do concello: as ligazóns de Furar levan ao seu enderezo. */
  URL,
  /** Sen páxina: non hai páxina pública, o pé non a liga e os correos non levan a ligazón. */
  NONE;

  public static Optional<LegalPageMode> fromName(String name) {
    return Arrays.stream(values()).filter(m -> m.name().equals(name)).findFirst();
  }
}
