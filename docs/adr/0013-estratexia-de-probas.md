<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0013 · Estratexia de probas por capa

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo)

## Contexto
Un problema habitual: tests rotos, unha CI que non os corre e moi poucos specs na web. O xurado valora viabilidade: hai que demostrar que funciona, non dicilo. Ten que funcionar en equipos modestos de 8 GB, con 4 GB para Docker.

## Decisión
| Capa | Ferramenta | Que proba | Nome · fase Maven |
|---|---|---|---|
| Dominio | JUnit 6 + AssertJ, sen Spring | Invariantes, cálculos, erros | `*Test` · surefire |
| Aplicación | JUnit + dobres en memoria dos portos `out` (Mockito só para colaboradores externos) | Casos de uso, eventos, permisos | `*Test` · surefire |
| Web (API) | `@WebMvcTest` (`spring-boot-webmvc-test`) + `spring-security-test` | Rutas, status, JSON, `problem+json` | `*WebTest` · surefire |
| Persistencia | `@DataJpaTest` (`spring-boot-data-jpa-test`) + Testcontainers PG 18.6 + Flyway | Mapeo, consultas, restricións, `@TenantId` | `*IT` · failsafe |
| Módulo | `@ApplicationModuleTest` + `Scenario` | Eventos entre módulos | `*IT` · failsafe |
| Integración | `AbstractIT` (`@SpringBootTest(RANDOM_PORT)`, PG real, `TestJwtConfig`) | Fluxos HTTP, illamento, seguridade, contrato | `*IT` · failsafe |
| Arquitectura | ArchUnit + Modulith | ADR-0005, con autoproba | `*Test` · surefire |
| Web (Angular) | Vitest (`ng test`, jsdom) + TestBed | Compoñentes, servizos, formulario dinámico | `*.spec.ts` |
| Punta a punta | Playwright + axe contra `compose --profile full` | Fluxos reais en gl, es e móbil; WCAG 2.1 AA | `web/e2e/*.e2e.ts` |

**Regras**
- **Nunca H2** (enforcer): só PostgreSQL real.
- **Un contexto para os IT:** todos herdan de `AbstractIT` (contedor estático compartido, `PostgresContainers.shared()`, conectado con `@DynamicPropertySource` coma en produción: a API como `furar_app` e Flyway como `furar_owner`, sen `@ServiceConnection`, que usaría o superusuario do contedor; sen `@MockitoBean`). Excepción: `OccupancyModelIT`, co seu pool de 20. `spring.test.context.cache.maxSize=4`, `forkCount=1`, `reuseForks=true`, Hikari `maximumPoolSize=5`, `MAVEN_OPTS=-Xmx1g`. D11 execútase co compose `full` parado.
- **Mockito en JDK 25:** cárgase como javaagent da JVM (`-javaagent:${org.mockito:mockito-core:jar}` en `argLine` de surefire e failsafe, vía `maven-dependency-plugin:properties`) para non depender da autoanexión, que o JDK vai prohibir.
- Cada caso de uso novo: test de aplicación e IT HTTP do camiño feliz e do erro principal.
- **Cobertura:** JaCoCo ≥ 80 % de liñas en `domain` e `application`, en `jacoco:check` **só co perfil `-Pcoverage`** (D11); os comandos parciais non o activan, así non fallan por cobertura. Web: `coverageThresholds` en `angular.json` = real − 3 puntos (90 % sentenzas e liñas, 87 % ramas, 81 % funcións, T15) sobre `core/`, `shared/` e `features/`, excluído `api/`; o umbral só sobe. Ningún test omitido nin enfocado: ESLint prohibe `.skip`, `.only`, `.fixme`, `.fail`, `.todo`, `xit`, `fit`… en specs e e2e (D24), Playwright ten `forbidOnly: true` e D34 esixe 0 omitidos e o número de tests de `--list` (T14).
- **Sen tests desactivados:** `@Disabled` (A9) e `.skip`/`.only` (ESLint) fallan.
- **Cada garda ten a súa proba negativa** (`scripts/probar-violacion.sh`, D28): unha regra que non falla ante a súa violación non conta.
- Testcontainers en Colima: `scripts/entorno.sh` fixa `DOCKER_HOST`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` e `TESTCONTAINERS_RYUK_DISABLED=true`; como Ryuk está desactivado, `verificar.sh` borra ao saír os contedores con `label=org.testcontainers=true`.

## Consecuencias
- Unha regresión de illamento, seguridade ou contrato rompe o build antes do PR.
- `mvn verify` precisa Docker; `mvn test` vale para iterar.

## Alternativas descartadas
- **H2:** sen `tstzrange`, `jsonb` nin restricións de exclusión; verdes falsos.
- **Mocks de todo:** proban a implementación, non o comportamento.
- **Jest:** Angular 22 trae Vitest.
- **`jacoco:check` sempre en `verify`:** fai fallar por cobertura calquera execución parcial.
