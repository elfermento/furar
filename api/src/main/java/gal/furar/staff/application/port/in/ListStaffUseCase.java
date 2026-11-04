// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ver o persoal do concello (plan do dominio 4, §2.3; permiso {@code staff.view}). */
public interface ListStaffUseCase {

  /** As pertenzas do concello, todas ou só as dun estado, das máis novas ás máis vellas. */
  List<StaffMemberView> list(@Nullable StaffStatus status);

  /**
   * @throws gal.furar.shared.domain.DomainException {@code staff.not-found}
   */
  StaffMemberView get(StaffMemberId id);
}
