// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import gal.furar.staff.application.port.out.RetainedMember;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** As pertenzas do concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class StaffMemberPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements StaffMemberRepository {

  private final StaffMemberSql sql;

  StaffMemberPersistenceAdapter(StaffMemberSql sql) {
    this.sql = sql;
  }

  @Override
  public Optional<StaffMember> find(StaffMemberId id) {
    return sql.find(tenant().id(), id.value());
  }

  @Override
  public Optional<StaffMember> findForUpdate(StaffMemberId id) {
    return sql.findForUpdate(tenant().id(), id.value());
  }

  @Override
  public List<StaffMember> list(@Nullable StaffStatus status) {
    return sql.list(tenant().id(), status);
  }

  @Override
  public List<StaffMember> findBySubjects(Collection<String> subjects) {
    return sql.findBySubjects(tenant().id(), subjects);
  }

  @Override
  public List<StaffMember> findByIds(Collection<StaffMemberId> ids) {
    return sql.findByIds(tenant().id(), ids.stream().map(StaffMemberId::value).toList());
  }

  @Override
  public int lockActiveManagers() {
    return sql.lockActiveManagers(tenant().id());
  }

  @Override
  public boolean insert(StaffMember member) {
    return sql.insert(tenant().id(), member);
  }

  @Override
  public boolean update(StaffMember member, long expectedVersion) {
    return sql.update(tenant().id(), member, expectedVersion);
  }

  @Override
  public List<RetainedMember> anonymiseDeactivatedBefore(Instant cutoff, Instant now) {
    return sql.anonymiseDeactivatedBefore(tenant().id(), cutoff, now);
  }

  @Override
  public List<RetainedMember> deactivateUnacceptedWithoutInvitation(
      Instant invitedBefore, String by, Instant now) {
    return sql.deactivateUnacceptedWithoutInvitation(tenant().id(), invitedBefore, by, now);
  }
}
