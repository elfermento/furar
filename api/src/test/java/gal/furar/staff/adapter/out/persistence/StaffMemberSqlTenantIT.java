// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.out.ActiveMembership;
import gal.furar.staff.application.port.out.RetainedMember;
import gal.furar.staff.domain.EmailAddress;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffRole;
import gal.furar.staff.domain.StaffStatus;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link StaffMemberSql} só le e cambia as pertenzas do seu
 * concello, coa RLS da transacción e co concello como parámetro, con filas co mesmo correo e a
 * mesma persoa nos dous. Tamén as garantías da táboa (plan do dominio 4, §2.1): os índices únicos
 * parciais, as restricións por estado e o bloqueo da regra do último xestor.
 */
class StaffMemberSqlTenantIT extends AbstractTenantIT {

  static final String EMAIL = "conserxe@example.org";
  static final String SUBJECT = "sub-conserxe";
  static final Instant AT = Instant.parse("2026-11-05T09:00:00Z");

  @Autowired StaffMemberSql subject;
  @Autowired TransactionTemplate tx;

  /** Unha pertenza por JDBC, sen filtro (o dono da base non ten RLS). */
  UUID insert(UUID municipality, String email, String status, String role, String subject) {
    UUID id = UUID.randomUUID();
    boolean accepted = !"INVITED".equals(status);
    boolean deactivated = "DEACTIVATED".equals(status);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, subject, role, status, language, invited_by,
               invited_at, accepted_at, deactivated_by, deactivated_at, created_at,
               updated_at, version)
            values (:id, :m, :email, :subject, :role, :status, 'gl', 'sub-xestora',
                    now() - interval '1 day',
                    :acceptedAt, :deactivatedBy, :deactivatedAt, now(), now(), 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", email)
        .param("subject", accepted ? subject : null, java.sql.Types.VARCHAR)
        .param("role", role)
        .param("status", status)
        .param(
            "acceptedAt",
            accepted ? java.time.OffsetDateTime.now() : null,
            java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param("deactivatedBy", deactivated ? "sub-xestora" : null, java.sql.Types.VARCHAR)
        .param(
            "deactivatedAt",
            deactivated ? java.time.OffsetDateTime.now() : null,
            java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
    return id;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insert(municipality, EMAIL, "ACTIVE", "MANAGER", SUBJECT);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(municipality, () -> new ArrayList<Object>(ids(subject.list(municipality, null))));
  }

  @Test
  void everyMethodSeesAndChangesOnlyItsOwnMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("persoal-a")));
    UUID b = id(createMunicipality(uniqueSlug("persoal-b")));
    UUID managerA = insert(a, EMAIL, "ACTIVE", "MANAGER", SUBJECT);
    UUID invitedA = insert(a, "nova@example.org", "INVITED", "STAFF", null);
    UUID managerB = insert(b, EMAIL, "ACTIVE", "MANAGER", SUBJECT);
    UUID staffB = insert(b, "outra@example.org", "ACTIVE", "STAFF", "sub-outra");

    StaffMember fromB = member(managerB, b);
    as(
        a,
        () -> {
          assertThat(subject.find(a, managerB)).isEmpty();
          assertThat(subject.findForUpdate(a, managerB)).isEmpty();
          assertThat(subject.find(a, managerA)).isPresent();
          assertThat(ids(subject.list(a, null))).containsExactlyInAnyOrder(managerA, invitedA);
          assertThat(ids(subject.list(a, StaffStatus.INVITED))).containsExactly(invitedA);
          assertThat(ids(subject.list(a, StaffStatus.DEACTIVATED))).isEmpty();
          assertThat(ids(subject.findBySubjects(a, Set.of(SUBJECT, "sub-outra"))))
              .containsExactly(managerA);
          assertThat(subject.findBySubjects(a, Set.of())).isEmpty();
          assertThat(ids(subject.findByIds(a, List.of(managerA, invitedA, managerB, staffB))))
              .containsExactlyInAnyOrder(managerA, invitedA);
          assertThat(subject.findByIds(a, List.of())).isEmpty();
          assertThat(subject.active(a, SUBJECT))
              .hasValueSatisfying(
                  m -> {
                    assertThat(m.id().value()).isEqualTo(managerA);
                    assertThat(m.role()).isEqualTo(StaffRole.MANAGER);
                    assertThat(m.lastSignInAt()).isNull();
                  });
          assertThat(subject.active(a, "sub-outra")).isEmpty();
          assertThat(subject.lockActiveManagers(a)).isEqualTo(1);
          assertThat(subject.recordSignIn(a, "sub-outra", AT)).isZero();
          assertThat(subject.recordSignIn(a, SUBJECT, AT)).isEqualTo(1);
          // Escribir como A cunha fila de B non toca nada.
          assertThat(subject.update(a, fromB.deactivate("sub-xestora", 2, AT), 0)).isFalse();
          return null;
        });

    assertThat(column(b, managerB, "status")).isEqualTo("ACTIVE");
    assertThat(column(b, managerB, "last_sign_in_at")).isNull();
    assertThat(column(b, staffB, "status")).isEqualTo("ACTIVE");
    assertThat(column(a, managerA, "last_sign_in_at")).isNotNull();
  }

  @Test
  void writesAndReadsEveryColumn() {
    UUID a = id(createMunicipality(uniqueSlug("persoal-ida")));
    StaffMember invited =
        StaffMember.invite(
            StaffMemberId.newId(),
            EmailAddress.of("Ana@Example.org"),
            StaffRole.STAFF,
            Language.ES,
            "sub-xestora",
            AT.plusNanos(123_456_789));
    StaffMember active =
        invited.accept("ana@example.org", "sub-ana", "Ana Pena", AT.plusSeconds(60));
    StaffMember promoted = active.changeRole(StaffRole.MANAGER, 1, AT.plusSeconds(90));

    as(
        a,
        () -> {
          assertThat(subject.insert(a, invited)).isTrue();
          StaffMember read = subject.find(a, invited.id().value()).orElseThrow();
          assertSame(read, invited);
          assertThat(read.version()).isZero();
          assertThat(subject.update(a, active, 0)).isTrue();
          assertSame(subject.find(a, invited.id().value()).orElseThrow(), active);
          // Versión vella: outra escritura chegou antes.
          assertThat(subject.update(a, promoted, 0)).isFalse();
          assertThat(subject.update(a, promoted, 1)).isTrue();
          StaffMember last = subject.findForUpdate(a, invited.id().value()).orElseThrow();
          assertSame(last, promoted);
          assertThat(last.version()).isEqualTo(2);
          return null;
        });
  }

  /** §2.1: dúas invitacións ao mesmo correo no mesmo concello → unha pertenza. */
  @Test
  void oneNonDeactivatedMembershipPerEmail() throws Exception {
    UUID a = id(createMunicipality(uniqueSlug("persoal-correo")));
    UUID b = id(createMunicipality(uniqueSlug("persoal-correo-b")));
    CyclicBarrier together = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        results.add(
            pool.submit(
                () -> {
                  together.await(10, TimeUnit.SECONDS);
                  return as(a, () -> subject.insert(a, newInvited(" Persoal@Example.org")));
                }));
      }
      List<Boolean> inserted = new ArrayList<>();
      for (Future<Boolean> r : results) {
        inserted.add(r.get(30, TimeUnit.SECONDS));
      }
      assertThat(inserted).containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
    assertThat(count(a, "email = 'persoal@example.org'")).isEqualTo(1);
    // Noutro concello, o mesmo correo é outra pertenza.
    assertThat(as(b, () -> subject.insert(b, newInvited("persoal@example.org")))).isTrue();

    // Desactivada, volver invitar crea outra fila e o historial queda.
    UUID first =
        jdbc.sql("select id from staff_member where municipality_id = :m")
            .param("m", a)
            .query(UUID.class)
            .single();
    as(
        a,
        () -> {
          StaffMember deactivated =
              subject.find(a, first).orElseThrow().deactivate("sub-xestora", 0, AT);
          assertThat(subject.update(a, deactivated, 0)).isTrue();
          assertThat(subject.insert(a, newInvited("persoal@example.org"))).isTrue();
          assertThat(subject.insert(a, newInvited("persoal@example.org"))).isFalse();
          return null;
        });
    assertThat(count(a, "email = 'persoal@example.org'")).isEqualTo(2);
  }

  @Test
  void oneActiveMembershipPerPersonAndMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("persoal-sub")));
    UUID b = id(createMunicipality(uniqueSlug("persoal-sub-b")));
    insert(a, "un@example.org", "ACTIVE", "MANAGER", SUBJECT);
    assertThatThrownBy(() -> insert(a, "dous@example.org", "ACTIVE", "STAFF", SUBJECT))
        .isInstanceOf(DataIntegrityViolationException.class);
    // A mesma persoa desactivada, ou activa noutro concello con outro rol, si.
    insert(a, "tres@example.org", "DEACTIVATED", "STAFF", SUBJECT);
    insert(b, "un@example.org", "ACTIVE", "STAFF", SUBJECT);
    assertThat(as(b, () -> subject.active(b, SUBJECT).map(ActiveMembership::role)))
        .contains(StaffRole.STAFF);
    assertThat(as(a, () -> subject.active(a, SUBJECT).map(ActiveMembership::role)))
        .contains(StaffRole.MANAGER);
  }

  @Test
  void theTableRejectsInconsistentRows() {
    UUID a = id(createMunicipality(uniqueSlug("persoal-ck")));
    UUID ok = insert(a, "ok@example.org", "INVITED", "STAFF", null);
    List<String> broken =
        List.of(
            // Correo sen normalizar ou sen arroba.
            "update staff_member set email = 'Ok@example.org' where id = :id",
            "update staff_member set email = ' ok@example.org' where id = :id",
            "update staff_member set email = 'okexample.org' where id = :id",
            // Rol, estado e idioma fóra da lista.
            "update staff_member set role = 'PLATFORM_ADMIN' where id = :id",
            "update staff_member set status = 'DELETED' where id = :id",
            "update staff_member set language = 'en' where id = :id",
            // Activa sen persoa nin aceptación.
            "update staff_member set status = 'ACTIVE' where id = :id",
            // Invitada con persoa.
            "update staff_member set subject = 's', accepted_at = now() where id = :id",
            // Desactivada sen quen, ou datos de desactivación sen estar desactivada.
            "update staff_member set status = 'DEACTIVATED', deactivated_at = now() where id = :id",
            "update staff_member set deactivated_by = 'x', deactivated_at = now() where id = :id",
            // Acceso anterior sen último.
            "update staff_member set previous_sign_in_at = now() where id = :id",
            "update staff_member set version = -1 where id = :id");
    for (String sql : broken) {
      assertThatThrownBy(() -> jdbc.sql(sql).param("id", ok).update())
          .as(sql)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  /**
   * §1.2.8: a regra do último xestor decídese coas pertenzas xestoras bloqueadas. Se dúas
   * desactivacións chegan á vez, a segunda espera e despois conta só a que queda.
   */
  @Test
  void lockingTheActiveManagersSerializesTheLastManagerRule() throws Exception {
    UUID a = id(createMunicipality(uniqueSlug("persoal-bloqueo")));
    UUID first = insert(a, "un@example.org", "ACTIVE", "MANAGER", "sub-un");
    insert(a, "dous@example.org", "ACTIVE", "MANAGER", "sub-dous");
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> holder =
          pool.submit(
              () ->
                  as(
                      a,
                      () -> {
                        int managers = subject.lockActiveManagers(a);
                        StaffMember m = subject.findForUpdate(a, first).orElseThrow();
                        assertThat(subject.update(a, m.deactivate("sub-dous", managers, AT), 0))
                            .isTrue();
                        locked.countDown();
                        await(release);
                        return managers;
                      }));
      await(locked);
      Future<Integer> waiter = pool.submit(() -> as(a, () -> subject.lockActiveManagers(a)));
      // Mentres a primeira non remata, a segunda espera polo bloqueo.
      Thread.sleep(300);
      assertThat(waiter.isDone()).isFalse();
      release.countDown();
      assertThat(holder.get(30, TimeUnit.SECONDS)).isEqualTo(2);
      assertThat(waiter.get(30, TimeUnit.SECONDS)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  /** §1.2.5: un acceso novo móvese a «anterior» unha soa vez, aínda con peticións á vez. */
  @Test
  void aSignInIsRecordedOncePerAuthTime() throws Exception {
    UUID a = id(createMunicipality(uniqueSlug("persoal-acceso")));
    UUID member = insert(a, EMAIL, "ACTIVE", "STAFF", SUBJECT);
    Instant first = AT;
    Instant second = AT.plus(2, ChronoUnit.HOURS);
    assertThat(as(a, () -> subject.recordSignIn(a, SUBJECT, first))).isEqualTo(1);
    CyclicBarrier together = new CyclicBarrier(10);
    ExecutorService pool = Executors.newFixedThreadPool(10);
    int recorded = 0;
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        results.add(
            pool.submit(
                () -> {
                  together.await(10, TimeUnit.SECONDS);
                  return as(a, () -> subject.recordSignIn(a, SUBJECT, second));
                }));
      }
      for (Future<Integer> r : results) {
        recorded += r.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }
    assertThat(recorded).isEqualTo(1);
    StaffMember m = as(a, () -> subject.find(a, member).orElseThrow());
    assertThat(m.lastSignInAt()).isEqualTo(second);
    assertThat(m.previousSignInAt()).isEqualTo(first);
    // Un auth_time vello non cambia nada; o acceso non sobe a versión.
    assertThat(as(a, () -> subject.recordSignIn(a, SUBJECT, first))).isZero();
    assertThat(m.version()).isZero();
  }

  private StaffMember newInvited(String email) {
    return StaffMember.invite(
        StaffMemberId.newId(),
        EmailAddress.of(email),
        StaffRole.STAFF,
        Language.GL,
        "sub-xestora",
        Instant.now());
  }

  @Test
  void retentionTouchesOnlyItsOwnMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("persoal-conserva-a")));
    UUID b = id(createMunicipality(uniqueSlug("persoal-conserva-b")));
    UUID invitedA = insert(a, "nova@example.org", "INVITED", "STAFF", null);
    UUID invitedB = insert(b, "nova@example.org", "INVITED", "STAFF", null);
    UUID deactivatedA = insert(a, "vella@example.org", "DEACTIVATED", "STAFF", "sub-vella");
    UUID deactivatedB = insert(b, "vella@example.org", "DEACTIVATED", "STAFF", "sub-vella");
    Instant later = Instant.now().plus(1, ChronoUnit.DAYS);

    as(
        a,
        () -> {
          // Devolven as pertenzas tocadas e o seu rol, para o rexistro de actividade (A4).
          assertThat(subject.deactivateUnacceptedWithoutInvitation(a, later, "furar:proba", AT))
              .containsExactly(new RetainedMember(new StaffMemberId(invitedA), StaffRole.STAFF));
          assertThat(subject.anonymiseDeactivatedBefore(a, later, AT))
              .containsExactly(
                  new RetainedMember(new StaffMemberId(deactivatedA), StaffRole.STAFF));
          return null;
        });

    assertThat(column(a, invitedA, "status")).isEqualTo("DEACTIVATED");
    assertThat(column(a, invitedA, "email")).isEqualTo("anon-" + invitedA + "@anonimizada.invalid");
    assertThat(column(a, deactivatedA, "email"))
        .isEqualTo("anon-" + deactivatedA + "@anonimizada.invalid");
    assertThat(column(b, invitedB, "status")).isEqualTo("INVITED");
    assertThat(column(b, invitedB, "email")).isEqualTo("nova@example.org");
    assertThat(column(b, deactivatedB, "email")).isEqualTo("vella@example.org");
  }

  private StaffMember member(UUID id, UUID municipality) {
    return as(municipality, () -> subject.find(municipality, id).orElseThrow());
  }

  private <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  private Object column(UUID municipality, UUID id, String column) {
    return jdbc.sql(
            "select " + column + " from staff_member where municipality_id = :m and id = :id")
        .param("m", municipality)
        .param("id", id)
        .query((rs, n) -> Optional.ofNullable(rs.getObject(1)))
        .single()
        .orElse(null);
  }

  private long count(UUID municipality, String where) {
    return jdbc.sql("select count(*) from staff_member where municipality_id = :m and " + where)
        .param("m", municipality)
        .query(Long.class)
        .single();
  }

  private static List<UUID> ids(List<StaffMember> members) {
    return members.stream().map(m -> m.id().value()).toList();
  }

  private static void assertSame(StaffMember read, StaffMember expected) {
    assertThat(read).usingRecursiveComparison().ignoringFields("version").isEqualTo(expected);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
