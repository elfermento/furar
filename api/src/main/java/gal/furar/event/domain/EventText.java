// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Texto do evento en galego e castelán (nome ou descrición): os dous, coma o resto da app. */
public record EventText(String gl, String es) {

  public static final int NAME_MAX = 100;
  public static final int DESCRIPTION_MAX = 2000;

  public EventText {
    gl = gl.strip();
    es = es.strip();
  }

  /** O nome: obrigatorio nos dous idiomas. */
  public static EventText name(@Nullable String gl, @Nullable String es) {
    String g = strip(gl);
    String e = strip(es);
    if (g == null || e == null || g.length() > NAME_MAX || e.length() > NAME_MAX) {
      throw new DomainException(EventError.NAME_INVALID, Map.of("max", NAME_MAX));
    }
    return new EventText(g, e);
  }

  /** A descrición: opcional, pero se a hai, nos dous idiomas; nula se os dous están baleiros. */
  public static @Nullable EventText description(@Nullable String gl, @Nullable String es) {
    String g = strip(gl);
    String e = strip(es);
    if (g == null && e == null) {
      return null;
    }
    if (g == null || e == null || g.length() > DESCRIPTION_MAX || e.length() > DESCRIPTION_MAX) {
      throw new DomainException(EventError.DESCRIPTION_INVALID, Map.of("max", DESCRIPTION_MAX));
    }
    return new EventText(g, e);
  }

  public String in(Language language) {
    return language == Language.ES ? es : gl;
  }

  private static @Nullable String strip(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
