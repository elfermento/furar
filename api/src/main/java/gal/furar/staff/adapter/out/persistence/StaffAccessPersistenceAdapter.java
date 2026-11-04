// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import gal.furar.staff.application.port.out.ActiveMembership;
import gal.furar.staff.application.port.out.StaffAccessRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * As pertenzas activas no concello actual: falla sen concello ou sen transacción (ADR-0006). A
 * transacción é de quen chama: a lectura de cada petición vai na súa transacción curta propia.
 */
@Component
class StaffAccessPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements StaffAccessRepository {

  private final StaffMemberSql sql;

  StaffAccessPersistenceAdapter(StaffMemberSql sql) {
    this.sql = sql;
  }

  @Override
  public Optional<ActiveMembership> active(String subject) {
    return sql.active(tenant().id(), subject);
  }

  @Override
  public boolean recordSignIn(String subject, Instant authTime) {
    return sql.recordSignIn(tenant().id(), subject, authTime) == 1;
  }
}
