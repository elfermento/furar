<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: EUPL-1.2

Código por correo do segundo factor do persoal (ADR-0029): no acceso (furar-email-code,
EmailCodeAuthenticator) e na alta (SecondFactorChoice). O código chega ao correo da conta
(furarEmailCodeTo) e vale furarEmailCodeMinutes minutos. Campo «code»; «Enviar outro código» manda
o campo «resend» (a extensión fai esperar 60 s entre envíos). Na alta, se hai a onde volver
(furarSecondFactorBack), «Cambiar de método» na cabeceira envía «back» (volve á escolla).
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('code'); section>
<!-- template: furar-email-code.ftl -->
    <#if section = "header">
        ${msg("furarEmailCodeTitle")}
    <#elseif section = "headerAction">
        <#if furarSecondFactorBack??>
            <form id="furar-second-factor-back" action="${url.loginAction}" method="post">
                <button type="submit" name="back" value="true" class="${properties.kcButtonLinkClass!} furar-back">${msg("furarSecondFactorBack")}</button>
            </form>
        </#if>
    <#elseif section = "form">
        <p class="instruction" id="furar-email-code-sent">${msg("furarEmailCodeSent", (furarEmailCodeTo!''), (furarEmailCodeMinutes!5))}</p>
        <form id="furar-email-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post" novalidate="novalidate">
            <@field.input name="code" label=msg("furarEmailCodeLabel") autocomplete="one-time-code" autofocus=true />
            <@buttons.actionGroup>
                <@buttons.button id="furar-email-code-submit" label="doLogIn"/>
            </@buttons.actionGroup>
        </form>
    <#elseif section = "info">
        <form id="furar-email-code-resend-form" class="furar-verify-resend" action="${url.loginAction}" method="post">
            <p id="furar-email-code-resend-question">${msg("furarEmailCodeNotReceived")}</p>
            <button id="furar-email-code-resend" class="${properties.kcButtonSecondaryClass!}" type="submit" name="resend" value="true" aria-describedby="furar-email-code-resend-question">${msg("furarEmailCodeResend")}</button>
        </form>
    </#if>
</@layout.registrationLayout>
