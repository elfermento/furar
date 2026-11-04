// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import static gal.furar.staff.domain.StaffMembers.assertFails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** O correo normalizado das pertenzas (plan do dominio 4, §1.2.6). */
class EmailAddressTest {

  @Test
  void itIsStrippedAndLowercased() {
    assertThat(EmailAddress.of("  Ana.Pena@Concello.GAL\t").value())
        .isEqualTo("ana.pena@concello.gal");
    assertThat(EmailAddress.of("a@b").value()).isEqualTo("a@b");
  }

  @Test
  void twoAddressesMatchAfterNormalizing() {
    EmailAddress email = EmailAddress.of("ana@example.org");
    assertThat(email.matches(" ANA@example.org ")).isTrue();
    assertThat(email.matches("ana@example.com")).isFalse();
    assertThat(email.matches(null)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"", "  ", "ab", "anaexample.org", "@example.org", "ana@", "ana pena@example.org"})
  void malformedAddressesAreRejected(String raw) {
    assertFails(StaffError.EMAIL_INVALID, () -> EmailAddress.of(raw));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ana@anonimizada.invalid", "Ana@Example.INVALID", "a@invalid"})
  void theReservedInvalidDomainIsRejected(String raw) {
    assertFails(StaffError.EMAIL_INVALID, () -> EmailAddress.of(raw));
  }

  @Test
  void anAnonymisedAddressNamesTheMembershipAndIsNeverReal() {
    StaffMemberId id =
        new StaffMemberId(java.util.UUID.fromString("0190d9a2-0000-7000-8000-000000000001"));
    EmailAddress anonymised = EmailAddress.anonymised(id);
    assertThat(anonymised.value())
        .isEqualTo("anon-0190d9a2-0000-7000-8000-000000000001@anonimizada.invalid");
    assertThat(anonymised.isAnonymised()).isTrue();
    assertThat(EmailAddress.of("anon-x@example.org").isAnonymised()).isFalse();
  }

  @Test
  void from3To254Characters() {
    String local = "a".repeat(64);
    String longest = local + "@" + "b".repeat(254 - 65);
    assertThat(EmailAddress.of(longest).value()).hasSize(254);
    assertFails(StaffError.EMAIL_INVALID, () -> EmailAddress.of(longest + "c"));
  }

  @Test
  void theRecordOnlyHoldsNormalizedAddresses() {
    assertThatThrownBy(() -> new EmailAddress("Ana@example.org"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EmailAddress("sen-arroba"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rolesAreFurarRoles() {
    assertThat(StaffRole.MANAGER.role()).isEqualTo(gal.furar.shared.security.Role.MANAGER);
    assertThat(StaffRole.STAFF.role()).isEqualTo(gal.furar.shared.security.Role.STAFF);
  }
}
