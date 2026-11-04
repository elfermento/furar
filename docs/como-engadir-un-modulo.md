<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Como engadir un módulo

Todos os módulos fanse igual. O de referencia é `municipality` (con `customfields` aplicado): cando dubides, cópiao. Tempo obxectivo do esqueleto: **10 minutos** (medido: xerar 2 s por variante; xerar as dúas, pasar as gardas, os tests web e a lista do E2E e desfacer 59 s). O esqueleto xa é un CRUD editable de punta a punta (API e web: lista, alta, edición con `If-Match`, E2E con axe) e, se o pides, con campos personalizados tamén no formulario; o resto é negocio.

Exemplo: módulo `facility` (instalación), agregado `Facility`, para que os nomes se recoñezan; como `facility` xa existe, o comando de abaixo xera un módulo ficticio, `space`. Substitúe `facility`/`Facility`/`facilities` polo teu.

## 0. Antes de escribir código

1. O termo está en [`glosario.md`](glosario.md) (gl, es, en). Se non, engádeo.
2. Se o módulo trae unha decisión nova (non cuberta polas ADR), escribe a ADR primeiro ([ADR-0001](adr/0001-rexistro-de-decisions.md)).
3. Decide as dependencias permitidas (só a API pública doutros módulos), se usa campos personalizados e que permisos novos precisa (o seu `enum <Nome>Permission`, ADR-0005 ARQ3-2).

## 1. Xerar o esqueleto

```sh
source scripts/entorno.sh
scripts/novo-modulo.sh --custom-fields SPACE space Space spaces espazos
#                      (opcional: target)     módulo Nome  plural ruta web (galego)
scripts/novo-modulo.sh --remove space    # desfaino
```

`--custom-fields TARGET` (o código do destino en maiúsculas: `SPACE`, `RESIDENT_PROFILE`, `ORGANIZATION` ou outro novo; o módulo rexístrao cun `CustomFieldTargetProvider` xerado, e cada destino ten un só dono: o script rexeita un que xa rexistre outro módulo, como hoxe `MUNICIPALITY`, `FACILITY` e `BOOKING`) engade o cableado completo de [ADR-0011](adr/0011-campos-personalizados.md): `CustomFieldValues` no dominio, validación no caso de uso (`CustomFieldValidator`, coa audiencia de quen escribe), os valores nas táboas tipadas de `customfields` (`CustomFieldValueStore.replace` desde a persistencia, na mesma transacción ca a fila; `loadAll` unha vez por lista), proxección ao ler (`project` no detalle, `projectAll` nas listas), `customFields` nos DTO e `FacilityCustomFieldsIT` (validación, visibilidade e outro concello). A táboa do módulo non leva columna de campos: nin `jsonb` nin índice GIN. Sen a opción, nada diso.

Copia [`templates/module/`](../templates/module/) substituíndo os marcadores, formata o xerado (google-java-format e prettier) e deixa un módulo que **xa pasa todas as gardas** (D45, `scripts/probar-plantilla.sh`). Crea:

```
api/src/main/java/gal/furar/facility/
  package-info.java                         @ApplicationModule(displayName = "Facility", allowedDependencies = {"shared"} ou {"shared", "customfields"})
  FacilityCreated.java                      evento público (record con municipalityId)
  FacilityQueries.java, FacilitySummary.java API pública síncrona para outros módulos (records, A19)
  domain/Facility.java                      agregado inmutable: invariantes no construtor, create/rehydrate/update, version
  domain/FacilityId.java                    record(UUID value), UUID v7
  domain/FacilityError.java                 enum implements ErrorCode: facility.not-found, facility.name-invalid
  application/port/in/Create…, Get…, ListFacilities…, UpdateFacilityUseCase.java (+ Command), FacilityView.java
  application/port/out/FacilityRepository.java          findById, findAllById, findPage, insert, update
  application/service/FacilityService.java  @Transactional; @RequiresPermission(FacilityPermission.Code.MANAGE) ou @PublicUseCase en cada método; publica FacilityCreated
  application/service/FacilityQueryService.java         implementa FacilityQueries
  adapter/in/web/FacilityController.java    /api/v1/municipalities/{slug}/facilities: GET lista e detalle (@PublicEndpoint), POST alta, PUT con If-Match; ETag
  adapter/in/web/FacilityWebMapper.java, dto/CreateFacilityRequest.java, dto/UpdateFacilityRequest.java, dto/FacilityResponse.java
  adapter/out/persistence/FacilityEntity.java          @Entity con @TenantId, @Version, Persistable<UUID>
  adapter/out/persistence/FacilityJpaRepository.java, FacilityPersistenceMapper.java
  adapter/out/persistence/FacilityPersistenceAdapter.java  extends TenantScopedPersistenceAdapter
api/src/main/resources/db/migration/V{AAAAMMDDHHMM}__facility_create.sql   municipality_id + FK + índice + RLS e política facility_tenant
api/src/test/java/gal/furar/facility/
  domain/FacilityTest.java                  invariantes
  application/FacilityServiceTest.java      con InMemoryFacilityRepository (separado por concello)
  FacilityIT.java                           extends AbstractIT: feliz, edición (200 e 412 con If-Match vello), 400 problem+json, outro concello non ve nin le, 401 e 403
  FacilityCustomFieldsIT.java               (con --custom-fields) valida, oculta os campos STAFF ao anónimo e ignora definicións doutro concello
web/src/app/features/facility/
  facility.routes.ts                        lazy: lista (pública), novo e :id (managerGuard)
  facility-list.page.ts / .html / .spec.ts  lista accesible (OnPush, signals, erros con <furar-problem-alert>); ligazóns de alta e edición para quen xestiona
  facility-edit.page.ts / .html / .spec.ts  alta e edición: formulario reactivo, erros no seu campo (aria-describedby) e foco, If-Match coa versión lida e 412 → recargar;
                                            (con --custom-fields) <furar-custom-fields-form> coas definicións de FACILITY e erros da API en customFields.<código>
  facility.store.ts                         única porta ao cliente xerado (list, get, create, update e, con campos, definitions)
web/e2e/facility.e2e.ts                     lista pública e alta/edición só con teclado, expectNoA11yViolations e 320 px (proxectos gl, es e gl-mobile)
```

E insire, en ficheiros existentes (**cambia antes de `facility`:** con [ADR-0005, ARQ3-2](adr/0005-monolito-modular-hexagonal.md#ficheiros-compartidos-e-traballo-en-paralelo-arq3-2-arq4-2) permisos, mensaxes e textos van en ficheiros do propio módulo e só queda unha liña de rexistro por ficheiro central):

| Ficheiro | Que |
|---|---|
| `web/src/i18n/gl.json`, `es.json` | `titles.facility`, `facility.*`, `errors.facility.*` |
| `web/src/app/app.routes.ts` | `/:concello/instalacions`, enriba da marca `// novo-modulo:` |

Rexistra a migración nova en `db/migrations.lock` (`scripts/migracions.sh fixar`; ADR-0010). Garda copia deses ficheiros (e de `api/openapi.json` e `migrations.lock`) en `.novo-modulo/facility/` (ignorado). `scripts/novo-modulo.sh --remove facility` borra o xerado, restaura as copias e limpa as saídas compiladas de `api/target` (Maven non borra clases nin migracións de fontes que xa non existen). Se a xeración falla a medias, desfaise soa.

Detrás da xeración (o script lémbrao):

1. Rexenerar o contrato (paso 6) para que a web vexa os endpoints novos.
2. Decidir en `FacilityPermission` que roles teñen `facility.manage` (o xerado: só `MANAGER`; a plataforma non ve datos dos concellos, [ADR-0007](adr/0007-seguridade-pechada.md)) e, se lle dás outro rol, engadir a liña a `RolePermissionsTest.expectedMatrix`.
3. Glosario e textos reais en gl/es (a plantilla pon o plural en inglés como título).

Límites coñecidos da plantilla: só un campo núcleo (`name`); o E2E xerado entra como `xestor-naron` (o xestor do concello ten `FACILITY_MANAGE` desde o xerado) e D45 compílao e lístao (`tsc`, `playwright test --list`), pero executalo pide o perfil `full` co módulo despregado (D34). O nome da táboa sae do `Nome` en snake_case: evita palabras reservadas de SQL (`user`, `order`).

Outro módulo que precise datos deste **na mesma transacción** usa `FacilityQueries` (amplía `FacilitySummary` co que faga falta, sempre records e tipos de Java); se só ten que reaccionar a un cambio, escoita `FacilityCreated` ([ADR-0005](adr/0005-monolito-modular-hexagonal.md), «evento ou consulta»). Os módulos de apoio (avisos, tarifas, indicadores) usan a mesma forma: borra as accións que non precises (ADR-0005, ARQ2-5). Se tes que bloquear ou referenciar filas doutro módulo, só pola súa consulta pública e cunha FK composta cara á súa raíz (ADR-0012, «Fronteira»).

## 2. Encher, en orde

1. **Migración** (`V…__facility_create.sql`, xa xerada; complétaa): `id uuid primary key`, `municipality_id uuid not null references municipality(id)`, `unique (municipality_id, id)`, **FK compostas** cara a outras táboas do concello (`foreign key (municipality_id, space_id) references space (municipality_id, id)`; SchemaTenancyIT falla se non), columnas núcleo tipadas, `created_at`, `updated_at`, `version`; índice `(municipality_id, …)`; RLS e a política `facility_tenant` (xa xeradas). Os campos personalizados non van nesta táboa, senón nas de `customfields`. Restricións de negocio na base (`check`, `unique`, `exclude`).
2. **Dominio:** invariantes no construtor/fábrica (`Facility.create(...)`), comportamento en métodos con nome de negocio (`close()`, `rename()`), erros con `FacilityError`. Test de dominio primeiro.
3. **Casos de uso:** un `…UseCase` por acción, con `@RequiresPermission(FacilityPermission.Code.…)` ou `@PublicUseCase`. Validan campos personalizados con `CustomFieldValidator` (o destino do módulo) e pasan ao dominio o `CustomFieldValues` resultante; gardan polo porto `out`; publican eventos (sempre con `municipalityId`). Test con repositorio en memoria.
4. **Persistencia:** entidade con `@TenantId UUID municipalityId`, `@Version Long version`, `Persistable<UUID>`. O adaptador carga a entidade e aplica os cambios (`entity.apply(domain)`), nunca `merge`; garda os campos personalizados con `CustomFieldValueStore.replace` na mesma transacción e lé os dunha lista cun só `loadAll` (unha consulta por páxina, non por fila). `ddl-auto=validate` debe arrincar. O caso de uso proxéctaos para quen mira con `CustomFieldProjector` (`project`, `projectAll`).
   - **SQL nativo** (`@NativeSql(scope = MUNICIPALITY)`): cada `select`/`update`/`delete` con `and municipality_id = :m`, cada `insert` con `municipality_id` na lista de columnas, o predicado fóra de parénteses e sen `or`, `union` nin `case` no nivel 0 (A8b). O seu `<Clase>TenantIT` estende `AbstractTenantIT` (A8b): implementa `seed(concello)` e `visibleTo(concello)`, que chama á clase; a proba fixa crea dous concellos e esixe que cada un vexa só o seu. Ten que correr con tests > 0 (D11). Para cada método máis, engade casos que sementen por JDBC (`jdbc.sql("insert … (municipality_id, …)")`) filas en dous concellos, con ids e códigos distintos, e comproba para cada método da clase, dentro de `TenantContext.runAs(A)`, que non le nin cambia nada de B (`containsExactlyInAnyOrder` dos ids de A; contar as filas de B por JDBC despois de escribir). O SQL vai sempre enteiro como literal ou constante `static final` (A8b falla se se constrúe en execución, T11). A8b só mira o texto do SQL; este IT e Row Level Security son a proba de verdade. **Condición de entrada:** a primeira táboa con SQL nativo `MUNICIPALITY` non entra sen RLS activa e sen `TenantRlsSpringIT` verde (o concello fíxao `RowLevelSecurityJpaDialect` ao comezar cada transacción de `TenantJpaTransactionManager`; os métodos do adaptador só se chaman dentro dunha transacción, [ADR-0006](adr/0006-multiconcello.md)).
   - **Portabilidade** ([ADR-0018](adr/0018-portabilidade.md)): a plantilla xa trae `FacilityDataSection` (sección `facility` do ficheiro `furar-export`, coa mesma `FacilityResponse`), `ExportFacilitiesUseCase`/`ImportFacilitiesUseCase`, `Facility.restore(...)` e `findAll()`. Cada columna núcleo nova vai tamén á resposta e a `RestoreFacilityCommand`; se a sección depende doutra (p. ex. `booking` de `facility`), sobe o seu `@Order`. Sen sección, A21 fai fallar o build.
5. **Web (API):** controlador fino: DTO ↔ comando, chama ao caso de uso, DTO de resposta con `ETag`; modificacións con `If-Match`. Só os públicos levan `@PublicEndpoint`; o permiso vive no caso de uso. Nunca devolve o dominio nin a entidade.
6. **Contrato:** `(cd api && ./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiContractIT -Dfurar.openapi.write=true)` para rexenerar `api/openapi.json` e revisa o diff.
7. **Web (Angular):** `npm run api:generate`; a lista e o formulario xa están xerados: engade cada campo núcleo novo ao `FormGroup` e ao modelo de `facility-edit.page` co patrón de `name` (`<furar-field>`, `aria-invalid`, `describedBy`) e un caso no seu spec. Textos só en `gl.json`/`es.json` (tampouco en propiedades TS dos compoñentes: D24, T10).
8. **E2E:** `web/e2e/facility.e2e.ts` xa cobre lista, alta e edición con `expectNoA11yViolations(page)` en cada pantalla; amplíao co fluxo de negocio e, se no paso 2 dás o permiso a outro rol, proba tamén con el.

## 3. Comprobar

Os comandos de [`definicion-de-feito.md`](definicion-de-feito.md) para «Módulo novo». Se cambias a plantilla, `scripts/probar-plantilla.sh` (D45) xera `probe`, pásalle as gardas e desfaino. Mínimo antes do PR:

```sh
source scripts/entorno.sh
(cd api && ./mvnw -B spotless:apply && ./mvnw -B -Pcoverage verify)
(cd web && npm run api:generate && npm run lint -- --max-warnings 0 && npm run i18n:check && npm test -- --watch=false --coverage && npm run build)
```

## Lista de revisión do PR

- [ ] Termo no glosario; ADR se hai decisión nova.
- [ ] Toda táboa nova con `municipality_id`, `unique (municipality_id, id)` e FK compostas (SchemaTenancyIT verde) e entidade con `@TenantId`.
- [ ] IT que proba que outro concello non ve nin cambia os datos (datos do outro concello sementados por JDBC: unha fuga ten que fallar nunha aserción). Antes do PR, `scripts/rapido.sh --tenancy api` verde (D14 sen esperar á CI).
- [ ] SQL nativo só en `@NativeSql(scope = …)`, con literais; se é `MUNICIPALITY`, co seu `<Clase>TenantIT` (A8b, D11), RLS activa nas súas táboas (`NativeSqlRowLevelSecurityIT`) e chamado só dentro dunha transacción (ADR-0006, ARQ2-1).
- [ ] Sección de portabilidade completa: toda columna núcleo nova sae na exportación e volve na importación (A21, ADR-0018).
- [ ] Cada caso de uso con `@RequiresPermission`/`@PublicUseCase`; permisos no `enum` do módulo, cos seus roles; os endpoints públicos xustificados no PR.
- [ ] Accións sensibles (descarga masiva, cambios de roles, borrado de datos persoais) rexistradas con `AuditLog`, boas e fallidas (ADR-0020).
- [ ] Erros con `ErrorCode` e texto en `messages/<modulo>_gl.properties`, `messages/<modulo>_es.properties`, `gl.json`, `es.json`.
- [ ] `api/openapi.json` actualizado e revisado.
- [ ] Campos personalizados validados no caso de uso (se aplica) e marcados os datos persoais. Se a entidade ten datos dunha persoa (perfil, reserva, club): ao anonimizala, borra tamén os valores marcados con `erasePersonalData` e, ao borrala, todos con `deleteAll` (IT que o comprobe; `personalData` para o acceso e a portabilidade; [`datos-persoais.md`](datos-persoais.md)). Se escribe alguén sen `STAFF_DATA_VIEW` (veciño, club), IT por HTTP: escribir un campo que non ve → 400 `customfield.unknown-field`.
- [ ] Sen texto solto en modelos; compoñentes `OnPush` < 200 liñas de HTML e < 250 de TS; a feature non importa outras features.
- [ ] E2E con axe en gl, es e móbil; formulario usable só con teclado.
- [ ] Commits Conventional Commits en inglés con `Signed-off-by`.

## Reservas e ocupación

O módulo `booking` non usa só a plantilla: a ocupación segue [ADR-0012](adr/0012-ocupacion-exclusiva-e-prazas.md) (táboas `booking_occupancy` e `use_slot`, restricións de exclusión, `CHECK` de prazas e unha praza por persoa na táboa `booking`) e as súas FK a `facility_use` e `facility_use_part` están na lista pechada `CrossModuleForeignKeys`. A persistencia é SQL nativo (`BookingSql`, `@NativeSql(MUNICIPALITY)` co seu `BookingSqlTenantIT`), sen entidades JPA: `tstzrange`, exclusións, `on conflict` e `for update`. O modelo SQL próbao `OccupancyModelIT` (D19) sobre as migracións de produción; os casos de uso da persoa levan `@PersonUseCase` ([ADR-0025](adr/0025-reservas-fase-1.md)).
