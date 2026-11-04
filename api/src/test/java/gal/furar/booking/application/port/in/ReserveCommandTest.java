// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.IdempotencyKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A pegada da petición de reservar (ADR-0025, punto 19): o que se pide, sen a clave. */
class ReserveCommandTest {

  static final UUID FACILITY = UUID.fromString("0192f0a0-0000-7000-8000-000000000001");
  static final UUID USE = UUID.fromString("0192f0a0-0000-7000-8000-000000000002");
  static final UUID PART = UUID.fromString("0192f0a0-0000-7000-8000-000000000003");
  static final Instant START = Instant.parse("2026-11-10T17:00:00Z");

  static ReserveCommand command(Instant start, int slots, UUID part, String key) {
    return new ReserveCommand(FACILITY, USE, start, slots, part, new IdempotencyKey(key));
  }

  @Test
  void theSameRequestHasTheSameFingerprintWhateverTheKey() {
    assertThat(command(START, 1, null, "a").fingerprint())
        .isEqualTo(command(START, 1, null, "b").fingerprint())
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }

  @Test
  void theSameInstantInAnotherOffsetIsTheSameRequest() {
    Instant madrid = OffsetDateTime.parse("2026-11-10T18:00:00+01:00").toInstant();
    assertThat(command(madrid, 1, null, "a").fingerprint())
        .isEqualTo(command(START, 1, null, "a").fingerprint());
  }

  @Test
  void anyChangeInWhatIsAskedChangesTheFingerprint() {
    String base = command(START, 1, null, "a").fingerprint();
    assertThat(command(START.plusSeconds(3600), 1, null, "a").fingerprint()).isNotEqualTo(base);
    assertThat(command(START, 2, null, "a").fingerprint()).isNotEqualTo(base);
    assertThat(command(START, 1, PART, "a").fingerprint()).isNotEqualTo(base);
    assertThat(
            new ReserveCommand(USE, FACILITY, START, 1, null, new IdempotencyKey("a"))
                .fingerprint())
        .isNotEqualTo(base);
  }
}
