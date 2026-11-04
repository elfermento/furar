// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.security.Role;
import gal.furar.staff.application.port.out.PlatformAdminRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * ADR-0028, punto 2: a primeira administración é a conta co correo verificado de {@code
 * FURAR_ADMIN_EMAIL} mentres non hai ningunha; despois, cambiar o correo non fai nada. O correo non
 * sae no evento.
 */
class PlatformAdminsServiceTest {

  static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

  /** Táboa en memoria: como a sentenza real, só dá o rol se non hai ningunha administración. */
  static final class InMemoryAdmins implements PlatformAdminRepository {
    final Set<String> subjects = new LinkedHashSet<>();

    @Override
    public boolean isAdmin(String subject) {
      return subjects.contains(subject);
    }

    @Override
    public boolean grantFirst(UUID id, String subject, Instant at) {
      return subjects.isEmpty() && subjects.add(subject);
    }
  }

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

  final InMemoryAdmins admins = new InMemoryAdmins();
  final List<AuditEvent> events = new ArrayList<>();

  PlatformAdminsService service(String configured) {
    return new PlatformAdminsService(
        admins,
        events::add,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new NoDatabaseTransactions(),
        configured);
  }

  @Test
  void theVerifiedAccountOfTheConfiguredEmailBecomesTheFirstAdministrator() {
    PlatformAdminsService service = service(" Admin@Concello.gal ");

    assertThat(service.isPlatformAdmin("other", "outra@concello.gal")).isFalse();
    assertThat(service.isPlatformAdmin("unverified", null)).isFalse();
    assertThat(admins.subjects).isEmpty();
    assertThat(events).isEmpty();

    assertThat(service.isPlatformAdmin("first", "ADMIN@concello.gal ")).isTrue();
    assertThat(admins.subjects).containsExactly("first");
    assertThat(events)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo(PlatformAdminsService.FIRST_ADMIN);
              assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
              assertThat(e.municipalityId()).isNull();
              assertThat(e.knownActorRoles()).containsExactly(Role.PLATFORM_ADMIN);
              assertThat(e.details().toString()).doesNotContainIgnoringCase("concello.gal");
            });

    // Despois: segue séndoo, sen outro evento; outra conta co mesmo correo non o pasa a ser.
    assertThat(service.isPlatformAdmin("first", null)).isTrue();
    assertThat(service.isPlatformAdmin("second", "admin@concello.gal")).isFalse();
    assertThat(events).hasSize(1);
  }

  /** Cambiar a variable con administración xa creada non fai nada (D13 da maqueta 45). */
  @Test
  void changingTheEmailOnceThereIsAnAdministratorDoesNothing() {
    admins.subjects.add("existing");
    PlatformAdminsService service = service("nova@concello.gal");

    assertThat(service.isPlatformAdmin("new", "nova@concello.gal")).isFalse();
    assertThat(service.isPlatformAdmin("existing", null)).isTrue();
    assertThat(events).isEmpty();
  }

  /**
   * A web pide á vez o acceso á plataforma e as pertenzas ao persoal: a outra petición dálle o rol
   * mentres esta non o ve aínda e a súa inserción non fai nada. Esta tamén ten que dicir que si (se
   * non, a web volvía á portada en vez de á alta do concello ata recargar).
   */
  @Test
  void aConcurrentRequestThatLostTheGrantStillSeesTheAdministrator() {
    PlatformAdminRepository racing =
        new PlatformAdminRepository() {
          @Override
          public boolean isAdmin(String subject) {
            return admins.isAdmin(subject);
          }

          @Override
          public boolean grantFirst(UUID id, String subject, Instant at) {
            // A outra petición inseriu e fixo commit primeiro: esta non insire nada.
            admins.subjects.add(subject);
            return false;
          }
        };
    PlatformAdminsService service =
        new PlatformAdminsService(
            racing,
            events::add,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NoDatabaseTransactions(),
            "admin@concello.gal");

    assertThat(service.isPlatformAdmin("first", "admin@concello.gal")).isTrue();
    // O evento é da petición que inseriu, non desta.
    assertThat(events).isEmpty();
  }

  @Test
  void withoutTheVariableNobodyBecomesAdministrator() {
    PlatformAdminsService service = service("");

    assertThat(service.isPlatformAdmin("first", "admin@concello.gal")).isFalse();
    assertThat(admins.subjects).isEmpty();
  }

  @Test
  void aMalformedVariableStopsTheStart() {
    assertThatThrownBy(() -> service("non-é-un-correo"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FURAR_ADMIN_EMAIL")
        .hasMessageNotContaining("non-é-un-correo");
  }
}
