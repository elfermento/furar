// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Primeiro paso do subfluxo do segundo factor, despois da condición {@code furar-second-factor}
 * (ADR-0029): se a conta aínda non ten ningún método, engádelle a acción {@value
 * SecondFactorChoice#PROVIDER_ID} ({@link SecondFactorChoice}: escoller a aplicación ou o código
 * por correo e dalo de alta antes de entrar). Se xa ten un, non fai nada: os subfluxos seguintes
 * piden o seu.
 *
 * <p>Quita {@code CONFIGURE_TOTP} se quedara dunha alta sen rematar, para que a persoa volva á
 * escolla e non a unha alta da aplicación que xa non quere.
 */
public final class SecondFactorSetup implements Authenticator, AuthenticatorFactory {

  /** Id do provedor nos fluxos do realm. */
  public static final String PROVIDER_ID = "furar-second-factor-setup";

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  /** Se a conta xa ten un método do segundo factor. */
  static boolean hasMethod(UserModel user) {
    return SecondFactorMethods.configured(
            user.credentialManager().isConfiguredFor(OTPCredentialModel.TYPE),
            EmailCodeStep.configuredFor(user))
        .isPresent();
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    UserModel user = context.getUser();
    if (!hasMethod(user)) {
      user.removeRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
      user.addRequiredAction(SecondFactorChoice.PROVIDER_ID);
    }
    context.success();
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    context.success();
  }

  @Override
  public boolean requiresUser() {
    return true;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

  @Override
  public Authenticator create(KeycloakSession session) {
    return this;
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
  public String getDisplayType() {
    return "Furar: set up the second factor if missing";
  }

  @Override
  public String getReferenceCategory() {
    return null;
  }

  @Override
  public String getHelpText() {
    return "If the account has neither an authenticator app nor the e-mail code, adds the action"
        + " that lets the person choose one and set it up before signing in.";
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Override
  public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return REQUIREMENTS.clone();
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return List.of();
  }
}
