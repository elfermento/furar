// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O persoal cambia unha reserva co mesmo formulario, relleno: o oco (na mesma instalación), para
 * quen é (só nas que fixo o persoal), o pago e os campos propios; e se se avisa á persoa.
 *
 * @param expectedVersion a do {@code If-Match}
 * @param holder para quen é (o idioma dos correos queda); nulo = non se toca (sempre nulo nas
 *     reservas cunha conta)
 * @param paid se xa pagou; nulo = non se toca
 * @param notifyPerson se se lle manda o aviso do cambio por correo (se ten correo)
 * @param customFields campos propios tal como chegan; nulo = non se tocan
 */
public record ChangeBookingByStaffCommand(
    BookingId id,
    long expectedVersion,
    UUID useId,
    Instant start,
    int slots,
    @Nullable UUID partId,
    @Nullable Contact holder,
    @Nullable Boolean paid,
    boolean notifyPerson,
    @Nullable Map<String, Object> customFields) {

  public ChangeBookingByStaffCommand {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    if (customFields != null) {
      Map<String, Object> copy = new TreeMap<>();
      customFields.forEach(
          (code, value) -> {
            if (value != null) {
              copy.put(code, value);
            }
          });
      customFields = Collections.unmodifiableMap(copy);
    }
  }

  /** Para quen é: o nome e, se os deu, o correo e o teléfono. */
  public record Contact(String name, @Nullable String email, @Nullable String phone) {

    public Contact {
      Objects.requireNonNull(name, "name");
    }
  }
}
