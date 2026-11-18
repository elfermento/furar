// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.FacilityDay;
import gal.furar.facility.FacilityQueries;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * O calendario dunha instalación para as tempadas (ADR-0039, punto 8; {@link
 * FacilityQueries#calendar}): festivos, tramos sen cortar polos bloqueos, os bloqueos co motivo e o
 * uso exclusivo; e a vista previa dunha tempada que pasa por un festivo.
 */
class FacilityCalendarIT extends AbstractSeasonIT {

  @Autowired FacilityQueries facilities;
  @Autowired TransactionTemplate tx;

  @Test
  void eachDayCarriesHolidayOpenHoursBlocksAndTheExclusiveUse() {
    Facility f = facility("calendario", true);
    UUID use = whole(f, Map.of());
    LocalDate blocked = LocalDate.now(ZONE).plusDays(3);
    LocalDate exclusiveDay = blocked.plusDays(1);
    UUID tournament =
        id(
            use(
                f,
                "Torneo",
                "WHOLE",
                Map.of(
                    "availability",
                    "DATES",
                    "periods",
                    List.of(Map.of("from", exclusiveDay.toString(), "to", exclusiveDay.toString())),
                    "weekdays",
                    127,
                    "blocksOthers",
                    true)));
    assertThat(blockHours(f, blocked, 600, 660, null).status()).isBetween(200, 299);
    LocalDate holiday =
        jdbc.sql(
                "select min(date) from public_holiday where date > current_date + 2"
                    + " and date < current_date + 300")
            .query(LocalDate.class)
            .single();

    List<FacilityDay> days =
        TenantContext.runAs(
            TenantRef.of(f.municipality()),
            () ->
                tx.execute(
                    s ->
                        facilities.calendar(
                            f.id(),
                            blocked,
                            holiday.isAfter(exclusiveDay) ? holiday : exclusiveDay)));

    FacilityDay withBlock = days.getFirst();
    assertThat(withBlock.date()).isEqualTo(blocked);
    assertThat(withBlock.open()).hasSize(1);
    assertThat(withBlock.open().getFirst().startMinute()).isEqualTo(OPENS);
    assertThat(withBlock.open().getFirst().endMinute()).isEqualTo(CLOSES);
    assertThat(withBlock.blocks()).hasSize(1);
    assertThat(withBlock.blocks().getFirst().reason()).isEqualTo("Mantemento");
    assertThat(withBlock.exclusiveUseId()).isNull();
    assertThat(days.get(1).exclusiveUseId()).isEqualTo(tournament);
    assertThat(days.stream().filter(FacilityDay::holiday).map(FacilityDay::date)).contains(holiday);

    // A tempada que pasa polo festivo, polo bloqueo e polo uso exclusivo.
    UUID club = organization(f, "Club Calendario");
    JsonNode overHoliday =
        previewed(
            f,
            schedule(
                club,
                f,
                use,
                null,
                List.of(holiday.getDayOfWeek().getValue()),
                600,
                690,
                holiday.minusDays(7).isBefore(blocked) ? holiday : holiday.minusDays(7),
                holiday.plusDays(7)));
    assertThat(overHoliday.get("holidays").toString()).contains(holiday.toString());
    JsonNode overBlock =
        previewed(
            f,
            schedule(
                club,
                f,
                use,
                null,
                List.of(blocked.getDayOfWeek().getValue(), exclusiveDay.getDayOfWeek().getValue()),
                600,
                690,
                blocked,
                exclusiveDay));
    assertThat(overBlock.get("conflicts")).hasSize(2);
    assertThat(overBlock.get("conflicts").get(0).get("kind").asString()).isEqualTo("BLOCK");
    assertThat(overBlock.get("conflicts").get(0).get("label").asString()).isEqualTo("Mantemento");
    assertThat(overBlock.get("conflicts").get(1).get("kind").asString())
        .isEqualTo("OTHER_USE_EXCLUSIVE");
    assertThat(overBlock.get("conflicts").get(1).get("label").asString()).isEqualTo("Torneo");
  }
}
