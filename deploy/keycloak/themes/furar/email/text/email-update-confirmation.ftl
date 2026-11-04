<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/email-update-confirmation.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 1b9f789a48674f2d3bf6b6e0eec54e00e80588e496c79e62ca611acdb580de33
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto, coa ligazón curta
(furarShortLink, da extensión de Furar; sen ela, o URL longo).
-->
<#import "footer.ftl" as footer>
${msg("furarUpdateEmailTitle")}

${msg("furarUpdateEmailLead", realmName, newEmail)}

${msg("furarUpdateEmailButton")}:
${furarShortLink!link}

${msg("furarLinkNote", linkExpirationFormatter(linkExpiration))}

${msg("furarUpdateEmailIgnore")}

<@footer.text/>
