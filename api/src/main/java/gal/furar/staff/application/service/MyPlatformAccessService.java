// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.staff.application.port.in.MyPlatformAccessUseCase;
import org.springframework.stereotype.Service;

/**
 * Se a persoa da sesión é administración da plataforma (ADR-0028). O rol é o efectivo da petición:
 * cando chega aquí, a autorización xa leu a táboa (e, no primeiro acceso, xa o deu). Sen
 * transacción propia: non le nada máis.
 */
@Service
public class MyPlatformAccessService implements MyPlatformAccessUseCase {

  private static final RoleGrant PLATFORM_ADMIN = new RoleGrant(RoleGrant.ANY, Role.PLATFORM_ADMIN);

  private final CurrentActor currentActor;

  public MyPlatformAccessService(CurrentActor currentActor) {
    this.currentActor = currentActor;
  }

  @Override
  @PersonUseCase
  public boolean platformAdmin() {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    return actor.grants().contains(PLATFORM_ADMIN);
  }
}
