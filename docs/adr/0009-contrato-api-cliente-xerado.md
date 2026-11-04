<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0009 · OpenAPI versionado e cliente TS xerado

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo)

## Contexto
Se os endpoints se copian á man na web, desincronízanse. As bases piden interoperabilidade.

## Decisión
- **API `/api/v1`**, JSON, recursos en inglés e plural kebab-case (`/municipalities/{slug}/custom-fields`). Paxinación `?page=0&size=20` → `{ items, page, size, totalItems }`. Datas ISO 8601 (`Instant` con `Z`; `LocalDate`). Recursos editables devolven `ETag` (versión) e as modificacións piden `If-Match` (412 `common.stale-version`). O contrato tamén o di: os métodos `@VersionedResource` declaran `ETag` nas respostas 200 e 201, e todo 201 declara `Location`; `ContractHeadersIT` chama de verdade a cada operación e comproba que o declarado e o enviado coinciden (curso, 2026-10-23). **Crear reserva é idempotente** (`POST …/me/bookings`, borrador IETF `draft-ietf-httpapi-idempotency-key-header`): a cabeceira `Idempotency-Key` é obrigatoria (sen ela, 400 `booking.idempotency-key-missing`; mal formada, 400 `booking.idempotency-key-invalid`); a mesma clave coa mesma petición devolve a mesma reserva (201, mesmo `Location`, `ETag` da versión de agora) e cunha petición distinta dá 422 `booking.idempotency-key-reused`. O contrato declara a cabeceira e as respostas; o deseño está en [ADR-0025](0025-reservas-fase-1.md) punto 19.
- **Públicos:** `GET /municipalities`, `GET /municipalities/{slug}`, `GET …/theme`, `GET …/custom-fields` (ADR-0007).
- **springdoc 3.1.1** xera OpenAPI 3.1. Os `ErrorCode` publícanse como `enum` no esquema `Problem`.
- **Determinista:** `springdoc.writer-with-order-by-keys=true`, `writer-with-default-pretty-printer=true`, `@OpenAPIDefinition(servers = @Server(url = "/"))` (sen porto aleatorio), fin de liña `\n`.
- **Contrato versionado:** `api/openapi.json`. `OpenApiContractIT` pide `/v3/api-docs`, compara como **JSON normalizado** e, se difire, falla imprimindo o diff e o comando para rexeneralo (`-Dfurar.openapi.write=true`, o único modo que escribe o ficheiro).
- **Cliente TS:** `ng-openapi-gen` xera `web/src/app/api/` desde `../api/openapi.json` en `prebuild`, `pretest`, `prelint` e `prestart`. Non se versiona. A web compila con `strictTemplates`, así que un campo renombrado rompe tamén os modelos HTML. `scripts/probar-contrato.sh` demóstrao: nunha copia do contrato renomea un campo da resposta, engade un obrigatorio á petición e cambia un tipo, e esixe que `ng build` falle co erro TS de cada caso (D23).
- **Swagger UI** só en `dev`; OpenAPI público en `/api/v1/openapi.json`.

## Consecuencias
- Un único contrato para web, terceiros e memoria (anexo). O diff de `openapi.json` é a revisión da API.

## Alternativas descartadas
- **Contrato primeiro (YAML á man):** máis lento; o código manda e o diff vixía.
- **Versionar o cliente xerado:** diffs enormes e risco de editalo á man.
- **openapi-generator:** require Java no build da web.
- **Comprobar con `git diff`:** non protexe antes do primeiro commit e duplica o IT.
- **`tsc --noEmit` como proba de compilación:** non revisa os modelos HTML.
