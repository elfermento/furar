// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.people.application.port.out.AccountClosures;
import gal.furar.people.domain.AccountDeletionBlocker;
import gal.furar.shared.account.AccountClosureStep;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.tenancy.TenantRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * ADR-0043: cando Keycloak avisa de que eliminou unha conta, primeiro {@code close_account} e
 * despois os pasos dos módulos; un evento no rexistro de cada concello tocado, só coas cifras.
 */
class AccountClosureServiceTest {

  static final UUID NARON = UUID.fromString("00000000-0000-7000-8000-000000000001");
  static final UUID FERROL = UUID.fromString("00000000-0000-7000-8000-000000000002");
  static final UUID VERIN = UUID.fromString("00000000-0000-7000-8000-000000000003");
  static final String SUB = "019a0b6e-0000-7000-8000-00000000abcd";

  /** Transaccións sen base: o servizo só precisa que o código corra dentro dunha. */
  static final class NoDatabaseTransactions extends AbstractPlatformTransactionManager {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }

  final List<String> calls = new ArrayList<>();
  final List<AuditEvent> audited = new ArrayList<>();
  Map<UUID, Long> closed = Map.of();
  Map<UUID, Long> anonymised = Map.of();
  RuntimeException stepFailure;

  final AccountClosures closures =
      new AccountClosures() {
        @Override
        public List<AccountDeletionBlocker> blockers(String subject) {
          return List.of();
        }

        @Override
        public Map<UUID, Long> close(String subject) {
          calls.add("close_account " + subject);
          return closed;
        }
      };

  final AccountClosureStep bookings =
      new AccountClosureStep() {
        @Override
        public String detail() {
          return "bookingsAnonymized";
        }

        @Override
        public Map<UUID, Long> close(String subject) {
          calls.add("bookings " + subject);
          if (stepFailure != null) {
            throw stepFailure;
          }
          return anonymised;
        }
      };

  AccountClosureService service() {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    beans.addBean("bookings", bookings);
    return new AccountClosureService(
        closures,
        beans.getBeanProvider(AccountClosureStep.class),
        () -> List.of(new TenantRef(NARON, "naron"), new TenantRef(FERROL, "ferrol")),
        audited::add,
        new NoDatabaseTransactions());
  }

  @Test
  void closeAccountFirstThenTheModulesAndOneEventPerMunicipalityWithOnlyTheFigures() {
    closed = Map.of(NARON, 3L);
    anonymised = Map.of(NARON, 2L, FERROL, 1L);

    long total = service().closed(SUB);

    assertThat(total).isEqualTo(6);
    assertThat(calls).containsExactly("close_account " + SUB, "bookings " + SUB);
    assertThat(audited).hasSize(2);
    AuditEvent naron = event(NARON);
    assertThat(naron.action()).isEqualTo(AccountClosureService.CLOSE);
    assertThat(naron.systemActor()).isEqualTo(AccountClosureService.SUBJECT);
    assertThat(naron.municipalitySlug()).isEqualTo("naron");
    assertThat(naron.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
    assertThat(naron.details()).isEqualTo(Map.of("rows", 3L, "bookingsAnonymized", 2L));
    assertThat(event(FERROL).details()).isEqualTo(Map.of("bookingsAnonymized", 1L));
    assertThat(audited)
        .allSatisfy(e -> assertThat(e.details().toString()).doesNotContain(SUB))
        .allSatisfy(e -> assertThat(e.knownActorRoles()).isEmpty());
  }

  @Test
  void aSecondNoticeTouchesNothingAndLeavesNoEvent() {
    assertThat(service().closed(SUB)).isZero();
    assertThat(audited).isEmpty();
  }

  @Test
  void aMunicipalityWithoutSlugStillGetsItsEvent() {
    closed = Map.of(VERIN, 1L);
    service().closed(SUB);
    assertThat(event(VERIN).municipalitySlug()).isNull();
  }

  @Test
  void ifAModuleFailsWhatWasDoneIsRecordedAndKeycloakHearsTheError() {
    closed = Map.of(NARON, 4L);
    stepFailure = new IllegalStateException("db down");

    assertThatThrownBy(() -> service().closed(SUB)).isSameAs(stepFailure);

    assertThat(audited).hasSize(1);
    assertThat(event(NARON).details()).isEqualTo(Map.of("rows", 4L));
  }

  @Test
  void zerosDoNotCount() {
    Map<UUID, Map<String, Long>> done = new java.util.LinkedHashMap<>();
    AccountClosureService.add(done, "rows", Map.of(NARON, 0L));
    assertThat(done).isEmpty();
    AccountClosureService.add(done, "rows", Map.of(NARON, 2L));
    AccountClosureService.add(done, "rows", Map.of(NARON, 1L));
    assertThat(done).isEqualTo(Map.of(NARON, Map.of("rows", 3L)));
  }

  private AuditEvent event(UUID municipality) {
    return audited.stream()
        .filter(e -> municipality.equals(e.municipalityId()))
        .findFirst()
        .orElseThrow();
  }
}
