// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import gal.furar.staff.application.port.out.StaffInvitationRepository;
import gal.furar.staff.domain.InvitationDelivery;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffInvitationId;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.TokenHash;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** As invitacións do concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class StaffInvitationPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements StaffInvitationRepository {

  private final StaffInvitationSql sql;

  StaffInvitationPersistenceAdapter(StaffInvitationSql sql) {
    this.sql = sql;
  }

  @Override
  public void insert(StaffInvitation invitation) {
    sql.insert(tenant().id(), invitation);
  }

  @Override
  public Optional<StaffInvitation> findByHash(TokenHash hash) {
    return sql.findByHash(tenant().id(), hash);
  }

  @Override
  public boolean markUsed(StaffInvitationId id, Instant now) {
    return sql.markUsed(tenant().id(), id.value(), now);
  }

  @Override
  public List<StaffInvitation> latestPerMember() {
    return sql.latestPerMember(tenant().id());
  }

  @Override
  public Optional<StaffInvitation> latestOf(StaffMemberId member) {
    return sql.latestOf(tenant().id(), member.value());
  }

  @Override
  public Map<StaffMemberId, Integer> sendCountPerMember() {
    Map<StaffMemberId, Integer> counts = new HashMap<>();
    sql.sendCountPerMember(tenant().id()).forEach((m, n) -> counts.put(new StaffMemberId(m), n));
    return counts;
  }

  @Override
  public List<Instant> sendsOf(StaffMemberId member) {
    return sql.sendsOf(tenant().id(), member.value());
  }

  @Override
  public List<Instant> sendsSince(Instant since, int limit) {
    return sql.sendsSince(tenant().id(), since, limit);
  }

  @Override
  public void supersedeOpen(StaffMemberId member, Instant now) {
    sql.supersedeOpen(tenant().id(), member.value(), now);
  }

  @Override
  public List<StaffInvitationId> deleteUnusedExpiredBefore(Instant cutoff) {
    return sql.deleteUnusedExpiredBefore(tenant().id(), cutoff).stream()
        .map(StaffInvitationId::new)
        .toList();
  }

  @Override
  public List<StaffInvitationId> idsOf(Collection<StaffMemberId> members) {
    if (members.isEmpty()) {
      return List.of();
    }
    return sql.idsOf(tenant().id(), members.stream().map(StaffMemberId::value).toList()).stream()
        .map(StaffInvitationId::new)
        .toList();
  }

  @Override
  public boolean recordDelivery(StaffInvitationId id, InvitationDelivery delivery) {
    return sql.recordDelivery(tenant().id(), id.value(), delivery);
  }
}
