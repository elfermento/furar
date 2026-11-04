// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Language;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Código da reserva (ADR-0025, punto 13), prezo (§1.2.11) e persoa que reserva. */
class BookingValuesTest {

  @Test
  void codesUseAnAlphabetWithoutLookAlikes() {
    assertThat(BookingCode.ALPHABET).hasSize(32).doesNotContain("0", "O", "1", "I");
    Random random = new Random(7);
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 2_000; i++) {
      BookingCode code = BookingCode.random(random);
      assertThat(code.value()).matches("^FUR-[A-HJ-NP-Z2-9]{4}$");
      seen.add(code.value());
    }
    assertThat(seen).hasSizeGreaterThan(1_990);
  }

  @Test
  void codesWithLookAlikesOrAnotherShapeAreRejected() {
    for (String bad :
        new String[] {
          "FUR-AB0C", "FUR-ABOC", "FUR-AB1C", "FUR-ABIC", "FUR-ab23", "FUR-AB2", "XYZ-AB23"
        }) {
      assertThatThrownBy(() -> new BookingCode(bad)).isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(new BookingCode("FUR-AB23")).hasToString("FUR-AB23");
  }

  @Test
  void aPaidPriceMultipliesBySlots() {
    assertThat(Price.paid(750).forSlots(3)).isEqualTo(Price.paid(2250));
    assertThat(Price.free().forSlots(3)).isEqualTo(Price.free());
    assertThat(Price.none().forSlots(2)).isEqualTo(Price.none());
    assertThat(Price.paid(750).initialPayment()).isEqualTo(PaymentStatus.PENDING);
    assertThat(Price.free().initialPayment()).isEqualTo(PaymentStatus.NONE);
    assertThat(Price.none().initialPayment()).isEqualTo(PaymentStatus.NONE);
  }

  @Test
  void onlyAPaidPriceHasAnAmount() {
    assertThatThrownBy(() -> new Price(PriceKind.PAID, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Price(PriceKind.FREE, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Price.paid(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Price.paid(100).forSlots(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theHolderHasWhatTheTableAsks() {
    assertThatThrownBy(() -> new Holder("", "Ana", "a@b.gal", Language.GL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Holder("s", "x".repeat(201), "a@b.gal", Language.GL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Holder("s", "Ana", "sen-arroba", Language.ES))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
