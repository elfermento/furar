<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe password-validation.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 a7a7714a846e89d205f82e0cf1987c998f9e67988879906cb7ff90234db2aef1
(X09 avisa se a da versión fixada cambia). Úsano register.ftl e login-update-password.ftl.

Cambio: sen validación no navegador. A base mostraba os erros ao saír do campo; Furar só pon
unha axuda nunha liña entre o rótulo e o campo («Mínimo 12 caracteres», furarPasswordHelp, en
register.ftl e login-update-password.ftl) e o erro chega ao enviar, de Keycloak, que valida a
política do realm no servidor (lonxitude, distinto do correo e do usuario e a lista de
contrasinais comúns, que o navegador non pode comprobar).
-->
<#macro templates>
</#macro>

<#macro script field="">
</#macro>
