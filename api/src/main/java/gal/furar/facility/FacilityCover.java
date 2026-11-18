// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A imaxe de portada dunha instalación para outros módulos (A19: só tipos da raíz e de Java): a
 * primeira da galería ou, se non ten, o debuxo suxerido, como na ficha pública. Sen bytes: {@code
 * large} e {@code thumb} son as rutas públicas no concello actual.
 *
 * @param id UUID da foto ou código do debuxo
 * @param uploadedAt só nas fotos
 * @param name nome do debuxo, só nos debuxos
 */
public record FacilityCover(
    UUID facilityId,
    Kind kind,
    String id,
    Text alt,
    int width,
    int height,
    String large,
    String thumb,
    @Nullable Instant uploadedAt,
    @Nullable Text name) {

  public FacilityCover {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(alt, "alt");
  }

  /** Foto da biblioteca ou debuxo de Furar. */
  public enum Kind {
    PHOTO,
    DRAWING
  }

  /** Texto en gl e es. */
  public record Text(String gl, String es) {}
}
