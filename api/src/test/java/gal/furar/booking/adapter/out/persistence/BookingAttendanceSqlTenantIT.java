// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.Booking;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingAttendanceSql} só le e marca as reservas do seu
 * concello, coa RLS da transacción e co concello como parámetro. Co concello alleo como parámetro
 * non ve nin cambia nada.
 */
class BookingAttendanceSqlTenantIT extends AbstractTenantIT {

  /** Rematou hai dúas horas. */
  static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(3 * 3600);

  static final String ACTOR = "furar:no-show";

  private final List<AttendanceSeeds.Seeded> seeded = new ArrayList<>();

  @Autowired BookingAttendanceSql subject;
  @Autowired TransactionTemplate tx;

  AttendanceSeeds.Seeded insert(UUID municipality) {
    AttendanceSeeds.Seeded row =
        AttendanceSeeds.booking(jdbc, municipality, START, "NONE", "AT", seeded.size());
    seeded.add(row);
    return row;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insert(municipality).booking();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                s ->
                    seeded.stream()
                        .flatMap(row -> subject.findLocked(municipality, row.booking()).stream())
                        .<Object>map(b -> b.id().value())
                        .toList()));
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("asiste-a")));
    UUID b = id(createMunicipality(uniqueSlug("asiste-b")));
    AttendanceSeeds.Seeded theirs = insert(b);
    Instant now = Instant.now();

    List<Object> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s ->
                        List.<Object>of(
                            subject.findLocked(b, theirs.booking()).isPresent(),
                            subject.findByCodeLocked(b, theirs.code()).isPresent(),
                            subject.findByCodeLocked(a, theirs.code()).isPresent(),
                            subject
                                .noShowCandidates(
                                    b, theirs.facility(), START.minusSeconds(60), now, 10)
                                .size(),
                            subject.markNoShow(b, List.of(theirs.booking()), now, ACTOR).size())));

    assertThat(crossed).containsExactly(false, false, false, 0, 0);
    assertThat(attendance(theirs.booking())).isEqualTo("NONE");
  }

  /** No seu concello: atopa a reserva polo código, vea como candidata e márcaa unha soa vez. */
  @Test
  void inItsOwnMunicipalityFindsTheCandidatesAndMarksThemOnce() {
    UUID a = id(createMunicipality(uniqueSlug("asiste-c")));
    AttendanceSeeds.Seeded mine = insert(a);
    Instant now = Instant.now();

    List<Object> own =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s -> {
                      List<Object> seen = new ArrayList<>();
                      seen.add(
                          subject
                              .findByCodeLocked(a, mine.code())
                              .map(Booking::id)
                              .map(id -> id.value())
                              .orElse(null));
                      // Só as que empezaron despois de acender o control e xa remataron.
                      seen.add(subject.noShowCandidates(a, mine.facility(), START, now, 10).size());
                      seen.add(
                          subject
                              .noShowCandidates(
                                  a, mine.facility(), START.minusSeconds(60), START, 10)
                              .size());
                      List<UUID> candidates =
                          subject.noShowCandidates(
                              a, mine.facility(), START.minusSeconds(60), now, 10);
                      seen.add(candidates);
                      List<BookingAttendanceSql.Marked> marked =
                          subject.markNoShow(a, candidates, now, ACTOR);
                      seen.add(marked.size());
                      seen.add(marked.getFirst().emailKey());
                      // Xa tocada: nin candidata nin se volve marcar.
                      seen.add(
                          subject
                              .noShowCandidates(a, mine.facility(), START.minusSeconds(60), now, 10)
                              .size());
                      seen.add(subject.markNoShow(a, candidates, now, ACTOR).size());
                      seen.add(subject.markNoShow(a, List.of(), now, ACTOR).size());
                      return seen;
                    }));

    assertThat(own)
        .containsExactly(
            mine.booking(), 0, 0, List.of(mine.booking()), 1, AttendanceSeeds.EMAIL, 0, 0, 0);
    assertThat(attendance(mine.booking())).isEqualTo("NO_SHOW");
    assertThat(
            jdbc.sql("select attendance_by || ' ' || version from booking where id = :id")
                .param("id", mine.booking())
                .query(String.class)
                .single())
        .isEqualTo(ACTOR + " 1");
  }

  private String attendance(UUID booking) {
    return jdbc.sql("select attendance from booking where id = :id")
        .param("id", booking)
        .query(String.class)
        .single();
  }
}
