// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** O período da descarga en CSV: un día vale; doce meses, si; un día máis, non. */
class BookingExportServiceTest {

  @Test
  void atMostTwelveMonths() {
    LocalDate jan1 = LocalDate.parse("2026-01-01");
    assertThatCode(() -> BookingExportService.check(jan1, jan1)).doesNotThrowAnyException();
    assertThatCode(() -> BookingExportService.check(jan1, LocalDate.parse("2026-12-31")))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> BookingExportService.check(jan1, LocalDate.parse("2027-01-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode().code()).isEqualTo("booking.export-range-too-long");
              assertThat(e.params()).containsEntry("months", 12);
            });
    assertThatThrownBy(() -> BookingExportService.check(jan1, LocalDate.parse("2025-12-31")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode().code()).isEqualTo("common.invalid-parameter"));
  }
}
