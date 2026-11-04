// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.LocalizedText;
import java.util.Objects;

/** Deporte co seu nome en gl e es, do catálogo ou do concello. */
public record Sport(SportRef ref, LocalizedText label) {

  public Sport {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(label, "label");
  }
}
