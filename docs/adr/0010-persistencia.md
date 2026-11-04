<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0010 · Persistencia: Flyway, UUIDv7, UTC e convencións SQL

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo)
- **Revisada 2026-10-25 (dominio 1):** horas de calendario como data + minutos ([ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md) §8) e valores dos campos personalizados fóra do `jsonb` ([ADR-0011](0011-campos-personalizados.md)).

## Contexto
Liquibase é FSL desde a versión 5, non libre. Expor `java.sql.Timestamp` na API é un erro habitual. As reservas dependen de horas exactas con cambio de hora. O dominio é puro e a entidade JPA vai aparte (ADR-0005): hai que mapear sen perder o bloqueo optimista.

## Decisión
- **Flyway Community 12.4.0 (Apache-2.0)** con `spring-boot-starter-flyway` + `flyway-database-postgresql` (Boot 4 non migra só con `flyway-core`). SQL en `api/src/main/resources/db/migration/`.
  - Nome: `V{AAAAMMDDHHMM}__{modulo}_{que}.sql`. Unha migración aplicada non se edita. `outOfOrder=false`.
  - **Rexistro de aplicadas:** `db/migrations.lock` garda o SHA-256 de cada `V*.sql` e fai de historial dun concello xa instalado. `MigrationLockTest` (e `MigrationsIT`) fallan se unha rexistrada cambia ou desaparece, ou se hai unha sen rexistrar; unha base nova sempre migra ben, así que sen isto só o vería `flyway validate` no despregue en produción. `scripts/migracions.sh fixar` só engade liñas (`novo-modulo.sh` chámao); `scripts/migracions.sh base <ref>` falla se cambiou unha liña que xa estaba en `<ref>` (rama base ou última etiqueta), para que tampouco se poida «arranxar» editando o rexistro.
  - Primeira migración, `V…__platform_base.sql`: `btree_gist` e a táboa `event_publication` co DDL oficial de Modulith 2.1.1 para PostgreSQL. A infraestrutura da plataforma vai en migracións `platform_*` propias, nunca dentro da dun módulo (ARQ-8).
  - Datos de demo (CC0) en `db/demo/`, só co perfil `demo`.
  - `MigrationsIT`: base baleira → todas aplicadas, `flyway_schema_history` non baleira e `validate` sen pendentes.
- **Táboas:** singular, `snake_case`, prefixo do módulo se o nome é xenérico. Columnas comúns: `id uuid primary key`, `municipality_id uuid not null`, `created_at timestamptz not null`, `updated_at timestamptz not null`, `version bigint not null`.
- **IDs UUIDv7 en Java:** `shared.domain.Ids.newV7()` escrito a man (~15 liñas, con test de orde e de bits de versión e variante; o JDK 25 non o trae).
- **Mapeo dominio ↔ JPA:** a entidade leva `@Version Long version` (null = nova) e implementa `Persistable<UUID>` (A18). O adaptador **carga a entidade e aplica os cambios** (`entity.apply(domain)`), nunca fai `merge` dunha entidade reconstruída. O dominio garda a versión; a API exponaa como `ETag` / `If-Match` (412).
- **Tempo:** `timestamptz` en UTC (`Instant`). Zona do concello (`Europe/Madrid` por defecto) só para calcular e mostrar. Intervalos `tstzrange` `[inicio, fin)`. `java.time.Clock` inxectado. Excepción acoutada: as regras de calendario (semana, excepcións, bloqueos) van na API como data + minuto do día en Europe/Madrid, e o servidor calcula o intervalo real (os bloqueos gardan as dúas formas: `date` e minutos, e `starts_at`/`ends_at` con `period tstzrange`; ADR-0022 §8).
- **`jsonb`** só para textos traducidos e tema (e as etiquetas dos campos personalizados; os valores van en táboas tipadas). `hibernate.type.json_format_mapper` cun `Jackson3JsonFormatMapper` propio (`PersistenceConfig`; hai Jackson 2 e 3 no classpath); `@JdbcTypeCode(SqlTypes.JSON)`; números como `BigDecimal` (`USE_BIG_DECIMAL_FOR_FLOATS`).
- **Eventos de Modulith por JDBC** (`spring-modulith-starter-jdbc`), `spring.modulith.events.jdbc.schema-initialization.enabled=false` (crea a táboa Flyway) e `republish-outstanding-events-on-restart=true`.
- **Hibernate:** `ddl-auto=validate`, `open-in-view=false`.

## Consecuencias
- Migracións SQL portables: se Flyway cambiase de licenza, lévanse a outra ferramenta.
- `ddl-auto=validate` detecta no arranque calquera desfase entre entidade e táboa.
- Un `save` non fai `SELECT` de máis e dúas edicións simultáneas dan 412, non unha perda silenciosa.

## Alternativas descartadas
- **Liquibase:** FSL desde a 5.0, non OSI.
- **`uuidv7()` de PostgreSQL 18:** o dominio necesitaría a base para ter ID e os tests de dominio non serían puros.
- **Librería externa de UUIDv7:** unha dependencia para 15 liñas.
- **IDs secuenciais:** revelan volume e complican importar datos.
- **Rexistro de eventos JPA de Modulith:** entidade sen `@TenantId` e sesións sen concello en fíos asíncronos.
- **EAV ou táboas por concello:** ver ADR-0011.
