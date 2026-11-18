<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/password-reset.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 7aed2f985513c7419c54dfc267c739d8c284b99dabf64537278b5352f60c7f54
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto, coa ligazón curta
(furarShortLink, da extensión de Furar; sen ela, o URL longo).
-->
<#import "footer.ftl" as footer>
${msg("furarResetTitle")}

${msg("furarResetLead", realmName)}

${msg("furarResetButton")}:
${furarShortLink!link}

${msg("furarLinkNote", linkExpirationFormatter(linkExpiration))}

${msg("furarResetIgnore")}

<@footer.text/>
