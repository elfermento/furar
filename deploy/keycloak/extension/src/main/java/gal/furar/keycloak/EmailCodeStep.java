// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.lang.System.Logger.Level;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * A pantalla e o envío do código por correo (ADR-0029), comúns ao acceso ({@link
 * EmailCodeAuthenticator}) e á alta ({@link SecondFactorChoice}). O estado vai na nota {@value
 * #NOTE} da sesión de autenticación ({@link EmailCode}); non se rexistran nin o código nin o
 * correo.
 */
final class EmailCodeStep {

  /** Nota da sesión de autenticación co estado do código. */
  static final String NOTE = "furar.email-code";

  /** Modelo do tema (login) e do correo (email). */
  static final String PAGE = "furar-email-code.ftl";

  static final String MAIL = "furar-email-code.ftl";
  static final String MAIL_SUBJECT = "furarEmailCodeSubject";

  /** Campos do formulario. */
  static final String FIELD_CODE = "code";

  static final String FIELD_RESEND = "resend";

  private static final System.Logger LOG = System.getLogger(EmailCodeStep.class.getName());
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Que pasou co que enviou a persoa. */
  enum Result {
    /** Código bo: quen chama remata o paso. */
    VALID,
    /** Código malo (un intento fallido, para o bloqueo); a pantalla vai en {@code page}. */
    WRONG,
    /** Calquera outra cousa: a pantalla vai en {@code page}. */
    SHOWN
  }

  /** O resultado e, se non é {@link Result#VALID}, a pantalla que se mostra. */
  record Outcome(Result result, Response page) {}

  private final KeycloakSession session;
  private final RealmModel realm;
  private final UserModel user;
  private final AuthenticationSessionModel authSession;

  EmailCodeStep(
      KeycloakSession session,
      RealmModel realm,
      UserModel user,
      AuthenticationSessionModel authSession) {
    this.session = session;
    this.realm = realm;
    this.user = user;
    this.authSession = authSession;
  }

  /** Se a conta ten a credencial do código por correo. */
  static boolean configuredFor(UserModel user) {
    return user != null
        && user.credentialManager()
            .getStoredCredentialsByTypeStream(SecondFactorMethods.EMAIL_CODE_TYPE)
            .findAny()
            .isPresent();
  }

  /** Garda o método escollido na conta (despois do primeiro código bo). */
  static void configure(UserModel user) {
    if (configuredFor(user)) {
      return;
    }
    CredentialModel credential = new CredentialModel();
    credential.setType(SecondFactorMethods.EMAIL_CODE_TYPE);
    credential.setUserLabel("Correo");
    credential.setCreatedDate(Time.currentTimeMillis());
    credential.setSecretData("{}");
    credential.setCredentialData("{}");
    user.credentialManager().createStoredCredential(credential);
  }

  private Optional<EmailCode> current() {
    return EmailCode.fromNote(authSession.getAuthNote(NOTE));
  }

  /** Esquece o código deste acceso (acertou, ou volve á escolla). */
  void clear() {
    authSession.removeAuthNote(NOTE);
  }

  /**
   * A pantalla do código. Se non hai un código que aínda valla, manda un novo (unha recarga non
   * manda outro).
   */
  Response start(LoginFormsProvider form) {
    long now = Time.currentTimeSeconds();
    Optional<EmailCode> code = current();
    if (code.isPresent() && code.get().usable(now)) {
      return page(form);
    }
    if (code.isPresent() && !code.get().sendsLeft()) {
      return page(form.setError("furarEmailCodeTooManySends"));
    }
    return send(form, code, now);
  }

  /** Atende o formulario do código: «Enviar outro código» ou o código escrito. */
  Outcome action(MultivaluedMap<String, String> params, LoginFormsProvider form) {
    long now = Time.currentTimeSeconds();
    Optional<EmailCode> code = current();
    if (params.containsKey(FIELD_RESEND)) {
      if (code.isPresent() && code.get().waitSeconds(now) > 0) {
        return shown(page(form.setError("furarEmailCodeWait", code.get().waitSeconds(now))));
      }
      if (code.isPresent() && !code.get().sendsLeft()) {
        return shown(page(form.setError("furarEmailCodeTooManySends")));
      }
      return shown(send(form, code, now));
    }
    if (code.isEmpty()) {
      return shown(send(form, code, now));
    }
    EmailCode.Check check = code.get().check(params.getFirst(FIELD_CODE), now);
    if (check == EmailCode.Check.VALID) {
      clear();
      return new Outcome(Result.VALID, null);
    }
    boolean attempt = code.get().usable(now);
    if (attempt) {
      authSession.setAuthNote(NOTE, code.get().after().toNote());
    }
    String message =
        check == EmailCode.Check.WRONG ? "furarEmailCodeInvalid" : "furarEmailCodeExpired";
    Response page = page(form.addError(new FormMessage(FIELD_CODE, message)));
    return new Outcome(attempt ? Result.WRONG : Result.SHOWN, page);
  }

  private static Outcome shown(Response page) {
    return new Outcome(Result.SHOWN, page);
  }

  private Response send(LoginFormsProvider form, Optional<EmailCode> previous, long now) {
    if (user.getEmail() == null || user.getEmail().isBlank()) {
      return page(form.setError("furarEmailCodeNoEmail"));
    }
    EmailCode code = EmailCode.issue(now, previous, RANDOM);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("code", code.code());
    attributes.put("ttlMinutes", EmailCode.TTL_SECONDS / 60);
    try {
      session
          .getProvider(EmailTemplateProvider.class)
          .setRealm(realm)
          .setUser(user)
          .setAuthenticationSession(authSession)
          .send(MAIL_SUBJECT, MAIL, attributes);
    } catch (EmailException e) {
      LOG.log(
          Level.WARNING,
          "Could not send the second factor e-mail code (" + e.getClass().getSimpleName() + ")");
      return page(form.setError("furarEmailCodeNotSent"));
    }
    authSession.setAuthNote(NOTE, code.toNote());
    return page(form);
  }

  private Response page(LoginFormsProvider form) {
    return form.setAttribute("furarEmailCodeTo", user.getEmail())
        .setAttribute("furarEmailCodeMinutes", EmailCode.TTL_SECONDS / 60)
        .createForm(PAGE);
  }
}
