<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/email-verification.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 daa0d3394415d15c8e5954436007056c783e1f24b5aec69f37b164490890b84b
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto, coa ligazón curta
(furarShortLink, da extensión de Furar; sen ela, o URL longo).
-->
<#import "footer.ftl" as footer>
${msg("furarVerifyTitle")}

${msg("furarVerifyLead", realmName)}

${msg("furarVerifyButton")}:
${furarShortLink!link}

${msg("furarVerifyNote", linkExpirationFormatter(linkExpiration))}

${msg("furarVerifyIgnore")}

<@footer.text/>
