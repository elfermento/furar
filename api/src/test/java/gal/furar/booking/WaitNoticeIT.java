// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cando se anuncia que unha hora quedou libre (ADR-0033, punto 7): en todo caso de uso que solta
 * ocupación, só das franxas esperadas que pasan de ocupadas a libres e sen datos de ninguén.
 */
class WaitNoticeIT extends AbstractWaitIT {

  static final Map<String, Object> TWO_SEATS = Map.of("capacity", 2);

  UUID seats(Facility f) {
    return id(use(f, "Natación", "SEATS", TWO_SEATS));
  }

  @Test
  void cancellingByThePersonOrByTheStaffAnnouncesTheHourOnce() {
    Facility f = facility("aviso-cancelar", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    UUID first = booked(xan, f, use, at(day, 600));
    UUID second = booked(xan, f, use, at(day, 660));
    UUID nobody = booked(xan, f, use, at(day, 720));
    waiting(person(), f, use, at(day, 600));
    waiting(person(), f, use, at(day, 600));
    waiting(person(), f, use, at(day, 660));

    List<BookingSlotReleased> byPerson =
        released(() -> assertThat(cancelMine(xan, f, first).status()).isEqualTo(200));
    List<BookingSlotReleased> byStaff =
        released(() -> assertThat(cancelByStaff(f, second).status()).isEqualTo(200));
    // Sen esperas por esa hora, nada.
    List<BookingSlotReleased> unwaited =
        released(() -> assertThat(cancelMine(xan, f, nobody).status()).isEqualTo(200));

    assertThat(byPerson).hasSize(1);
    BookingSlotReleased event = byPerson.getFirst();
    assertThat(event.municipalityId()).isEqualTo(f.municipality());
    assertThat(event.facilityId()).isEqualTo(f.id());
    assertThat(event.useId()).isEqualTo(use);
    assertThat(event.start()).isEqualTo(at(day, 600));
    assertThat(event.end()).isEqualTo(at(day, 660));
    assertThat(byStaff).extracting(BookingSlotReleased::start).containsExactly(at(day, 660));
    assertThat(byStaff.getFirst().releaseId()).isNotEqualTo(event.releaseId());
    assertThat(unwaited).isEmpty();
    // As esperas seguen vivas: reserva a primeira que entre.
    assertThat(waitsIn(f)).isEqualTo(3);
  }

  @Test
  void changingTheHourAnnouncesTheOldOne() {
    Facility f = facility("aviso-cambiar", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    UUID booking = booked(xan, f, use, at(day, 600));
    waiting(person(), f, use, at(day, 600));

    // De unha franxa a dúas que seguen pisando a das 10: a hora segue chea, non se avisa.
    List<BookingSlotReleased> longer =
        released(
            () ->
                assertThat(changeMine(xan, f, booking, use, at(day, 600), 2).status())
                    .isEqualTo(200));
    List<BookingSlotReleased> moved =
        released(
            () ->
                assertThat(changeMine(xan, f, booking, use, at(day, 720), 1).status())
                    .isEqualTo(200));

    assertThat(longer).isEmpty();
    assertThat(moved).extracting(BookingSlotReleased::start).containsExactly(at(day, 600));
  }

  /** Por prazas: a última praza que se solta avisa; a segunda cancelación xa non. */
  @Test
  void aSecondCancellationBySeatsDoesNotAnnounceAgain() {
    Facility f = facility("aviso-prazas", true);
    UUID use = seats(f);
    Instant ten = at(tomorrow(), 600);
    String xan = person();
    String xoana = person();
    UUID one = booked(xan, f, use, ten);
    UUID two = booked(xoana, f, use, ten);
    waiting(person(), f, use, ten);

    List<BookingSlotReleased> lastSeat =
        released(() -> assertThat(cancelMine(xan, f, one).status()).isEqualTo(200));
    List<BookingSlotReleased> again =
        released(() -> assertThat(cancelMine(xoana, f, two).status()).isEqualTo(200));

    assertThat(lastSeat).hasSize(1);
    assertThat(again).isEmpty();
  }

  /** A hora que ocupaba outro uso da instalación tamén se anuncia a quen espera por este. */
  @Test
  void theHourAnotherUseOccupiedIsAnnounced() {
    Facility f = facility("aviso-outro-uso", true);
    UUID padel = whole(f, "Pádel", Map.of());
    UUID tennis = whole(f, "Tenis", Map.of());
    Instant ten = at(tomorrow(), 600);
    String xan = person();
    UUID booking = booked(xan, f, tennis, ten);
    waiting(person(), f, padel, ten);

    List<BookingSlotReleased> events =
        released(() -> assertThat(cancelMine(xan, f, booking).status()).isEqualTo(200));

    assertThat(events).extracting(BookingSlotReleased::useId).containsExactly(padel);
  }

  /** Un bloqueo que cancela a reserva quita a hora: non se anuncia e a espera bórrase. */
  @Test
  void aBlockThatCancelsDoesNotAnnounce() {
    Facility f = facility("aviso-bloqueo", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    UUID booking = booked(person(), f, use, at(day, 600));
    waiting(person(), f, use, at(day, 600));

    List<BookingSlotReleased> events =
        released(
            () ->
                assertThat(blockHours(f, day, 570, 690, List.of(cancelDecision(booking))).status())
                    .isEqualTo(201));

    assertThat(events).isEmpty();
    assertThat(waitsIn(f)).isZero();
    assertThat(tallyIn(f)).isZero();
  }

  /**
   * Un cambio na instalación que cancela reservas e deixa a hora aberta: un uso pasa a bloquear os
   * demais, cancélase a reserva do outro uso e a hora queda libre para quen esperaba por el.
   */
  @Test
  void aFacilityChangeThatCancelsAnnouncesTheHourThatStaysOpen() {
    Facility f = facility("aviso-exclusivo", true);
    UUID padel = whole(f, "Pádel", Map.of());
    LocalDate day = tomorrow();
    Map<String, Object> tournament = new LinkedHashMap<>();
    tournament.put("name", "Torneo");
    tournament.put("rentalMode", "WHOLE");
    tournament.put("slotMinutes", 60);
    tournament.put("availability", "DATES");
    tournament.put("periods", List.of(Map.of("from", day.toString(), "to", day.toString())));
    tournament.put("weekdays", 127);
    String manager = TestTokens.manager(f.slug());
    String uses = facilities(f.slug()) + "/" + f.id() + "/uses";
    Api.Response created = api().post(uses, tournament).token(manager).send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID tournamentUse = id(created.json());
    UUID booking = booked(person(), f, padel, at(day, 600));
    waiting(person(), f, tournamentUse, at(day, 600));
    String waitsForPadel = person();
    booked(person(), f, padel, at(day, 660));
    waiting(waitsForPadel, f, padel, at(day, 660));

    tournament.put("blocksOthers", true);
    UUID other =
        jdbc.sql("select id from booking where use_id = :u and lower(period) = :s")
            .param("u", padel)
            .param("s", at(day, 660).atOffset(java.time.ZoneOffset.UTC))
            .query(UUID.class)
            .single();
    tournament.put("decisions", List.of(cancelDecision(booking), cancelDecision(other)));
    List<Api.Response> saved = new ArrayList<>();
    List<BookingSlotReleased> events =
        released(
            () ->
                saved.add(
                    api()
                        .put(uses + "/" + tournamentUse, tournament)
                        .ifMatch(created.etagVersion())
                        .token(manager)
                        .send()));

    assertThat(saved.getFirst().status()).as(saved.getFirst().toString()).isEqualTo(200);
    assertThat(events).extracting(BookingSlotReleased::useId).containsExactly(tournamentUse);
    assertThat(events).extracting(BookingSlotReleased::start).containsExactly(at(day, 600));
    // Quen esperaba polo outro uso ese día xa non ten hora: a súa espera bórrase, sen reconto.
    assertThat(myWaits(waitsForPadel, f)).isEmpty();
    assertThat(waitsIn(f)).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select (details ->> 'facilityChange') || ' ' || (details ->> 'count')"
                        + " from audit_event where municipality_id = :m"
                        + " and action = 'booking.waits-removed'")
                .param("m", f.municipality())
                .query(String.class)
                .list())
        .as("as dos outros usos bórranse; a do uso que bloquea consérvase")
        .containsExactly("ExclusiveUse 1");
    assertThat(tallyIn(f)).isZero();
  }

  /**
   * Esperar e cancelar á vez: co bloqueo da instalación, ou a cancelación ve a espera e anuncia, ou
   * a espera ve a hora libre e dá 409. Nunca unha espera sen aviso cunha hora libre.
   */
  @Test
  void waitingWhileTheHourIsBeingCancelledIsNeverLeftWithoutNotice() throws Exception {
    Facility f = facility("aviso-carreira", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    for (int round = 0; round < 4; round++) {
      Instant hour = at(day, 540 + round * 60);
      UUID booking = booked(xan, f, use, hour);
      String ana = person();
      List<Api.Response> responses = new ArrayList<>();
      List<BookingSlotReleased> events =
          released(
              () -> {
                try {
                  responses.addAll(
                      together(
                          List.of(
                              () -> join(ana, f, use, hour), () -> cancelMine(xan, f, booking))));
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });

      Api.Response joined = responses.get(0);
      assertThat(responses.get(1).status()).isEqualTo(200);
      if (joined.status() == 201) {
        assertThat(events).as("esperou antes: anúnciase").hasSize(1);
      } else {
        assertThat(problem(joined)).isEqualTo("409 booking.wait-slot-free");
        assertThat(events).isEmpty();
      }
    }
  }
}
