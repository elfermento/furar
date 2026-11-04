// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Excepcións: pechado sen tramo, aberto cun tramo válido (P9), motivo opcional. */
class ScheduleExceptionTest {

  static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
  static final FacilityId FACILITY = FacilityId.newId();
  static final LocalDate DAY = LocalDate.parse("2026-12-24");

  static ScheduleException create(ExceptionSpec spec) {
    return ScheduleException.create(ScheduleExceptionId.newId(), FACILITY, spec, NOW);
  }

  static void assertFields(ExceptionSpec spec, String code, String... fields) {
    assertThatThrownBy(() -> create(spec))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode().code()).isEqualTo(code);
              assertThat(e.violations()).extracting(FieldViolation::field).containsExactly(fields);
            });
  }

  @Test
  void openWithOneIntervalAndTrimmedReason() {
    ScheduleException e = create(new ExceptionSpec(DAY, true, 540, 840, "  Noiteboa "));
    assertThat(e.isOpen()).isTrue();
    assertThat(e.hours()).containsExactly(new TimeRange(540, 840));
    assertThat(e.reason()).isEqualTo("Noiteboa");
    assertThat(e.version()).isNull();
    assertThat(create(new ExceptionSpec(DAY, true, 0, 1440, null)).hours())
        .containsExactly(new TimeRange(0, 1440));
  }

  @Test
  void closedHasNoIntervalAndBlankReasonIsNone() {
    ScheduleException e = create(new ExceptionSpec(DAY, false, null, null, "   "));
    assertThat(e.isOpen()).isFalse();
    assertThat(e.hours()).isEmpty();
    assertThat(e.reason()).isNull();
  }

  @Test
  void fieldsAreCheckedTogether() {
    assertFields(
        new ExceptionSpec(null, true, null, 840, "x".repeat(201)),
        "facility.exception-invalid",
        "date",
        "reason",
        "startMinute");
    assertFields(
        new ExceptionSpec(DAY, false, 540, 840, null),
        "facility.exception-invalid",
        "startMinute",
        "endMinute");
  }

  @Test
  void intervalFollowsTheWeekRules() {
    assertFields(
        new ExceptionSpec(DAY, true, 840, 540, null), "facility.interval-invalid", "endMinute");
    assertFields(
        new ExceptionSpec(DAY, true, 540, 1455, null), "facility.interval-invalid", "endMinute");
    assertFields(
        new ExceptionSpec(DAY, true, 545, 840, null), "facility.interval-invalid", "endMinute");
  }

  @Test
  void updateKeepsIdentityAndVersion() {
    ScheduleException stored =
        ScheduleException.rehydrate(
            ScheduleExceptionId.newId(), FACILITY, DAY, null, null, 3L, NOW, NOW);
    Instant later = NOW.plusSeconds(60);
    ScheduleException changed =
        stored.update(new ExceptionSpec(DAY.plusDays(7), true, 600, 720, "Festa local"), later);
    assertThat(changed.id()).isEqualTo(stored.id());
    assertThat(changed.version()).isEqualTo(3L);
    assertThat(changed.date()).isEqualTo(DAY.plusDays(7));
    assertThat(changed.createdAt()).isEqualTo(NOW);
    assertThat(changed.updatedAt()).isEqualTo(later);
  }
}
