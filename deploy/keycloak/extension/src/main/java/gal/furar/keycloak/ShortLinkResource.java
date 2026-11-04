// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

/**
 * {@code GET /realms/<realm>/l/<código>} ({@link ShortLink}): 302 ao URL da acción gardado; se o
 * código non existe ou caducou, a páxina de erro do tema de acceso co texto {@code
 * furarShortLinkExpired} (como pedir outro). Sen sesión nin cookies; non se rexistra o código.
 */
public final class ShortLinkResource implements RealmResourceProvider {

  /** Texto da páxina de erro (tema de acceso, messages_gl e messages_es). */
  static final String EXPIRED_MESSAGE = "furarShortLinkExpired";

  private final KeycloakSession session;

  ShortLinkResource(KeycloakSession session) {
    this.session = session;
  }

  @Override
  public Object getResource() {
    return this;
  }

  /** Abre a ligazón curta. */
  @GET
  @Path("{code}")
  public Response open(@PathParam("code") String code) {
    RealmModel realm = session.getContext().getRealm();
    return ShortLink.resolve(
            realm == null ? null : realm.getId(), code, session.singleUseObjects()::get)
        .map(
            url ->
                Response.status(Response.Status.FOUND)
                    .location(URI.create(url))
                    .header("Cache-Control", "no-store")
                    .header("Referrer-Policy", "no-referrer")
                    .build())
        .orElseGet(
            () ->
                session
                    .getProvider(LoginFormsProvider.class)
                    .setError(EXPIRED_MESSAGE)
                    .createErrorPage(Response.Status.NOT_FOUND));
  }

  @Override
  public void close() {}
}
