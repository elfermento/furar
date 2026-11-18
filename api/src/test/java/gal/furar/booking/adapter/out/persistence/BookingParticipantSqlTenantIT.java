// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.Participant;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingParticipantSql} só le e cambia as persoas das
 * reservas do seu concello, coa RLS da transacción e co concello como parámetro. Co concello alleo
 * como parámetro non ve nin cambia nada.
 */
class BookingParticipantSqlTenantIT extends AbstractTenantIT {

  static final Instant START = Instant.parse("2031-03-10T17:00:00Z");
  static final String KEY = "e:ana@example.org";

  /** As reservas sementadas por esta clase: {@code visibleTo} pregunta por todas. */
  private final List<UUID> seeded = new ArrayList<>();

  @Autowired BookingParticipantSql subject;
  @Autowired TransactionTemplate tx;

  /** Unha reserva por prazas confirmada (coa súa instalación e uso) e o seu titular con praza. */
  Seeded insertBooking(UUID municipality) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
    UUID booking = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility
              (id, municipality_id, name, street, postal_code, locality, latitude,
               longitude, created_at, updated_at, version)
            values (:id, :m, :name, 'Rúa do Alto', '15570', 'Narón', 43.5, -8.2,
                    now(), now(), 0)
            """)
        .param("id", facility)
        .param("m", municipality)
        .param("name", "Piscina " + facility)
        .update();
    jdbc.sql(
            """
            insert into facility_use
              (id, municipality_id, facility_id, name, rental_mode, capacity, slot_minutes,
               availability, created_at, updated_at, version)
            values (:id, :m, :f, 'Nado libre', 'SEATS', 20, 60, 'ALWAYS', now(), now(), 0)
            """)
        .param("id", use)
        .param("m", municipality)
        .param("f", facility)
        .update();
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_email_key, holder_language, created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'SEATS',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED', 'NONE',
                    'NONE', :start, 'sub-ana', 'Ana Rey', 'ana@example.org', 'ana@example.org',
                    'gl', now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("code", "FUR-" + code(seeded.size()))
        .param("f", facility)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .update();
    jdbc.sql(
            """
            insert into booking_participant
              (id, municipality_id, booking_id, use_id, period, kind, person_key, position)
            values (:id, :m, :b, :u, tstzrange(:start, :start + interval '1 hour', '[)'),
                    'HOLDER', :key, 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("b", booking)
        .param("u", use)
        .param("start", OffsetDateTime.ofInstant(START, ZoneOffset.UTC))
        .param("key", KEY)
        .update();
    seeded.add(booking);
    return new Seeded(booking, use);
  }

  private static String code(int n) {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    return "PT" + alphabet.charAt(n / 32 % 32) + alphabet.charAt(n % 32);
  }

  record Seeded(UUID booking, UUID use) {}

  @Override
  protected Object seed(UUID municipality) {
    return insertBooking(municipality).booking();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                s ->
                    subject.of(municipality, seeded).stream()
                        .<Object>map(BookingParticipantSql.Row::bookingId)
                        .toList()));
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("persoas-a")));
    UUID b = id(createMunicipality(uniqueSlug("persoas-b")));
    Seeded theirs = insertBooking(b);
    Instant end = START.plusSeconds(3600);

    List<Object> crossed =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s ->
                        List.<Object>of(
                            subject.of(b, List.of(theirs.booking())).size(),
                            subject
                                .seated(b, theirs.use(), List.of(KEY), START, end, new UUID(0, 0))
                                .size(),
                            subject.rekeyHolder(b, theirs.booking(), "e:outra@example.org"),
                            subject.unkeyHolder(b, theirs.booking()),
                            subject.replace(b, theirs.booking()),
                            subject.deactivate(b, theirs.booking()),
                            subject.anonymise(b, List.of(theirs.booking())))));

    assertThat(crossed).containsExactly(0, 0, 0, 0, 0, 0, 0);
    assertThat(
            jdbc.sql(
                    "select person_key || '/' || active from booking_participant"
                        + " where booking_id = :b")
                .param("b", theirs.booking())
                .query(String.class)
                .single())
        .as("a fila do outro concello segue como estaba")
        .isEqualTo(KEY + "/true");
  }

  /** No seu concello: a praza vese, a clave cambia, e cancelar, cambiar e anonimizar sóltana. */
  @Test
  void inItsOwnMunicipalityReadsAndChangesItsRows() {
    UUID a = id(createMunicipality(uniqueSlug("persoas-c")));
    Seeded mine = insertBooking(a);
    Seeded other = insertBooking(a);
    Instant end = START.plusSeconds(3600);
    UUID none = new UUID(0, 0);

    List<Object> own =
        TenantContext.runAs(
            TenantRef.of(a),
            () ->
                tx.execute(
                    s -> {
                      List<Object> seen = new ArrayList<>();
                      seen.add(subject.seated(a, mine.use(), List.of(KEY), START, end, none));
                      seen.add(
                          subject.seated(a, mine.use(), List.of(KEY), START, end, mine.booking()));
                      seen.add(
                          subject.seated(
                              a, mine.use(), List.of(KEY), end, end.plusSeconds(60), none));
                      seen.add(subject.rekeyHolder(a, mine.booking(), "e:outra@example.org"));
                      seen.add(subject.seated(a, mine.use(), List.of(KEY), START, end, none));
                      seen.add(subject.unkeyHolder(a, mine.booking()));
                      UUID extra = UUID.randomUUID();
                      subject.insert(
                          a,
                          extra,
                          mine.booking(),
                          mine.use(),
                          START,
                          end,
                          Participant.companion("Uxía Rey", 2016),
                          "w:" + extra,
                          1);
                      seen.add(subject.of(a, List.of(mine.booking())).size());
                      seen.add(subject.replace(a, mine.booking()));
                      seen.add(subject.of(a, List.of(mine.booking())).size());
                      seen.add(subject.deactivate(a, other.booking()));
                      seen.add(subject.of(a, List.of(other.booking())).size());
                      seen.add(subject.anonymise(a, List.of(mine.booking(), other.booking())));
                      return seen;
                    }));

    assertThat(own)
        .as(
            "ten praza; sen contar a súa reserva, non; outra hora, non; co correo cambiado, o vello"
                + " xa non; dúas persoas; substituídas non saen; canceladas si; anonimizadas todas")
        .containsExactly(List.of(KEY), List.of(), List.of(), 1, List.of(), 1, 2, 2, 0, 1, 1, 3);
    assertThat(
            jdbc.sql(
                    "select count(*) from booking_participant where booking_id in (:b)"
                        + " and person_key like 'x:%' and not active and name is null"
                        + " and dependent_id is null")
                .param("b", List.of(mine.booking(), other.booking()))
                .query(Integer.class)
                .single())
        .isEqualTo(3);
  }
}
