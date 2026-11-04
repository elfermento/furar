<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0019 · Autenticación pública: Cl@ve e Chave365 como IdP SAML de Keycloak, probado con «Cl@ve simulada»

- **Estado:** aceptada · 2026-11-04 (proposta o 2026-10-22, AP2-03; IdP simulado na demo validado polo mantedor o 2026-10-30)
- **Revisada 2026-11-04:** aceptada. A pasarela real queda como débeda documentada: a adhesión, o certificado e os metadatos son do organismo (puntos 3 e 4) e a extensión eIDAS faise co contorno de probas da pasarela.

## Contexto
As bases v3 poñen como requisito técnico **obrigatorio** a «interoperabilidade: autenticación pública e estándares abertos». [ADR-0007](0007-seguridade-pechada.md) deixaba Cl@ve e Chave365 para a fase 2 cun diagrama: nada que o xurado (Xunta e un concello) puidese probar. Conectar coa pasarela real esixe a adhesión do organismo, un certificado e o contorno de probas do SGAD ou da Amtega; ningún está ao noso alcance antes da entrega.

## Decisión
1. **Un só camiño técnico:** a veciñanza entra sempre por Keycloak (OIDC + PKCE). Cl@ve (SAML 2.0, perfil eIDAS) e Chave365 son **provedores de identidade SAML de Keycloak**. A web e a API non cambian: reciben o mesmo token con `sub` e roles.
2. **Demo que funciona sen adhesión:** o realm `clave-simulada` (só en `dev` e `full`, `deploy/keycloak/import-demo/clave-simulada-realm.json`) fai de IdP SAML 2.0 e está dado de alta no realm `furar` como provedor `clave-simulada` (`deploy/keycloak/demo/overlay.json`):
   - respostas e asercións **asinadas e validadas** (`validateSignature`, `wantAssertionsSigned`); as chaves lense do descritor de metadatos (`useMetadataDescriptorUrl`), como se fará coa pasarela real;
   - `NameID` **persistente e opaco** (`G-…`), gardado só na ligazón federada. O nome de usuario en Furar é o correo que manda o IdP (o correo é o usuario no realm, P13 de [ADR-0027](0027-persoal-e-permisos-fase-1.md)); o mapeador `${ALIAS}.${NAMEID}` só contaría se se desactivase esa opción. Nunca é o NIF;
   - o IdP envía `nif`, `email`, `firstName` e `lastName`, como Cl@ve; Furar **só importa nome e correo** (mapeadores), así que o NIF non chega nin á base de Keycloak de Furar nin ao token;
   - *first broker login* estándar: conta nova se o correo non existe; se existe, Keycloak pide entrar coa conta local antes de ligalas (nunca automaticamente);
   - usuaria de proba: NIF `00000000T`, contrasinal de demo.
3. **Produción:** o realm de produción **non leva** ningún IdP (compróbao D52). Alta da pasarela real paso a paso en [`docs/instalacion.md` §12](../instalacion.md#12-clve-e-chave365); a adhesión fírmaa o concello ou, nun despregue compartido, a Deputación.
4. **Extensións eIDAS de Cl@ve** (`RequestedAttributes`, nivel de garantía): o provedor SAML de Keycloak 26 non as envía. Primeira opción: unha extensión pequena (SPI de *identity provider*) que as engada á `AuthnRequest`; decídese co contorno de probas do SGAD. Chave365: protocolo e alta que confirme a Amtega.
5. **Proba:** D52 (`scripts/proba-identidade-keycloak.sh clave`) entra cun navegador real por `clave-simulada` e comproba que o usuario de Furar non ten o NIF; o E2E `web/e2e/clave-simulada.e2e.ts` fai o percorrido desde a web no perfil `full` (D34).

## Consecuencias
- O requisito obrigatorio ten algo que funciona e que se proba: na memoria, «o mesmo conector SAML que usará a pasarela Cl@ve ou Chave365; a adhesión asínaa o concello».
- Poñer a pasarela real é configuración (metadatos, certificado, mapeadores) máis, para Cl@ve, a extensión eIDAS.
- A demo engade un realm e un usuario; o perfil `full` segue cabendo en 3,5 GB (mesmo proceso de Keycloak).

## Alternativas descartadas
- **Só documentar a fase 2:** un requisito obrigatorio sen nada executable.
- **Un IdP SAML aparte (SimpleSAMLphp, outro contedor):** máis memoria e outra peza que manter para o mesmo resultado.
- **IdP OIDC simulado:** Cl@ve é SAML; probaría outro protocolo.
- **Importar o NIF sempre:** máis datos dos necesarios (RGPD art. 5.1.c); só se un trámite o esixe, como atributo de Keycloak e nunca no token ([ADR-0017](0017-datos-persoais.md)).
