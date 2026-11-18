// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** Datos fixos dos tests do dominio do persoal, cun reloxo fixo. */
final class StaffMembers {

  static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");
  static final String INVITER = "sub-xestora";
  static final String SUBJECT = "sub-conserxe";
  static final EmailAddress EMAIL = EmailAddress.of("conserxe@example.org");

  private StaffMembers() {}

  static StaffMember invited(StaffRole role) {
    return StaffMember.invite(StaffMemberId.newId(), EMAIL, role, Language.GL, INVITER, NOW);
  }

  static StaffMember active(StaffRole role) {
    return invited(role).accept(EMAIL.value(), SUBJECT, "Ana Conserxe", NOW);
  }

  static void assertFails(StaffError error, ThrowingCallable call) {
    DomainException e = catchThrowableOfType(DomainException.class, call);
    assertThat(e).as("expected %s", error.code()).isNotNull();
    assertThat(e.errorCode()).isEqualTo(error);
  }
}
