// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.common.util.Time;
import org.keycloak.events.Details;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Paso do formulario de rexistro (ADR-0027, revisión do 2026-11-02): se a petición de acceso trae
 * unha proba válida da invitación do persoal (parámetro {@code furar_invitation}, que asina a API
 * de Furar) e a conta nova ten ese mesmo correo, o correo queda verificado e Keycloak non manda o
 * correo de verificación: a ligazón da invitación xa chegou a ese enderezo. En calquera outro caso
 * (sen proba, outra firma, caducada, outro correo, xa usada ou sen segredo) non fai nada e o
 * rexistro segue coma sempre.
 *
 * <ul>
 *   <li><b>Onde está a proba:</b> Keycloak garda os parámetros adicionais do URL de acceso na
 *       sesión de autenticación como nota do cliente ({@code client_request_param_<nome>}). Non se
 *       rexistra nunca.
 *   <li><b>Correo fixo:</b> con proba válida, a páxina recibe {@code furarInvitationEmail} e o tema
 *       pon o campo só de lectura; aínda así, compárase co correo da conta creada.
 *   <li><b>Un só uso:</b> a proba consómese ao crear a conta ({@code singleUseObjects}, ata que
 *       caduque) e bórrase da sesión. Os correos non se repiten no realm, así que, aínda sen ese
 *       rexistro (reinicio de Keycloak), cada correo só se verifica unha vez por este camiño.
 *   <li><b>Rexistro:</b> un evento {@code VERIFY_EMAIL} co detalle {@code
 *       furar_verified_by=staff_invitation} e a invitación.
 * </ul>
 *
 * <p>Ademais, só para a vista (revisión do 2026-11-03): se o URL de acceso trae {@code
 * furar_setup=1} (a web pono só mentres non hai ningún concello), a páxina recibe {@code furarSetup}
 * e o tema non mostra a frase de privacidade do rexistro, que nomea un responsable que aínda non
 * existe. Non ten efecto de seguridade: quen escriba o URL á man só agocha unha frase informativa
 * na súa propia vista da páxina; sen o parámetro, a frase sae coma sempre ({@link
 * RegistrationSetup}).
 *
 * <p>Tamén só para a vista (ADR-0017, revisión do 2026-11-03): o parámetro {@code furar_privacy} trae
 * o camiño da páxina «Privacidade» do concello desde o que se entra; a páxina recibe {@code
 * furarPrivacyUrl}, na orixe do {@code redirect_uri} xa validado, e o tema liga a ela na frase de
 * privacidade ({@link RegistrationPrivacy}). Sen el, a frase non sae.
 *
 * <p>O segredo vén da variable {@code FURAR_INVITATION_PROOF_SECRET} (o mesmo valor ca na API, 32
 * caracteres ou máis). Sen ela, o paso non fai nada e avísase unha vez ao arrincar.
 */
public final class InvitationEmailVerification implements FormAction, FormActionFactory {

  /** Id do provedor no fluxo de rexistro do realm. */
  public static final String PROVIDER_ID = "furar-invitation-email";

  /** Parámetro do URL de acceso que trae a proba. */
  public static final String PARAMETER = "furar_invitation";

  /** Nota do cliente onde Keycloak garda o parámetro (AuthorizationEndpoint). */
  static final String CLIENT_NOTE = "client_request_param_" + PARAMETER;

  /** Atributo da páxina de rexistro co correo invitado (o tema pono só de lectura). */
  static final String FORM_ATTRIBUTE = "furarInvitationEmail";

  static final String SECRET_VARIABLE = "FURAR_INVITATION_PROOF_SECRET";
  static final String DETAIL_VERIFIED_BY = "furar_verified_by";
  static final String DETAIL_INVITATION = "furar_invitation_id";
  static final String VERIFIED_BY_INVITATION = "staff_invitation";
  private static final String SINGLE_USE_PREFIX = "furar-invitation-proof:";

  private static final System.Logger LOG = System.getLogger(InvitationEmailVerification.class.getName());

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  private Optional<InvitationProof> verifier = Optional.empty();

  // --- FormAction ---------------------------------------------------------------------------

  @Override
  public void buildPage(FormContext context, LoginFormsProvider form) {
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    claims(authSession).ifPresent(c -> form.setAttribute(FORM_ATTRIBUTE, c.email()));
    if (authSession != null
        && RegistrationSetup.isSetup(authSession.getClientNote(RegistrationSetup.CLIENT_NOTE))) {
      form.setAttribute(RegistrationSetup.FORM_ATTRIBUTE, Boolean.TRUE);
    }
    if (authSession != null) {
      RegistrationPrivacy.link(
              authSession.getRedirectUri(),
              authSession.getClientNote(RegistrationPrivacy.CLIENT_NOTE))
          .ifPresent(url -> form.setAttribute(RegistrationPrivacy.FORM_ATTRIBUTE, url));
    }
  }

  @Override
  public void validate(ValidationContext context) {
    // Non rexeita nada: sen proba válida, o rexistro é o de sempre.
    context.success();
  }

  @Override
  public void success(FormContext context) {
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    UserModel user = context.getUser();
    Optional<InvitationProof.Claims> found = claims(authSession);
    authSession.removeClientNote(CLIENT_NOTE);
    if (user == null || found.isEmpty() || !found.get().isFor(user.getEmail())) {
      return;
    }
    InvitationProof.Claims claims = found.get();
    long lifespan = Math.max(1, claims.expiresAt() - Time.currentTimeSeconds());
    if (!context
        .getSession()
        .singleUseObjects()
        .putIfAbsent(SINGLE_USE_PREFIX + claims.invitationId(), lifespan)) {
      return;
    }
    user.setEmailVerified(true);
    user.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
    authSession.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
    context
        .getEvent()
        .clone()
        .event(EventType.VERIFY_EMAIL)
        .user(user)
        .detail(Details.EMAIL, user.getEmail())
        .detail(DETAIL_VERIFIED_BY, VERIFIED_BY_INVITATION)
        .detail(DETAIL_INVITATION, claims.invitationId())
        .success();
  }

  private Optional<InvitationProof.Claims> claims(AuthenticationSessionModel authSession) {
    if (verifier.isEmpty() || authSession == null) {
      return Optional.empty();
    }
    return verifier.get().verify(authSession.getClientNote(CLIENT_NOTE), Time.currentTimeSeconds());
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

  // --- FormActionFactory ----------------------------------------------------------------------

  @Override
  public FormAction create(KeycloakSession session) {
    return this;
  }

  @Override
  public void init(Config.Scope config) {
    verifier = InvitationProof.withSecret(System.getenv(SECRET_VARIABLE));
    if (verifier.isEmpty()) {
      LOG.log(
          Level.WARNING,
          SECRET_VARIABLE
              + " is missing or shorter than "
              + InvitationProof.MIN_SECRET_LENGTH
              + " characters: staff invitations will not verify the e-mail address");
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
    return "Furar: e-mail verified by the staff invitation";
  }

  @Override
  public String getHelpText() {
    return "Marks the e-mail address as verified when the registration comes from a Furar staff"
        + " invitation with a valid signed proof for the same address.";
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
