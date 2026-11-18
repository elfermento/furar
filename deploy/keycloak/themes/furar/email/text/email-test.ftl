<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe text/email-test.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 62db32ffb9edb9986f25a716e77cc4e48f106e02f023efceb6d4950a5be09408
(X09 avisa se a da versión fixada cambia). Cambio: a mesma información ca o correo HTML de Furar, en texto.
-->
<#import "footer.ftl" as footer>
${msg("furarTestTitle")}

${msg("furarTestLead", realmName)}

<@footer.text/>
