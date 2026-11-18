// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Modo dunha páxina legal e o enderezo da páxina propia do concello. Gárdanse por separado: cambiar
 * de modo non borra o enderezo. Valídao {@link LegalDetails}.
 *
 * @param mode como se publica a páxina
 * @param url páxina do concello (https); obrigatoria só no modo {@link LegalPageMode#URL}
 */
public record LegalPageSettings(LegalPageMode mode, @Nullable String url) {

  public static final LegalPageSettings FURAR = new LegalPageSettings(LegalPageMode.FURAR, null);

  public LegalPageSettings {
    Objects.requireNonNull(mode, "mode");
    url = url == null || url.isBlank() ? null : url.strip();
  }

  /** O enderezo ao que levan as ligazóns de Furar, se a páxina é a do concello. */
  public @Nullable String externalUrl() {
    return mode == LegalPageMode.URL ? url : null;
  }

  /** Se a páxina se publica (en Furar ou fóra): no modo {@link LegalPageMode#NONE}, non. */
  public boolean published() {
    return mode != LegalPageMode.NONE;
  }
}
