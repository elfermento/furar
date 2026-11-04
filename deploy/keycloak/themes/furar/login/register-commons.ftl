<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe register-commons.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 bbdc14a314bffa71c0fc1a4a33dde6cb5df43130ffac60ab18a74ff554d6ebb0
(X09 avisa se a da versión fixada cambia).
RGPD (ADR-0017): no rexistro non hai casiña de «acepto os termos» (a base é 6.1.e, non o
consentimento) senón a información básica nunha frase curta, sempre visible e antes do botón: que o
concello trata os datos para xestionar a conta, e «Privacidade e os teus dereitos», a páxina
«Privacidade» do concello desde o que se entra, coa política completa (responsable, DPD, base,
dereitos, destinatarios, conservación e reclamación; LOPDGDD art. 11). Revisión do 2026-11-03: xa
non hai datos do despregue (FURAR_PRIVACY_*); eses datos son os legais de cada concello. A web pon
o camiño da páxina no parámetro furar_privacy e a extensión (RegistrationPrivacy) dá furarPrivacyUrl,
na orixe do redirect_uri xa validado. Sen ligazón (entrada sen concello) a frase non sae. O título
vai só para lectores de pantalla. Proba: D51.
Coa proba da invitación do persoal (furarInvitationEmail, a mesma condición que pon o correo só de
lectura en register.ftl) non sae a frase: os datos do persoal chegan de quen invita, así que a
información vai na primeira comunicación, o correo da invitación (RGPD art. 14; ADR-0027).
Tampouco sae mentres non hai ningún concello (furarSetup, revisión do 2026-11-03): a web engade
furar_setup=1 ao URL de acceso. Só vista: un URL feito á man só cambia a propia vista de quen o
escribe.
-->
<#macro termsAcceptance>
    <#if furarPrivacyUrl?? && !(furarInvitationEmail?? || furarSetup??)>
    <section id="furar-privacy-notice" class="furar-privacy-notice" aria-labelledby="furar-privacy-notice-title">
        <h2 id="furar-privacy-notice-title" class="furar-sr-only">${msg("furarPrivacyTitle")}</h2>
        <p>${kcSanitize(msg("furarPrivacySentence", furarPrivacyUrl))?no_esc}</p>
    </section>
    </#if>
</#macro>
