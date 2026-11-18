// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.staff.application.port.out.StaffAccountLookup;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ADR-0029: o segundo factor é para o persoal activo dalgún concello, a administración da
 * plataforma e, mentres non hai ningunha, a conta de {@code FURAR_ADMIN_EMAIL}. A veciñanza, nunca.
 */
class SecondFactorServiceTest {

  final PlatformAdminsServiceTest.InMemoryAdmins admins =
      new PlatformAdminsServiceTest.InMemoryAdmins();
  final Set<String> activeStaff = new HashSet<>();
  final StaffAccountLookup accounts = activeStaff::contains;

  SecondFactorService service(String firstAdminEmail) {
    return new SecondFactorService(
        admins, accounts, new PlatformAdminsServiceTest.NoDatabaseTransactions(), firstAdminEmail);
  }

  @Test
  void residentsNeverNeedIt() {
    assertThat(service("").required("vecina", "vecina@example.org")).isFalse();
    assertThat(service("admin@example.org").required("vecina", null)).isFalse();
  }

  @Test
  void activeStaffAndPlatformAdministratorsNeedIt() {
    activeStaff.add("xestora");
    admins.subjects.add("plataforma");
    SecondFactorService service = service("");
    assertThat(service.required("xestora", "xestora@example.org")).isTrue();
    assertThat(service.required("plataforma", null)).isTrue();
  }

  @Test
  void losingTheRoleRemovesItAtTheNextSignIn() {
    activeStaff.add("xestora");
    SecondFactorService service = service("");
    assertThat(service.required("xestora", null)).isTrue();
    activeStaff.remove("xestora");
    assertThat(service.required("xestora", null)).isFalse();
  }

  @Test
  void theFirstAdministratorEmailNeedsItOnlyWhileThereIsNoAdministrator() {
    SecondFactorService service = service("Admin@Example.org");
    assertThat(service.required("nova", " admin@example.ORG ")).isTrue();
    assertThat(service.required("nova", "outra@example.org")).isFalse();
    admins.subjects.add("plataforma");
    assertThat(service.required("nova", "admin@example.org")).isFalse();
  }
}
