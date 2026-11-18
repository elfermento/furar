<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/identity-provider-link.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 77c3813c59069cb6a26a8e7ebe24861d16906da13864fd55a2a453e38211646f
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarIdpLinkTitle", identityProviderDisplayName) button=msg("furarIdpLinkButton") link=link note=msg("furarLinkNote", linkExpirationFormatter(linkExpiration)) ignore=msg("furarIdpLinkIgnore")>${msg("furarIdpLinkLead", identityProviderDisplayName, realmName, identityProviderContext.username)}</@layout.mail>
