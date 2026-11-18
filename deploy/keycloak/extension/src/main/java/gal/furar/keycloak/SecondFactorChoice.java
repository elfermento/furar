// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.lang.System.Logger.Level;
import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Alta do segundo factor (ADR-0029, punto 2): a primeira vez, a persoa
 * escolle entre a <b>aplicación de autenticación</b> (a primeira e recomendada) e o <b>código por
 * correo</b>, e dáa de alta antes de entrar.
 *
 * <ul>
 *   <li>Aplicación: a alta de Keycloak ({@code CONFIGURE_TOTP}: o QR e o primeiro código), dentro
 *       desta acción (chámase á acción de Keycloak co contexto desta), para poder volver: o
 *       formulario envía aquí.
 *   <li>Correo: manda un código ({@link EmailCodeStep}) e, co primeiro bo, garda a credencial
 *       {@value SecondFactorMethods#EMAIL_CODE_TYPE}. Ata entón a conta segue sen método.
 *   <li>Desde as dúas altas, «Cambiar de método» (o campo {@value #FIELD_BACK}) volve á escolla
 *       sen gardar nada.
 *   <li>Se a instalación non ofrece o correo ({@value SecondFactorMethods#EMAIL_VARIABLE}{@code
 *       =false}) ou a conta non ten correo, vai directa á aplicación, sen escolla nin volver.
 * </ul>
 *
 * Non se pode lanzar desde a aplicación ({@code kc_action}): cambiar de método é cousa da
 * administración (borrar a credencial na consola de Keycloak).
 */
public final class SecondFactorChoice implements RequiredActionProvider, RequiredActionFactory {

  /** Id da acción no realm (e na conta que a ten pendente). */
  public static final String PROVIDER_ID = "furar-choose-second-factor";

  /** Modelo do tema (login). */
  static final String PAGE = "furar-second-factor-choice.ftl";

  /** Campo do formulario da escolla. */
  static final String FIELD_METHOD = "method";

  /** Campo do formulario «Cambiar de método»: volver á escolla. */
  static final String FIELD_BACK = "back";

  /** Nota da sesión: escolleu o correo e está a dar o primeiro código. */
  static final String EMAIL_CHOSEN = "furar.second-factor.email-chosen";

  /** Nota da sesión: escolleu a aplicación e está a dala de alta. */
  static final String APP_CHOSEN = "furar.second-factor.app-chosen";

  /** Atributo do tema: hai a onde volver («Cambiar de método» na cabeceira). */
  static final String BACK_ATTRIBUTE = "furarSecondFactorBack";

  private static final System.Logger LOG = System.getLogger(SecondFactorChoice.class.getName());

  private boolean emailOffered = true;

  private List<SecondFactorMethods.Method> offered(UserModel user) {
    String email = user.getEmail();
    return SecondFactorMethods.offered(emailOffered, email != null && !email.isBlank());
  }

  private static EmailCodeStep step(RequiredActionContext context) {
    return new EmailCodeStep(
        context.getSession(),
        context.getRealm(),
        context.getUser(),
        context.getAuthenticationSession());
  }

  /** A alta da aplicación de Keycloak ({@code CONFIGURE_TOTP}). */
  private static RequiredActionProvider totp(RequiredActionContext context) {
    return context
        .getSession()
        .getKeycloakSessionFactory()
        .getProviderFactory(
            RequiredActionProvider.class, UserModel.RequiredAction.CONFIGURE_TOTP.name())
        .create(context.getSession());
  }

  /**
   * O formulario da acción, con {@value #BACK_ATTRIBUTE} se hai a onde volver. Keycloak dá un só
   * formulario por petición (o provedor vai na caché da sesión): o atributo segue nos formularios
   * que pide despois a alta da aplicación de Keycloak co mesmo contexto.
   */
  private static LoginFormsProvider backForm(RequiredActionContext context, boolean back) {
    LoginFormsProvider form = context.form();
    return back ? form.setAttribute(BACK_ATTRIBUTE, Boolean.TRUE) : form;
  }

  private static void chooseApp(RequiredActionContext context, boolean back) {
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    authSession.removeAuthNote(EMAIL_CHOSEN);
    authSession.setAuthNote(APP_CHOSEN, "true");
    backForm(context, back);
    totp(context).requiredActionChallenge(context);
  }

  private static void chooseEmail(RequiredActionContext context) {
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    authSession.removeAuthNote(APP_CHOSEN);
    authSession.setAuthNote(EMAIL_CHOSEN, "true");
    EmailCodeStep step = step(context);
    step.clear();
    context.challenge(step.start(backForm(context, true)));
  }

  /** Volve á escolla: esquece a alta empezada (o código por correo e a marca do método). */
  private void backToChoice(RequiredActionContext context) {
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    authSession.removeAuthNote(APP_CHOSEN);
    authSession.removeAuthNote(EMAIL_CHOSEN);
    step(context).clear();
    context.challenge(choicePage(context));
  }

  @Override
  public void evaluateTriggers(RequiredActionContext context) {}

  @Override
  public void requiredActionChallenge(RequiredActionContext context) {
    UserModel user = context.getUser();
    if (SecondFactorSetup.hasMethod(user)) {
      context.success();
      return;
    }
    List<SecondFactorMethods.Method> offered = offered(user);
    boolean back = SecondFactorMethods.canGoBack(offered);
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    if (!back || authSession.getAuthNote(APP_CHOSEN) != null) {
      chooseApp(context, back);
      return;
    }
    if (authSession.getAuthNote(EMAIL_CHOSEN) != null) {
      context.challenge(step(context).start(backForm(context, true)));
      return;
    }
    context.challenge(choicePage(context));
  }

  @Override
  public void processAction(RequiredActionContext context) {
    UserModel user = context.getUser();
    if (SecondFactorSetup.hasMethod(user)) {
      context.success();
      return;
    }
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();
    List<SecondFactorMethods.Method> offered = offered(user);
    boolean back = SecondFactorMethods.canGoBack(offered);
    if (params.containsKey(FIELD_BACK) && back) {
      backToChoice(context);
      return;
    }
    if (params.containsKey(FIELD_METHOD) && back) {
      var method = SecondFactorMethods.chosen(params.getFirst(FIELD_METHOD), offered);
      if (method.isEmpty()) {
        context.challenge(choicePage(context));
      } else if (method.get() == SecondFactorMethods.Method.APP) {
        chooseApp(context, true);
      } else {
        chooseEmail(context);
      }
      return;
    }
    if (!back || authSession.getAuthNote(APP_CHOSEN) != null) {
      // A alta da aplicación: o primeiro código bo garda a credencial e remata (success); a nota
      // morre coa sesión de acceso.
      authSession.setAuthNote(APP_CHOSEN, "true");
      backForm(context, back);
      totp(context).processAction(context);
      return;
    }
    if (authSession.getAuthNote(EMAIL_CHOSEN) == null) {
      context.challenge(choicePage(context));
      return;
    }
    EmailCodeStep.Outcome outcome =
        step(context).action(params, backForm(context, true));
    if (outcome.result() == EmailCodeStep.Result.VALID) {
      EmailCodeStep.configure(user);
      authSession.removeAuthNote(EMAIL_CHOSEN);
      context.success();
    } else {
      context.challenge(outcome.page());
    }
  }

  private Response choicePage(RequiredActionContext context) {
    return context.form().createForm(PAGE);
  }

  @Override
  public RequiredActionProvider create(KeycloakSession session) {
    return this;
  }

  @Override
  public void init(Config.Scope config) {
    String value = System.getenv(SecondFactorMethods.EMAIL_VARIABLE);
    emailOffered = SecondFactorMethods.emailOffered(value);
    if (SecondFactorMethods.emailSettingUnknown(value)) {
      LOG.log(
          Level.WARNING,
          SecondFactorMethods.EMAIL_VARIABLE
              + " is neither true nor false: only the authenticator app is offered (ADR-0029)");
    }
    LOG.log(
        Level.INFO,
        "Second factor set-up offers "
            + (emailOffered ? "the authenticator app and the e-mail code" : "the authenticator app"));
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
    return "Furar: choose and set up the second factor";
  }

  @Override
  public boolean isOneTimeAction() {
    return true;
  }
}
