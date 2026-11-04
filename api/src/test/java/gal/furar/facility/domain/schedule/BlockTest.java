// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Bloqueos: un día cun tramo válido (P13), período real en Europe/Madrid cos cambios de hora,
 * motivo opcional, solape e «cabe no outro».
 */
class BlockTest {

  static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
  static final FacilityId FACILITY = FacilityId.newId();

  static Block create(String date, Integer start, Integer end) {
    return create(new BlockSpec(date == null ? null : LocalDate.parse(date), start, end, null));
  }

  static Block create(BlockSpec spec) {
    return Block.create(BlockId.newId(), FACILITY, spec, "manager-1", NOW);
  }

  static void assertFields(BlockSpec spec, String code, String... fields) {
    assertThatThrownBy(() -> create(spec))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode().code()).isEqualTo(code);
              assertThat(e.violations()).extracting(FieldViolation::field).containsExactly(fields);
            });
  }

  @Test
  void aDayWithAnIntervalGivesItsRealPeriod() {
    Block b =
        create(new BlockSpec(LocalDate.parse("2026-11-03"), 480, 720, "  Mantemento do céspede "));
    assertThat(b.interval()).isEqualTo(new TimeRange(480, 720));
    assertThat(b.start()).isEqualTo(Instant.parse("2026-11-03T07:00:00Z"));
    assertThat(b.end()).isEqualTo(Instant.parse("2026-11-03T11:00:00Z"));
    assertThat(b.reason()).isEqualTo("Mantemento do céspede");
    assertThat(b.createdBy()).isEqualTo("manager-1");
    assertThat(b.version()).isNull();
  }

  @Test
  void allDayIsMidnightToMidnight() {
    Block b = create("2026-07-15", 0, 1440);
    assertThat(b.start()).isEqualTo(Instant.parse("2026-07-14T22:00:00Z"));
    assertThat(b.end()).isEqualTo(Instant.parse("2026-07-15T22:00:00Z"));
  }

  /** 25 de outubro de 2026: de 2:00 a 3:00 pasa dúas veces; o bloqueo cobre as dúas. */
  @Test
  void theRepeatedHourOfTheAutumnChangeIsBlockedWhole() {
    Block b = create("2026-10-25", 120, 180);
    assertThat(b.start()).isEqualTo(Instant.parse("2026-10-25T00:00:00Z"));
    assertThat(b.end()).isEqualTo(Instant.parse("2026-10-25T02:00:00Z"));
    assertThat(Duration.between(b.start(), b.end())).isEqualTo(Duration.ofHours(2));
  }

  /** 29 de marzo de 2026: de 2:00 a 3:00 non existe. */
  @Test
  void anIntervalThatTheSpringChangeEmptiesIsInvalid() {
    assertFields(
        new BlockSpec(LocalDate.parse("2026-03-29"), 120, 150, null),
        "facility.block-invalid-period",
        "endMinute");
    Block across = create("2026-03-29", 60, 240);
    assertThat(Duration.between(across.start(), across.end())).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void anInvalidIntervalIsAnInvalidPeriod() {
    assertFields(
        new BlockSpec(LocalDate.parse("2026-11-03"), 720, 480, null),
        "facility.block-invalid-period",
        "endMinute");
    assertFields(
        new BlockSpec(LocalDate.parse("2026-11-03"), 480, 480, null),
        "facility.block-invalid-period",
        "endMinute");
    assertFields(
        new BlockSpec(LocalDate.parse("2026-11-03"), 485, 720, null),
        "facility.block-invalid-period",
        "endMinute");
    assertFields(
        new BlockSpec(LocalDate.parse("2026-11-03"), 0, 1455, null),
        "facility.block-invalid-period",
        "endMinute");
  }

  @Test
  void fieldsAreCheckedTogetherAndABlankReasonIsNone() {
    assertFields(
        new BlockSpec(null, null, null, "x".repeat(201)),
        "facility.block-invalid",
        "date",
        "startMinute",
        "endMinute",
        "reason");
    assertThat(create(new BlockSpec(LocalDate.parse("2026-11-03"), 480, 720, "  ")).reason())
        .isNull();
  }

  @Test
  void overlapsAndWithinUseTheRealPeriod() {
    Block morning = create("2026-11-03", 480, 720);
    assertThat(morning.overlaps(create("2026-11-03", 690, 780))).isTrue();
    // Seguidos non se pisan (fin excluído).
    assertThat(morning.overlaps(create("2026-11-03", 720, 780))).isFalse();
    assertThat(morning.overlaps(create("2026-11-04", 480, 720))).isFalse();
    assertThat(create("2026-11-03", 540, 600).within(morning)).isTrue();
    assertThat(create("2026-11-03", 420, 600).within(morning)).isFalse();
  }

  @Test
  void updateKeepsIdentityAuthorAndVersion() {
    Block b =
        Block.rehydrate(
            BlockId.newId(),
            FACILITY,
            LocalDate.parse("2026-11-03"),
            new TimeRange(480, 720),
            Instant.parse("2026-11-03T07:00:00Z"),
            Instant.parse("2026-11-03T11:00:00Z"),
            null,
            "manager-1",
            3L,
            NOW,
            NOW);
    Instant later = NOW.plusSeconds(60);
    Block changed =
        b.update(new BlockSpec(LocalDate.parse("2026-11-04"), 600, 660, "Pintura"), later);
    assertThat(changed.id()).isEqualTo(b.id());
    assertThat(changed.version()).isEqualTo(3L);
    assertThat(changed.createdBy()).isEqualTo("manager-1");
    assertThat(changed.createdAt()).isEqualTo(NOW);
    assertThat(changed.updatedAt()).isEqualTo(later);
    assertThat(changed.start()).isEqualTo(Instant.parse("2026-11-04T09:00:00Z"));
  }
}
