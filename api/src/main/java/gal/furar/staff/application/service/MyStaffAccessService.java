// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.staff.application.port.in.MyStaffAccess;
import gal.furar.staff.application.port.in.MyStaffAccessUseCase;
import gal.furar.staff.application.port.out.ActiveMembership;
import gal.furar.staff.application.port.out.StaffAccessRepository;
import gal.furar.staff.application.port.out.StaffAccountLookup;
import gal.furar.staff.domain.StaffRole;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O acceso da persoa da sesión á xestión do concello (plan do dominio 4, §2.3). O rol é o efectivo
 * da petición (o da táboa). Cando chega aquí, a autorización xa leu a táboa e rexistrou o acceso
 * desta sesión, así que o anterior xa é o de verdade. Di tamén se a conta é de traballo nalgún
 * concello, para que a reserva da veciñanza o avise antes.
 */
@Service
public class MyStaffAccessService implements MyStaffAccessUseCase {

  private final CurrentActor currentActor;
  private final StaffAccessRepository access;
  private final StaffAccountLookup accounts;

  public MyStaffAccessService(
      CurrentActor currentActor, StaffAccessRepository access, StaffAccountLookup accounts) {
    this.currentActor = currentActor;
    this.access = access;
    this.accounts = accounts;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public MyStaffAccess mine() {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    StaffRole role = role(actor, TenantContext.require().slug());
    Instant previous =
        role == null
            ? null
            : access.active(actor.subject()).map(ActiveMembership::previousSignInAt).orElse(null);
    return new MyStaffAccess(role, previous, accounts.isActiveAnywhere(actor.subject()));
  }

  /** O rol de persoal máis alto no concello. */
  private static @Nullable StaffRole role(Actor actor, @Nullable String slug) {
    Set<RoleGrant> all = actor.grants();
    return Arrays.stream(StaffRole.values())
        .filter(r -> all.contains(new RoleGrant(String.valueOf(slug), r.role())))
        .findFirst()
        .orElse(null);
  }
}
