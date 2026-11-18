// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import jakarta.ws.rs.core.Response;
import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.Optional;
import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.events.EventType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserManager;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Eliminar a propia conta (ADR-0043): a acción {@value #PROVIDER_ID}, que a web lanza con {@code
 * kc_action=furar-delete-account}. Substitúe a {@code delete_account} de Keycloak, que segue
 * desactivada no realm: esa non se pode condicionar e borraría a conta aínda que a persoa teña
 * reservas pendentes.
 *
 * <ol>
 *   <li>Pide entrar outra vez antes de nada (idade máxima do acceso 0, coma a de Keycloak).
 *   <li>Pregúntalle á API se se pode ({@link AccountClosureApi#deletable}). Se non (reservas,
 *       partidas ou inscricións pendentes, persoal ou plataforma) ou se a API non responde, a
 *       páxina di por que e só ofrece volver á web: <b>fallo pechado</b>.
 *   <li>Se se pode, a confirmación co que se borra e o que se conserva ({@value #PAGE}).
 *   <li>Ao confirmar volve preguntar á API (pode cambiar entre as dúas) e só entón borra a conta
 *       en Keycloak e deixa o evento {@code DELETE_ACCOUNT} (só o id da conta, sen o correo).
 *       Despois do commit, {@link AccountClosureListener} avisa á API, que limpa os datos.
 * </ol>
 *
 * «Cancelar» e «Volver á web» envían {@code cancel-aia}: Keycloak volve á web coa sesión. Non
 * rexistra o {@code sub} nin o correo.
 */
public final class AccountDeletion implements RequiredActionProvider, RequiredActionFactory {

  /** Id da acción no realm e no {@code kc_action} da web. */
  public static final String PROVIDER_ID = "furar-delete-account";

  /** Modelo do tema (login). */
  static final String PAGE = "furar-delete-account.ftl";

  /** Atributo do tema: que mostra a páxina ({@link State}, en minúsculas). */
  static final String STATE_ATTRIBUTE = "furarDeleteAccount";

  /** Atributo do tema: a raíz da web, para «Ir á web» despois de eliminar. */
  static final String HOME_ATTRIBUTE = "furarHomeUrl";

  private static final System.Logger LOG = System.getLogger(AccountDeletion.class.getName());

  /** O que mostra a páxina. */
  enum State {
    /** A confirmación: que se borra e que se conserva. */
    CONFIRM,
    /** Queda algo pendente: hai que cancelalo na web. */
    BLOCKED,
    /** A API non respondeu: proba máis tarde. */
    UNAVAILABLE,
    /** A conta xa se eliminou. */
    DELETED;

    String attribute() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private Optional<AccountClosureApi> api = Optional.empty();

  /**
   * Que se pode facer, coa resposta da API: sen resposta, nada (fallo pechado).
   *
   * @param allowed o que di a API; baleiro se non respondeu ben
   */
  static State decide(Optional<Boolean> allowed) {
    if (allowed.isEmpty()) {
      return State.UNAVAILABLE;
    }
    return allowed.get() ? State.CONFIRM : State.BLOCKED;
  }

  private State ask(UserModel user) {
    State state = decide(api.flatMap(a -> a.deletable(user.getId())));
    if (state == State.UNAVAILABLE) {
      LOG.log(
          Level.WARNING,
          "Furar API did not answer whether the account can be deleted; nothing deleted (ADR-0043)");
    }
    return state;
  }

  private static Response page(RequiredActionContext context, State state) {
    return context.form().setAttribute(STATE_ATTRIBUTE, state.attribute()).createForm(PAGE);
  }

  @Override
  public void evaluateTriggers(RequiredActionContext context) {}

  @Override
  public void requiredActionChallenge(RequiredActionContext context) {
    context.challenge(page(context, ask(context.getUser())));
  }

  @Override
  public void processAction(RequiredActionContext context) {
    UserModel user = context.getUser();
    State state = ask(user);
    if (state != State.CONFIRM) {
      context.challenge(page(context, state));
      return;
    }
    KeycloakSession session = context.getSession();
    RealmModel realm = context.getRealm();
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    ClientModel client = authSession.getClient();
    String home = home(session, client);
    if (!new UserManager(session).removeUser(realm, user)) {
      context.getEvent().event(EventType.DELETE_ACCOUNT).user(user).error("user_delete_error");
      context.challenge(page(context, State.UNAVAILABLE));
      return;
    }
    // Só o id da conta (o que precisa a API para limpar), sen o correo nin o nome.
    context.getEvent().event(EventType.DELETE_ACCOUNT).client(client).user(user).success();
    new AuthenticationSessionManager(session).removeAuthenticationSession(realm, authSession, true);
    var form = context.form().setAttribute(STATE_ATTRIBUTE, State.DELETED.attribute());
    if (home != null) {
      form.setAttribute(HOME_ATTRIBUTE, home);
    }
    context.challenge(form.createForm(PAGE));
  }

  /** A raíz do cliente desde o que se entrou (a web), se a ten. */
  private static String home(KeycloakSession session, ClientModel client) {
    if (client == null || client.getBaseUrl() == null && client.getRootUrl() == null) {
      return null;
    }
    return ResolveRelative.resolveRelativeUri(session, client.getRootUrl(), client.getBaseUrl());
  }

  @Override
  public RequiredActionProvider create(KeycloakSession session) {
    return this;
  }

  @Override
  public void init(Config.Scope config) {
    api = AccountClosureApi.fromEnvironment();
    if (api.isEmpty()) {
      LOG.log(
          Level.WARNING,
          InvitationEmailVerification.SECRET_VARIABLE
              + " missing or too short: nobody can delete their account (ADR-0043)");
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
  public String getDisplayText() {
    return "Furar: delete own account";
  }

  @Override
  public InitiatedActionSupport initiatedActionSupport() {
    return InitiatedActionSupport.SUPPORTED;
  }

  /** Sempre pide entrar outra vez antes de eliminar, coma a acción de Keycloak. */
  @Override
  public int getMaxAuthAge(KeycloakSession session) {
    return 0;
  }
}
