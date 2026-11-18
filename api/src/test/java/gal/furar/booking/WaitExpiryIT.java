// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.WaitExpiryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Ao pasar a hora, a espera bórrase e queda o reconto anónimo (ADR-0033, puntos 8 e 12); a baixa
 * dunha conta borra as súas esperas.
 */
@ExtendWith(OutputCaptureExtension.class)
class WaitExpiryIT extends AbstractWaitIT {

  @Autowired WaitExpiryService expiry;

  /** Leva a espera a unha hora xa empezada. */
  void past(UUID wait, OffsetDateTime start) {
    jdbc.sql(
            "update booking_wait set slot_start = :s, slot_end = :s + interval '1 hour'"
                + " where id = :id")
        .param("s", start)
        .param("id", wait)
        .update();
  }

  @Test
  void pastWaitsAreDeletedAndCountedOnce(CapturedOutput output) throws Exception {
    Facility f = facility("espera-vence", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    booked(xan, f, use, at(day, 600));
    booked(xan, f, use, at(day, 660));
    String sub = "ana-" + UUID.randomUUID();
    String ana = person(sub);
    UUID first = waiting(ana, f, use, at(day, 600));
    UUID second = waiting(person(), f, use, at(day, 600));
    UUID alive = waiting(ana, f, use, at(day, 660));
    OffsetDateTime started =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withMinute(0).withSecond(0).withNano(0);
    past(first, started);
    past(second, started);

    // Antes da tarefa xa non se listan.
    assertThat(myWaits(ana, f)).containsExactly(alive);

    // Dúas pasadas á vez non contan dobre.
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> runs = List.of(pool.submit(expiry::apply), pool.submit(expiry::apply));
      for (Future<Integer> run : runs) {
        run.get();
      }
    } finally {
      pool.shutdownNow();
    }
    expiry.apply();

    assertThat(waitsIn(f)).isEqualTo(1);
    assertThat(
            jdbc.sql("select waits from booking_wait_tally where use_id = :u and slot_start = :s")
                .param("u", use)
                .param("s", started)
                .query(Integer.class)
                .list())
        .containsExactly(2);
    assertThat(myWaits(ana, f)).containsExactly(alive);
    // O rexistro técnico só leva cantas: nin a conta nin o correo.
    assertThat(output).contains("wait expiry municipality=" + f.municipality());
    assertThat(output.toString()).doesNotContain(email(sub));
  }

  @Test
  void theOldTallyIsForgotten() {
    Facility f = facility("espera-reconto", true);
    UUID use = whole(f, Map.of());
    Instant old = Instant.now().minus(java.time.Duration.ofDays(6 * 366));
    Instant recent = Instant.now().minus(java.time.Duration.ofDays(4 * 365));
    for (Instant at : List.of(old, recent)) {
      jdbc.sql(
              """
              insert into booking_wait_tally
                (municipality_id, facility_id, use_id, slot_start, waits)
              values (:m, :f, :u, :s, 3)
              """)
          .param("m", f.municipality())
          .param("f", f.id())
          .param("u", use)
          .param("s", at.atOffset(ZoneOffset.UTC))
          .update();
    }

    expiry.apply();

    assertThat(tallyIn(f)).isEqualTo(3);
  }

  /** {@code close_account} borra as esperas da conta, en todos os concellos, e só as súas. */
  @Test
  void closingTheAccountDeletesItsWaits() {
    Facility f = facility("espera-baixa", true);
    Facility g = facility("espera-baixa-outro", true);
    Instant ten = at(tomorrow(), 600);
    String sub = "ana-" + UUID.randomUUID();
    String bea = person();
    for (Facility where : List.of(f, g)) {
      UUID use = whole(where, Map.of());
      booked(person(), where, use, ten);
      waiting(person(sub), where, use, ten);
      waiting(bea, where, use, ten);
    }

    jdbc.sql("select close_account(:k)").param("k", sub).query().singleRow();

    assertThat(
            jdbc.sql("select count(*) from booking_wait where account_subject = :k")
                .param("k", sub)
                .query(Integer.class)
                .single())
        .isZero();
    assertThat(waitsIn(f) + waitsIn(g)).isEqualTo(2);
  }
}
