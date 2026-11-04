// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link NotificationDeliverySql} só le e cambia os correos do seu
 * concello, coa RLS da transacción e co concello como parámetro. Tamén que garda e le cada columna,
 * e as filas das invitacións ao persoal (plan do dominio 4, D4-06).
 */
class NotificationDeliverySqlTenantIT extends AbstractTenantIT {

  /** A mesma reserva (id) nos dous concellos: a clave só é única dentro de cada un. */
  static final UUID BOOKING = UUID.randomUUID();

  final List<UUID> seededBookings = new CopyOnWriteArrayList<>();

  @Autowired NotificationDeliverySql subject;
  @Autowired TransactionTemplate tx;

  UUID insert(UUID municipality, UUID booking) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, booking_id, kind, event_id, recipient, language, status,
               attempts, error, sent_at, created_at, updated_at)
            values (:id, :m, :b, 'CONFIRMED', 'once', 'ana@example.org', 'es', 'FAILED', 1,
                    'MailSendException', null, now(), now())
            """)
        .param("id", id)
        .param("m", municipality)
        .param("b", booking)
        .update();
    seededBookings.add(booking);
    return booking;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insert(municipality, UUID.randomUUID());
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                s -> {
                  List<Object> seen = new ArrayList<>();
                  for (UUID booking : seededBookings) {
                    subject
                        .find(municipality, booking, MailKind.CONFIRMED, "once")
                        .ifPresent(d -> seen.add(d.bookingId()));
                  }
                  return seen;
                }));
  }

  @Test
  void readsWhatItWroteAndUpdatesOnlyItsOwnRow() {
    UUID a = id(createMunicipality(uniqueSlug("correo-a")));
    UUID b = id(createMunicipality(uniqueSlug("correo-b")));
    insert(b, BOOKING);
    Instant sentAt = Instant.parse("2026-10-27T08:30:00Z");
    Delivery failed =
        new Delivery(
            BOOKING,
            MailKind.CHANGED,
            "v2",
            "ana@example.org",
            Language.GL,
            DeliveryStatus.FAILED,
            1,
            "MailSendException: ConnectException",
            null);
    Delivery sent =
        new Delivery(
            BOOKING,
            MailKind.CONFIRMED,
            "once",
            "ana@example.org",
            Language.GL,
            DeliveryStatus.SENT,
            2,
            null,
            sentAt);

    int[] touched = new int[2];
    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                s -> {
                  subject.insert(a, failed, sentAt);
                  // A fila CONFIRMED de B ten a mesma reserva: A non a cambia.
                  touched[0] = subject.update(a, sent, sentAt);
                  subject.insert(a, sent, sentAt);
                  touched[1] = subject.update(a, sent, sentAt);
                }));

    assertThat(touched).containsExactly(0, 1);
    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                s -> {
                  assertThat(subject.find(a, BOOKING, MailKind.CHANGED, "v2")).contains(failed);
                  assertThat(subject.find(a, BOOKING, MailKind.CONFIRMED, "once")).contains(sent);
                }));
    assertThat(
            jdbc.sql(
                    "select status from notification_delivery where municipality_id = :m"
                        + " and booking_id = :b")
                .param("m", b)
                .param("b", BOOKING)
                .query(String.class)
                .list())
        .containsExactly("FAILED");
  }

  @Test
  void invitationRowsStayInTheirMunicipalityAndAreOnePerInvitation() {
    UUID a = id(createMunicipality(uniqueSlug("convite-a")));
    UUID b = id(createMunicipality(uniqueSlug("convite-b")));
    UUID invitation = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-28T20:00:00Z");

    TenantContext.runAs(
        TenantRef.of(b),
        () ->
            tx.executeWithoutResult(
                s ->
                    subject.insert(
                        b,
                        InvitationMailDelivery.failed(
                            invitation, "ana@example.org", Language.GL, "MailSendException"),
                        now)));
    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                s -> {
                  // A mesma invitación (id) en B non se ve desde A.
                  assertThat(subject.invitationStatus(a, invitation)).isEmpty();
                  subject.insert(
                      a,
                      InvitationMailDelivery.sent(invitation, "ana@example.org", Language.ES, now),
                      now);
                  assertThat(subject.invitationStatus(a, invitation)).contains(DeliveryStatus.SENT);
                }));
    TenantContext.runAs(
        TenantRef.of(b),
        () ->
            tx.executeWithoutResult(
                s ->
                    assertThat(subject.invitationStatus(b, invitation))
                        .contains(DeliveryStatus.FAILED)));

    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    TenantRef.of(a),
                    () ->
                        tx.executeWithoutResult(
                            s ->
                                subject.insert(
                                    a,
                                    InvitationMailDelivery.sent(
                                        invitation, "ana@example.org", Language.GL, now),
                                    now))))
        .as("un só correo por invitación")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.sql(
                    "select kind || ' ' || status || ' ' || attempts || ' ' ||"
                        + " (booking_id is null) from notification_delivery"
                        + " where invitation_id = :i order by municipality_id = :a")
                .param("i", invitation)
                .param("a", a)
                .query(String.class)
                .list())
        .containsExactly("STAFF_INVITATION FAILED 1 true", "STAFF_INVITATION SENT 1 true");
  }

  /**
   * A2: esquecer os envíos dunhas invitacións e a conservación do ano só borran no seu concello; a
   * mesma invitación (id) e as filas vellas doutro concello quedan.
   */
  @Test
  void forgettingAndPurgingTouchOnlyTheirOwnMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("correo-esquece-a")));
    UUID b = id(createMunicipality(uniqueSlug("correo-esquece-b")));
    UUID invitation = UUID.randomUUID();
    Instant old = Instant.parse("2024-01-10T10:00:00Z");
    Instant recent = Instant.now();
    for (UUID m : List.of(a, b)) {
      TenantContext.runAs(
          TenantRef.of(m),
          () ->
              tx.executeWithoutResult(
                  s ->
                      subject.insert(
                          m,
                          InvitationMailDelivery.sent(
                              invitation, "ana@example.org", Language.GL, recent),
                          recent)));
      insertAt(m, UUID.randomUUID(), old);
      insertAt(m, UUID.randomUUID(), recent);
    }
    Instant cutoff = recent.minus(java.time.Duration.ofDays(365));

    int[] done = new int[3];
    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                s -> {
                  done[0] = subject.deleteInvitations(a, List.of(invitation));
                  done[1] = subject.deleteInvitations(a, List.of(invitation));
                  done[2] = subject.deleteCreatedBefore(a, cutoff);
                }));

    assertThat(done).containsExactly(1, 0, 1);
    assertThat(rows(a)).isEqualTo(1);
    assertThat(rows(b)).isEqualTo(3);
  }

  private void insertAt(UUID municipality, UUID booking, Instant createdAt) {
    jdbc.sql(
            """
            insert into notification_delivery
              (id, municipality_id, booking_id, kind, event_id, recipient, language, status,
               attempts, error, sent_at, created_at, updated_at)
            values (:id, :m, :b, 'CONFIRMED', 'once', 'ana@example.org', 'gl', 'FAILED', 1,
                    null, null, :at, :at)
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("b", booking)
        .param("at", createdAt.atOffset(java.time.ZoneOffset.UTC))
        .update();
  }

  private int rows(UUID municipality) {
    return jdbc.sql("select count(*) from notification_delivery where municipality_id = :m")
        .param("m", municipality)
        .query(Integer.class)
        .single();
  }

  @Test
  void aRowIsForABookingOrAnInvitationNeverBoth() {
    UUID m = id(createMunicipality(uniqueSlug("convite-ck")));
    String insert =
        """
        insert into notification_delivery
          (id, municipality_id, booking_id, invitation_id, kind, event_id, recipient, language,
           status, attempts, created_at, updated_at, sent_at)
        values (gen_random_uuid(), :m, :b, :i, :kind, 'once', 'ana@example.org', 'gl', 'SENT', 1,
                now(), now(), now())
        """;
    UUID id = UUID.randomUUID();

    for (Object[] bad :
        List.<Object[]>of(
            new Object[] {id, id, "STAFF_INVITATION"},
            new Object[] {null, null, "STAFF_INVITATION"},
            new Object[] {id, null, "STAFF_INVITATION"},
            new Object[] {null, id, "CONFIRMED"})) {
      assertThatThrownBy(
              () ->
                  jdbc.sql(insert)
                      .param("m", m)
                      .param("b", bad[0], java.sql.Types.OTHER)
                      .param("i", bad[1], java.sql.Types.OTHER)
                      .param("kind", bad[2])
                      .update())
          .as("booking %s, invitation %s, %s", bad[0], bad[1], bad[2])
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }
}
