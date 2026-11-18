// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/** Fábrica de {@link ShortLinkResource}: o id é o camiño, {@code /realms/<realm>/l}. */
public final class ShortLinkResourceFactory implements RealmResourceProviderFactory {

  @Override
  public RealmResourceProvider create(KeycloakSession session) {
    return new ShortLinkResource(session);
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return ShortLink.PATH;
  }
}
