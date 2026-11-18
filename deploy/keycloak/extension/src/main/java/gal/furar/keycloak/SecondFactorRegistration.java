// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.common.util.Time;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Paso do formulario de rexistro (ADR-0029): a conta nova que vai ser persoal configura o segundo
 * factor xa, antes de volver á web, e non no seguinte acceso. Créase dentro do rexistro, que non
 * pasa polo fluxo de acceso. Engade á conta a acción {@value SecondFactorChoice#PROVIDER_ID}
 * (escoller a aplicación ou o código por correo e dalo de alta, {@link SecondFactorChoice}) se:
 *
 * <ul>
 *   <li>o rexistro vén dunha invitación do persoal válida para ese correo ({@code
 *       furar_invitation}), ou
 *   <li>a API de Furar di que a conta o precisa (a de {@code FURAR_ADMIN_EMAIL} mentres non hai
 *       ningunha administración, {@link SecondFactorPolicy}).
 * </ul>
 *
 * Sen resposta da API non engade nada: o seguinte acceso pasa pola condición {@link
 * SecondFactorCondition}, que non deixa entrar sen saber. Vai antes de {@code
 * furar-invitation-email}, que consome a proba.
 */
public final class SecondFactorRegistration implements FormAction, FormActionFactory {

  /** Id do provedor no fluxo de rexistro do realm. */
  public static final String PROVIDER_ID = "furar-second-factor-registration";

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  private Optional<SecondFactorPolicy> policy = Optional.empty();
  private Optional<InvitationProof> invitations = Optional.empty();

  /** Se a conta nova ten que configurar o segundo factor (a API só se pregunta se fai falta). */
  static boolean needsSetup(boolean invited, Supplier<Optional<Boolean>> answer) {
    return invited || answer.get().orElse(false);
  }

  @Override
  public void buildPage(FormContext context, LoginFormsProvider form) {}

  @Override
  public void validate(ValidationContext context) {
    context.success();
  }

  @Override
  public void success(FormContext context) {
    UserModel user = context.getUser();
    if (user == null) {
      return;
    }
    if (needsSetup(
        invited(context.getAuthenticationSession(), user),
        () -> policy.flatMap(p -> p.required(user.getId(), user.getEmail())))) {
      user.addRequiredAction(SecondFactorChoice.PROVIDER_ID);
    }
  }

  private boolean invited(AuthenticationSessionModel authSession, UserModel user) {
    if (authSession == null || invitations.isEmpty()) {
      return false;
    }
    return invitations
        .get()
        .verify(
            authSession.getClientNote(InvitationEmailVerification.CLIENT_NOTE),
            Time.currentTimeSeconds())
        .filter(c -> c.isFor(user.getEmail()))
        .isPresent();
  }

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

  @Override
  public FormAction create(KeycloakSession session) {
    return this;
  }

  @Override
  public void init(Config.Scope config) {
    policy = SecondFactorPolicy.fromEnvironment();
    invitations =
        InvitationProof.withSecret(System.getenv(InvitationEmailVerification.SECRET_VARIABLE));
  }

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
    return "Furar: staff configure the second factor on registration";
  }

  @Override
  public String getHelpText() {
    return "Adds the action that chooses and sets up the second factor to a new account that"
        + " comes from a staff invitation or that the Furar API marks as needing one.";
  }

  @Override
  public String getReferenceCategory() {
    return null;
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
