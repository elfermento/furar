// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Filas de proba das entidades e as súas tempadas (ADR-0039): unha instalación cun uso enteiro,
 * unha entidade, unha serie e unha solicitude, por JDBC e sen filtro de concello.
 */
final class SeasonSeeds {

  private SeasonSeeds() {}

  record Place(UUID facility, UUID use) {}

  static UUID organization(JdbcClient jdbc, UUID municipality, String name) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into booking_organization
              (id, municipality_id, name, email, created_at, updated_at, version)
            values (:id, :m, :name, 'club@example.org', now(), now(), 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("name", name)
        .update();
    return id;
  }

  static Place place(JdbcClient jdbc, UUID municipality) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
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
        .param("name", "Pavillón " + facility)
        .update();
    jdbc.sql(
            """
            insert into facility_use
              (id, municipality_id, facility_id, name, rental_mode, slot_minutes,
               availability, created_at, updated_at, version)
            values (:id, :m, :f, 'Pista enteira', 'WHOLE', 60, 'ALWAYS', now(), now(), 0)
            """)
        .param("id", use)
        .param("m", municipality)
        .param("f", facility)
        .update();
    return new Place(facility, use);
  }

  static UUID series(JdbcClient jdbc, UUID municipality, UUID organization, Place place) {
    UUID id = UUID.randomUUID();
    LocalDate first = LocalDate.now().plusDays(7);
    jdbc.sql(
            """
            insert into booking_series
              (id, municipality_id, organization_id, facility_id, use_id, rental_mode, weekdays,
               start_minute, end_minute, first_date, last_date, payment_status, idempotency_key,
               idempotency_fingerprint, created_by, created_at, updated_at, version)
            values (:id, :m, :o, :f, :u, 'WHOLE', '{1,3}', 1140, 1230, :first, :last, 'PENDING',
                    :key, repeat('a', 64), 'sub-xestor', now(), now(), 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("o", organization)
        .param("f", place.facility())
        .param("u", place.use())
        .param("first", first)
        .param("last", first.plusDays(60))
        .param("key", "key-" + id)
        .update();
    return id;
  }

  static UUID request(JdbcClient jdbc, UUID municipality, UUID organization, Place place) {
    UUID id = UUID.randomUUID();
    LocalDate first = LocalDate.now().plusDays(7);
    jdbc.sql(
            """
            insert into booking_series_request
              (id, municipality_id, organization_id, facility_id, use_id, rental_mode, weekdays,
               start_minute, end_minute, first_date, last_date, note, requested_by_subject,
               requested_by_email, status, created_at, updated_at, version)
            values (:id, :m, :o, :f, :u, 'WHOLE', '{5}', 1080, 1170, :first, :last, 'Infantís',
                    'sub-resp', 'resp@example.org', 'PENDING', now(), now(), 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("o", organization)
        .param("f", place.facility())
        .param("u", place.use())
        .param("first", first)
        .param("last", first.plusDays(60))
        .update();
    return id;
  }
}
