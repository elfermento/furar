<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe password-commons.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 7aa5d6b69b69162426efce57c2a641a562020e45f1bba00df8575def92bca242
(X09 avisa se a da versión fixada cambia). Cambio: sen a casiña «Pechar a sesión noutros
dispositivos» ao cambiar o contrasinal (unha decisión de máis nun paso que debe ser simple).
Sen ela, Keycloak non pecha as outras sesións.
-->
<#macro logoutOtherSessions>
</#macro>
