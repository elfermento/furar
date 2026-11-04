<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/executeActions.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 058c0f9c5fa224e1290d44fae0768b898e8acd6804f3ae086259be7242bff396
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#outputformat "plainText">
<#assign furarActions><#if requiredActions??><#list requiredActions><#items as a>${msg("requiredAction.${a}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarActionsTitle") button=msg("furarActionsButton") link=link note=msg("furarLinkNote", linkExpirationFormatter(linkExpiration)) ignore=msg("furarActionsIgnore")>${msg("furarActionsLead", realmName, furarActions)}</@layout.mail>
