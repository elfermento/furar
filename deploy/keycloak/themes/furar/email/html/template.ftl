<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/template.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 1e2a39459aa8211afadfba39db4ee4aad38ab8624c53a39fe1500f076f5df1d8
(X09 avisa se a da versión fixada cambia). Cambio: o deseño dos correos da API
(api/src/main/resources/mail/staff-invitation.html): banda co nome do despregue (o do realm),
tarxeta co título, o texto, o botón e, baixo el, «Non funciona o botón? Abre esta ligazón:» coa
ligazón curta (furarShortLink, da extensión de Furar; maqueta 55, opción C, 2026-11-04: o botón
leva o URL orixinal; sen ligazón curta, o URL longo), e o pé (por que chega). Sen ligazón a
«Privacidade» (revisión do 2026-11-03): a páxina é de cada concello e estes correos son da conta
común, que non sabe de que concello vén.
Seguro para clientes de correo: táboas, estilos en liña, ancho 520, sen fontes web nin imaxes.
- emailLayout: o que usan os modelos de base que Furar non substitúe (o texto vai na tarxeta).
- mail: título, texto (nested), botón con ligazón, a ligazón curta, liña do prazo (note) e aviso final. Os textos
  curtos van centrados (maqueta 51, revisión do 2026-11-03).
-->
<#macro page title>
<!doctype html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light">
<meta name="supported-color-schemes" content="light">
<meta name="referrer" content="no-referrer">
<title>${title}</title>
</head>
<body style="margin:0;padding:0;background-color:#f4f7fa;-webkit-text-size-adjust:100%;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f7fa;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1a1a1a;">
<tr>
<td align="center" bgcolor="#0b5c8e" style="background-color:#0b5c8e;padding:16px 24px 8px;">
<table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center">
<tr>
<td valign="middle" style="padding:0 12px 0 0;"><table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td width="28" height="28" bgcolor="#ffffff" style="width:28px;height:28px;background-color:#ffffff;border-radius:8px;font-size:0;line-height:0;">&nbsp;</td></tr></table></td>
<td valign="middle" style="font-size:17px;line-height:22px;font-weight:700;color:#ffffff;">${realmName}</td>
<td valign="middle" style="padding:0 12px;"><table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td width="1" height="18" bgcolor="#79a5c1" style="width:1px;height:18px;background-color:#79a5c1;font-size:0;line-height:0;">&nbsp;</td></tr></table></td>
<td valign="middle" style="font-size:15px;line-height:22px;color:#dae7ee;">${msg("furarMailService")}</td>
</tr>
</table>
</td>
</tr>
<tr>
<td align="center" style="padding:0 16px;background-color:#f4f7fa;background-image:linear-gradient(#0b5c8e,#0b5c8e 32px,#f4f7fa 32px,#f4f7fa);">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;margin:8px auto 0;">
<tr>
<td bgcolor="#ffffff" style="background-color:#ffffff;border-radius:16px;padding:28px 24px;box-shadow:0 2px 10px rgba(0,0,0,0.08);">
<#nested>
</td>
</tr>
<tr>
<td align="center" style="padding:16px 8px 32px;font-size:13px;line-height:20px;color:#55595e;">
<p style="margin:0;">${msg("furarMailWhy", realmName)}</p>
</td>
</tr>
</table>
</td>
</tr>
</table>
</body>
</html>
</#macro>

<#macro emailLayout>
<@page title=realmName>
<div style="font-size:15px;line-height:22px;color:#1a1a1a;">
<#nested>
</div>
</@page>
</#macro>

<#macro mail title button="" link="" note="" ignore="">
<@page title=title>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
<tr>
<td align="center" style="padding:0 0 20px;border-bottom:1px solid #dde3e8;">
<p style="margin:0;font-size:26px;line-height:32px;font-weight:800;color:#1a1a1a;">${title}</p>
</td>
</tr>
<tr>
<td align="center" style="padding:20px 0 0;font-size:15px;line-height:22px;color:#55595e;text-align:center;"><#nested></td>
</tr>
<#if button?has_content && link?has_content>
<tr>
<td style="padding:20px 0 0;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
<tr>
<td align="center" bgcolor="#0b5c8e" style="background-color:#0b5c8e;border-radius:999px;">
<a href="${link}" rel="noreferrer" style="display:block;padding:13px 16px;font-size:15px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:999px;">${button}</a>
</td>
</tr>
</table>
</td>
</tr>
<tr>
<#assign furarFallback = furarShortLink!link>
<td align="center" style="padding:12px 0 0;font-size:13px;line-height:18px;color:#55595e;text-align:center;">${msg("furarMailCopy")}<br><a href="${furarFallback}" rel="noreferrer" style="color:#0b5c8e;word-break:break-all;">${furarFallback}</a></td>
</tr>
</#if>
<#if note?has_content>
<tr>
<td align="center" style="padding:16px 0 0;font-size:15px;line-height:22px;color:#1a1a1a;text-align:center;">${note}</td>
</tr>
</#if>
<#if ignore?has_content>
<tr>
<td align="center" style="padding:${note?has_content?then('12','16')}px 0 0;font-size:15px;line-height:22px;color:#55595e;text-align:center;">${ignore}</td>
</tr>
</#if>
</table>
</@page>
</#macro>
