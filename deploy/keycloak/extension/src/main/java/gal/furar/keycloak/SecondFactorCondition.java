// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.AuthenticatorUtil;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.common.util.Time;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Condición dos fluxos de acceso de Furar (ADR-0029): o subfluxo do segundo factor só corre se a
 * conta ten que usalo. Despois desta condición, quen aínda non ten ningún método escólleo e dáo de
 * alta ({@link SecondFactorSetup}, {@link SecondFactorChoice}); quen o ten, dá o código da
 * aplicación ({@code auth-otp-form}) ou o do correo ({@link EmailCodeAuthenticator}).
 *
 * <ul>
 *   <li><b>Non</b> se o segundo factor xa se deu neste acceso (ao vincular unha conta dun provedor
 *       de identidade, o fluxo despois do provedor volve pasar por aquí).
 *   <li><b>Si</b> se o acceso vén dunha invitación do persoal válida para o correo da conta
 *       ({@code furar_invitation}, a mesma proba ca o rexistro): vai ser persoal ao volver á web.
 *   <li>Se non, <b>o que diga a API</b> de Furar ({@link SecondFactorPolicy}): persoal activo dalgún
 *       concello, administración da plataforma ou a conta de {@code FURAR_ADMIN_EMAIL} mentres non
 *       hai ningunha.
 *   <li><b>Sen resposta da API:</b> se a conta xa ten un método configurado (a aplicación ou o
 *       código por correo), pídese (nunca se salta por erro); se non, o acceso para cunha páxina de erro ({@code furarSecondFactorUnavailable}):
 *       sen saber se é persoal, non se deixa entrar só co contrasinal.
 * </ul>
 */
public final class SecondFactorCondition
    implements ConditionalAuthenticator, ConditionalAuthenticatorFactory {

  /** Id do provedor nos fluxos do realm. */
  public static final String PROVIDER_ID = "furar-second-factor";

  /** Mensaxe da páxina de erro (tema de acceso, gl e es). */
  static final String UNAVAILABLE_MESSAGE = "furarSecondFactorUnavailable";

  private static final System.Logger LOG = System.getLogger(SecondFactorCondition.class.getName());

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  /** O que resolve a condición. */
  enum Decision {
    REQUIRED,
    NOT_REQUIRED,
    UNAVAILABLE
  }

  private Optional<SecondFactorPolicy> policy = Optional.empty();
  private Optional<InvitationProof> invitations = Optional.empty();

  /**
   * @param invited se o acceso trae unha invitación válida para esta conta
   * @param answer o que di a API (só se pregunta se fai falta)
   * @param hasMethod se a conta xa ten un método configurado (aplicación ou código por correo)
   */
  static Decision decide(boolean invited, Supplier<Optional<Boolean>> answer, boolean hasMethod) {
    if (invited) {
      return Decision.REQUIRED;
    }
    Optional<Boolean> required = answer.get();
    if (required.isPresent()) {
      return required.get() ? Decision.REQUIRED : Decision.NOT_REQUIRED;
    }
    return hasMethod ? Decision.REQUIRED : Decision.UNAVAILABLE;
  }

  /** Se entre as credenciais xa usadas neste acceso está un segundo factor. */
  static boolean alreadyGiven(List<String> credentials) {
    return credentials.contains(OTPCredentialModel.TYPE)
        || credentials.contains(SecondFactorMethods.EMAIL_CODE_TYPE);
  }

  // --- ConditionalAuthenticator ------------------------------------------------------------

  @Override
  public boolean matchCondition(AuthenticationFlowContext context) {
    UserModel user = context.getUser();
    if (user == null) {
      return false;
    }
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    if (authSession != null && alreadyGiven(AuthenticatorUtil.getAuthnCredentials(authSession))) {
      return false;
    }
    Decision decision =
        decide(
            invited(context.getAuthenticationSession(), user),
            () -> policy.flatMap(p -> p.required(user.getId(), user.getEmail())),
            SecondFactorSetup.hasMethod(user));
    if (decision == Decision.UNAVAILABLE) {
      LOG.log(
          Level.WARNING,
          "Furar API did not answer whether the account needs a second factor; sign-in stopped"
              + " (ADR-0029)");
      throw new AuthenticationFlowException(
          AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR,
          "furar_second_factor_unavailable",
          UNAVAILABLE_MESSAGE);
    }
    return decision == Decision.REQUIRED;
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
  public void action(AuthenticationFlowContext context) {}

  @Override
  public boolean requiresUser() {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

  // --- ConditionalAuthenticatorFactory -----------------------------------------------------

  @Override
  public ConditionalAuthenticator getSingleton() {
    return this;
  }

  @Override
  public void init(Config.Scope config) {
    policy = SecondFactorPolicy.fromEnvironment();
    invitations =
        InvitationProof.withSecret(System.getenv(InvitationEmailVerification.SECRET_VARIABLE));
    if (policy.isEmpty()) {
      LOG.log(
          Level.WARNING,
          InvitationEmailVerification.SECRET_VARIABLE
              + " is missing or short: staff sign-ins without a configured second factor will"
              + " stop"
              + " (ADR-0029)");
    } else {
      LOG.log(Level.INFO, "Second factor policy from " + policy.get().endpoint());
    }
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
    return "Furar: account needs a second factor";
  }

  @Override
  public String getHelpText() {
    return "Matches staff and platform administrators of Furar (asked to the Furar API), or a"
        + " sign-in that comes from a valid staff invitation.";
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
