// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.BookingConfirmed;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.ProbeEvents;
import gal.furar.testsupport.RecordingMailSender;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D14 (ADR-0006): un evento procesado en asíncrono ({@code @ApplicationModuleListener}) le e
 * escribe no concello do evento, non no de quen o publicou nin en ningún. Tamén a escoita real dos
 * correos das reservas (ADR-0025, punto 15): le a reserva e o concello do evento e rexistra o envío
 * nel.
 */
class AsyncListenerTenantIT extends AbstractIT {

  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate tx;

  @Test
  void listenerRunsInTheEventMunicipality() throws InterruptedException {
    String slugA = uniqueSlug("async-a");
    String slugB = uniqueSlug("async-b");
    UUID a = id(createMunicipality(slugA));
    UUID b = id(createMunicipality(slugB));
    createField(slugA, textField("existing_a", "STAFF"));
    String code = "probe_" + UUID.randomUUID().toString().substring(0, 8);

    // Publicado desde B, pero o evento é de A.
    TenantContext.runAs(
        new TenantRef(b, slugB),
        () ->
            tx.executeWithoutResult(s -> events.publishEvent(new ProbeEvents.ProbeEvent(a, code))));

    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (!ProbeEvents.ProbeListener.SEEN.containsKey(code) && Instant.now().isBefore(deadline)) {
      Thread.sleep(50);
    }
    ProbeEvents.Observation seen = ProbeEvents.ProbeListener.SEEN.get(code);
    assertThat(seen).as("listener ran").isNotNull();
    assertThat(seen.tenant()).isEqualTo(a);
    assertThat(seen.definitionsBefore()).isEqualTo(1);
    assertThat(seen.thread()).isNotEqualTo(Thread.currentThread().getName());

    UUID owner =
        jdbc.sql("select municipality_id from custom_field where code = :c")
            .param("c", code)
            .query(UUID.class)
            .single();
    assertThat(owner).isEqualTo(a);
    assertThat(TenantContext.current()).isEmpty();
  }

  @Autowired RecordingMailSender mails;

  @Test
  void theBookingMailListenerReadsAndWritesInTheEventMunicipality() throws Exception {
    String slugA = uniqueSlug("correo-async-a");
    String slugB = uniqueSlug("correo-async-b");
    UUID a = id(createMunicipality(slugA));
    UUID b = id(createMunicipality(slugB));
    String email = "async-" + UUID.randomUUID() + "@example.org";
    UUID booking = insertBooking(a, email);

    // Publicado desde B, pero a reserva e o evento son de A.
    TenantContext.runAs(
        new TenantRef(b, slugB),
        () -> tx.executeWithoutResult(s -> events.publishEvent(new BookingConfirmed(a, booking))));

    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (mails.sentTo(email).isEmpty() && Instant.now().isBefore(deadline)) {
      Thread.sleep(50);
    }
    assertThat(mails.sentTo(email)).as("correo enviado").hasSize(1);
    assertThat(mails.sentTo(email).getFirst().getFrom()[0].toString())
        .as("co nome do concello A")
        .contains("Concello " + slugA);
    assertThat(
            jdbc.sql(
                    "select municipality_id from notification_delivery where booking_id = :b"
                        + " and status = 'SENT'")
                .param("b", booking)
                .query(UUID.class)
                .list())
        .containsExactly(a);
    assertThat(TenantContext.current()).isEmpty();
  }

  /** Unha reserva confirmada en {@code municipality}, sementada por JDBC (sen filtro). */
  private UUID insertBooking(UUID municipality, String email) {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
    UUID booking = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility
              (id, municipality_id, name, street, postal_code, locality, ine_code, latitude,
               longitude, created_at, updated_at, version)
            values (:id, :m, 'Pista do Alto', 'Rúa do Alto', '15570', 'Narón', '15054', 43.5,
                    -8.2, now(), now(), 0)
            """)
        .param("id", facility)
        .param("m", municipality)
        .update();
    jdbc.sql(
            """
            insert into facility_use
              (id, municipality_id, facility_id, name, rental_mode, slot_minutes, availability,
               created_at, updated_at, version)
            values (:id, :m, :f, 'Pádel', 'WHOLE', 60, 'ALWAYS', now(), now(), 0)
            """)
        .param("id", use)
        .param("m", municipality)
        .param("f", facility)
        .update();
    OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0);
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_language, created_at, updated_at, version)
            values (:id, :m, 'FUR-AS7N', :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED',
                    'NONE', 'NONE', :start, 'sub-async', 'Ana Rey', :email, 'gl', now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", municipality)
        .param("f", facility)
        .param("u", use)
        .param("start", start)
        .param("email", email)
        .update();
    return booking;
  }
}
