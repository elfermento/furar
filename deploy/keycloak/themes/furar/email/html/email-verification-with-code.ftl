<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/email-verification-with-code.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 bdcc21ca90123a18340bfd0f3a690d05f357cc0766add1997ff6f42fca11b571
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarVerifyCodeTitle") ignore=msg("furarVerifyIgnore")>${msg("furarVerifyCodeLead", realmName)}<br><span style="display:block;padding:16px 0 0;font-size:32px;line-height:40px;font-weight:700;letter-spacing:0.3em;color:#1a1a1a;text-align:center;">${code}</span></@layout.mail>
