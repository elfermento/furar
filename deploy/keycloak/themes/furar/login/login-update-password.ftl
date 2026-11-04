<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe login-update-password.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 0cea2ddf59acc4ac36c7d849dc7c1a554466cf0cc6679f18ae08daee28f6258e
(X09 avisa se a da versión fixada cambia). Cambios: no contrasinal novo, a axuda nunha liña entre o
rótulo e o campo («Mínimo 12 caracteres», furarPasswordHelp, coa lonxitude da política do realm;
furar.css sóbea á vista); o erro chega ao
enviar (password-validation.ftl). Sen «Pechar a sesión noutros dispositivos» (password-commons.ftl).
É a páxina do rexistro (Keycloak 26.8 pide o contrasinal despois de verificar o correo), do
restablecemento e do cambio do contrasinal.
-->
<#import "template.ftl" as layout>
<#import "password-commons.ftl" as passwordCommons>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "password-validation.ftl" as validator>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
<!-- template: login-update-password.ftl -->
    <#if section = "header">
        ${msg("updatePasswordTitle")}
    <#elseif section = "form">
        <form id="kc-passwd-update-form" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate="novalidate">
            <@field.password name="password-new" label=msg("passwordNew") fieldName="password" autocomplete="new-password" autofocus=true>
                <div class="${properties.kcInputHelperTextItemClass}" id="furar-password-help-password-new">
                    <span class="${properties.kcInputHelperTextItemTextClass}">${msg("furarPasswordHelp", (passwordPolicies.length!12)?c)}</span>
                </div>
            </@field.password>
            <@field.password name="password-confirm" label=msg("passwordConfirm") autocomplete="new-password" />

            <@buttons.actionGroup horizontal=true>
                <#if isAppInitiatedAction??>
                    <@buttons.button id="kc-submit" name="login" label="doSubmit"/>
                    <@buttons.button id="kc-cancel" label="doCancel" name="cancel-aia" type="secondary"/>
                <#else>
                    <@buttons.button id="kc-submit" name="login" label="doSubmit"/>
                </#if>
            </@buttons.actionGroup>
        </form>

        <@validator.templates/>
        <@validator.script field="password-new"/>
    </#if>
</@layout.registrationLayout>
