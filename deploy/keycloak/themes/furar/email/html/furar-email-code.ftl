<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: EUPL-1.2

Código do segundo factor por correo (ADR-0029, extensión de Furar: EmailCodeStep). O deseño dos
outros correos (template.ftl, macro mail); o código grande ao centro, como o de verificar o correo.
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarEmailCodeTitle") note=msg("furarEmailCodeNote", ttlMinutes) ignore=msg("furarEmailCodeIgnore")>${msg("furarEmailCodeLead", realmName)}<br><span style="display:block;padding:16px 0 0;font-size:32px;line-height:40px;font-weight:700;letter-spacing:0.3em;color:#1a1a1a;text-align:center;">${code}</span></@layout.mail>
