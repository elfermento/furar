// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import gal.furar.indicators.domain.Change.Direction;
import gal.furar.indicators.domain.Change.Kind;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** Ocupación limitada ao 100 % e comparación co período anterior (plan do dominio 3, §1.2.4–5). */
class RatioAndChangeTest {

  @Test
  void ratioIsCappedAt100Percent() {
    assertThat(new Ratio(30, 60).value()).hasValue(0.5);
    Ratio over = new Ratio(150, 100);
    assertThat(over.value()).hasValue(1.0);
    assertThat(over.cappedPart()).isEqualTo(100);
    assertThat(new Ratio(0, 100).value()).hasValue(0.0);
  }

  @Test
  void ratioWithoutOpenHoursHasNoValue() {
    Ratio closed = new Ratio(30, 0);
    assertThat(closed.defined()).isFalse();
    assertThat(closed.value()).isEmpty();
    assertThat(closed.cappedPart()).isZero();
  }

  @Test
  void ratioRejectsNegativesAndNaN() {
    assertThatThrownBy(() -> new Ratio(-1, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Ratio(1, -10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Ratio(Double.NaN, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Ratio(1, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void percentChange() {
    Change up = Change.percent(12, 10);
    assertThat(up.kind()).isEqualTo(Kind.PERCENT);
    assertThat(up.value().getAsDouble()).isCloseTo(20.0, within(1e-9));
    assertThat(up.direction()).isEqualTo(Direction.UP);
    assertThat(Change.percent(8, 10).value().getAsDouble()).isCloseTo(-20.0, within(1e-9));
    assertThat(Change.percent(8, 10).direction()).isEqualTo(Direction.DOWN);
    assertThat(Change.percent(10, 10).direction()).isEqualTo(Direction.SAME);
    assertThat(Change.percent(0, 10).value()).hasValue(-100.0);
  }

  @Test
  void withoutAPreviousValueThereIsNoChange() {
    assertThat(Change.percent(5, 0).value()).isEmpty();
    assertThat(Change.percent(5, 0).direction()).isEqualTo(Direction.NONE);
    assertThat(Change.percent(0, 0).value()).isEmpty();
  }

  @Test
  void pointsChangeBetweenRatios() {
    Change points = Change.points(new Ratio(60, 100), new Ratio(25, 50));
    assertThat(points.kind()).isEqualTo(Kind.POINTS);
    assertThat(points.value().getAsDouble()).isCloseTo(10.0, within(1e-9));
    assertThat(points.direction()).isEqualTo(Direction.UP);
    // O tope tamén vale na comparación: 150 % conta como 100 %.
    assertThat(Change.points(new Ratio(150, 100), new Ratio(90, 100)).value().getAsDouble())
        .isCloseTo(10.0, within(1e-9));
    // Anterior ao 0 % (con horas abertas): hai cambio en puntos.
    assertThat(Change.points(new Ratio(20, 100), new Ratio(0, 100)).value().getAsDouble())
        .isCloseTo(20.0, within(1e-9));
  }

  @Test
  void pointsWithoutOpenHoursHaveNoValue() {
    assertThat(Change.points(new Ratio(60, 100), new Ratio(0, 0)).value()).isEmpty();
    assertThat(Change.points(new Ratio(0, 0), new Ratio(60, 100)).value()).isEmpty();
    assertThat(Change.points(new Ratio(0, 0), new Ratio(60, 100)).direction())
        .isEqualTo(Direction.NONE);
  }

  @Test
  void changeNeedsKindAndValue() {
    assertThatThrownBy(() -> new Change(null, OptionalDouble.empty()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Change(Kind.PERCENT, null))
        .isInstanceOf(NullPointerException.class);
  }
}
