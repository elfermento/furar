// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Onde é o evento: unha instalación do concello ou outro sitio (texto libre, «Saída da praza»).
 * Exactamente un dos dous.
 */
public record EventPlace(@Nullable UUID facilityId, @Nullable String other) {

  public static final int OTHER_MAX = 200;

  public EventPlace {
    String text = other == null || other.isBlank() ? null : other.strip();
    if ((facilityId == null) == (text == null) || (text != null && text.length() > OTHER_MAX)) {
      throw new DomainException(EventError.PLACE_INVALID, Map.of("max", OTHER_MAX));
    }
    other = text;
  }

  public static EventPlace facility(UUID facilityId) {
    return new EventPlace(Objects.requireNonNull(facilityId, "facilityId"), null);
  }

  public static EventPlace other(String text) {
    return new EventPlace(null, text);
  }
}
