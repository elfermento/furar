// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

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
 * Reservar un oco.
 *
 * @param start inicio da primeira franxa (un {@code start} da dispoñibilidade)
 * @param slots franxas seguidas (1–16)
 * @param partId a parte escollida; sen ela nun uso por partes, «tanto me ten»
 * @param key a clave de reintento (ADR-0025, punto 19)
 * @param customFields campos propios da reserva tal como chegan (ADR-0011); nulo = ningún
 * @param participants para quen é, nos usos por prazas (ADR-0031); nulo = só quen reserva
 */
public record ReserveCommand(
    UUID facilityId,
    UUID useId,
    Instant start,
    int slots,
    @Nullable UUID partId,
    IdempotencyKey key,
    Map<String, Object> customFields,
    ParticipantSelection participants) {

  public ReserveCommand {
    participants = participants == null ? ParticipantSelection.SELF : participants;
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(key, "key");
    // Map.copyOf non admite nulos: un campo baleiro (null) vale o mesmo que non mandalo.
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

  /** Sen campos propios. */
  public ReserveCommand(
      UUID facilityId,
      UUID useId,
      Instant start,
      int slots,
      @Nullable UUID partId,
      IdempotencyKey key) {
    this(facilityId, useId, start, slots, partId, key, Map.of(), ParticipantSelection.SELF);
  }

  /** Unha praza para quen reserva. */
  public ReserveCommand(
      UUID facilityId,
      UUID useId,
      Instant start,
      int slots,
      @Nullable UUID partId,
      IdempotencyKey key,
      Map<String, Object> customFields) {
    this(facilityId, useId, start, slots, partId, key, customFields, ParticipantSelection.SELF);
  }

  /**
   * A pegada da petición: SHA-256 (hex) do que se pide, sen a clave. Dúas peticións coa mesma clave
   * e a mesma pegada son a mesma intención de reservar; con outra pegada, un erro do cliente.
   * {@code start} vai como {@link Instant#toString()}, así {@code 17:00:00Z} e {@code
   * 18:00:00+01:00} son o mesmo. Os campos propios só entran se os hai, en orde de código: unha
   * petición sen eles ten a mesma pegada ca antes de existiren.
   */
  public String fingerprint() {
    String canonical =
        String.join(
            "|",
            "v1",
            facilityId.toString(),
            useId.toString(),
            start.toString(),
            Integer.toString(slots),
            partId == null ? "" : partId.toString());
    if (!customFields.isEmpty()) {
      canonical += "|cf:" + customFields;
    }
    // Para quen é só entra se non é «só eu»: a pegada dun cliente antigo non cambia.
    if (!participants.isDefault()) {
      canonical += "|p:" + participants.canonical();
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
    }
  }
}
