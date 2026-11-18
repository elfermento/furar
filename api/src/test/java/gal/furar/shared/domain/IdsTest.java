// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

  @Test
  void hasVersion7AndRfcVariant() {
    UUID id = Ids.newV7();
    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void encodesTheTimestampAndSortsByTime() {
    Instant t1 = Instant.parse("2026-10-22T08:00:00.123Z");
    Instant t2 = t1.plusMillis(1);
    UUID a = Ids.newV7(Clock.fixed(t1, ZoneOffset.UTC));
    UUID b = Ids.newV7(Clock.fixed(t2, ZoneOffset.UTC));
    assertThat(a.getMostSignificantBits() >>> 16).isEqualTo(t1.toEpochMilli());
    assertThat(a.toString().compareTo(b.toString())).isNegative();
  }

  @Test
  void isUnique() {
    Set<UUID> ids = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      ids.add(Ids.newV7());
    }
    assertThat(ids).hasSize(10_000);
  }
}
