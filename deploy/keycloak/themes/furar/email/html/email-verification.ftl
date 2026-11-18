<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/email-verification.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 4fdb1a4fd413ef724eb9dcfe8802da47a56efd6b28fcd236b8d2ca1983bb8998
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarVerifyTitle") button=msg("furarVerifyButton") link=link note=msg("furarVerifyNote", linkExpirationFormatter(linkExpiration)) ignore=msg("furarVerifyIgnore")>${msg("furarVerifyLead", realmName)}</@layout.mail>
