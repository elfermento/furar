// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.lang.System.Logger.Level;
import java.security.SecureRandom;
import java.util.Map;
import org.keycloak.email.EmailException;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * O provedor de modelos de correo de Keycloak (FreeMarker) coa ligazón curta ({@link ShortLink}):
 * a todo correo con ligazón de acción (verificar o correo, cambiar o contrasinal, accións pedidas
 * pola administración, cambio de correo, vinculación cunha conta externa) engádelle o atributo
 * {@code furarShortLink}, que o tema mostra baixo o botón. Se non se pode gardar, o correo sae igual
 * co URL longo. O resto, coma o de Keycloak.
 */
final class ShortLinkEmailTemplateProvider extends FreeMarkerEmailTemplateProvider {

  private static final System.Logger LOG =
      System.getLogger(ShortLinkEmailTemplateProvider.class.getName());
  private static final SecureRandom RANDOM = new SecureRandom();

  ShortLinkEmailTemplateProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  protected void addLinkInfoIntoAttributes(
      String link, long expirationInMinutes, Map<String, Object> attributes)
      throws EmailException {
    super.addLinkInfoIntoAttributes(link, expirationInMinutes, attributes);
    RealmModel target = realm != null ? realm : session.getContext().getRealm();
    if (target == null) {
      return;
    }
    try {
      ShortLink.create(
              link,
              target.getId(),
              target.getName(),
              expirationInMinutes * 60,
              session.singleUseObjects()::put,
              RANDOM)
          .ifPresent(url -> attributes.put(ShortLink.MAIL_ATTRIBUTE, url));
    } catch (RuntimeException e) {
      // Sen o código nin o URL no rexistro: o correo sae co URL longo.
      LOG.log(
          Level.WARNING,
          "Could not store the short link; the e-mail shows the full link ("
              + e.getClass().getSimpleName()
              + ")");
    }
  }
}
