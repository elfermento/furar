<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: EUPL-1.2

Ligazón curta dos correos caducada ou descoñecida (GET /realms/<realm>/l/<código>, recurso
ShortLinkResource da extensión de Furar, 404). Título «A ligazón caducou», unha liña ao centro e
dous botóns a todo o ancho, un debaixo do outro: «Entrar e reenviar o correo» (furarLoginUrl: o
acceso co cliente da web; se o correo está sen verificar, Keycloak ofrece reenvialo) e «Cambiar o
contrasinal» (furarResetUrl: «Esqueciches o contrasinal?»). Os URL fainos a extensión
(ExpiredLinkPage); sen eles, só o texto. Sen ligazón de volver á web: os botóns xa levan alí.
-->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("furarLinkExpiredTitle")}
    <#elseif section = "form">
        <!-- template: furar-link-expired.ftl -->
        <div id="furar-link-expired" class="furar-link-expired">
            <p class="instruction">${msg("furarLinkExpiredText")}</p>
            <#if furarLoginUrl?? && furarResetUrl??>
                <a id="furar-link-expired-login" class="${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" href="${furarLoginUrl}">${msg("furarLinkExpiredLogin")}</a>
                <a id="furar-link-expired-reset" class="${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!}" href="${furarResetUrl}">${msg("furarLinkExpiredReset")}</a>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
