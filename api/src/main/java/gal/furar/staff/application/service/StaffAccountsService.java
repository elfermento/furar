// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.booking.StaffAccounts;
import gal.furar.staff.application.port.out.StaffAccountLookup;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.StaffMember;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * A SPI {@link StaffAccounts} de {@code booking} (ADR-0027, revisión do 2026-10-30): unha conta con
 * pertenza activa en calquera concello é de traballo e non reserva como veciña; e o nome e o rol de
 * quen do persoal fixo unha reserva no nome doutra persoa. Non é un caso de uso: chámaa a reserva,
 * que xa comprobou a sesión e os permisos.
 */
@Service
class StaffAccountsService implements StaffAccounts {

  private final StaffAccountLookup lookup;
  private final StaffMemberRepository members;

  StaffAccountsService(StaffAccountLookup lookup, StaffMemberRepository members) {
    this.lookup = lookup;
    this.members = members;
  }

  @Override
  public boolean isStaffAccount(String subject) {
    return lookup.isActiveAnywhere(subject);
  }

  @Override
  public Map<String, Member> members(Collection<String> subjects) {
    if (subjects.isEmpty()) {
      return Map.of();
    }
    Map<String, Member> bySubject = new HashMap<>();
    for (StaffMember m : members.findBySubjects(Set.copyOf(subjects))) {
      // A máis nova primeiro: unha persoa desactivada e volta a invitar sae co seu último nome.
      String subject = m.subject();
      if (subject != null) {
        bySubject.putIfAbsent(subject, new Member(name(m), Role.valueOf(m.role().name())));
      }
    }
    return bySubject;
  }

  private static @Nullable String name(StaffMember m) {
    if (m.email().isAnonymised()) {
      return null;
    }
    String name = m.displayName();
    return name == null ? m.email().value() : name;
  }
}
