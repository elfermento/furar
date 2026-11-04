<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/executeActions.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 b735334d11d90216ab6194db3f0792270ec7278a24395e12b53646034bc43097
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto, coa ligazón curta
(furarShortLink, da extensión de Furar; sen ela, o URL longo).
-->
<#assign furarActions><#if requiredActions??><#list requiredActions><#items as a>${msg("requiredAction.${a}")}<#sep>, </#sep></#items></#list></#if></#assign>
<#import "footer.ftl" as footer>
${msg("furarActionsTitle")}

${msg("furarActionsLead", realmName, furarActions)}

${msg("furarActionsButton")}:
${furarShortLink!link}

${msg("furarLinkNote", linkExpirationFormatter(linkExpiration))}

${msg("furarActionsIgnore")}

<@footer.text/>
