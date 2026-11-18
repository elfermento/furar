// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.municipality.MunicipalityOpenMatchPolicy;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * «Buscar xente para xogar» do concello (ADR-0040): apagado por defecto, horas de 1 a 72 e o último
 * cambio, desde o que a tarefa do prazo mira as partidas.
 */
class OpenMatchPolicyTest {

  static final Instant T1 = Instant.parse("2026-11-12T10:00:00Z");
  static final Instant T2 = Instant.parse("2026-11-13T10:00:00Z");

  @Test
  void offByDefaultWithTwelveHours() {
    assertThat(OpenMatchPolicy.OFF.enabled()).isFalse();
    assertThat(OpenMatchPolicy.OFF.cancelHours()).isEqualTo(12);
  }

  @Test
  void hoursGoFromOneToSeventyTwo() {
    for (int hours : new int[] {0, 73}) {
      assertThatThrownBy(() -> OpenMatchPolicy.OFF.changed(true, hours, T1))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode().code())
          .isEqualTo("municipality.open-match-policy-invalid");
    }
    assertThat(OpenMatchPolicy.OFF.changed(true, 72, T1).cancelHours()).isEqualTo(72);
  }

  @Test
  void turningOnKeepsTheFirstMomentAndEveryChangeMovesChangedAt() {
    OpenMatchPolicy on = OpenMatchPolicy.OFF.changed(true, 12, T1);
    assertThat(on.since()).isEqualTo(T1);
    assertThat(on.changedAt()).isEqualTo(T1);

    OpenMatchPolicy same = on.changed(true, 12, T2);
    assertThat(same.since()).isEqualTo(T1);
    assertThat(same.changedAt()).isEqualTo(T1);

    OpenMatchPolicy raised = on.changed(true, 24, T2);
    assertThat(raised.since()).isEqualTo(T1);
    assertThat(raised.changedAt()).isEqualTo(T2);

    OpenMatchPolicy off = raised.changed(false, 24, T2);
    assertThat(off.enabled()).isFalse();
  }

  @Test
  void theDeadlineTaskLooksFromTheLastChange() {
    assertThat(new MunicipalityOpenMatchPolicy(T1, 12, T2).deadlinesFrom()).isEqualTo(T2);
    assertThat(new MunicipalityOpenMatchPolicy(T2, 12, T1).deadlinesFrom()).isEqualTo(T2);
    assertThat(new MunicipalityOpenMatchPolicy(null, 12, T2).deadlinesFrom()).isNull();
  }
}
