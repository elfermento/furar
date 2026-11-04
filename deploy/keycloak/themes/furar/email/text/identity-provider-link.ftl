<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/identity-provider-link.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 27cc3ed36aea5c0686e81c63ee9fa19f3742635331de6c2ddffe1512bcec1075
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto, coa ligazón curta
(furarShortLink, da extensión de Furar; sen ela, o URL longo).
-->
<#import "footer.ftl" as footer>
${msg("furarIdpLinkTitle", identityProviderDisplayName)}

${msg("furarIdpLinkLead", identityProviderDisplayName, realmName, identityProviderContext.username)}

${msg("furarIdpLinkButton")}:
${furarShortLink!link}

${msg("furarLinkNote", linkExpirationFormatter(linkExpiration))}

${msg("furarIdpLinkIgnore")}

<@footer.text/>
