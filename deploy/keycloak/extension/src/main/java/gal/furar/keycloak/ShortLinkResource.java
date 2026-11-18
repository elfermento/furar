// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.security.SecureRandom;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.Urls;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.util.ResolveRelative;

/**
 * {@code GET /realms/<realm>/l/<código>} ({@link ShortLink}): 302 ao URL da acción gardado; se o
 * código non existe ou caducou, 404 coa páxina do tema {@value ExpiredLinkPage#TEMPLATE} («A
 * ligazón caducou» e os botóns para pedir outra, {@link ExpiredLinkPage}). Sen sesión nin cookies;
 * non se rexistra o código.
 */
public final class ShortLinkResource implements RealmResourceProvider {

  private static final SecureRandom RANDOM = new SecureRandom();

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
        .orElseGet(() -> expired(realm));
  }

  /**
   * A páxina da ligazón caducada. A volta dos botóns é a raíz do cliente da web ({@code rootUrl} e
   * {@code baseUrl}, a mesma que figura nos seus {@code redirectUris}); sen cliente, a páxina sae
   * sen botóns.
   */
  private Response expired(RealmModel realm) {
    LoginFormsProvider form =
        session.getProvider(LoginFormsProvider.class).setStatus(Response.Status.NOT_FOUND);
    ClientModel client =
        realm == null
            ? null
            : session.clients().getClientByClientId(realm, ExpiredLinkPage.CLIENT_ID);
    String redirectUri =
        client == null || !client.isEnabled()
            ? null
            : ResolveRelative.resolveRelativeUri(session, client.getRootUrl(), client.getBaseUrl());
    if (redirectUri != null && !redirectUri.isBlank()) {
      String realmUrl =
          Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName());
      String locale = session.getContext().resolveLocale(null).toLanguageTag();
      form.setAttribute(
              ExpiredLinkPage.LOGIN_ATTRIBUTE,
              ExpiredLinkPage.loginUrl(realmUrl, redirectUri, locale, RANDOM))
          .setAttribute(
              ExpiredLinkPage.RESET_ATTRIBUTE,
              ExpiredLinkPage.resetUrl(realmUrl, redirectUri, locale));
    }
    Response page = form.createForm(ExpiredLinkPage.TEMPLATE);
    // Cada visita leva un state novo nos botóns: nunca da caché.
    return Response.fromResponse(page)
        .header("Cache-Control", null)
        .header("Cache-Control", "no-store")
        .build();
  }

  @Override
  public void close() {}
}
