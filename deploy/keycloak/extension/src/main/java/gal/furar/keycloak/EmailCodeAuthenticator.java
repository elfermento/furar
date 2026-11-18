// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * O código por correo no acceso (ADR-0029): para quen escolleu este método na alta, seis cifras
 * ao seu correo (o mesmo SMTP do realm), {@link EmailCode}. Cada código malo conta para o bloqueo
 * tras intentos fallidos do realm, coma un contrasinal malo.
 *
 * <p>Vai no subfluxo do segundo factor, detrás de «Condition - user configured»: só corre para
 * quen ten a credencial {@value SecondFactorMethods#EMAIL_CODE_TYPE} e non a aplicación (se ten as
 * dúas, pídese a aplicación).
 */
public final class EmailCodeAuthenticator implements Authenticator, AuthenticatorFactory {

  /** Id do provedor nos fluxos do realm. */
  public static final String PROVIDER_ID = "furar-email-code";

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  private static EmailCodeStep step(AuthenticationFlowContext context) {
    return new EmailCodeStep(
        context.getSession(),
        context.getRealm(),
        context.getUser(),
        context.getAuthenticationSession());
  }

  /** Se a conta está bloqueada polos intentos fallidos: entón a pantalla xa vai no contexto. */
  private static boolean locked(AuthenticationFlowContext context) {
    String error = AuthenticatorUtils.getDisabledByBruteForceEventError(context, context.getUser());
    if (error == null) {
      return false;
    }
    context.getEvent().user(context.getUser()).error(error);
    context.forceChallenge(
        context.form().setError("furarEmailCodeLocked").createForm(EmailCodeStep.PAGE));
    return true;
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    if (locked(context)) {
      return;
    }
    context.challenge(step(context).start(context.form()));
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    if (locked(context)) {
      return;
    }
    EmailCodeStep.Outcome outcome =
        step(context)
            .action(context.getHttpRequest().getDecodedFormParameters(), context.form());
    switch (outcome.result()) {
      case VALID -> context.success(SecondFactorMethods.EMAIL_CODE_TYPE);
      case WRONG -> {
        context.getEvent().user(context.getUser()).error(Errors.INVALID_USER_CREDENTIALS);
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, outcome.page());
      }
      case SHOWN -> context.challenge(outcome.page());
    }
  }

  @Override
  public boolean requiresUser() {
    return true;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return SecondFactorMethods.configured(
            user.credentialManager().isConfiguredFor(OTPCredentialModel.TYPE),
            EmailCodeStep.configuredFor(user))
        .filter(m -> m == SecondFactorMethods.Method.EMAIL)
        .isPresent();
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
    return "Furar: e-mail code";
  }

  @Override
  public String getReferenceCategory() {
    return SecondFactorMethods.EMAIL_CODE_TYPE;
  }

  @Override
  public String getHelpText() {
    return "Sends a six-digit code to the account's e-mail and asks for it. Runs for accounts that"
        + " chose the e-mail code as their second factor.";
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
