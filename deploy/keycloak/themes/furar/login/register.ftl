<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe register.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 75661cb29cd3139aa7b8bcfed5bad9192cb3a2f4788f88c98777f0888725dc24
(X09 avisa se a da versión fixada cambia). Cambio: o contrasinal (no formulario desde o 2026-11-02,
ADR-0027) vai despois dos apelidos (correo, nome, apelidos, contrasinal e repetilo, maqueta 36),
non xusto baixo o correo, coa axuda nunha liña entre o rótulo e o campo («Mínimo 12 caracteres»,
furarPasswordHelp; vai despois do campo no HTML e furar.css sóbea á vista); o erro chega ao enviar (password-validation.ftl). Ao final, «Xa tes conta?
Entrar» no canto de «« Volver ao acceso». Coa proba da invitación do persoal (furarInvitationEmail, extensión de Furar,
ADR-0027), o correo invitado queda só de lectura: a conta nace con ese correo verificado.
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "user-profile-commons.ftl" as userProfileCommons>
<#import "register-commons.ftl" as registerCommons>
<#import "password-validation.ftl" as validator>
<@layout.registrationLayout displayMessage=messagesPerField.exists('global') displayRequiredFields=true; section>
<!-- template: register.ftl -->

    <#if section = "header">
        <#if messageHeader??>
            ${msg(messageHeader)}
        <#else>
            ${msg("registerTitle")}
        </#if>
    <#elseif section = "form">
        <form id="kc-register-form" class="${properties.kcFormClass!}" action="${url.registrationAction}" method="post" novalidate="novalidate">
            <@userProfileCommons.userProfileFormFields; callback, attribute>
                <#if callback = "afterField">
                <#-- Furar (maqueta 36): o contrasinal despois do nome e os apelidos, ao final dos datos -->
                    <#if passwordRequired?? && attribute.name == 'lastName'>
                        <@field.password name="password" required=true label=msg("password") autocomplete="new-password">
                            <div class="${properties.kcInputHelperTextItemClass}" id="furar-password-help-password">
                    <span class="${properties.kcInputHelperTextItemTextClass}">${msg("furarPasswordHelp", (passwordPolicies.length!12)?c)}</span>
                </div>
                        </@field.password>
                        <@field.password name="password-confirm" required=true label=msg("passwordConfirm") autocomplete="new-password" />
                    </#if>
                </#if>
            </@userProfileCommons.userProfileFormFields>

            <@registerCommons.termsAcceptance/>

            <#if recaptchaRequired?? && (recaptchaVisible!false)>
                <div class="form-group">
                    <div class="${properties.kcInputWrapperClass!}">
                        <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}" data-action="${recaptchaAction}"></div>
                    </div>
                </div>
            </#if>

            <#if recaptchaRequired?? && !(recaptchaVisible!false)>
                <script>
                    function onSubmitRecaptcha(token) {
                        document.getElementById("kc-register-form").requestSubmit();
                    }
                </script>
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <button class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!} g-recaptcha"
                            data-sitekey="${recaptchaSiteKey}" data-callback="onSubmitRecaptcha" data-action="${recaptchaAction}" type="submit" id="kc-submit">
                        ${msg("doRegister")}
                    </button>
                </div>
            <#else>
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
                </div>
            </#if>

            <p id="kc-form-options" class="furar-switch">${msg("furarHaveAccount")} <a href="${url.loginUrl}">${msg("furarSignIn")}</a></p>

        </form>

        <#if furarInvitationEmail??>
            <script>
                <#outputformat "JavaScript">
                (function () {
                    var email = document.getElementById("email");
                    if (!email) return;
                    email.value = ${furarInvitationEmail?c};
                    email.readOnly = true;
                    var control = email.closest(${("." + properties.kcInputClass!)?c});
                    if (control) control.classList.add(${(properties.kcFormReadOnlyClass!)?c});
                })();
                </#outputformat>
            </script>
        </#if>

        <@validator.templates/>
        <@validator.script field="password"/>
    </#if>
</@layout.registrationLayout>
