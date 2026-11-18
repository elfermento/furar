<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: EUPL-1.2

Alta do segundo factor do persoal (ADR-0029, punto 2): escoller a
aplicación de autenticación (primeira, recomendada e marcada) ou o código por correo. Da acción
furar-choose-second-factor da extensión de Furar (SecondFactorChoice); campo «method» (app ou
email). Sen nomear ningunha aplicación.
-->
<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=true; section>
<!-- template: furar-second-factor-choice.ftl -->
    <#if section = "header">
        ${msg("loginTotpTitle")}
    <#elseif section = "form">
        <form id="furar-second-factor-choice" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <fieldset class="${properties.kcFormGroupClass!} furar-choice">
                <legend class="furar-choice__legend">${msg("furarSecondFactorChoose")}</legend>
                <div class="pf-v5-c-radio furar-choice__option">
                    <input class="pf-v5-c-radio__input" type="radio" id="furar-method-app" name="method" value="app" checked aria-describedby="furar-method-app-help">
                    <label class="pf-v5-c-radio__label" for="furar-method-app">${msg("furarSecondFactorApp")} <span class="furar-choice__badge">${msg("furarSecondFactorRecommended")}</span></label>
                    <span class="pf-v5-c-radio__description" id="furar-method-app-help">${msg("furarSecondFactorAppHelp")}</span>
                </div>
                <div class="pf-v5-c-radio furar-choice__option">
                    <input class="pf-v5-c-radio__input" type="radio" id="furar-method-email" name="method" value="email" aria-describedby="furar-method-email-help">
                    <label class="pf-v5-c-radio__label" for="furar-method-email">${msg("furarSecondFactorEmail")}</label>
                    <span class="pf-v5-c-radio__description" id="furar-method-email-help">${msg("furarSecondFactorEmailHelp")}</span>
                </div>
            </fieldset>
            <@buttons.actionGroup>
                <@buttons.button id="furar-second-factor-continue" label="doContinue"/>
            </@buttons.actionGroup>
        </form>
    </#if>
</@layout.registrationLayout>
