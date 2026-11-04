// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.notification.application.port.out.DeliveryLog;
import gal.furar.notification.application.service.NotificationRetentionService;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.testsupport.AbstractIT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A2 (docs/datos-persoais.md, «Avisos do servizo»): o rexistro de correos non garda o correo de
 * destino para sempre. A tarefa diaria borra en cada concello as filas de máis dun ano (reservas e
 * invitacións) e deixa as recentes; repetila non cambia nada e o rexistro técnico non leva correos.
 * Reloxo fixo no instante da proba: a base é compartida.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationRetentionIT extends AbstractIT {

  @Autowired MunicipalityDirectory municipalities;
  @Autowired DeliveryLog log;
  @Autowired PlatformTransactionManager transactions;
  @Autowired NotificationRetentionService scheduled;

  @Test
  void deletesDeliveriesOlderThanAYearInEveryMunicipality(CapturedOutput output) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    NotificationRetentionService retention =
        new NotificationRetentionService(
            municipalities, log, transactions, Period.ofYears(1), Clock.fixed(now, ZoneOffset.UTC));
    UUID a = id(createMunicipality(uniqueSlug("correo-conserva-a")));
    UUID b = id(createMunicipality(uniqueSlug("correo-conserva-b")));
    String recipient = "conserva-" + UUID.randomUUID() + "@example.org";
    UUID oldBookingA = booking(a, recipient, now.minus(Duration.ofDays(400)));
    UUID oldInvitationB = invitation(b, recipient, now.minus(Duration.ofDays(367)));
    UUID recentA = booking(a, recipient, now.minus(Duration.ofDays(300)));
    UUID recentB = invitation(b, recipient, now.minus(Duration.ofDays(10)));

    assertThat(retention.apply()).isGreaterThanOrEqualTo(2);

    assertThat(exists(oldBookingA)).isFalse();
    assertThat(exists(oldInvitationB)).isFalse();
    assertThat(exists(recentA)).isTrue();
    assertThat(exists(recentB)).isTrue();

    retention.apply();
    assertThat(exists(recentA)).isTrue();
    assertThat(exists(recentB)).isTrue();
    assertThat(output).contains("notification retention municipality=" + a);
    assertThat(output).doesNotContain(recipient);
  }

  @Test
  void theScheduledBeanKeepsAYear() {
    UUID m = id(createMunicipality(uniqueSlug("correo-conserva-bean")));
    UUID recent = booking(m, "conserva@example.org", Instant.now().minus(Duration.ofDays(300)));
    scheduled.apply();
    assertThat(exists(recent)).isTrue();
  }

  /** Unha fila dun correo de reserva por JDBC (dono, sen RLS), rexistrada en {@code at}. */
  UUID booking(UUID municipality, String recipient, Instant at) {
    return insert(municipality, recipient, at, "CONFIRMED", UUID.randomUUID(), null);
  }

  /** Unha fila do correo dunha invitación ao persoal, rexistrada en {@code at}. */
  UUID invitation(UUID municipality, String recipient, Instant at) {
    return insert(municipality, recipient, at, "STAFF_INVITATION", null, UUID.randomUUID());
  }

  UUID insert(
      UUID municipality,
      String recipient,
      Instant at,
      String kind,
      @Nullable UUID booking,
      @Nullable UUID invitation) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, booking_id, invitation_id, kind, event_id, recipient,
               language, status, attempts, error, sent_at, created_at, updated_at)
            values (:id, :m, :booking, :invitation, :kind, 'once', :recipient, 'gl', 'SENT', 1,
                    null, :at, :at, :at)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("booking", booking, java.sql.Types.OTHER)
        .param("invitation", invitation, java.sql.Types.OTHER)
        .param("kind", kind)
        .param("recipient", recipient)
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
    return id;
  }

  boolean exists(UUID id) {
    return jdbc.sql("select count(*) from notification_delivery where id = :id")
            .param("id", id)
            .query(Integer.class)
            .single()
        == 1;
  }
}
