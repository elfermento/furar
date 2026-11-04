// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.ImageRef;
import gal.furar.shared.domain.LocalizedText;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha imaxe xa resolta para pintala: a referencia, o texto alternativo, as medidas da grande e,
 * segundo sexa, cando se subiu (foto da biblioteca) ou o nome (debuxo de Furar).
 */
public record ImageView(
    ImageRef ref,
    LocalizedText alt,
    int width,
    int height,
    @Nullable Instant createdAt,
    @Nullable LocalizedText name) {

  public ImageView {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(alt, "alt");
  }
}
