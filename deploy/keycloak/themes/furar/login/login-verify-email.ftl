<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe login-verify-email.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 990458482c0f7be9478ed61509df69e0853e14c03c5f3d4b39f0af9ad4222528
(X09 avisa se a da versión fixada cambia). Cambios (maqueta 45, 2026-11-03), fóra das accións
pedidas desde a aplicación (isAppInitiatedAction, que quedan coma na base):
- Sen o aviso «Tes que verificar o teu correo electrónico para activar a conta»
  (verifyEmailMessage): Keycloak pono como aviso (warning) cada vez que pinta a páxina, tamén
  despois de reenviar, e repetía o que xa di o texto. Os erros (p. ex. agardar antes de reenviar,
  emailVerifySendCooldown) seguen saíndo.
- Un só texto (furarVerifyEmailSent), sen o enderezo.
- Abaixo, ao centro, «Non recibiches o correo?» (emailVerifyInstruction2) e un
  botón de verdade, «Reenviar o correo» (furarVerifyEmailResend), no canto de «Preme aquí para
  mandalo de novo». Envía o formulario por POST ao mesmo url.loginAction ca a ligazón da base:
  Keycloak reenvía o correo igual (VerifyEmail.processAction, co seu tempo de espera).
Proba: D51.
-->
<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=!isAppInitiatedAction?? displayMessage=(isAppInitiatedAction?? || !(message?has_content && message.type = 'warning')); section>
<!-- template: login-verify-email.ftl -->
    <#if section = "header">
        ${msg("emailVerifyTitle")}
    <#elseif section = "form">
        <p class="instruction" id="furar-verify-email-sent">
            <#if !isAppInitiatedAction??>
                ${msg("furarVerifyEmailSent")}
            <#elseif verifyEmail??>
                ${msg("emailVerifyInstruction1",verifyEmail)}
            <#else>
                ${msg("emailVerifyInstruction4",user.email)}
            </#if>
        </p>
        <#if isAppInitiatedAction??>
            <form id="kc-verify-email-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                <@buttons.actionGroup horizontal=true>
                    <#if verifyEmail??>
                        <@buttons.button id="kc-resend" label="emailVerifyResend" type="secondary"/>
                    <#else>
                        <@buttons.button id="kc-send" label="emailVerifySend"/>
                    </#if>
                    <@buttons.button id="kc-cancel" label="doCancel" type="secondary" name="cancel-aia" value="true"/>
                </@buttons.actionGroup>
            </form>
        </#if>
    <#elseif section = "info">
        <#if !isAppInitiatedAction??>
            <form id="kc-verify-email-resend-form" class="furar-verify-resend" action="${url.loginAction}" method="post">
                <p id="furar-verify-resend-question">${msg("emailVerifyInstruction2")}</p>
                <button id="kc-resend" class="${properties.kcButtonSecondaryClass!}" type="submit" aria-describedby="furar-verify-resend-question">${msg("furarVerifyEmailResend")}</button>
            </form>
        </#if>
    </#if>
</@layout.registrationLayout>
