// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.booking.domain.season.SeriesSchedule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Crear unha tempada (ADR-0039, punto 8).
 *
 * @param skip as datas con choque que viu o xestor na vista previa: se baixo o bloqueo non son
 *     exactamente estas, non se crea nada
 * @param key a clave de reintento
 */
public record CreateSeriesCommand(
    SeriesCommand series, SortedSet<LocalDate> skip, IdempotencyKey key) {

  public CreateSeriesCommand {
    Objects.requireNonNull(series, "series");
    Objects.requireNonNull(key, "key");
    skip =
        Collections.unmodifiableSortedSet(new TreeSet<>(skip == null ? Set.<LocalDate>of() : skip));
  }

  public UUID organizationId() {
    return series.organizationId();
  }

  public SeriesSchedule schedule() {
    return series.schedule();
  }

  /** SHA-256 (hex) da entrada canónica: a entidade, o horario e as datas saltadas. */
  public String fingerprint() {
    String canonical =
        String.join(
            "|",
            "v1",
            series.organizationId().toString(),
            series.schedule().canonical(),
            skip.stream().map(LocalDate::toString).collect(Collectors.joining(",")));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
    }
  }
}
