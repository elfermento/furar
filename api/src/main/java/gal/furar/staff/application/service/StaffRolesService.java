// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.StaffRoles;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.out.ActiveMembership;
import gal.furar.staff.application.port.out.StaffAccessRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Os roles do persoal desde a táboa de pertenzas, en cada petición (ADR-0027, puntos 2 e 5).
 *
 * <ul>
 *   <li><b>Transacción curta propia</b> ({@code REQUIRES_NEW}): a autorización corre antes da
 *       transacción do caso de uso e, sen transacción, a RLS non devolve nada. Se xa hai unha (o
 *       caso de uso preguntou primeiro), non se mestura con ela: pode ser de só lectura.
 *   <li><b>Unha lectura por petición</b>: o rol e o último acceso nunha sentenza. A escritura só
 *       vai cando o {@code auth_time} é novo, unha vez por sesión.
 *   <li><b>Concorrencia do último acceso:</b> o {@code update} condicional ({@code last_sign_in_at
 *       < :authTime}) bloquea a fila e, en {@code READ COMMITTED}, as peticións que agardaban
 *       volven avaliar a condición despois do commit da primeira e non tocan nada. Só a que toca a
 *       fila rexistra {@code staff.sign-in}, na mesma transacción: o evento queda se e só se queda
 *       o acceso.
 * </ul>
 */
@Service
class StaffRolesService implements StaffRoles {

  static final String SIGN_IN = "staff.sign-in";

  private final StaffAccessRepository access;
  private final AuditLog audit;
  private final TransactionTemplate tx;

  StaffRolesService(
      StaffAccessRepository access, AuditLog audit, PlatformTransactionManager transactions) {
    this.access = access;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public Optional<Role> activeRole(
      String subject, TenantRef municipality, @Nullable Instant authTime) {
    Optional<ActiveMembership> membership =
        TenantContext.runAs(
            municipality,
            () -> Objects.requireNonNull(tx.execute(s -> read(subject, municipality, authTime))));
    return membership.map(m -> m.role().role());
  }

  private Optional<ActiveMembership> read(
      String subject, TenantRef municipality, @Nullable Instant authTime) {
    Optional<ActiveMembership> membership = access.active(subject);
    if (authTime != null
        && membership.isPresent()
        && membership.get().isNewSignIn(authTime)
        && access.recordSignIn(subject, authTime)) {
      ActiveMembership m = membership.get();
      audit.record(
          new AuditEvent(
                  SIGN_IN,
                  AuditEvent.Outcome.SUCCESS,
                  municipality.id(),
                  municipality.slug(),
                  Map.of("memberId", m.id().value().toString(), "role", m.role().name()))
              // A petición aínda non ve o rol da táboa (estase lendo agora): vai no evento.
              .withActorRole(m.role().role()));
    }
    return membership;
  }
}
