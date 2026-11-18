<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe login.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 c9ec5c28a14aa30f0d6887044f431c2c04cbe1ad7732c1bf4a1c3c89250efe31
(X09 avisa se a da versión fixada cambia). Cambio: tras un acceso fallido (erro no correo ou no
contrasinal), baixo o campo do contrasinal, o aviso xenérico do bloqueo (macro lockoutNotice):
«Despois de N intentos fallidos, a conta bloquéase M minutos» e, se o realm deixa restablecelo,
que se xa está bloqueada agarde ou use «Esqueciches o contrasinal?». É o mesmo texto exista ou
non a conta e estea ou non bloqueada: non di cantos intentos quedan nin se a conta está
bloqueada, porque iso permitiría saber que contas existen (por iso tamén Keycloak dá a unha conta
bloqueada o mesmo erro ca a un contrasinal incorrecto, accountTemporarilyDisabledMessage).
N e M saen da configuración do realm (realm.attributes: failureFactor e o menor de
waitIncrementSeconds e maxFailureWaitSeconds, a espera do primeiro bloqueo); sen protección de
forza bruta ou con bloqueo permanente non se mostra. docs/ens.md (op.acc.6.8).
-->
<#macro lockoutNotice>
    <#assign furarAttrs = realm.attributes>
    <#assign furarFailures = (furarAttrs["failureFactor"]!"0")?number>
    <#assign furarWait = [(furarAttrs["waitIncrementSeconds"]!"0")?number, (furarAttrs["maxFailureWaitSeconds"]!"0")?number]?min>
    <#if messagesPerField.existsError('username','password') && (furarAttrs["bruteForceProtected"]!"") == "true"
        && (furarAttrs["permanentLockout"]!"") != "true" && furarFailures gt 0 && furarWait gt 0>
        <div class="${properties.kcInputHelperTextItemClass}" id="furar-lockout-notice">
            <span class="${properties.kcInputHelperTextItemTextClass}">
                ${msg("furarLockoutNotice", furarFailures?c, (furarWait / 60)?ceiling?c)}<#if realm.resetPasswordAllowed> ${msg("furarLockoutNoticeReset", msg("doForgotPassword"))}</#if>
            </span>
        </div>
    </#if>
</#macro>
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "social-providers.ftl" as identityProviders>
<#import "passkeys.ftl" as passkeys>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
<!-- template: login.ftl -->

    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <#if realm.password>
                <form id="kc-form-login" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate="novalidate">
                    <#if !usernameHidden??>
                        <#assign label>
                            <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                        </#assign>
                        <@field.input name="username" label=label error=messagesPerField.getFirstError('username','password')
                            autofocus=true autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'username')}" value=login.username!'' />
                        <@field.password name="password" label=msg("password") error="" forgotPassword=realm.resetPasswordAllowed autofocus=usernameHidden?? autocomplete="current-password">
                            <@lockoutNotice/>
                            <#if realm.rememberMe && !usernameHidden??>
                                <@field.checkbox name="rememberMe" label=msg("rememberMe") value=login.rememberMe?? />
                            </#if>
                        </@field.password>
                    <#else>
                        <@field.password name="password" label=msg("password") forgotPassword=realm.resetPasswordAllowed autofocus=usernameHidden?? autocomplete="current-password">
                            <@lockoutNotice/>
                            <#if realm.rememberMe && !usernameHidden??>
                                <@field.checkbox name="rememberMe" label=msg("rememberMe") value=login.rememberMe?? />
                            </#if>
                        </@field.password>
                    </#if>

                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                    <@buttons.loginButton />
                </form>
            </#if>
            </div>
        </div>
        <@passkeys.conditionalUIData />
    <#elseif section = "socialProviders" >
        <#if realm.password && social.providers?? && social.providers?has_content>
            <@identityProviders.show social=social/>
        </#if>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    </#if>

</@layout.registrationLayout>
