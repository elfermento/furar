<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0015 · Despregue: Compose, contedores non root e ≤ 3,5 GB

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo) · **revisada o 2026-10-25** (dominio 1: ruta `/tiles`, límite de subida das fotos, variables de enderezos e teselas; ver «Dominio 1»)

## Contexto
O xurado e un concello teñen que instalalo cun comando e sen compilar nada. Ten que funcionar en equipos modestos de 8 GB (4 GB e 2 CPU para Docker): compilar API e web dentro de Docker mentres arrancan os servizos dá OOM. Erros habituais que se evitan: Dockerfile con JDK, root e sen healthcheck.

## Decisión
**Portos**

| Servizo | Interno | Publicado |
|---|---|---|
| web (Caddy) | 8080 | `full`: 8080 (única entrada) |
| api | 8081 (app) · 9081 (actuator: `health`; en `prod` tamén `metrics`) | `dev`: a API corre no host en 8081 |
| keycloak | 8082 (`/auth`) · 9000 (xestión) | `dev`: 8082 |
| postgres | 5432 | `dev`: 5432 |
| mailpit | 8025 / 1025 | `dev` e `full`: 8025 |

Caddy: `/` → estáticos (fallback SPA), `/api/*` → api, `/auth/*` → keycloak, `/actuator*` → 404 `problem+json`, `/healthz` → 200. O sitio extra `http://127.0.0.1:8080` (o healthcheck do contedor cando o sitio é HTTPS) só atende a loopback e `FURAR_ADMIN_IPS`; desde fóra corta a conexión (SEC2-02, X08). `FURAR_PUBLIC_URL`: `dev` `http://localhost:4200` (`ng serve` fai de proxy de `/api` e `/auth`), `full` `http://localhost:8080`.

**Dominio 1 (revisión do 2026-10-25, [ADR-0023](0023-enderezos-e-mapa-sen-terceiros.md)).** Que cambia e por que: o mapa e as fotos das instalacións precisan dúas rutas novas en Caddy, e o buscador de enderezos a primeira saída a internet da API.
- **`/tiles/{z}/{x}/{y}.png`** (z 0–19, só `GET`/`HEAD`; o resto, 404/405): Caddy fai de proxy cara a `FURAR_MAP_TILES_UPSTREAM` (esquema e host) + `FURAR_MAP_TILES_PATH`, sen `X-Forwarded-*`, `Cookie`, `Authorization` nin `Referer` cara a fóra, sen `Set-Cookie` de volta, co `User-Agent` de Furar e `Cache-Control` longo. Na demo apunta ás teselas públicas de OSM; en produción, a un servidor propio ou contratado (a política de OSM non admite tráfico de produción). **Desde o 2026-11-03** ([ADR-0023](0023-enderezos-e-mapa-sen-terceiros.md), revisión): por defecto en todos os perfís, o Mapa base do IGN (`https://www.ign.es`), cunha plantilla `FURAR_MAP_TILES_TEMPLATE` (camiño e query con `{z}`, `{x}`, `{y}`) no canto de `FURAR_MAP_TILES_PATH`, e `FURAR_MAP=off` deixa `/tiles` en 404.
- **Límite de corpo:** `/api/*` segue en 256 KB e `/api/v1/municipality-imports` en 50 MB; só o `POST /api/v1/municipalities/*/photos` (subida á biblioteca de imaxes do concello) sobe a 9 MB. Na API, `spring.servlet.multipart.max-file-size=8MB` e `max-request-size=9MB`, así o erro dunha foto grande sae da API (`facility.photo-too-large`) e non de Caddy. Máximo 12 imaxes por instalación.
- **Variables novas** (en `compose*.yaml` e `.env.example`): `FURAR_MAP_TILES_UPSTREAM`, `FURAR_MAP_TILES_PATH` (Caddy; desde o 2026-11-03, `FURAR_MAP`, `FURAR_MAP_TILES_UPSTREAM` e `FURAR_MAP_TILES_TEMPLATE` en Caddy e `FURAR_MAP`, `FURAR_MAP_ATTRIBUTION` e `FURAR_MAP_ATTRIBUTION_URL` na API, ningunha obrigatoria), `FURAR_GEOCODER=cartociudad|fixed` (API; por defecto `cartociudad`, `fixed` en tests e e2e) e `FURAR_HTTP_PROXY` (API, opcional). Ningunha é segredo.
- **Saídas a internet:** a API, a `www.cartociudad.es`; Caddy, ao servidor de teselas (por defecto `www.ign.es`). Ningunha desde o navegador. Proxy corporativo, destinos e `ALTER COLLATION … REFRESH VERSION` das colacións ICU (orde alfabética do listado): [`instalacion.md`](../instalacion.md).

**Perfís:** `dev` (postgres, keycloak, mailpit) e `full` (+ api + web). Ademais `compose.prod.yaml` para un concello.

**Construír e arrincar por separado:** `docker compose --profile full build api`, despois `web`, despois `keycloak`, en serie; logo `up -d --wait` sen `--build`. Dockerfiles con BuildKit e `--mount=type=cache` (`~/.m2`, caché de npm), Maven con `-DskipTests -Xmx768m` (os tests xa pasaron). Un concello **non compila**: `compose.prod.yaml` usa `${FURAR_REGISTRY}/furar-{api,web,keycloak}:<versión>@sha256:…` publicadas pola CI no rexistro da forxa canónica (ADR-0016).

**Memoria** (`mem_limit`, suma ≤ 3.584 MiB): postgres 512 · keycloak 1.024 (`-Xmx512m`) · api 1.024 · web 128 · mailpit 128 = 2.816 MiB. `scripts/memoria.sh` mide límites, uso tras o e2e e pico durante o build.

**Imaxes** (todas por `etiqueta@sha256`)
- `api/Dockerfile`: `eclipse-temurin:25.0.4.1_1-jdk-alpine` → `./mvnw -B -DskipTests package` → capas (`java -Djarmode=tools -jar app.jar extract --layers --launcher`) → `eclipse-temurin:25.0.4.1_1-jre-alpine`, `USER 10001`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC`, `HEALTHCHECK CMD wget -qO- http://127.0.0.1:9081/actuator/health/readiness || exit 1`. Sen BuildKit (Colima sen buildx) non hai caché de montaxe e un `--no-cache` volve baixar todo: `api/.mvn/maven.config` reintenta os 5xx de Maven Central (o resolver só reintenta 429 e 503), `go-offline` exclúe site, install e deploy, e `verificar.sh` borra os contedores intermedios que deixa un build fallido. (busybox); `management.endpoint.health.probes.enabled=true` con `db` en readiness.
- **As tres imaxes constrúense co contexto na raíz** (`.dockerignore` de lista de permitidos) para copiar `LICENSE`, e levan `org.opencontainers.image.{title,licenses,source,version,revision}` desde os build args `FURAR_REVISION`, `FURAR_VERSION` e `FURAR_SOURCE_URL` (EUPL art. 5, LIC-9; D37 compróbao). Texto da EUPL en `/licenses/EUPL-1.2.txt` (api, keycloak) e `/srv/LICENSE.txt` (web, servido na web).
- `web/Dockerfile`: precisa ademais `api/openapi.json`; `node:24.22.1-alpine` + `npm ci` → `caddy:2.11.7-alpine` con `XDG_DATA_HOME=/data`, `XDG_CONFIG_HOME=/config` (de 10001), `USER 10001`, `:8080`, `HEALTHCHECK wget -qO- http://127.0.0.1:8080/healthz`.
- `deploy/keycloak/Dockerfile`: unha etapa compila coa JDK a extensión de Furar (`deploy/keycloak/extension`, `furar-keycloak-extension.jar` en `providers/`: invitación, rexistro, ligazóns curtas e privacidade); despois `kc.sh build` (Postgres, health, `/auth`, caché local por variables `KC_*`) cos temas e as listas de contrasinais comúns; arrinca con `start --optimized --import-realm`. Healthcheck por bash: `exec 3<>/dev/tcp/127.0.0.1/9000; printf 'GET /auth/health/ready HTTP/1.0\r\n\r\n' >&3; grep -q '^HTTP/[0-9.]* 200 ' <&3`: mírase o código (200/503), non o texto `UP`, porque a resposta DOWN do arranque tamén o contén e Docker daba `healthy` antes de importar o realm. `start_period` 90 s (api 60 s).
- `postgres:18.6-alpine`: volume en **`/var/lib/postgresql`** (PG 18 usa `/var/lib/postgresql/18/docker`); `pg_isready -U $POSTGRES_USER`; o seu entrypoint baixa a uid 70 antes de arrincar o servidor (D36 mide o PID 1). Bases `furar` e `keycloak` (`deploy/postgres/init/`).

**Roles de PostgreSQL** (SEC-02, `deploy/postgres/init/05-furar.sh`, só no primeiro arranque dun volume baleiro):

| Rol | Para que | Permisos |
|---|---|---|
| `postgres` (`POSTGRES_USER`) | init, copias, restauración | superusuario; ningún servizo o usa |
| `furar_owner` | só o servizo `migrate` (Flyway) | dono da base e do esquema `public`: DDL das migracións |
| `furar_app` | a API (`spring.datasource`) | `CONNECT` e só `SELECT/INSERT/UPDATE/DELETE` (privilexios por defecto de `furar_owner`); en `flyway_schema_history`, só `SELECT`; sen superusuario, sen `BYPASSRLS`, sen acceso a `keycloak` |
| `keycloak` | Keycloak | dono só da base `keycloak` |

**Migracións nun servizo dun só uso (SEC2-03):** `migrate` é a imaxe da API co argumento `migrate` (`FurarMigration`: só a autoconfiguración de Flyway, sen web nin JPA); migra como `furar_owner` e sae. `api` depende del con `service_completed_successfully` e ten `SPRING_FLYWAY_ENABLED=false` (`full`) ou o perfil `prod` (Flyway desactivado), **sen `FURAR_DB_OWNER_PASSWORD` no seu contorno**: un volcado do contorno ou da memoria do proceso que atende peticións non dá DDL. Fóra de Compose (API no host, IT), a API segue migrando ao arrincar. Do mesmo xeito, o argumento `test-mail <correo>` (`FurarTestMail`: só a autoconfiguración do correo, sen base nin web) envía un correo de proba polo SMTP do `.env` e sae; `scripts/instalar.sh` úsao antes do primeiro arranque. A migración `V202610221300__platform_history_read_only.sql` quítalle a `furar_app` o `INSERT/UPDATE/DELETE/TRUNCATE` en `flyway_schema_history`, que os privilexios por defecto lle darían: unha inxección SQL non pode borrar nin falsear o historial. **Regra para as táboas de auditoría (ENS):** a migración que as cree deixa a `furar_app` só `SELECT, INSERT` (`revoke update, delete, truncate`), e `DatabaseRolesIT` engade o caso. `event_publication` (rexistro de eventos de Modulith) segue con DML: a API márcaos como completados.

Gardas: `ProdProfileGuard` non deixa arrincar a API en `prod` se `FURAR_DB_USER` é `postgres`/`furar` ou igual ao dono, con contrasinais de demo, con Flyway activado ou con `FURAR_DB_OWNER_PASSWORD` no contorno; no modo `migrate` só comproba o dono (non de demo, non superusuario); `DatabaseRoleCheck`, xa conectado, comproba `is_superuser = off` e que non pode crear no esquema (en `prod` négase a arrincar). Os IT usan os mesmos roles e o mesmo script (`DatabaseRolesIT`: DDL, `COPY … TO PROGRAM` e escribir en `flyway_schema_history` → *permission denied*; o modo `migrate` migra e sae con 0). Un volume creado antes deste cambio non ten os roles: en local, `docker compose down -v`.
- Todos con healthcheck e `depends_on: condition: service_healthy`.

**Realm:** `deploy/keycloak/import/furar-realm.json` (realm, clientes `furar-web` e `furar-api`, fluxo de rexistro `furar-registration`, política de contrasinais, bloqueo e eventos; **sen roles, grupos, segundo factor nin usuarios**: os roles saen das táboas de Furar, [ADR-0027](0027-persoal-e-permisos-fase-1.md) e [ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md)) é o único que monta `compose.prod.yaml`. Temas `furar` de acceso, correo e conta con galego completo (X09 compara as claves coa versión de Keycloak; X10 recibe en Mailpit o correo de verificación en galego). `full` e `dev` montan ademais `deploy/keycloak/import-demo/`: 14 usuarios de demo (`admin-plataforma`, `xestor-naron`, `recepcion-naron`, `ex-recepcion-naron`, `vecino-naron`, `xestor-ferrol` e os xestores dos concellos de exemplo), o cliente `furar-e2e` con *direct access grants* (só para D33 e o e2e) e o realm do IdP «Cl@ve simulada» ([ADR-0019](0019-autenticacion-publica-clave.md)).

**Actualizar o realm (OPS-R2-3):** `--import-realm` só crea o realm se non existe; os cambios de `deploy/keycloak/import/` (roles e clientes dos módulos novos, a cláusula informativa no rexistro) non chegan a unha instalación existente. **Decisión para v0.1.x: notas de realm obrigatorias.** Cada versión que cambie o realm trae `docs/notas-realm/<versión>.md` cos pasos a man e como comprobalos; `scripts/notas-release.sh` compara coa etiqueta anterior e sen esa nota non hai release. A primeira, v0.1.0, impórtao desde cero e non ten pasos ([nota](../notas-realm/v0.1.0.md)); as notas con data anteriores son probas do desenvolvemento. **Antes de v0.2.0** (cando cheguen os roles dos módulos novos) avaliarase unha ferramenta libre que aplica o realm (Apache-2.0) como servizo dun só uso, igual ca `migrate`: aplica o realm de forma idempotente en cada arranque. Criterios: versión compatible coa de Keycloak fixada, memoria (é unha JVM máis no arranque, pero sae) e que non borre o que creou o concello (roles `naron:*`, usuarios): só con `import.remote-state.enabled` e sen `import.managed.*: full`. Descártase reimportar sobrescribindo (`--override true`): borraría usuarios e credenciais.

**Produción (`compose.prod.yaml`)**
- **Volume propio `pgdata-prod`** (SEC-09): unha base creada antes co perfil `full` (realm de demo con contrasinais públicos e cliente `furar-e2e`) nunca se reutiliza.
- **Administración de Keycloak só local:** porto `127.0.0.1:8082` con `KC_HOSTNAME_ADMIN`, por túnel SSH; Caddy pecha `/auth/admin` e o realm `master` salvo `FURAR_ADMIN_IPS`, obrigatorio (ADR-0007).
- **Probado de verdade (D48, OPS-3):** as imaxes de D29 súbense a un rexistro local, descárganse por etiqueta e digest e arrincan co `.env` de produción, HTTPS coa CA interna de Caddy (`furar.internal`), `COMPOSE_FILE`, copia e restauración.
- **Arquitectura publicada: `linux/amd64`** (OPS-4). A CI publica as mesmas imaxes que pasaron o e2e (non se reconstrúen), e o e2e corre en amd64. Para ARM64 (un VPS ARM, Raspberry Pi 5), o concello constrúe as súas e súbeas ao seu rexistro con `scripts/publicar-imaxes.sh` (todas as bases son multiarquitectura). Publicar arm64 desde a CI precisaría emulación (QEMU) sen e2e nesa arquitectura; queda para cando haxa un runner arm64.
- `scripts/preflight.sh` comproba Docker, Compose ≥ 2.24.4 (`!override`), memoria e a configuración antes de instalar ou actualizar.

**Segredos:** `.env.example` sen segredos reais (gitleaks na CI); valores de demo só en `dev`/`full`. `compose.prod.yaml` esixe `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`, `POSTGRES_PASSWORD`, `FURAR_DB_OWNER_PASSWORD`, `FURAR_DB_PASSWORD`, `KC_DB_PASSWORD`, `FURAR_INVITATION_PROOF_SECRET` (segredos), `FURAR_PUBLIC_URL`, `FURAR_SMTP_HOST`, `FURAR_SMTP_FROM`, `FURAR_ADMIN_IPS` e `FURAR_REGISTRY` (coa versión e os digests das imaxes) con `${VAR:?}`. O correo de Keycloak e da API sae do mesmo SMTP: `FURAR_SMTP_PORT` (587), `FURAR_SMTP_STARTTLS` (`true`), `FURAR_SMTP_SSL` (`false`), `FURAR_SMTP_AUTH` (`true`), `FURAR_SMTP_USER` e `FURAR_SMTP_PASSWORD`, e engade HTTPS automático de Caddy co dominio do concello.

**Operación:** `scripts/copia.sh` e `scripts/restaurar.sh` (`pg_dump` de `furar` e `keycloak` como `postgres`; a restauración crea `furar` con dono `furar_owner`). Copias con rotación (`--conservar N`) e cifradas con age (BSD-3-Clause) cando se define `FURAR_COPIAS_AGE_DESTINATARIO`: o volcado cífrase no fluxo e nunca queda en claro no disco; a chave privada gárdase fóra do servidor (OPS-7). Sen age no sistema, os scripts usan a imaxe local `furar-age` (`deploy/ferramentas/age/Dockerfile`, chaves pola entrada estándar), así que D41 proba sempre o cifrado; `restaurar.sh` comproba que a chave descifra antes de borrar nada. Cada copia garda `furar.txt` (`FURAR_VERSION` e última migración de Flyway) e `restaurar.sh` négase a cargar unha copia doutra versión ca do `.env` (OPS-R2-2): restaurar arrinca a API do `.env`, e unha API nova migraría outra vez a copia vella. Volver atrás é, por iso, primeiro a versión vella no `.env` e despois restaurar. Pendente desde v0.1.0: proba de actualización vN-1 → vN → volta atrás na parte e2e. `scripts/copia-diaria.sh` (cron) rexistra copia e envío, sae con erro se falla calquera das dúas, aplica a conservación tamén no destino (`rclone delete --min-age`) e avisa opcionalmente a un comprobador de tarefas (OPS-R2-4). `docs/instalacion.md` describe instalación, copia e actualización.

**Observabilidade** (OPS-8): en `prod`, rexistros JSON ECS na consola (`logging.structured.format.console: ecs`), cun `http.request.id` por petición (`RequestIdFilter`: toma `X-Request-Id` se é seguro ou xera un, e devólveo na resposta) para atopar nos rexistros o erro que ve unha persoa. `/actuator/metrics` só no 9081 interno, que Caddy nunca publica. Prometheus (`micrometer-registry-prometheus`, Apache-2.0) queda como opción cando un concello teña onde recollelo.

**Demo pública (OPS-R3-2):** un VPS na UE (4 GB, amd64) instalado **exactamente como di [`instalacion.md`](../instalacion.md)**: Docker, `compose.prod.yaml`, Caddy con Let's Encrypt e as imaxes da release. Así a demo é tamén a proba real da guía (DNS e certificado público incluídos, o que D48 non cobre). Sen panel de despregue: os habituais poñen o seu proxy en 80/443 (choca cos portos de Caddy), suman as súas bases de datos a un servidor de 4 GB onde Furar xa reserva 2,7 GB e algúns son *open-core*, fóra da regra «só OSI/FSF». Se algún día fai falta un panel, ten que ser 100 % libre e non ocupar 80/443.

**Contedores sen privilexios (SEC3-04):** todos con `cap_drop: [ALL]` e `no-new-privileges` (ancla `x-hardening` de `compose.yaml`). Postgres engade só `CHOWN`, `DAC_OVERRIDE`, `FOWNER`, `SETGID` e `SETUID`: o seu entrypoint arrinca como root para preparar o directorio de datos e baixa a `postgres`. `api`, `migrate`, `web` e `mailpit` levan a raíz só lectura con `tmpfs` (`/tmp`; Caddy tamén `/config` e, fóra de produción, `/data`). Keycloak non: escribe en `/opt/keycloak/data`. A imaxe web quítalle a `caddy` a capacidade de ficheiro `cap_net_bind_service` (con `cap_drop: [ALL]` o exec fallaría; escoita en 8080/8443). Próbao D36.

**Perfil `full` só neste equipo (SEC3-01):** leva o realm de demo (usuarios e contrasinais públicos) e `admin`/`admin-demo` en `master`, así que Caddy publícase en `127.0.0.1` (`FURAR_HTTP_BIND`). Abrilo á rede local é unha decisión explícita (`FURAR_HTTP_BIND=0.0.0.0`) e daquela convén `FURAR_ADMIN_IPS=127.0.0.1/32`; en macOS con Colima ou Docker Desktop nin iso abonda, porque todo chega a Caddy como loopback. D31 comproba que no perfil `full` non hai ningún porto fóra de `127.0.0.1`. O valor por defecto de `FURAR_ADMIN_IPS` en local segue sendo redes privadas: nun Linux, o `docker-proxy` entrega as conexións de `localhost` coa IP da ponte (172.x), e con só loopback a consola de Keycloak deixaría de abrir desde o propio equipo.

## Consecuencias
- `git clone` + un comando = Furar con dous concellos de exemplo; un concello instala imaxes publicadas nun servidor de 4 GB.
- Actualizar a versión = cambiar a etiqueta; o servizo `migrate` aplica as migracións antes de que arrinque a API. A proba de actualización sobre datos (D-futura) engádese tras a primeira release.

## Alternativas descartadas
- **`up --build` nun paso:** OOM ou build lentísimo en 4 GB; e un concello tería que compilar.
- **`user: "70:70"` en postgres:** `initdb` falla sobre un volume de root; o entrypoint oficial xa baixa privilexios.
- **Keycloak `start` sen build optimizado:** reaumenta en cada arranque (máis tempo e memoria).
- **Fotos e teselas fóra de Caddy (servidor de ficheiros ou CDN):** outro servizo que operar, e unha CDN é un terceiro que ve a IP de quen mira.
- **Subir o límite de `/api/*` enteiro a 9 MB:** calquera ruta aceptaría corpos grandes; o límite vai só onde fai falta.
- **Kubernetes/Helm:** excesivo para un concello pequeno.
- **Nginx:** válido; Caddy dá HTTPS automático e configuración máis curta.
- **GraalVM nativo:** builds lentos e máis risco en catro semanas.
- **Imaxes multiarquitectura desde a CI (agora):** romperían a garantía «publícase o que pasou o e2e» para arm64; ver Produción.
- **Copias cifradas con GPG:** age ten unha chave curta e sen anel de chaves; menos erros de operación.
