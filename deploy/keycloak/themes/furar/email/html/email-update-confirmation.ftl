<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/email-update-confirmation.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 c80241e398a91956387be47dae8088f1b9ff51a7143a739ffaeca9f996ebb104
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarUpdateEmailTitle") button=msg("furarUpdateEmailButton") link=link note=msg("furarLinkNote", linkExpirationFormatter(linkExpiration)) ignore=msg("furarUpdateEmailIgnore")>${msg("furarUpdateEmailLead", realmName, newEmail)}</@layout.mail>
