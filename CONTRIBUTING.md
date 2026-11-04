<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Contribuír a Furar

Grazas por axudar. Furar é software libre (EUPL-1.2) para os concellos.

1. **Le antes:** [`docs/arquitectura.md`](docs/arquitectura.md), as [ADR](docs/adr/) e as [convencións](#convencións) de abaixo. Unha decisión nova leva ADR.
2. **Commits:** [Conventional Commits](https://www.conventionalcommits.org) en inglés e asinados con DCO: `git commit -s` engade `Signed-off-by` e certifica o [Developer Certificate of Origin](https://developercertificate.org). Non hai CLA. `scripts/probar-commits.sh` compróbao (D46), tamén o primeiro commit; `--autoproba` demostra nun repo temporal que rexeita os commits malos (D47). Activa unha vez o hook que o comproba antes de crear o commit: `git config core.hooksPath .githooks`.
3. **Cabeceiras:** cada ficheiro novo leva `SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar` e `SPDX-License-Identifier: EUPL-1.2` (código) ou `CC0-1.0` (documentación e datos de exemplo). Se o ficheiro non admite comentarios, anótao en `REUSE.toml`.
4. **Dependencias:** só con licenza de [`scripts/allowed-licenses.txt`](scripts/allowed-licenses.txt), versión exacta e publicada hai polo menos 14 días.
5. **Antes do PR:** `scripts/verificar.sh` (ou a parte que toques: `--parte compliance|api|web|e2e`). A CI executa o mesmo; o PR precisa `ci-ok` en verde.
6. **Seguridade:** non abras incidencias públicas con vulnerabilidades: [SECURITY.md](SECURITY.md) (correo e canal privado). Nos tests, ningún literal con aspecto de credencial: usa valores obviamente sintéticos construídos en execución (`"x".repeat(16)`); gitleaks (D07) revisa todo o repo e non se amplía a *allowlist* por ruta.
7. **Responsabilidade do que se achega:** quen asina o commit con `Signed-off-by` revisouno, enténdeo e responde del, sexa cal sexa a ferramenta coa que o fixo: o DCO aplícase igual, e non se achega código que reproduza código de terceiros cunha licenza incompatible. A titularidade dos dereitos é de quen contribúe (bases v3 do Certame, apartado 9).
8. **Conduta:** [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Como se decide: [GOVERNANCE.md](GOVERNANCE.md). Cambios visibles: unha liña en [CHANGELOG.md](CHANGELOG.md).

## Regras de traballo

- Documentación do repo en galego; código, commits e claves en inglés ([ADR-0003](docs/adr/0003-idioma.md)). Un termo novo vai ao [glosario](docs/glosario.md).
- Módulo novo: sempre con `scripts/novo-modulo.sh` e [`docs/como-engadir-un-modulo.md`](docs/como-engadir-un-modulo.md); se cambias `templates/module/`, pasa `scripts/probar-plantilla.sh`. Se falta unha regra de dominio, pregúntase antes de inventala.
- Se tocas un `.sh` ou un workflow, pasa D54: `scripts/verificar.sh --parte compliance` (shellcheck `-S warning` e actionlint).
- En cada cambio, `scripts/rapido.sh` (sen Docker, < 3 min). O completo (`scripts/verificar.sh`) corre na CI e antes de cada etiqueta. A DoD está conxelada: só se engaden filas para a regresión dun fallo real ([ADR-0016](docs/adr/0016-ci-e-verificacion.md)).
- Non se desactivan tests nin regras para que pase o build. Se unha regra estorba, proponse unha ADR.
- Ten que funcionar en equipos modestos de 8 GB: nada de builds pesados en paralelo; imaxes sempre en serie.
- Toda imaxe de terceiros e Action por digest; dependencias novas cunha versión de polo menos 14 días. Nada de Lombok, H2, Liquibase, Angular Material, Flowbite nin Google Fonts (as iconas Material Symbols van como SVG copiados, [ADR-0021](docs/adr/0021-sistema-de-deseno.md)).

## Comandos

Antes de nada: `source scripts/entorno.sh` (Node 24 no `PATH`, variables de Testcontainers para Colima e as funcións `api_ut`/`api_it`).

| Que | Comando |
|---|---|
| Servizos de desenvolvemento | `docker compose --profile dev up -d --wait` |
| API en local | `(cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo)` |
| Web en local | `(cd web && npm start)` → http://localhost:4200/naron |
| Formatar API | `(cd api && ./mvnw -B spotless:apply)` |
| Tests rápidos API (sen Docker) | `(cd api && ./mvnw -B test)` |
| Todo API, con cobertura | `(cd api && ./mvnw -B -Pcoverage verify)` (co compose `full` parado) |
| Uns poucos tests | `api_ut 'XxxTest'` · `api_it 'XxxIT'` |
| Rexenerar contrato | `(cd api && ./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiContractIT -Dfurar.openapi.write=true)` |
| Cliente TS | `(cd web && npm run api:generate)` |
| Web: lint, textos, tests, build | `(cd web && npm run lint -- --max-warnings 0 && npm run i18n:check && npm test -- --watch=false --coverage && npm run build)` |
| Todo levantado | `for s in api web keycloak; do docker compose --profile full build $s; done && docker compose --profile full up -d --wait` → http://localhost:8080/naron (nunca `up --build`: non cabe en memoria) |
| E2E | `(cd web && npx playwright test --project=gl --project=es --project=gl-mobile)` |
| Carril rápido (cada cambio) | `scripts/rapido.sh` (ou `api`, `web`; `--tenancy api` engade D14, antes do PR dun módulo con táboas) |
| Verificación completa | `scripts/verificar.sh` (ou `--parte compliance\|api\|web\|e2e`) |
| Módulo novo / desfacelo | `scripts/novo-modulo.sh [--custom-fields SPACE] space Space spaces espazos` · `scripts/novo-modulo.sh --remove space` (exemplo ficticio) |
| As gardas fallan ante violacións (D28) | `scripts/probar-violacion.sh [a-z]` (sen letras, os 26 casos) |
| A plantilla pasa as gardas (D45) | `scripts/probar-plantilla.sh` |
| Commits (D46), a súa autoproba (D47) e o primeiro commit (D46a) | `scripts/probar-commits.sh` · `--autoproba` · `--primeiro` |
| Rexistro RGPD e Cl@ve simulada en Keycloak (D51, D52) | `scripts/proba-identidade-keycloak.sh [rexistro\|clave]` (Keycloak de usar e tirar, ~1 GB) |

## Convencións

- API: `gal.furar.<modulo>` con `domain` · `application/port/{in,out}` · `application/service` · `adapter/in/web` · `adapter/out/persistence` ([ADR-0005](docs/adr/0005-monolito-modular-hexagonal.md)). Outros módulos só pola API pública (paquete raíz: `XxxQueries` + records, sen tipos internos, A19) e eventos; nunca `shared..config` (A20).
- Rutas `/api/v1/municipalities/{slug}/...` (sen concello, só: `/api/v1/municipalities` e `/municipality-imports` (plataforma), `/privacy-notice`, `/deployment-settings` e `/holidays` (do despregue), `/audit-events` (rexistro da plataforma) e `/me/platform-access` e `/me/staff-memberships` (da persoa)); permisos no caso de uso (`@RequiresPermission` ou `@PublicUseCase`) e matriz en `RolePermissions`; endpoints públicos con `@PublicEndpoint`.
- RGPD no acceso: nunca «acepto os termos» nin consentimento para o servizo (base 6.1.e). Responsable, DPD, dereitos e encargado saen dos datos legais de cada concello (páxina «Privacidade»; o rexistro de Keycloak leva unha frase xenérica coa ligazón); a web non promete funcións que non existen ([ADR-0017](docs/adr/0017-datos-persoais.md)). O IdP «Cl@ve simulada» é só de demo e non importa o NIF ([ADR-0019](docs/adr/0019-autenticacion-publica-clave.md)).
- Erros: `enum XxxError implements ErrorCode`, código `modulo.motivo` con texto en gl e es (API e web).
- SQL nativo `MUNICIPALITY`: `and municipality_id = :m` en cada lectura e escritura, a columna en cada `insert`, o predicado fóra de parénteses e sen `or`/`union`/`case` no nivel 0 (A8b); predicado dentro do `WHERE` principal; `<Clase>TenantIT` que estende `AbstractTenantIT` e cuxo `visibleTo` chama á clase; sen `@Query(nativeQuery = true)` nin anotacións de persistencia fóra da lista pechada de A22; só sobre táboas con Row Level Security (`NativeSqlRowLevelSecurityIT`). Toda táboa do concello trae RLS e a política `<táboa>_tenant` na súa migración, desde a plantilla (`V202610231100__row_level_security.sql` é o modelo; `SchemaTenancyIT` esíxeo, [ADR-0006](docs/adr/0006-multiconcello.md)).
- Táboas con `municipality_id`; entidades con `@TenantId`, `@Version` e `Persistable<UUID>`; adaptadores que herdan de `TenantScopedPersistenceAdapter`; eventos con `municipalityId`; migracións `V{AAAAMMDDHHMM}__{modulo}_{que}.sql`, nunca editar unha aplicada (`db/migrations.lock`, `scripts/migracions.sh fixar`).
- Horas en `Instant`/`timestamptz` (UTC); nunca `LocalDateTime` en DTO nin `java.util.Date`.
- Web: `core/` · `shared/` · `features/<modulo>/` (unha feature non importa outra); signals, `OnPush`, nada de despregue en `environment.ts`, textos en `web/src/i18n/{gl,es}.json`; formularios accesibles; campos personalizados con `<furar-custom-fields-form>`.
- Tests: `*Test` (sen Docker), `*IT` (PostgreSQL real), `*.spec.ts`, `web/e2e/*.e2e.ts` con axe ([ADR-0013](docs/adr/0013-estratexia-de-probas.md)).
