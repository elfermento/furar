// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Filas de proba para o SQL da asistencia e dos bloqueos por faltas: unha instalación co seu uso e
 * unha reserva confirmada dunha hora, por JDBC e sen filtro de concello.
 */
final class AttendanceSeeds {

  static final String EMAIL = "ana@example.org";
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private AttendanceSeeds() {}

  record Seeded(UUID facility, UUID booking, String code) {}

  /**
   * Unha reserva confirmada que empeza en {@code start}, coa marca de asistencia indicada.
   *
   * @param prefix dúas letras do alfabeto dos códigos, distintas en cada clase de proba
   * @param n distingue os códigos dentro da clase
   */
  static Seeded booking(
      JdbcClient jdbc, UUID municipality, Instant start, String attendance, String prefix, int n) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
    UUID booking = UUID.randomUUID();
    String code = "FUR-" + prefix + ALPHABET.charAt(n / 32 % 32) + ALPHABET.charAt(n % 32);
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
    OffsetDateTime at = OffsetDateTime.ofInstant(start, ZoneOffset.UTC);
    boolean marked = !"NONE".equals(attendance);
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_email_key, holder_language, created_at, updated_at, version,
               attendance, attendance_at, attendance_by)
            values (:id, :m, :code, :f, :u, 'SEATS',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED', 'NONE',
                    'NONE', :start, 'sub-ana', 'Ana Rey', :email, :email,
                    'gl', now(), now(), 0, :attendance,
                    case when :marked then now() end, case when :marked then 'sub-staff' end)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("code", code)
        .param("f", facility)
        .param("u", use)
        .param("start", at)
        .param("email", EMAIL)
        .param("attendance", attendance)
        .param("marked", marked)
        .update();
    return new Seeded(facility, booking, code);
  }

  /** Un bloqueo vixente dese correo causado por esa reserva. */
  static UUID block(JdbcClient jdbc, UUID municipality, UUID booking, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into booking_no_show_block
              (id, municipality_id, holder_email_key, starts_at, ends_at, created_at)
            values (:id, :m, :email, :from, :to, :from)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", EMAIL)
        .param("from", OffsetDateTime.ofInstant(now.minusSeconds(3600), ZoneOffset.UTC))
        .param("to", OffsetDateTime.ofInstant(now.plusSeconds(86400), ZoneOffset.UTC))
        .update();
    jdbc.sql(
            """
            insert into booking_no_show_block_fault (municipality_id, block_id, booking_id)
            values (:m, :block, :booking)
            """)
        .param("m", municipality)
        .param("block", id)
        .param("booking", booking)
        .update();
    return id;
  }
}
