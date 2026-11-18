<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe html/email-test.ftl de base (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 cd02418df2a5719a2f4b531a621f6b78a8da72be6c2829adb9c8478ce8618613
(X09 avisa se a da versión fixada cambia). Cambio: o deseño de Furar (template.ftl, macro mail) e textos propios (furar*).
-->
<#import "template.ftl" as layout>
<@layout.mail title=msg("furarTestTitle")>${msg("furarTestLead", realmName)}</@layout.mail>
