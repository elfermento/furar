// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.IdempotencyKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O persoal reserva no nome dunha persoa sen conta (mostrador ou teléfono): o mesmo oco ca {@link
 * ReserveCommand}, para quen é e, se o uso ten prezo, se xa pagou.
 *
 * @param holder para quen é (sen conta: nome e, se os deu, correo e teléfono)
 * @param paid se xa pagou no mostrador (só nos usos de pago; nos outros non conta)
 * @param key a clave de reintento (ADR-0025, punto 19), como ao reservar a persoa
 * @param customFields campos propios da reserva tal como chegan (ADR-0011); nulo = ningún
 */
public record ReserveForCommand(
    UUID facilityId,
    UUID useId,
    Instant start,
    int slots,
    @Nullable UUID partId,
    Holder holder,
    boolean paid,
    IdempotencyKey key,
    Map<String, Object> customFields) {

  public ReserveForCommand {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(holder, "holder");
    Objects.requireNonNull(key, "key");
    if (holder.hasAccount()) {
      throw new IllegalArgumentException("The staff books for a person without account");
    }
    Map<String, Object> copy = new TreeMap<>();
    if (customFields != null) {
      customFields.forEach(
          (code, value) -> {
            if (value != null) {
              copy.put(code, value);
            }
          });
    }
    customFields = Collections.unmodifiableMap(copy);
  }

  /**
   * A pegada da petición: SHA-256 (hex) do oco, para quen é, o pago e os campos propios, sen a
   * clave. Dúas peticións coa mesma clave e a mesma pegada son a mesma intención de reservar.
   */
  public String fingerprint() {
    String canonical =
        String.join(
            "|",
            "for-v1",
            facilityId.toString(),
            useId.toString(),
            start.toString(),
            Integer.toString(slots),
            partId == null ? "" : partId.toString(),
            holder.name(),
            Objects.toString(holder.email(), ""),
            Objects.toString(holder.phone(), ""),
            holder.language().code(),
            Boolean.toString(paid),
            customFields.toString());
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
    }
  }
}
