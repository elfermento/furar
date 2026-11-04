<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0004 · Versións exactas e verificadas

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo)

## Contexto
Rangos (`^`, `latest`) fan builds distintos cada día e rompen a demo. Acaban obrigando a usar `npm --force`. As versións verificáronse o 2026-10-22 en Maven Central, rexistro npm, Docker Hub, Quay e PyPI (non de memoria).

## Decisión
Versións exactas; ningún rango. Cando Spring Boot xestiona unha librería, úsase a súa versión (probadas xuntas) salvo motivo escrito aquí.

| Peza | Versión | Fonte · nota |
|---|---|---|
| Java (JDK/JRE) | 25 LTS · build `eclipse-temurin:25.0.4.1_1-jdk-alpine`, execución `eclipse-temurin:25.0.4.1_1-jre-alpine` | Docker Hub |
| Maven / Wrapper | 3.10.0 / 3.3.4 | Maven Central |
| Spring Boot | 4.1.1 | Maven Central (4.2 só en milestone) |
| Spring Framework / Security | 7.0.9 / 7.1.1 | xestionadas por Boot |
| Hibernate ORM | 7.4.5.Final | xestionada por Boot (Apache-2.0) |
| Spring Modulith | 2.1.1 | Maven Central |
| ArchUnit | 1.4.2 | a mesma que trae Modulith 2.1.1 (evita dúas versións; 1.5.1 existe) |
| Flyway | 12.4.0 | xestionada por Boot 4.1.1. Existe 13.11.1, pero a autoconfiguración de Boot 4.1 está probada coa 12; revísase ao subir a Boot 4.2 |
| springdoc-openapi | 3.1.1 | compilada contra Boot 4.1.0 |
| Testcontainers | 2.0.5 (`testcontainers-postgresql`) | xestionada por Boot |
| JUnit Jupiter / AssertJ | 6.0.3 / 3.27.7 | xestionadas por Boot |
| Driver PostgreSQL | 42.7.13 | xestionado por Boot |
| json-schema-validator (networknt) | 3.0.8 | só en tests (ADR-0011) |
| Spotless Maven + google-java-format | 3.10.4 + 1.36.1 | Maven Central (1.37.0 falla con Spotless 3.10.4) |
| ZXing core | 3.5.3 | Maven Central (Apache-2.0; QR do código da reserva, ADR-0025) |
| jsoup | 1.23.2 | Maven Central (MIT; saneado do texto propio das páxinas legais, ADR-0017) |
| license-maven-plugin · cyclonedx-maven-plugin | 2.7.1 · 2.9.3 | Maven Central |
| Mockito | 5.23 (xestionada por Boot) | cargada como javaagent da JVM (ADR-0013) |
| PostgreSQL | 18.6 (`postgres:18.6-alpine`) | Docker Hub |
| Keycloak | 26.8.1 (`quay.io/keycloak/keycloak:26.8.1`) | Quay; a extensión de Furar (`deploy/keycloak/extension`) compila contra `keycloak-services` e as súas pezas (`server-spi`, `server-spi-private`, `core`, `common`) 26.8.1, `provided` |
| Caddy | 2.11.7 (`caddy:2.11.7-alpine`) | Docker Hub |
| Mailpit | v1.31.6 (`axllent/mailpit:v1.31.6`) | Docker Hub |
| Node | 24 LTS (`.nvmrc` = 24; `engines` `>=24.15 <25`) · imaxe `node:24.22.1-alpine` | nodejs.org (24.22.1, 2026-10-20, a da máquina) |
| Angular (core, cli, build) / CDK | 22.2.3 / 22.2.4 | npm |
| TypeScript | 6.0.3 | Angular 22 pide `>=6.0 <6.1`; a 7.0.2 non é compatible |
| RxJS | 7.8.2 | npm |
| Tailwind CSS (+ @tailwindcss/postcss) | 4.3.3 | npm |
| Transloco / keys-manager | 8.4.0 / 8.1.1 | npm |
| Leaflet | 1.9.4 | npm (BSD-2-Clause; mapa, ADR-0023) |
| ECharts | 6.1.0 | npm (Apache-2.0; indicadores, ADR-0026) |
| Material Symbols | 0.47.6 (`@material-symbols/svg-400`) | npm; só os camiños SVG que se usan, copiados en `shared/ui/icon/` sen a dependencia (Apache-2.0) |
| oidc-client-ts (Apache-2.0) | 3.5.0 | npm |
| ng-openapi-gen | 1.1.0 | npm (OpenAPI 3.1, MIT, sen Java) |
| ESLint / angular-eslint / typescript-eslint | 10.13.0 / 22.6.0 / 8.72.0 | npm |
| Prettier | 3.9.10 | npm |
| Vitest / @vitest/coverage-v8 / jsdom | 5.0.5 / 5.0.5 / 30.1.3 | npm (MIT) |
| Playwright / @axe-core/playwright | 1.64.1 / 4.14.0 | npm |
| license-checker-rseidelsohn · @cyclonedx/cyclonedx-npm | 5.0.1 · 6.0.1 | npm |
| commitlint (cli; regras propias en `commitlint.config.mjs`, sen `config-conventional`) | 21.2.4 | npm (fixada en `scripts/probar-commits.sh`) |
| Fonte | Atkinson Hyperlegible Next (`@fontsource`, 5.3.0, OFL-1.1) | npm, servida desde Furar |
| reuse (imaxe `fsfe/reuse:6.2.0`) | 6.2.0 | PyPI / Docker Hub |
| Trivy (imaxe `aquasec/trivy:0.75.0`) | 0.75.0 | Docker Hub; 0.76.x ten menos de 14 días (ver Consecuencias) |
| gitleaks (imaxe `zricethezav/gitleaks:v8.30.1`) | 8.30.1 | Docker Hub (MIT) |
| Renovate (só `renovate-config-validator` na CI) | 44.176.2 | npm |
| yq (imaxe `mikefarah/yq:4.54.1`) · JaCoCo | 4.54.1 · 0.8.15 | Docker Hub · Maven Central |

**Imaxes por digest:** toda imaxe de terceiros vai como `etiqueta@sha256:…` en `compose*.yaml` e `FROM` (os digests póñense ao crear os ficheiros; Renovate actualízaos).

### Artefactos de Spring Boot 4 (modularizado)
Boot 4 separa a autoconfiguración por tecnoloxía; sen o *starter* correcto a peza non arrinca e non dá erro.

| Uso | Artefacto |
|---|---|
| REST | `spring-boot-starter-webmvc` |
| JWT | `spring-boot-starter-security-oauth2-resource-server` |
| JPA | `spring-boot-starter-data-jpa` |
| Migracións | `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` |
| Validación · actuator | `spring-boot-starter-validation` · `spring-boot-starter-actuator` |
| Modulith | `spring-modulith-starter-core` + `spring-modulith-starter-jdbc` (eventos por JDBC, ADR-0010) |
| OpenAPI | `org.springdoc:springdoc-openapi-starter-webmvc-ui` |
| Test | `spring-boot-starter-test`, `spring-boot-webmvc-test`, `spring-boot-data-jpa-test`, `spring-boot-testcontainers`, `spring-security-test`, `org.testcontainers:testcontainers-postgresql` (paquete `org.testcontainers.postgresql`), `spring-modulith-starter-test`, `com.tngtech.archunit:archunit-junit5` |

`MigrationsIT` falla se `flyway_schema_history` está baleira tras arrincar: protexe contra o starter esquecido.

## Consecuencias
- Build reproducible; `npm ci` co `package-lock.json` versionado; `maven-enforcer` comproba Java 25 e converxencia de dependencias.
- Renovate abre PR para subir versións; ningún cambio de versión sen CI verde.
- **Idade mínima de 14 días** (Renovate `minimumReleaseAge`) para toda subida automática. Na fixación inicial aplícase estrita ás ferramentas de seguridade e CI (por iso Trivy 0.75.0 e non 0.76.1, que é xusto o tipo de ferramenta atacada en 2026); as versións do produto son as últimas estables verificadas, e os parches LTS de seguridade (Node 24.22.1) entran sen esperar.
- TypeScript 7 queda bloqueado ata que Angular e typescript-eslint o admitan; Renovate agrupa Angular, TS e angular-eslint.

## Alternativas descartadas
- **Java 21:** 25 é a LTS actual, xa instalada e compatible con Boot 4.1.
- **Flyway 13.11.1 sobre Boot 4.1:** combinación non probada por Spring; o gaño non compensa o risco.
- **TypeScript 7:** Angular 22 e typescript-eslint 8.72 non o admiten.
- **Trivy 0.76.1:** publicada o 2026-10-21; non cumpre a idade mínima nunha ferramenta de seguridade.
- **openapi-generator (typescript-angular):** precisa Java no build da web; ng-openapi-gen é Node puro.
