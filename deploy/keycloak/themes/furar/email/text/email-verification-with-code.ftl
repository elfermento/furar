<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/email-verification-with-code.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 199cb6bd4143c43739211ca60dad64e2565610b7782542b31617f073705d6cdb
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto.
-->
<#import "footer.ftl" as footer>
${msg("furarVerifyCodeTitle")}

${msg("furarVerifyCodeLead", realmName)}

${code}

${msg("furarVerifyIgnore")}

<@footer.text/>
