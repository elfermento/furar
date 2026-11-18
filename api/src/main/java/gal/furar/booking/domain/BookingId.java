// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record BookingId(UUID value) {

  public BookingId {
    Objects.requireNonNull(value, "value");
  }

  public static BookingId newId() {
    return new BookingId(Ids.newV7());
  }
}
