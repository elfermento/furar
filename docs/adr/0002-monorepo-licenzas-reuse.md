<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0002 · Monorepo, licenzas e REUSE

- **Estado:** aceptada (decidida polo mantedor) · 2026-10-22

## Contexto
As bases piden licenza OSI ou FSF. O xurado ten que poder instalalo e entendelo nun só sitio. Con dous repos, un problema habitual é copiar os endpoints á man e esquecer a `LICENSE`.

## Decisión
- **Un repositorio:** `api/` · `web/` · `deploy/` · `docs/` · `scripts/` · `templates/`. O cliente TS sae do OpenAPI no mesmo commit.
- **Licenzas:** código **EUPL-1.2**; documentación e datos de exemplo **CC0-1.0**. Única excepción: `CODE_OF_CONDUCT.md`, adaptación do Contributor Covenant 2.1, que mantén a súa CC-BY-4.0 (titular orixinal en `scripts/third-party-holders.txt`).
- **Titular:** `SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar`.
- **REUSE desde o primeiro commit:** cabeceira SPDX en cada ficheiro; `REUSE.toml` para os que non admiten comentarios (JSON, xerados); textos en `LICENSES/`. `reuse lint` rompe a CI. Sen comodíns para binarios (fontes, iconas, imaxes): adoitan ser de terceiros e cada un anótase co seu titular e licenza reais. Un titular alleo é correcto se figura en `scripts/third-party-holders.txt` co seu motivo (D02): o Maven Wrapper (Apache-2.0, sen modificar) e as traducións ao galego dos temas de Keycloak, obra derivada dos `messages_en` de Keycloak: levan os dous titulares e `EUPL-1.2 AND Apache-2.0` (Apache-2.0 §4 permite licenciar a obra derivada se se conservan os avisos; LIC-10). Unha tradución dun texto alleo é sempre obra derivada: leva o titular e a licenza do orixinal ademais dos de Furar.
- **Dependencias:** só licenzas OSI/FSF de `scripts/allowed-licenses.txt` (MIT, MIT-0, Apache-2.0, BSD-2-Clause, BSD-3-Clause, ISC, 0BSD, EPL-2.0, MPL-2.0, LGPL-2.1-or-later, CC0-1.0, OFL-1.1, PostgreSQL, Unicode-3.0, BlueOak-1.0.0, Python-2.0, `GPL-2.0-only WITH Classpath-exception-2.0`). Compróbase en Maven (D03) e npm (D04) por separado para **execución** (o que se distribúe: sen excepcións) e **desenvolvemento**.
  - npm avalíase coa **expresión SPDX completa** (`scripts/licenzas-npm.mjs`): con `AND` teñen que estar permitidas todas, con `OR` abonda unha, `WITH` compárase enteiro; `*` (licenza adiviñada), `UNKNOWN`, `UNLICENSED` e `Custom:` fallan. O `--onlyAllow` de `license-checker` compara por subcadea (deixaba pasar «MIT AND SSPL-1.0» ou «MIT*»), por iso non se usa. Unha autoproba garda que a comprobación falla de verdade.
  - Excepcións nominais, só de desenvolvemento, por paquete **e licenza** (só esa licenza extra nese paquete; falla se xa non se usa), en `scripts/license-exceptions.txt` co seu motivo: `caniuse-lite` (CC-BY-4.0, **datos** de compatibilidade de navegadores que usan browserslist, `@angular/build` e Tailwind), `spdx-exceptions` e `spdx-ranges` (CC-BY-3.0, **datos** SPDX que usan as propias ferramentas de licenzas). Non son código nin se distribúen na imaxe. As tres licenzas CC BY (3.0 e 4.0) son libres segundo a FSF, así que «só OSI/FSF» cúmprese igual; quedan como excepcións nominais porque CC BY non está na lista de código e obriga a atribuír, e así cada paquete que a use pasa por aquí. A lista desta ADR e `scripts/license-exceptions.txt` teñen que coincidir (LIC-6).
  - SBOM CycloneDX en cada build; gitleaks contra segredos no repo.
- **Contribucións:** DCO (`Signed-off-by`), sen CLA. **Valida o mantedor** (ver Consecuencias).
- Prohibido no proxecto: Liquibase ≥ 5 (FSL), Lombok, Flowbite, Google Fonts, SDK de nubes privativas.

## Consecuencias
- Calquera concello pode usalo, cambialo e aloxalo; quen o ofreza como servizo ten que publicar os cambios (EUPL).
- Con DCO, o mantedor non pode relicenciar o código achegado por outros (p. ex. licenza dual). Aloxar e cobrar por servizo si se pode.
- Cada ficheiro novo leva cabeceira: a plantilla de módulo xa a trae.
- **Imaxes base (o que se redistribúe en GHCR):** `eclipse-temurin` (Alpine), `node` só para construír, `caddy` (Alpine), `postgres` (Alpine) e `quay.io/keycloak/keycloak`. Esta última é a imaxe oficial do proxecto Keycloak (CNCF, Apache-2.0), construída sobre **Red Hat UBI 9**: os paquetes RPM que trae son software libre (GPL, LGPL, MIT…), pero a capa base está suxeita á *UBI EULA*, que permite redistribuír e só restrinxe o uso das marcas de Red Hat. Non é unha licenza OSI do conxunto da imaxe, e por iso se declara aquí. Quen queira unha cadea 100 % OSI pode construír Keycloak desde a distribución zip (Apache-2.0) sobre `eclipse-temurin:25-jre-alpine`: o `Dockerfile` de `deploy/keycloak` só copia o tema e executa `kc.sh build`, así que o cambio é a liña `FROM` e un `ADD` do zip; non se fai por defecto porque a imaxe oficial é a que Keycloak proba e parchea (LIC-4).

- **Código fonte das imaxes base (LIC-12).** Publicar as imaxes redistribúe binarios GPL e LGPL das bases (busybox e musl en Alpine; glibc e bash en UBI; o JDK, GPL-2.0 con *Classpath Exception*). Cada release (`scripts/notas-release.sh`) leva unha sección «Código fonte das imaxes base»: o digest exacto de cada base, aports de Alpine na versión exacta (lida do SBOM), os SRPM de UBI 9, o código de Temurin, Caddy e Keycloak na súa etiqueta, e unha **oferta escrita** de tres anos (GPL-2.0 §3.b). O SBOM CycloneDX de cada imaxe lista os paquetes coa súa licenza.
- **Etiqueta `org.opencontainers.image.licenses`:** nas tres imaxes, `EUPL-1.2`, a licenza do que engade Furar. Describir a imaxe enteira cunha expresión sería longo e quedaría desfasado en cada actualización da base; o detalle por paquete está no SBOM. `scripts/proba-licenzas-imaxes.sh` (D37) esixe exactamente `EUPL-1.2`.

## Alternativas descartadas
- **Dous repos (api e web):** contrato copiado á man e dúas CI.
- **AGPL-3.0:** equivalente en rede, pero a EUPL é a da UE, ten 23 linguas e a lista de compatibles.
- **CC BY 4.0 para a documentación:** obriga a citar; búscase que os concellos fagan manuais sen trabas.
- **CLA:** frea contribucións dun proxecto público pequeno.
- **Rexeitar calquera paquete non OSI tamén en desenvolvemento:** obrigaría a quitar Angular e Tailwind por un ficheiro de datos; a excepción é nominal e non chega ao produto.
- **`--onlyAllow` de license-checker:** compara por subcadea; ver Decisión.
- **Keycloak propio sobre Temurin por defecto:** fóra das imaxes que proba e parchea o proxecto Keycloak; queda como alternativa documentada.
