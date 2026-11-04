// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.staff.StaffMemberSummary;
import gal.furar.staff.StaffQueries;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.StaffMember;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link StaffQueries}). Sempre no concello actual (o
 * repositorio falla sen el); sen permisos propios porque non é un caso de uso.
 */
@Service
class StaffQueryService implements StaffQueries {

  private final StaffMemberRepository members;

  StaffQueryService(StaffMemberRepository members) {
    this.members = members;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StaffMemberSummary> members(Set<String> subjects) {
    return members.findBySubjects(subjects).stream().map(StaffQueryService::summary).toList();
  }

  static StaffMemberSummary summary(StaffMember m) {
    return new StaffMemberSummary(
        m.id().value(),
        m.subject(),
        m.displayName(),
        m.email().isAnonymised() ? null : m.email().value(),
        StaffMemberSummary.Role.valueOf(m.role().name()),
        StaffMemberSummary.Status.valueOf(m.status().name()),
        m.deactivatedAt());
  }
}
