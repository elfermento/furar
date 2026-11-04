<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0005 · Monolito modular hexagonal e regras que rompen o build

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo)
- **Valida o mantedor:** dominio puro con entidades JPA aparte.
- **Revisada 2026-10-22:** A19, A20, consultas síncronas, unha soa forma de módulo, FK entre módulos e ficheiros por módulo (ARQ-3, ARQ-8, ARQ2-5, ARQ2-2, ARQ2-6).
- **Revisada 2026-10-23:** módulos que non editan ficheiros centrais, A20 e A22 (ARQ3-2, ARQ3-4, ARQ4-2, QA Q13).
- **Revisada 2026-10-25 (dominio 1, [ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md)):** FK cara a táboas publicadas e SPI dun só bean.
- **Revisada 2026-10-28 (dominio 3, [ADR-0026](0026-indicadores-fase-1.md)):** indicadores dun concello por consulta síncrona.

## Contexto
Once módulos en catro semanas, feitos en paralelo. Un problema habitual é que o ArchUnit pase sen detectar fugas: `MultipartFile` en aplicación, mapeadores que chaman a outros contextos, `@Lazy` para tapar ciclos, dominio anémico con `@Setter` e `java.sql.Timestamp` devolto pola API.

## Decisión
**Un só programa** (un despregue, unha base) dividido en **módulos de Spring Modulith** e, dentro de cada un, **portos e adaptadores**. Paquete base `gal.furar`.

| Paquete | Contén | Pode depender de |
|---|---|---|
| `gal.furar.<m>` (raíz) | API pública: consultas síncronas `XxxQueries` + records (`XxxSummary`), servizos de colaboración (`CustomFieldValidator`), eventos (`MunicipalityCreated`) | `shared`; nunca expón tipos de `domain`, `application` nin `adapter` (A19) |
| `<m>.domain` | Agregados, valores (records), eventos, erros | `java.*`, `org.jspecify`, `shared.domain` |
| `<m>.application.port.in` | `XxxUseCase` (interface) + `XxxCommand`/`XxxQuery` (records) | `domain`, `shared.domain` |
| `<m>.application.port.out` | `XxxRepository`, `XxxPort` (interfaces) | `domain` |
| `<m>.application.service` | `XxxService implements XxxUseCase`; `XxxQueryService implements XxxQueries` | portos, `domain`, API pública doutros módulos, `shared.security` (anotacións), `stereotype`/`transaction`/`ApplicationEventPublisher` de Spring |
| `<m>.adapter.in.web` | `XxxController`, `dto/` (records), `XxxWebMapper` | `port.in`, `domain`, Spring Web |
| `<m>.adapter.out.persistence` | `XxxEntity`, `XxxJpaRepository`, `XxxPersistenceAdapter`, `XxxPersistenceMapper` | `port.out`, `domain`, JPA |

`gal.furar.shared` é módulo `Type.OPEN`: `domain` (IDs, `CustomFieldValues`, `ErrorCode`, reloxo), `tenancy`, `security`, `web` (problemas, filtros). Os seus subpaquetes `config` son internos (A20).

### Colaboración entre módulos: evento ou consulta (ARQ-3)
- **Evento** (`XxxCreated`, escoitado despois do commit, nun fío propio e no concello do evento; `notification` con `@Async` + `@TransactionalEventListener`, [ADR-0025](0025-reservas-fase-1.md) punto 2) cando o outro módulo **reacciona** a un cambio e pode facelo despois (avisos, conservación, indicadores entre concellos). Os indicadores dun concello son unha consulta: `indicators` le cando o xestor abre o panel, a través de `BookingStatistics` e `FacilityQueries`, sen táboas propias (ADR-0026).
- **Consulta síncrona** (`XxxQueries` na raíz) cando o outro módulo **precisa un dato para decidir na mesma transacción** (booking pide a `facility` o uso, os seus tramos abertos e se hai un uso que bloquea; a `rules`, a norma aplicable). Devolve records da raíz (`FacilityUseSnapshot(id, rentalMode, capacity, partIds, slotMinutes)`), sempre dentro do concello actual, sen permisos propios (xa os comprobou o caso de uso que chama) e nunca por HTTP. Implementaa `XxxQueryService` en `application.service`. A plantilla xéraa (`XxxQueries` + `XxxSummary`).
- **Servizo de colaboración** (`CustomFieldValidator`, `CustomFieldProjector`) cando un módulo transversal fai traballo para o dono dentro do seu caso de uso.
- Nunca: ler as táboas doutro módulo (salvo a FK composta cara á súa raíz ou a unha táboa publicada, ver «Excepcións»), nin chamar ao seu `UseCase` desde un caso de uso propio (as comprobacións de permiso duplicaríanse).

### Unha soa forma de módulo (ARQ2-5)
Todos os módulos, tamén os de apoio (avisos, tarifas, indicadores), saen de `scripts/novo-modulo.sh` coa mesma forma: un `…UseCase` por acción e agregado no dominio. Se un módulo non precisa unha acción xerada, bórraa. As lecturas de listado poden devolver unha proxección desde o adaptador de persistencia (xa permitido por A1–A22) sen variante aparte. Motivo: unha regra documentada que a plantilla non xera acaba en tres formas distintas; o custo dunhas interfaces de máis é menor.

### Excepcións entre módulos (ARQ2-2)
Única excepción a «nunca ler as táboas doutro módulo»: FK compostas cara á **táboa raíz** doutro módulo (`municipality`, `facility`) **ou cara a unha táboa publicada** que o dono declara como tal porque outros módulos teñen que apuntar a ela (`facility_use` e `facility_use_part`, porque a reserva apunta a un uso e a unha parte; ADR-0012). As dúas cousas van nunha **lista pechada**, `CrossModuleForeignKeys` (`shared.tenancy`), que `SchemaTenancyIT` le: unha FK cara a unha táboa doutro módulo que non estea na lista fai fallar o build. Unha táboa publicada só se arquiva, nunca se borra (as FK seguen válidas), e o seu esquema cambia como unha API: con migración e aviso aos módulos que apuntan a ela. Unha FK non dá permiso para ler: o bloqueo e as lecturas fanse sempre pola consulta pública do dono (`FacilityQueries.lockFacility`), nunca con SQL propio.

### SPI cara a un módulo posterior: un bean, implementación por defecto
Cando o dono precisa que un módulo que depende del decida algo na mesma transacción (`facility` pregunta a `booking` que reservas afecta un bloqueo), o dono declara a interface na súa raíz (`BookingImpact`) e o outro implementaa (booking → facility, sen ciclo M1). Regras:
- **Un só bean.** O servizo do dono recibe `ObjectProvider<BookingImpact>` e usa `getIfAvailable(NoBookingImpact::new)`: sen ningún bean, a implementación por defecto do dono (que non é bean); con dous, Spring falla ao arrancar (`NoUniqueBeanDefinitionException`). Así o dono arrinca só (`@ApplicationModuleTest(STANDALONE)`) e nunca hai dúas opinións.
- Non se usa `@ConditionalOnMissingBean` nunha clase do propio programa: Spring Boot só o garante en autoconfiguración, porque depende da orde en que se rexistran os beans.
- A SPI só cambia estado na transacción; o que sae fóra (correo, web) vai por evento despois do commit.

### Ficheiros compartidos e traballo en paralelo (ARQ3-2, ARQ4-2)
Un módulo novo **non edita ficheiros centrais da API**: aporta os seus e o resto atópao no classpath.
- **Permisos por módulo.** `shared.security` só ten a interface `PermissionKey` (código estable, `facility.manage`, e os roles que o teñen). Cada módulo declara `enum FacilityPermission implements PermissionKey` no seu paquete raíz, e cada constante leva a súa fila de roles; os transversais van en `SharedPermission`, `PortabilityPermission` e `AuditPermission`. `RolePermissions` atopa os `enum` no classpath, como `ErrorCatalog` fai cos `ErrorCode`, e un código repetido impide arrincar. `@RequiresPermission` recibe o código, porque unha anotación non admite un atributo de tipo interface; para non escribilo a man, cada `enum` ten constantes `Code` (`@RequiresPermission(CustomFieldPermission.Code.MANAGE)`). Un código sen rexistrar nega sempre, e `RolePermissionsTest` falla se algún `@RequiresPermission` nomea un código que non existe ou se un permiso dá algo a un rol do concello sen estar en `expectedMatrix`.
- **Destinos de campos personalizados como valor.** `CustomFieldTarget` é un `record` cun código validado (`^[A-Z][A-Z0-9_]{1,39}$`: `MUNICIPALITY`, `FACILITY`, `BOOKING`), non un `enum` en `shared.domain` cos módulos futuros. Cada módulo que os usa rexistra o seu cun bean `CustomFieldTargetProvider`, que dá o destino e os seus campos núcleo (hoxe `MunicipalityCustomFieldTarget`, `FacilityCustomFieldTarget` e `BookingCustomFieldTarget`). Un destino sen rexistrar ou mal formado é `validation.failed` no campo `target`. A base só comproba o formato (`V202610231130`); a validez contra os rexistrados faise no caso de uso.
- **Mensaxes da API por módulo:** `messages/<modulo>_gl.properties` e `_es`, sen lista de basenames: `WebConfig` cárgaos todos desde o classpath, e `ErrorCatalogTest` percorre os mesmos. Os comúns (e `municipality.not-found`, que é de `shared`) van en `messages_*.properties`.
- **Web:** os textos van nun ficheiro por idioma (`web/src/i18n/gl.json` e `es.json`) e as rutas en `app.routes.ts`, con `loadChildren` cara ao `<feature>.routes.ts` das *features* con varias páxinas (`customfields`, `facility`, `municipality`) e `loadComponent` no resto. Estes son os únicos ficheiros centrais que toca un módulo novo.
- **Plantilla:** xera `<Nome>Permission`, `messages/<modulo>_*.properties` e, con campos, `<Nome>CustomFieldTarget`. `novo-modulo.sh` engade as claves do módulo a `gl.json` e `es.json` e a súa ruta a `app.routes.ts`, e garda copia deses ficheiros, de `api/openapi.json` e de `db/migrations.lock` para poder desfacelo.
- **Garda:** `probar-plantilla.sh` (D45) xera dous módulos de proba (con e sen campos personalizados), pásalles as gardas, os seus tests, o lint, os textos e o build, desfaios con `novo-modulo.sh --remove` e esixe que a árbore quede idéntica.

Dependencias de módulo fixadas en cada `package-info.java` e comprobadas por `ModularityTest`: `customfields = {"shared"}` (o concello tómao de `TenantContext`, nunca de `municipality`); `municipality` e `facility = {"shared", "customfields"}`; `booking = {"shared", "facility", "municipality", "customfields"}`; `notification = {"shared", "booking", "facility", "municipality"}`; `indicators = {"shared", "booking", "facility"}`; `staff = {"shared", "notification", "booking", "municipality"}`.

### Regras (fallan o build)
Modulith (`ModularityTest`, `ApplicationModules.of(FurarApplication.class).verify()`):
- M1 Sen ciclos entre módulos.
- M2 Outro módulo só usa o paquete raíz (os subpaquetes son internos).
- M3 Cada `package-info.java` declara `allowedDependencies` explícitas; `ModularityTest` comproba tamén a lista esperada de cada módulo (arriba).
- M4 Documentación xerada (`Documenter`) en `api/target/modulith-docs`.

ArchUnit (`ArchitectureTest`, sobre `gal.furar..`, só produción). Cada regra é unha constante `ArchRule` con `because("A<n> …")`:
- A1 `..domain..` non depende de `org.springframework..`, `jakarta..`, `org.hibernate..`, `tools.jackson..`, `com.fasterxml..`, `..application..`, `..adapter..`.
- A2 `..application..` non depende de `..adapter..`, `org.springframework.web..`, `org.springframework.data..`, `jakarta.servlet..`, `jakarta.persistence..`, `org.hibernate..`, `tools.jackson..`, `org.springframework.web.multipart..`.
- A3 `..adapter.in..` e `..adapter.out..` non dependen entre si.
- A4 `..adapter.in.web..` non depende de `..application.service..` nin de `..port.out..`.
- A5 Os métodos de `@RestController` non aceptan nin devolven (xenéricos incluídos) tipos de `..domain..` nin de `..persistence..`.
- A6 `@Entity`, `@Embeddable` e `Repository` de Spring Data só en `..adapter.out.persistence..`.
- A7 Toda `@Entity` ten un campo `@TenantId`, salvo as de `TenancyAllowlist` (`MunicipalityEntity` e os catálogos de plataforma, ADR-0006).
- A7b Todo `*PersistenceAdapter` que usa unha entidade con `@TenantId` herda de `TenantScopedPersistenceAdapter` (ADR-0006).
- A8 Todo acceso á base sen Hibernate só en clases `@NativeSql` de `..adapter.out.persistence..`: calquera tipo de `java.sql`, `javax.sql` (`DataSource`) e `org.springframework.jdbc`, o SQL nativo de JPA/Hibernate (`createNativeQuery`, `createNativeMutationQuery`, `doWork`, procedementos) e `@Query(nativeQuery = true)`. É por paquetes, non unha lista de clases: a lista anterior deixaba pasar `DataSource.getConnection().createStatement()` (QA2). Dentro de `@NativeSql`, A8b só admite `JdbcClient.sql` e `EntityManager.createNativeQuery` cun literal; `Connection`/`Statement` fallan porque A8b non pode ler o seu SQL (ler un `ResultSet` nun `RowMapper` si se permite).
- A8b Toda clase `@NativeSql` cumpre o alcance declarado: `MUNICIPALITY` con `municipality_id = :param` no `WHERE` principal de cada sentenza e `<Clase>TenantIT` que estende `AbstractTenantIT`; `GLOBAL` con motivo e só táboas de `TenancyAllowlist` (ADR-0006).
- A9 Prohibidos: `java.sql.Timestamp`, `java.sql.Date`, `java.util.Date`, `java.util.Calendar`, `lombok..`, `@Lazy`, `@Autowired` en campos, `System.out`/`System.err`, `printStackTrace`; e A9t, `org.junit.jupiter.api.Disabled` (tamén en tests).
- A10 `LocalDateTime` prohibido en DTO e entidades.
- A11 `MultipartFile` só en `..adapter.in.web..`.
- A12 Nomes: `*Controller` en `adapter.in.web`; `*UseCase` interface en `port.in`; `*Service` en `application.service`; `*Entity` en `persistence`; DTO terminan en `Request`/`Response`.
- A13 Campos do dominio `final` ou tipo `record`; ningún `set*` público en `..domain..`.
- A14 Sen ciclos entre paquetes dentro dun módulo.
- A15 Cada método público dun `*Service` que implementa un `*UseCase` leva exactamente unha de `@RequiresPermission`, `@PublicUseCase` ou `@PersonUseCase` (ADR-0007, ADR-0025).
- A16 `CustomFieldValues.trusted(..)` só se chama desde `gal.furar.customfields..`; `CustomFieldValues.fromStorage(..)` só desde `..adapter.out.persistence..` (ADR-0011).
- A17 `TenantContext.runAsPlatform(..)` só desde clases `@PlatformTask`.
- A18 As entidades con `@Version` implementan `Persistable<UUID>` (ADR-0010).
- A19 Os métodos públicos dos tipos da raíz dun módulo non aceptan nin devolven tipos de `..domain..`, `..application..` nin `..adapter..` (só records da raíz, `shared.domain` e Java): outro módulo depende dun contrato estable, non do modelo interno (ARQ-3).
- A20 Ningún módulo depende de `gal.furar.shared..config..` nin de `gal.furar.shared.adapter..`, nin das clases de plataforma de `shared` (`TenantFilter`, `RequestIdFilter`, `ProblemExceptionHandler`, `ErrorEndpoint`, `WebConfig`, `OpenApiConfig`, `TenantListenerInterceptor`, `TenancyConfig`, `TenantIdentifierResolver`), tampouco desde a web; o resto de `gal.furar.shared.web..` só desde `..adapter.in.web..` (ARQ-8, ARQ3-4). `shared` é `OPEN`, pero a súa infraestrutura non é API. Dividilo en `shared.kernel` e `platform` descártase: movería ~40 clases e os imports de todo o código para obter o mesmo que esta regra.
- A21 Todo módulo con entidades `@TenantId` ten unha `MunicipalityDataSection`: os datos do concello son portables (ADR-0018).
- A22 Só anotacións de persistencia dunha lista pechada (`ALLOWED_PERSISTENCE_ANNOTATIONS`); calquera outra de Hibernate, JPA ou Spring Data JPA falla (`@Formula`, `@Subselect`, `@SQLRestriction`, `@NativeQuery`, `@Procedure`...): `@TenantId` non filtra ese SQL e A8b non o le (QA Q13, QN2). Se fai falta, consulta explícita nunha clase `@NativeSql`.

**As regras próbanse a si mesmas:** `ArchitectureRulesSelfTest` aplica **as mesmas constantes** `ArchRule` a clases de exemplo que as violan (`api/src/test/java/archviolations/`, unha por regra) e esixe que fallen e que a mensaxe nomee o id da regra. `scripts/probar-violacion.sh` fai ademais violacións reais de punta a punta e esixe que o fallo nomee a regra (D28); as de Modulith saen como `M1 …`/`M2 …`.

Lombok, H2 e Liquibase bloquéanse tamén en `maven-enforcer` (`bannedDependencies`), xunto con `requirePluginVersions`.

## Consecuencias
- Un módulo novo sempre igual (plantilla e `scripts/novo-modulo.sh`); o revisor só mira negocio.
- Mapeadores dominio↔JPA e dominio↔DTO: máis código, curto e xerado pola plantilla (`scripts/novo-modulo.sh`, con `--custom-fields TARGET` para o cableado de campos personalizados).
- Cambiar un módulo a servizo aparte sería posible, pero non se prevé.

## Alternativas descartadas
- **Microservizos:** máis memoria (8 GB), máis operación, nada que gañar para un concello.
- **Capas clásicas sen módulos:** non para as fugas entre contextos.
- **Unha soa interface `XxxUseCases` por módulo e comandos aniñados (ARQ4-3, 2026-10-23):** aforra uns 8 ficheiros de ~10 liñas por módulo (os 10 de `port.in` da plantilla suman 114 liñas), pero o adaptador de portabilidade (`XxxDataSection`) pasaría a ver tamén crear e editar, o test en memoria tería que implementar todo e `municipality` quedaría distinto da plantilla xusto antes de `facility`. Export/Import non se fan opcionais: todo módulo con táboas ten que ser portable (A21, ADR-0018); un sen táboas borra eses dous ficheiros, como xa di a regra. O custo real dun módulo está no SQL e nos IT, non nestas interfaces.
- **Anotacións JPA no dominio:** menos código, pero o dominio volve depender da persistencia e reaparecen setters e proxies.
- **jMolecules:** engade vocabulario; as regras explícitas len mellor para o xurado.
- **Autoproba con copias das regras:** podería probar unha regra distinta da que se aplica; por iso se usan as mesmas constantes.
- **Lista de implementacións da SPI (`List<CapacityChangeGuard>`, ARQ3-5, retirada o 2026-10-25):** con varias, a orde e quen decide quedan sen definir, e unha lista baleira por erro de configuración pasaría calada.
- **FK só cara á raíz, coa reserva apuntando á instalación e o uso nunha columna sen FK:** a base non podería garantir que o uso é da instalación nin o modo do uso; a lista pechada de táboas publicadas mantén o control con dúas táboas máis.
- **Chamar ao `UseCase` doutro módulo para consultar:** duplica permisos e acopla ao dominio alleo; as `XxxQueries` devolven records estables.
- **Variante lixeira para módulos de apoio** (ARQ-9, retirada por ARQ2-5): ningunha ferramenta a xeraba.
- **Bloques entre marcas en ficheiros centrais** (ARQ2-6, retirado por ARQ3-2): seguían dando conflitos de *merge* no mesmo ficheiro e facían que `shared` coñecese os seus clientes.
- **Bean `ModulePermissions` por módulo e lista de basenames de mensaxes xerada** (ARQ3-2, retirados por ARQ4-2): cada constante do `enum` xa leva os seus roles e o classpath xa ten os ficheiros; unha lista máis era outro ficheiro central.
- **Códigos de destino de campos en minúsculas** (`facility`, ARQ3-2, retirado por ARQ4-2): cambiaría os datos gardados e o contrato que usa a web.
- **Indicadores dun concello por eventos e táboas propias** (retirado o 2026-10-28, ADR-0026): para ler o que xa está nas táboas de reservas e instalacións duplicaría datos e precisaría reconstruílos.
- **`@NamedInterface("api")` nun subpaquete en vez da raíz:** equivalente, pero con dúas convencións; a raíz xa é a API pública por defecto de Modulith.
