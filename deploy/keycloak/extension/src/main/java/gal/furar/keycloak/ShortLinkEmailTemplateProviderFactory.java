// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import org.keycloak.Config;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.EmailTemplateProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Fábrica de {@link ShortLinkEmailTemplateProvider}. Sen configuración: Keycloak toma por defecto o
 * provedor de maior {@link #order()} positivo cando hai varios e ningún configurado
 * ({@code DefaultKeycloakSessionFactory.resolveDefaultProvider}); o de base ({@code freemarker})
 * ten 0.
 */
public final class ShortLinkEmailTemplateProviderFactory implements EmailTemplateProviderFactory {

  /** Id do provedor. */
  public static final String PROVIDER_ID = "furar-short-link";

  /** Maior ca o do provedor de base (0), para ser o de por defecto. */
  static final int ORDER = 100;

  @Override
  public EmailTemplateProvider create(KeycloakSession session) {
    return new ShortLinkEmailTemplateProvider(session);
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public int order() {
    return ORDER;
  }
}
