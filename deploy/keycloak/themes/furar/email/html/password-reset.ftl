<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/password-reset.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 4a327c93a1b3ce126bd8a01906df21af3c099e2fafd712eb2b9bfbe9d8565655
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarResetTitle") button=msg("furarResetButton") link=link note=msg("furarLinkNote", linkExpirationFormatter(linkExpiration)) ignore=msg("furarResetIgnore")>${msg("furarResetLead", realmName)}</@layout.mail>
