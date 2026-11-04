<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# API de Furar

Spring Boot 4.1 · Java 25 · Spring Modulith 2.1 · PostgreSQL 18 · Flyway 12. Monolito modular hexagonal ([ADR-0005](../docs/adr/0005-monolito-modular-hexagonal.md)).

## Comandos

Antes: `source ../scripts/entorno.sh` (Docker de Colima e Testcontainers).

| Que | Comando |
|---|---|
| Arrincar en local (con `compose --profile dev`) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` |
| Formatar | `./mvnw -B spotless:apply` |
| Tests sen Docker (`*Test`) | `./mvnw -B test` |
| Todo, con cobertura ≥ 80 % (D11) | `./mvnw -B -Pcoverage verify` |
| Rexenerar `openapi.json` | `./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiContractIT -Dfurar.openapi.write=true` |
| Licenzas de terceiros | `./mvnw -B -q -Plicenses license:add-third-party` |
| SBOM | `./mvnw -B -q cyclonedx:makeAggregateBom` → `target/bom.json` |
| Imaxe | `docker build -t furar-api:local .` |

## Configuración

| Variable | Por defecto (dev) | Uso |
|---|---|---|
| `FURAR_DB_URL`, `FURAR_DB_USER`, `FURAR_DB_PASSWORD` | `jdbc:postgresql://localhost:5432/furar`, `furar_app`, `furar-app-demo` | Base de datos: rol só de datos, sen superusuario (ADR-0015) |
| `FURAR_DB_OWNER_USER`, `FURAR_DB_OWNER_PASSWORD` | `furar_owner`, `furar-owner-demo` | Flyway (dono do esquema). En Compose só os recibe o servizo `migrate` (`java … JarLauncher migrate`); en `prod` a API négase a arrincar se ve `FURAR_DB_OWNER_PASSWORD` |
| `FURAR_REQUIRED_LANGUAGES` | `gl` | Idiomas que todo concello ten que ofrecer (ADR-0003) |
| `FURAR_PUBLIC_URL` | `http://localhost:4200` | Orixe pública; o emisor do token é `${FURAR_PUBLIC_URL}/auth/realms/furar` |
| `FURAR_JWK_SET_URI` | `http://localhost:8082/auth/realms/furar/protocol/openid-connect/certs` | Chaves de Keycloak pola rede interna |
| `FURAR_API_PORT`, `FURAR_ACTUATOR_PORT` | `8081`, `9081` | API e actuator (só `health`) |

Perfís: `dev` (Swagger UI en `/swagger-ui.html`), `demo` (Narón, Ferrol e os oito concellos da comarca de Verín coas súas instalacións, `db/demo`; fotos desde `src/main/resources/demo/` ao arrincar; CC0), `prod` (sen valores por defecto; non arrinca con credenciais de demo nin URL de localhost), `test` (só tests).

## Onde está cada cousa

- `gal.furar.shared`: concello actual (`tenancy`), seguridade e permisos (`security`), erros RFC 9457 e contrato (`web`), IDs e campos validados (`domain`).
- `gal.furar.municipality`: concello, a rebanda de referencia.
- `gal.furar.customfields`: campos personalizados (só depende de `shared`).
- `src/test/java/gal/furar/architecture`: regras A1–A20 e M1–M3; `src/test/java/archviolations`: un exemplo que viola cada regra.
- `src/test/resources/db/occupancy-model`: modelo SQL de ocupación de ADR-0012, probado en `OccupancyModelIT`.

Módulo novo: [`docs/como-engadir-un-modulo.md`](../docs/como-engadir-un-modulo.md).
