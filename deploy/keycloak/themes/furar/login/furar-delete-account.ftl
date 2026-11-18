<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: EUPL-1.2

Eliminar a propia conta (ADR-0043): a acción furar-delete-account da extensión de Furar
(AccountDeletion), despois de entrar outra vez. furarDeleteAccount di que mostrar:
- confirm: que se borra e que se conserva, «Eliminar a conta» (vermello) e «Cancelar».
- blocked: a API di que queda algo pendente (reservas, inscricións, partidas, persoal); só
  «Volver á web».
- unavailable: a API non respondeu; non se borrou nada; só «Volver á web».
- deleted: xa se eliminou; «Ir á web» (furarHomeUrl, a raíz do cliente) se a hai.
«Cancelar» e «Volver á web» envían cancel-aia: Keycloak volve á web coa sesión.
-->
<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=false; section>
<!-- template: furar-delete-account.ftl -->
    <#assign state = furarDeleteAccount!"unavailable">
    <#if section = "header">
        <#if state = "confirm">
            ${msg("furarDeleteAccountTitle")}
        <#elseif state = "blocked">
            ${msg("furarDeleteAccountBlockedTitle")}
        <#elseif state = "deleted">
            ${msg("furarDeleteAccountDeletedTitle")}
        <#else>
            ${msg("furarDeleteAccountTitle")}
        </#if>
    <#elseif section = "form">
        <div id="furar-delete-account" class="furar-delete-account" data-state="${state}">
            <#if state = "deleted">
                <#if furarHomeUrl??>
                    <a id="furar-delete-account-home" class="${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" href="${furarHomeUrl}">${msg("furarDeleteAccountHome")}</a>
                </#if>
            <#else>
                <form id="furar-delete-account-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <#if state = "confirm">
                        <p>${msg("furarDeleteAccountErased")}</p>
                        <p>${msg("furarDeleteAccountKept")}</p>
                        <p class="furar-delete-account__final">${msg("irreversibleAction")}</p>
                        <@buttons.actionGroup>
                            <button id="furar-delete-account-confirm" class="${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!} furar-button-danger" name="confirm" type="submit">${msg("furarDeleteAccountConfirm")}</button>
                            <@buttons.button id="furar-delete-account-cancel" name="cancel-aia" label="doCancel" type="secondary"/>
                        </@buttons.actionGroup>
                    <#else>
                        <#if state = "blocked">
                            <p class="instruction">${msg("furarDeleteAccountBlocked")}</p>
                        <#else>
                            <p class="instruction">${msg("furarDeleteAccountUnavailable")}</p>
                        </#if>
                        <@buttons.actionGroup>
                            <@buttons.button id="furar-delete-account-back" name="cancel-aia" label="furarDeleteAccountBack"/>
                        </@buttons.actionGroup>
                    </#if>
                </form>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
