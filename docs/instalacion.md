<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Instalar Furar nun servidor do concello

Guía para o persoal informático dun concello (ou da deputación). Non hai que compilar nada: o instalador descarga as imaxes que publica a CI en cada versión ([ADR-0015](adr/0015-despregue.md)). Tempo: uns 20 minutos, a maior parte esperando descargas.

A instalación de produción próbase en cada verificación (D48, `scripts/proba-producion.sh`), agás o DNS e o certificado público.

## 1. Que fai falta

- [ ] **Servidor** Linux x86-64 (amd64) ou ARM64 (arm64) con 4 GB de RAM, 2 vCPU e 20 GB de disco. Mellor aloxado na UE (RGPD). As imaxes publícanse para as dúas arquitecturas e Docker descarga a do servidor; v0.1.0 só para amd64 (§11).
- [ ] **Docker** Engine 24 ou posterior con Docker Compose 2.24.4 ou posterior (`docker compose version`), `git` e `openssl`, e que arrinque co sistema (`sudo systemctl enable docker`): así Furar volve só despois dun reinicio (§8).
- [ ] **Un nome DNS** (p. ex. `reservas.concello.gal`) que apunte á IP do servidor.

  Créao o concello (ou quen leva o seu dominio ou o aloxamento), non Furar. Texto para pedilo:

  > Para o servizo de reservas deportivas, creade no dominio `concello.gal` un rexistro DNS de tipo **A** co nome `reservas` (`reservas.concello.gal`) que apunte á IP `203.0.113.10`; se o servidor ten IPv6, tamén un **AAAA** coa súa IPv6. E abride desde Internet os portos **80 e 443** (TCP) cara a ese servidor: o 80 fai falta para o certificado HTTPS.

  Pon o nome e a IP pública do servidor (detrás dun NAT, a do router). Para saber se xa resolve: `scripts/instalar.sh --comprobar-rede --dominio reservas.concello.gal`.
- [ ] **Portos 80 e 443** abertos desde Internet. O resto, pechado. Saída a Internet (directa ou por proxy) só cara aos destinos do §13.
- [ ] **Correo saínte (SMTP)** do concello: servidor, porto e cifrado, usuario, contrasinal e o enderezo que envía (§2, «Correo saínte»). Serve para os correos das contas e das reservas.
- [ ] **Datos legais do concello** (para despois da instalación, na web), revisados co DPD: denominación, NIF, domicilio, correo xeral, correo e teléfono do DPD e, se outra entidade aloxa o servizo, o encargado.
- [ ] Para as copias: [age](https://age-encryption.org) e rclone (`apt install age rclone`), §6.

Furar usa como máximo 2,7 GB de memoria (PostgreSQL 512 MB, Keycloak 1 GB, API 1 GB, web 128 MB).

## 2. Instalar

**Descarga** o código da versión:

```sh
git clone --branch v0.1.0 https://github.com/elfermento/furar.git /opt/furar   # a versión que vas instalar
cd /opt/furar
curl -fsSLO https://github.com/elfermento/furar/releases/download/v0.1.0/furar-v0.1.0.env
```

O ficheiro `furar-v0.1.0.env` é o da [release desa versión](https://github.com/elfermento/furar/releases) e queda en `/opt/furar`. Leva o rexistro, a versión e o digest de cada imaxe: o digest garante que se descarga exactamente a imaxe que pasou a CI desa etiqueta. A release trae tamén os SBOM e como comprobar a sinatura das imaxes (`cosign verify`, opcional).

**Executa** o instalador:

```sh
scripts/instalar.sh
```

Fai estas preguntas (Intro deixa o valor proposto, se o hai):

| Pregunta | Exemplo |
|---|---|
| Nome do servidor | `reservas.concello.gal` |
| Correo de quen creará a conta de administración | `informatica@concello.gal` |
| SMTP: servidor, cifrado, porto, usuario, contrasinal e remitente | `smtp.concello.gal`, `1` (STARTTLS), `587`, `reservas@concello.gal`, …, `reservas@concello.gal` |

Os datos de protección de datos non se preguntan: son os **datos legais de cada concello**, que se enchen na web despois da alta (§3). Con eles saen a cláusula informativa do RGPD (art. 13) da páxina «Privacidade» e a frase do rexistro ([`datos-persoais.md`](datos-persoais.md)).

### Correo saínte (SMTP)

O mesmo servidor envía os correos das contas (Keycloak: verificación e contrasinal) e os da API (reservas e invitacións ao persoal). Fai falta:

- **Servidor, porto e cifrado.** O instalador pregunta o cifrado e propón o porto:
  1. **STARTTLS (porto 587)**: o habitual nun servidor de envío.
  2. **SSL/TLS (porto 465)**: TLS desde o principio da conexión.
  3. **Sen cifrar (só servidor interno)**: un relay da rede do concello que non ofrece TLS (porto 25). Nunca a un servidor fóra da rede.
- **Usuario e contrasinal**, se o servidor pide autenticación (baleiro se non: o instalador desactívaa).
- **Remitente**: un enderezo co que o servidor deixe enviar a ese usuario (p. ex. `reservas@concello.gal`); se non, rexeita os correos ou cámbiao.
- **SPF, DKIM e DMARC** do dominio do remitente que inclúan ese servidor: sen eles, os correos acaban no correo non desexado ou non chegan.

**Correo de proba.** Despois de descargar as imaxes e antes do primeiro arranque, o instalador envía un correo ao de administración polo mesmo camiño ca a API (a súa imaxe co argumento `test-mail`) e di se o servidor o aceptou ou o motivo (p. ex. `535`: usuario ou contrasinal; `550`/`553`: remitente non permitido; `Connection refused` ou `timed out`: firewall ou porto). Se falla, non arrinca sen preguntar: executa outra vez `scripts/instalar.sh`, substitúe o `.env` (consérvanse os contrasinais) e corrixe o dato. «Aceptado» quere dicir que o servidor o colleu; se non chega, mira o correo non desexado e o SPF/DKIM/DMARC. Para probar outra vez en calquera momento: `scripts/instalar.sh --probar-correo` (ou `--correo-admin outro@concello.gal`).

**Cambialo despois.** Keycloak copia o SMTP ao realm **só no primeiro arranque**. Cun cambio posterior (servidor, porto, cifrado, contrasinal): `scripts/instalar.sh` substituíndo o `.env` (a API cólleo ao arrincar) **e** o mesmo na consola de Keycloak, *Realm settings* → *Email* (por túnel, §5): STARTTLS é *Enable StartTLS*, SSL/TLS é *Enable SSL*; *Test connection* envía alí un correo de proba.

**Que fai**, por orde:

1. Comproba que Docker está en marcha e que está o ficheiro da release.
2. Comproba, sen preguntar a ningún servizo de fóra, que o nome resolve e que os portos 80 e 443 están libres. Se o nome non resolve, para e di que pedir (§1); se resolve a unha IP que non é deste equipo, só avisa (normal detrás dun NAT ou dun firewall que reenvía os portos). Se outro programa (p. ex. outro servidor web) usa o 80 ou o 443, para.
3. Xera todos os contrasinais e segredos con `openssl`: non hai que inventar ningún.
4. Escribe `.env` en modo produción e con permisos 600. Se xa hai un `.env`, pregunta antes; se o substitúe, garda unha copia e conserva os contrasinais (a base xa creada só coñece eses).
5. Pasa `scripts/preflight.sh` (Docker, Compose, memoria e `.env` completo).
6. Descarga as imaxes (`docker compose pull`) e envía o correo de proba («Correo saínte»).
7. Arrinca (`docker compose up -d --wait`) e comproba `https://…/healthz`: distingue se aínda non responde ou se responde cun certificado que este equipo aínda non dá por bo (normal uns minutos, mentres Let's Encrypt o emite, ou cunha CA interna).
8. Di o enderezo que tes que abrir.

O primeiro arranque tarda uns 2 minutos: PostgreSQL crea as bases, Keycloak importa o realm `furar`, o servizo `migrate` aplica as migracións e sae (`Exited (0)` en `docker compose ps -a` é o normal) e arrinca a API. Caddy pide o certificado HTTPS a Let's Encrypt: o DNS e o porto 80 teñen que estar xa listos.

Comprobación: `curl -fsS https://reservas.concello.gal/healthz` responde `ok` e `docker compose ps` mostra os catro servizos `healthy`.

**Sen preguntas** (automatización): `scripts/instalar.sh --si --dominio … --correo-admin … --smtp-servidor … --smtp-cifrado starttls|ssl|ningun`, ou cos mesmos datos en variables de contorno (`FURAR_SMTP_PASSWORD` para o contrasinal, mellor ca nunha opción). Se o correo de proba falla, para; `--sen-correo-de-proba` sáltao. Se o nome non resolve, tamén para; `--sen-comprobar-dns` sáltao (p. ex. un nome só da rede interna). `--so-configurar` escribe o `.env` sen arrincar; `--axuda` lista todas as opcións.

Os datos de produción van nun volume propio (`pgdata-prod`): se antes probaches o perfil `full` no mesmo servidor (§10), os seus usuarios de demo **non** pasan a produción.

## 3. Abrir o enderezo

Abre `https://reservas.concello.gal`. Sen ningún concello, vese a portada con instalacións de exemplo (etiqueta «Exemplo», o buscador desactivado) e, arriba, «Entrar» ([ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md)):

1. **«Entrar» → «Crear a conta»** co correo que deches a `instalar.sh` (`FURAR_ADMIN_EMAIL`), nome, apelidos e un contrasinal de 12 caracteres ou máis.
2. **Verifica o correo:** chega «Verifica o teu correo» (remitente «Reservas deportivas»); a ligazón vale 30 minutos (o botón ou, baixo el, a ligazón curta `…/auth/realms/furar/l/XXXX-XXXX-XXXX`). Se non chega, mira no correo non desexado e, na páxina de Keycloak, «Reenviar o correo»; se tampouco, revisa o SMTP na consola de Keycloak (*Realm settings* → *Email* → *Test connection*, §5): este correo envíao Keycloak, que só leu o `.env` no primeiro arranque (§4).
3. **Volves á web xa como administración da plataforma**, no formulario «Novo concello». A primeira petición coa conta dese correo, verificado, dálle o rol, unha soa vez e no rexistro de actividade («Rexistro da plataforma»). Se xa tiñas conta con ese correo, «Entrar» abonda.
4. **Crea o concello** (o identificador vai no enderezo e non se cambia) e vas á súa páxina «Persoal» para invitar á primeira persoa xestora (§5).
5. **Configura o concello** (a administración da plataforma configúrao enteiro: datos, datos legais, instalacións, campos personalizados e persoal, e le o seu rexistro de actividade; non ve reservas nin indicadores nin exporta, [ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9): na sección «Concello», as pestanas Xeral, Aparencia, Servizo e **Legal** (os datos que usan os textos legais: denominación, NIF, domicilio, correo xeral e do DPD; e en «Privacidade», o encargado); despois, as instalacións. Os datos legais son opcionais: a páxina «Privacidade» mostra os que haxa.

Desde ese momento, `https://reservas.concello.gal` abre a portada dese concello (con varios concellos, a lista para escoller). A portada leva as instalacións de exemplo ata que o concello publica a primeira súa.

Calquera outra conta que se rexistre antes é unha conta normal, sen rol. Cambiar `FURAR_ADMIN_EMAIL` cando xa hai administración non fai nada. O correo nunca se mostra na web.

## 4. Configuración avanzada

Todo o demais está en `.env` cun valor por defecto seguro: para arrincar non hai que tocar nada. Para cambiar algo, edita `.env` e aplícao con `docker compose up -d`.

| Variable | Por defecto | Que fai | Cando cambiala |
|---|---|---|---|
| `FURAR_MAP` | baleiro: Mapa base do IGN | Mapa das instalacións (público, sen clave, CC BY 4.0; Furar xa mostra o recoñecemento). `off` = sen mapa, só o enderezo. | Se o servidor non pode saír a Internet ou non queres mapa. |
| `FURAR_MAP_TILES_UPSTREAM`, `FURAR_MAP_TILES_TEMPLATE`, `FURAR_MAP_ATTRIBUTION`, `FURAR_MAP_ATTRIBUTION_URL` | os do IGN | Outro servidor de teselas e o seu recoñecemento. | Cun servidor propio ou contratado (§13). |
| `FURAR_GEOCODER` | `cartociudad` | Suxestións de enderezos ao dar de alta unha instalación. `fixed` = sen saír a Internet (só uns enderezos gardados; o resto, a man). | Se a API non pode saír a Internet. |
| `FURAR_HTTP_PROXY` | baleiro | Proxy saínte (p. ex. `http://proxy.concello.local:3128`) para enderezos, mapa e certificado. | Se o servidor sae a Internet por proxy (§13). |
| `FURAR_AUDIT_VISIBLE` | `P1Y` | Canto tempo se ve cada evento do rexistro de actividade na xestión. | Só co DPD. |
| `FURAR_AUDIT_RETENTION` | `P3Y` | Canto se garda, bloqueado, antes de borralo (LOPDGDD art. 32): só o xestor o exporta cun motivo. Ten que ser máis longo ca `FURAR_AUDIT_VISIBLE` e polo menos `P1Y`. | Só co DPD: os tres anos son a prescrición das faltas ([`datos-persoais.md`](datos-persoais.md#rexistro-de-actividade-conservación-bloqueo-e-uso)). |
| `FURAR_ADMIN_EMAIL` | o que deches a `instalar.sh` | Correo da conta que pasa a ser a administración da plataforma no primeiro acceso (§3). Só conta mentres non hai ningunha; nunca se mostra. | Antes do primeiro acceso, se te equivocaches (`docker compose up -d api`). |
| `FURAR_ADMIN_IPS` | `127.0.0.1/32` (pechado) | Quen pode abrir a administración de Keycloak polo URL público. | Case nunca: adminístrase polo túnel SSH (§5). Ver a nota de abaixo. |
| `FURAR_REQUIRED_LANGUAGES` | `gl` | Idiomas que todo concello ten que ofrecer, separados por comas (`gl`, `es`): un concello sen un deles non se garda (`municipality.language-required`, [ADR-0003](adr/0003-idioma.md)). | Fóra de Galicia: `es` ou baleiro (ningún obrigatorio). Hoxe só hai `gl` e `es`. |
| `FURAR_SOURCE_URL` | o repositorio de Furar | A ligazón «Código fonte» do pé da web. | **Obrigatorio se o concello modifica Furar** (EUPL-1.2, art. 5). |
| `FURAR_SMTP_STARTTLS`, `FURAR_SMTP_SSL`, `FURAR_SMTP_AUTH` | `true`, `false`, `true` | Cifrado (STARTTLS ou SSL/TLS) e autenticación SMTP. | Pono o instalador segundo o cifrado escollido (§2, «Correo saínte»); sen usuario SMTP, `FURAR_SMTP_AUTH=false`. Un `.env` anterior sen `FURAR_SMTP_SSL` segue igual (STARTTLS). |
| `FURAR_COPIAS_*` | sen cifrar, sen envío | Cifrado, conservación e envío das copias. | Recomendado: §6. |
| `FURAR_HTTP_PORT`, `FURAR_HTTPS_PORT` | `80`, `443` | Portos de Caddy. | En produción, nunca (Let's Encrypt valida polo 80). |
| `FURAR_KC_ADMIN_PORT` | `8082` | Porto local (127.0.0.1) da consola de Keycloak. | Se o 8082 está ocupado. |

**Notas que conta saber:**

- **Contrasinais da base.** Só se aplican ao crear a base (primeiro arranque). Cambialos despois no `.env` non cambia os da base.
- **`FURAR_INVITATION_PROOF_SECRET`.** Segredo compartido entre a API e Keycloak: asina a ligazón da invitación ao persoal, coa que a conta nace co correo verificado ([ADR-0027](adr/0027-persoal-e-permisos-fase-1.md)). Aleatorio e de 32 caracteres ou máis; sen el a API non arrinca. Se o cambias, reinicia a API e Keycloak. Nun Keycloak instalado antes do 2026-11-02, o fluxo de rexistro cámbiase a man: [`notas-realm/2026-11-02-invitacion-verifica-o-correo.md`](notas-realm/2026-11-02-invitacion-verifica-o-correo.md).
- **Datos de privacidade.** Non van no `.env` (revisión do 2026-11-03): son os datos legais de cada concello, na web (§3, paso 5). O rexistro de Keycloak amosa unha frase xenérica coa ligazón á «Privacidade» do concello desde o que se entra; a páxina, todo. Unha instalación anterior pode borrar do `.env` as variables `FURAR_PRIVACY_*` (xa non se usan; `instalar.sh --substituir` quítaas) e do realm os textos `furarPrivacy*`: [`notas-realm/2026-11-03-privacidade-por-concello.md`](notas-realm/2026-11-03-privacidade-por-concello.md).
- **`FURAR_ADMIN_IPS`.** Se de verdade precisas abrir a administración a IPs fixas do concello, pon só esas (p. ex. `192.0.2.10/32`) e **nunca** `172.16.0.0/12` nin `0.0.0.0/0`: por IPv6, Docker entrega as conexións coa IP interna da súa ponte (172.x), así que ese rango abriría a consola a calquera.
- **Código fonte.** A web serve tamén `/LICENSE.txt` e `/3rdpartylicenses.txt`, e a imaxe da API leva `/licenses/EUPL-1.2.txt`, `/licenses/THIRD-PARTY.txt` e o SBOM `/licenses/bom.json`: non os retires.
- **Correo.** O mesmo SMTP serve a Keycloak (contas) e á API (reservas e invitacións); o nome visible do remitente é o do concello. Keycloak copia os `FURAR_SMTP_*` ao realm só no primeiro arranque: un cambio posterior (servidor, contrasinal, cifrado) vai no `.env` para a API (`docker compose up -d`) **e** na consola de Keycloak (*Realm settings* → *Email*, por túnel, §5). `scripts/instalar.sh --probar-correo` proba o do `.env`.
- **Imaxes propias.** Se non usas as imaxes publicadas, constrúe e publica as túas e usa o seu `furar-<versión>.env` (§11).

## 5. Persoal e administración

A administración da plataforma e o primeiro concello xa están feitos no §3: en Keycloak non hai que crear usuarios, grupos nin roles (Keycloak só autentica; os roles gárdaos Furar, [ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md)).

1. **Administración de Keycloak (só para soporte), por túnel SSH.** O usuario e o contrasinal iniciais están no `.env`: `grep KC_BOOTSTRAP .env`. Desde o teu ordenador: `ssh -L 8082:127.0.0.1:8082 usuario@servidor` e abre **http://localhost:8082/auth/admin** co usuario `KC_BOOTSTRAP_ADMIN_USERNAME`. (Polo URL público, `/auth/admin` responde 403.) Ese usuario é temporal: crea no realm `master` un administrador persoal e borra o temporal.
2. **Outra administración da plataforma ou quitala** (fase 1, sen pantalla): como dono da base. A persoa entra antes unha vez en Furar; o seu identificador (`sub`) está en Keycloak (*Users* → a persoa → *ID*). Engadir: `docker compose exec -T postgres psql -U postgres -d furar -c "insert into platform_admin (id, subject, granted_at, granted_by) values (gen_random_uuid(), '<sub>', now(), '<o teu sub>')"`. Quitar: `… -c "delete from platform_admin where subject = '<sub>'"`. Vale na seguinte petición. Se se perde o acceso de todas, borra as filas e usa outra vez o primeiro acceso (§3) co correo novo en `FURAR_ADMIN_EMAIL` (`docker compose up -d api`).
3. **Outro concello.** Menú da persoa → «Dar de alta un concello» (na lista de concellos) ou `/plataforma/concellos/novo`.
4. **Primeira persoa xestora, desde Furar.** No concello novo, Concello › **Persoal** (`/naron/xestion/concello/persoal`) → **Invitar**: o seu correo e o rol (*Xestión* ou *Recepción*); a plataforma pode invitar a todo o equipo. Chégalle un correo cunha ligazón (válida 7 días) que leva directa a crear a conta en Keycloak **con ese mesmo correo** xa posto (se xa ten conta, «Entrar»); escribe nome, apelidos e contrasinal no mesmo formulario, sen correo de verificación (a ligazón da invitación xa demostra que o correo é seu, `FURAR_INVITATION_PROOF_SECRET`) e, ao volver a Furar, a invitación acéptase soa e xa está dentro: Xestión nos datos do concello, Recepción nas reservas. As obrigas do persoal están no aviso legal do concello; se o concello quere que as acepten, faino co seu procedemento. Desde a mesma pantalla cámbiase o rol, desactívase ou reactívase (vale na seguinte petición; desactivar non borra a conta de Keycloak). Unha conta de traballo non reserva como veciña: cada persoa usa a conta do correo do traballo para o persoal e a súa persoal para reservar. **En Keycloak non se crean roles nin grupos**: o rol de cada persoa gárdao Furar.
   - **Sen segundo factor.** Todas as contas (persoal, plataforma e veciñanza) entran co correo e o contrasinal: 12 caracteres ou máis, distinto do correo e fóra da lista de contrasinais comúns. Quen perde o acceso ao seu correo non recupera a conta de traballo: a xestión desactívaa e invita o correo novo. O ENS pide dobre factor ao persoal na categoría MEDIA: se o teu sistema o precisa, mira [`ens.md`](ens.md) (op.acc.6 e R14).
   - **Comproba** (co túnel aberto): `scripts/comprobar-realm.sh --vivo` pide o usuario de administración e falla se queda o grupo `plataforma`, se `furar-api` ten algún rol ou se algún cliente emite `furar_roles`, se queda algo do segundo factor (rol `furar-2fa`, niveis de acceso, accións do OTP ou dos códigos de recuperación activas), se as accións WebAuthn están activas, se `furar-web` pode pedir `offline_access`, se a política do contrasinal non é a de Furar ou se o correo non é o usuario.

O realm de produción (`deploy/keycloak/import/furar-realm.json`) non trae usuarios nin o cliente de probas `furar-e2e`. Garda os eventos de acceso e de administración 90 días (*Realm settings* → *Events*). Os correos e as pantallas de acceso e da conta saen en galego ou castelán segundo o idioma da persoa.

## 6. Copias de seguridade

```sh
scripts/copia.sh --conservar 14        # → copias/AAAAMMDD-HHMMSS/; borra as anteriores ás 14 últimas
```

Garda as bases `furar` (reservas, concellos, campos) e `keycloak` (usuarios e contrasinais cifrados), cunha suma SHA-256. As copias conteñen **datos persoais**: cífraas, lévaas fóra do servidor e fixa a conservación segundo a política do concello (`--conservar N` borra as antigas do servidor).

**Cifrado con [age](https://age-encryption.org)** (libre, BSD-3-Clause; `apt install age`). Xera a chave **no teu ordenador, non no servidor**, e garda a privada nun lugar seguro (sen ela non se pode restaurar):

```sh
age-keygen -o furar-copias.key          # no teu ordenador; imprime a chave pública age1…
```

No `.env` do servidor pon só a pública: `FURAR_COPIAS_AGE_DESTINATARIO=age1…`. Desde entón `copia.sh` escribe `*.dump.age` e o volcado nunca queda en claro no disco. Instala `age` no servidor (§1). Se falta, `copia.sh` e `restaurar.sh` usan unha imaxe local (`furar-age`, co binario oficial comprobado por SHA-256), pero constrúese a primeira vez que fai falta e precisa descargar age: non o deixes para o día da restauración. Antes de borrar nada, `restaurar.sh` comproba que a chave privada descifra a copia. `rclone config` crea antes o destino (`copias-concello`) co servidor de copias do concello.

**Copia diaria ás 3:00 e envío fóra** con `cron` (`crontab -e`) e [rclone](https://rclone.org) (MIT; SFTP, S3, Nextcloud…). No `.env`: `FURAR_COPIAS_DESTINO=copias-concello:furar` e, se queres outros prazos, `FURAR_COPIAS_CONSERVAR` (copias no servidor, 14) e `FURAR_COPIAS_DIAS_DESTINO` (días no destino, 30). `copia-diaria.sh` fai a copia, envíaa, borra no destino as de máis días (`rclone copy` só non borraría nunca) e rexistra cada fase:

```cron
0 3 * * * cd /opt/furar && scripts/copia-diaria.sh >> /var/log/furar-copia.log 2>&1
```

Se algunha fase falla, o rexistro di cal (`ERRO: …`) e o script sae con erro. Para enterarte sen mirar o rexistro, pon en `FURAR_COPIAS_PING_URL` o URL dun comprobador de tarefas (hai servizos libres e autoaloxables): recibe un aviso cada día que remata ben e outro en `/fail` se falla, e avisa se un día non chega ningún. Se o destino ten ciclo de vida propio (S3), podes poñer `FURAR_COPIAS_DIAS_DESTINO=0` e fixalo alí.

Restaurar (para os servizos, **borra os datos actuais** e carga a copia):

```sh
scripts/restaurar.sh copias/20261119-030000
# copia cifrada: trae a chave privada só durante a restauración
FURAR_COPIAS_AGE_IDENTIDADE=/ruta/furar-copias.key scripts/restaurar.sh copias/20261119-030000
```

A ida e volta, cifrada e sen cifrar, e a rotación compróbanse en cada verificación (D41: `scripts/proba-copia.sh`; D48 en modo produción).

## 7. Actualizar

1. Fai unha copia: `scripts/copia.sh`.
2. Le as notas da release (cambios que requiran acción).
3. Cambia en `.env` `FURAR_VERSION` e os tres `*_DIGEST` polos do novo `furar-<versión>.env`; actualiza o repositorio á mesma etiqueta (`git fetch && git checkout v0.2.0`).
4. `scripts/preflight.sh && docker compose pull && docker compose up -d --wait`. O servizo `migrate` aplica as migracións antes de que arrinque a API; se falla, a API non arrinca (`docker compose logs migrate`).

**Volver atrás** (nesta orde: se restauras antes, a API nova arranca e migra outra vez a copia):

1. Pon de novo en `.env` a versión anterior (`FURAR_VERSION` e os tres `*_DIGEST` do seu `furar-<versión>.env`) e volve á súa etiqueta: `git checkout v0.1.0`.
2. `scripts/restaurar.sh copias/<copia do paso 1>`. A copia garda de que versión é e a súa última migración (`furar.txt`); `restaurar.sh` négase se non coincide coa `FURAR_VERSION` do `.env`, sen parar nin borrar nada.

As migracións só van cara adiante: non hai *undo*. Volver atrás é sempre restaurar a copia feita antes de actualizar, e pérdese o que se rexistrase despois.

O realm de Keycloak só se importa na primeira instalación: os cambios de realm dunha versión (roles ou clientes novos, textos legais) aplícanse a man seguindo as notas da release, ata que exista unha ferramenta para iso ([ADR-0015](adr/0015-despregue.md)).

## 8. Operación

| Que | Comando |
|---|---|
| Estado | `docker compose ps` |
| Rexistros | `docker compose logs -f api` (rotan: 3 × 10 MB por servizo) |
| Buscar unha petición | Os rexistros da API son JSON ECS: `docker compose logs api \| grep '<X-Request-Id>'` (a cabeceira vén en cada resposta) |
| Métricas | `docker compose exec api wget -qO- http://127.0.0.1:9081/actuator/metrics/http.server.requests` (porto interno, non publicado) |
| Reiniciar | `docker compose restart api` |
| Reinicio do servidor | Os servizos volven sós (`restart: unless-stopped`) se Docker arrinca co sistema (`sudo systemctl enable docker`). Os parados a man (`docker compose stop` ou `down`) non: `docker compose up -d --wait` |
| Parar sen perder datos | `docker compose down` (nunca `down -v`: borra a base de datos) |
| Memoria | `docker stats --no-stream` |
| Levar os datos dun concello a outra instalación | Xestión: `GET /api/v1/municipalities/<slug>/export` (ficheiro `furar-export-<slug>-<data>.json`); na nova, a plataforma: `POST /api/v1/municipality-imports` co ficheiro ([ADR-0018](adr/0018-portabilidade.md)) |

Seguridade: mantén o sistema e Docker actualizados, deixa abertos só os portos 80 e 443, administra Keycloak polo túnel SSH (`FURAR_ADMIN_IPS=127.0.0.1/32`), protexe `.env` (`chmod 600`) e revisa os eventos de Keycloak. Medidas do ENS e o que queda pendente: [ens.md](ens.md). Vulnerabilidades: [SECURITY.md](../SECURITY.md).

### Empezar de cero

`docker compose down -v` borra **todos** os volumes do proxecto: a base de datos e os certificados. Non o uses sen máis: fai antes unha copia (`scripts/copia.sh`) e comproba que proxecto é (`docker compose config | sed -n 's/^name: //p'`: produción é `furar`; o perfil de proba do §10, `furar-local`). Para borrar só a base desta instalación e conservar os certificados:

```sh
cd /opt/furar
docker compose down
docker volume ls --filter label=com.docker.compose.project=furar   # mira que é o que vas borrar
docker volume rm furar_pgdata-prod
scripts/instalar.sh --substituir
```

A base nova nace cos contrasinais do `.env` (`--substituir` consérvaos); a conta de administración e os concellos hai que crealos outra vez (§3).

## 9. Problemas frecuentes

| Síntoma | Causa habitual |
|---|---|
| `instalar.sh` di «Falta o ficheiro furar-<versión>.env» | Descárgao de *Releases*, da mesma versión ca o código, e déixao no cartafol de Furar (§2). |
| `instalar.sh` di «non resolve no DNS» | Aínda non existe o rexistro do nome ou non se propagou (unhas horas): pídeo co texto do §1. `scripts/instalar.sh --comprobar-rede` repite a comprobación. |
| `instalar.sh` di «Portos ocupados por outro programa» | Outro servidor web (nginx, apache2) ou outro proxecto usa o 80 ou o 443: `sudo ss -ltnp \| grep -E ':(80\|443) '` di cal; páralo e desactívao (`sudo systemctl disable --now nginx`). |
| `instalar.sh` di «xa hai unha base doutra instalación» | Hai un volume `furar_pgdata-prod` dunha instalación anterior e o `.env` é novo, con outros contrasinais: recupera o `.env` anterior ou borra a base (§8, «Empezar de cero»). |
| `required variable … is missing a value` | Falta ese valor en `.env`: `scripts/instalar.sh --substituir` volve preguntar os datos e conserva os contrasinais. |
| A API reinicia e o rexistro di `Refusing to start with profile 'prod'` | Contrasinal curto ou de demo, ou `FURAR_PUBLIC_URL` sen `https`. |
| O navegador avisa do certificado (ou o instalador di que responde cun certificado que aínda non é de confianza) | O DNS aínda non apunta ao servidor ou o porto 80 está pechado (Let's Encrypt valida por el); os primeiros minutos é normal. Mira `docker compose logs web`. |
| Ao entrar, Keycloak di *Invalid parameter: redirect_uri* | `FURAR_PUBLIC_URL` non coincide co URL do navegador. Se o cambias despois da instalación, corrixe tamén *Clients* → `furar-web` → *Valid redirect URIs* (o realm só se importa a primeira vez). |
| Non chegan correos | Os da API: `scripts/instalar.sh --probar-correo` di se o servidor acepta o SMTP do `.env` e, se non, por que (§2, «Correo saínte»). Os de Keycloak (contas): SMTP en *Realm settings* → *Email* → *Test connection* (o `.env` só se le no primeiro arranque, §4). Se o servidor os acepta e non chegan: correo non desexado e SPF, DKIM e DMARC do remitente. |
| Non chegan os correos das reservas | Datos SMTP en `.env` e `docker compose logs api` (`mail for booking … not sent`). Cada correo garda o resultado en `notification_delivery` (`FAILED` co motivo) e reinténtase unha vez aos poucos minutos. |
| `/auth/admin` dá 403 | É o esperado: usa o túnel SSH (§5). |
| Entrei co correo de `FURAR_ADMIN_EMAIL` e non vexo «Novo concello» | O correo ten que estar verificado (a ligazón de «Verifica o teu correo») e ser o mesmo ca `FURAR_ADMIN_EMAIL`; se xa había unha administración, ese correo non dá nada (§3). |
| Unha persoa do persoal perdeu o acceso ao seu correo | Na pantalla **Persoal**, desactívaa e invita o correo novo; a conta vella queda sen acceso á xestión. Se só esqueceu o contrasinal, «Esqueciches o contrasinal?» na pantalla de acceso. |
| `instalar.sh` di «aínda arrincando: keycloak … Agardo ata 15 minutos máis» | Normal no primeiro arranque dun servidor lento: Keycloak importa o realm e pasa do prazo do seu healthcheck, e Compose rende («dependency failed to start: container …-keycloak-1 is unhealthy»). O instalador agarda e segue só. Se aínda así falla, `docker compose logs --tail 50 keycloak`; se só tardaba, `docker compose up -d --wait` remata o arranque. Nun ARM64 con v0.1.0 (só amd64, emulada) tarda moito máis: usa unha versión con arm64 ou §11. |
| `preflight.sh` di que Compose é vello | Actualiza o paquete `docker-compose-plugin` (2.24.4 ou posterior). |
| O mapa sae gris (as teselas dan 502 ou 504) | Caddy non chega ao servidor de teselas (por defecto `www.ign.es`): proba `curl -I https://reservas.concello.gal/tiles/14/7905/5878.png` e mira `docker compose logs web`. Detrás dun proxy, `FURAR_HTTP_PROXY` (§13). Se o servidor non pode saír a Internet, `FURAR_MAP=off`. |
| Ao dar de alta unha instalación non saen suxestións de enderezo | A API non chega a CartoCiudad (aviso «enchelo a man» na web): saída a `www.cartociudad.es:443` ou `FURAR_HTTP_PROXY` (§13). |
| PostgreSQL avisa `collation "gl-x-icu" has version mismatch` | Actualizouse a ICU da imaxe de PostgreSQL: §13, colacións. |

## 10. Probar en local antes de instalar

Cun ordenador con Docker e 4 GB libres:

```sh
for s in api web keycloak; do docker compose --profile full build $s; done
docker compose --profile full up -d --wait
```

Abre http://localhost:8080/naron. Usuarios de demo en [`deploy/keycloak/README.md`](../deploy/keycloak/README.md).

Os datos de demo teñen as datas relativas ao día en que se aplican. O primeiro arrinque de cada día volve aplicar as sementes (`api/src/main/resources/db/demo`, placeholder `demo_day`), e a historia de reservas dos indicadores chega ata hoxe e ás reservas xa feitas da semana seguinte. As reservas de exemplo de «As miñas reservas» e da xestión quedan, en cambio, nas datas do primeiro arrinque: antes de ensinar a demo despois de varios días, recrea a base con `docker compose --profile full down -v` (borra o volume `furar-local_pgdata`, só o deste perfil; os de produción son doutro proxecto, `furar`) e volve levantala.

Este perfil é só para probar: usa contrasinais públicos de demo (tamén o administrador de Keycloak). Por iso só escoita en `127.0.0.1`: noutro equipo da rede non se ve. Para probalo nun servidor, entra cun túnel (`ssh -L 8080:127.0.0.1:8080 servidor` e abre http://localhost:8080/naron) en vez de abrilo á rede (`FURAR_HTTP_BIND`, ver `.env.example`). En produción úsase sempre `compose.prod.yaml`, con outro proxecto de Compose (`furar`, fronte ao `furar-local` deste perfil): contedores e volumes distintos, así que probar e instalar no mesmo equipo non se pisan.

## 11. Servidor ARM64 ou rexistro propio

As imaxes publicadas son para `linux/amd64` e `linux/arm64`: cada arquitectura pasa o e2e da CI no seu propio runner e a release publica un índice das dúas, así que un servidor ARM64 instálase igual (§2). A excepción é v0.1.0, só `linux/amd64`: nun ARM64 Docker emúlaa e todo vai moi lento (Keycloak pode tardar máis de 5 minutos en arrincar).

Para usar un rexistro do concello (ou v0.1.0 nun ARM64), constrúe as imaxes desde a mesma etiqueta no propio servidor (sae a súa arquitectura) e súbeas; todas as imaxes base son multiarquitectura:

```sh
git checkout v0.1.0
docker login rexistro.concello.gal
FURAR_REGISTRY=rexistro.concello.gal/furar scripts/publicar-imaxes.sh v0.1.0 --publicar
```

O script escanea con Trivy e escribe `.verificar/imaxes/furar-v0.1.0.env` cos digests das túas imaxes: cópiao ao `.env` en lugar do da release.

## 12. Cl@ve e Chave365

Furar acepta Cl@ve e Chave365 como **provedores de identidade SAML 2.0 de Keycloak** ([ADR-0019](adr/0019-autenticacion-publica-clave.md)): a veciñanza ve o botón na pantalla de acceso e volve a Furar coa sesión iniciada; a web e a API non cambian. En `dev` e `full` podes probalo xa co IdP simulado: na pantalla de acceso, «Cl@ve (simulada, só demo)», NIF `00000000T` e o contrasinal de demo.

Antes: o organismo que presta o servizo (o concello ou, nun despregue compartido, a Deputación) **solicita a adhesión** á pasarela (Cl@ve: SGAD, contorno de probas e despois produción; Chave365: Amtega) e obtén os seus metadatos SAML e un certificado de sinatura propio. Con eles, co túnel SSH aberto (§5):

1. **Exportar os metadatos de Furar (SP)**, que pide a pasarela: crea antes o provedor (paso 2) e descarga `https://reservas.concello.gal/auth/realms/furar/broker/clave/endpoint/descriptor`. Envíallos á pasarela coa solicitude.
2. **Dar de alta o provedor:** realm `furar` → *Identity providers* → *SAML v2.0*:
   - *Alias* `clave` (ou `chave365`); *Display name* «Cl@ve».
   - *Use entity descriptor* activado e *SAML entity descriptor* = URL dos metadatos da pasarela; *Use metadata descriptor URL* activado (as chaves da pasarela renóvanse soas).
   - *Service provider entity ID*: o que figure na adhesión.
   - *Principal type* `Subject NameID`; *NameID policy format* o que indique a pasarela (persistente se o permite).
   - *Want AuthnRequests signed* **activado**, co certificado do organismo: *Realm settings* → *Keys* → *Add provider* → `rsa` (importa a clave e o certificado da adhesión) e escolle o seu *Key ID* en *Signing key*.
   - *Validate signatures* e *Want assertions signed* **activados**. *Sync mode* `force`. *Trust email* só se a pasarela verifica o correo.
   - *First login flow* `first broker login` (se xa existe unha conta co mesmo correo, Keycloak pide entrar con ela antes de ligalas; nunca automaticamente).
3. **Mapeadores** (*Mappers* do provedor): *Attribute importer* para o nome, os apelidos e o correo cos nomes de atributo da pasarela → `firstName`, `lastName`, `email`; e *Username template importer* con `${ALIAS}.${NAMEID}` (co correo como usuario, a conta nova colle o correo da pasarela; o mapeador queda por se se desactiva esa opción). **Non importes o NIF** salvo que un trámite o esixa ([ADR-0017](adr/0017-datos-persoais.md)); se fai falta, como atributo da conta en Keycloak, nunca nun mapeador do token.
4. **Cl@ve, nivel de garantía e atributos eIDAS:** a pasarela pide as extensións `RequestedAttributes` e o nivel na `AuthnRequest`; Keycloak 26 non as envía por si só. Ata ter a extensión de Keycloak prevista no ADR-0019, consulta co SGAD se o contorno de probas acepta a petición sen elas.
5. **Probar** no contorno de probas da pasarela: entra desde `https://reservas.concello.gal/naron` → «Cl@ve»; en *Users* a persoa nova ten nome e correo e, en *Identity provider links*, a ligazón `clave`. Comproba que o nome de usuario é o correo e non o NIF.
6. **Baixa:** desactiva o provedor (*Enabled* off); as contas ligadas seguen podendo entrar co correo e contrasinal se os teñen.

Chave365: o mesmo procedemento cos metadatos e o protocolo que confirme a Amtega.

## 13. Mapa, enderezos e saídas a Internet

O navegador da veciñanza e do persoal só fala co servidor do concello: as teselas do mapa e as suxestións de enderezos pídeas Furar (ADR-0023).

**Destinos saíntes** (abre só estes no cortalumes ou no proxy):

| Quen | Destino | Para que |
|---|---|---|
| API | `www.cartociudad.es:443` | Suxestións de enderezos ao dar de alta ou editar unha instalación (só persoal de xestión). Con `FURAR_GEOCODER=fixed`, ningún. |
| web (Caddy) | `www.ign.es:443` (ou o host de `FURAR_MAP_TILES_UPSTREAM`) | Teselas do mapa (`/tiles/{z}/{x}/{y}.png`, z 0–19). Con `FURAR_MAP=off`, ningún. |
| web (Caddy) | `acme-v02.api.letsencrypt.org:443` | Certificado HTTPS (xa estaba). |

Se CartoCiudad non responde (2 s para conectar, 3 s para responder), a web deixa encher o enderezo e o punto a man; se o servidor de teselas non responde, o mapa queda gris pero o enderezo en texto segue á vista.

**Que sae cara ao servidor de teselas.** Caddy borra todas as cabeceiras da petición do navegador (IP e `X-Forwarded-*`, cookies, `Authorization`, `Referer`, idioma e navegador) e envía só `Host`, `Accept: image/png` e o `User-Agent` de Furar coa ligazón de `FURAR_SOURCE_URL`; a query do navegador tampouco pasa (só a da plantilla). De volta, quita `Set-Cookie` e pon `Cache-Control: public, max-age=604800` (o navegador garda cada tesela unha semana). Só acepta `GET` e `HEAD` (o resto, 405) e z de 0 a 19 (o resto de `/tiles`, 404). Próbao X08 (`scripts/proba-caddy.sh`) cun servidor de teselas falso.

**Proxy corporativo.** `FURAR_HTTP_PROXY` (p. ex. `http://proxy.concello.local:3128`) chega á API, que o usa para CartoCiudad, e a Caddy como `HTTPS_PROXY`: Caddy abre por el o túnel (`CONNECT`) cara ao servidor de teselas e a Let's Encrypt (X08 comproba o `CONNECT`). O tráfico interno (`api`, `keycloak`) vai en HTTP e non pasa polo proxy. O proxy ten que permitir `CONNECT` ao porto 443 dos destinos de arriba; se inspecciona TLS cunha CA propia, a API e Caddy rexeitarán a conexión: exclúe eses destinos da inspección.

**O mapa.** Por defecto (local, demo e produción) Furar usa o [Mapa base do IGN](https://www.ign.es/wmts/ign-base?SERVICE=WMTS&REQUEST=GetCapabilities) (servizo WMTS público e sen clave, capa `IGNBaseTodo`, teselas PNG en Web Mercator). Os datos do IGN usan a licenza CC BY 4.0: hai que recoñecelos á vista, e Furar pon ao pé de cada mapa «CC BY 4.0 scne.es» coa ligazón a `https://www.scne.es`, a fórmula do propio servizo. Non hai que configurar nada.

**Sen mapa.** Con `FURAR_MAP=off` no `.env` (e `docker compose up -d` para aplicalo), a web non carga ningún mapa: na ficha pública e no listado queda o enderezo en texto e, ao editar unha instalación, a latitude e a lonxitude énchense co buscador de enderezos ou a man. Caddy responde 404 en `/tiles` e non sae nada cara a fóra. Útil se o servidor non pode saír a Internet.

**Outro servidor de teselas (avanzado).** Calquera servidor de teselas ráster PNG de 256 px en Web Mercator (un propio da Deputación, un contratado…). Catro variables no `.env`:

| Variable | Que é | Exemplo |
|---|---|---|
| `FURAR_MAP_TILES_UPSTREAM` | Esquema e host do servidor | `https://teselas.concello.gal` |
| `FURAR_MAP_TILES_TEMPLATE` | Camiño e query dunha tesela, con `{z}`, `{x}` e `{y}`, sen espazos | `/mapa/{z}/{x}/{y}.png` ou `/wmts?LAYER=base&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}&FORMAT=image/png` |
| `FURAR_MAP_ATTRIBUTION` | O recoñecemento que pide a licenza dos datos (vai sempre á vista) | `© Colaboradores de OpenStreetMap` |
| `FURAR_MAP_ATTRIBUTION_URL` | Ligazón do recoñecemento | `https://www.openstreetmap.org/copyright` |

`{y}` conta desde arriba (esquema XYZ, o habitual; no WMTS, `TILEROW`). A query do navegador nunca pasa ao servidor; a da plantilla si, así que unha chave de acceso do provedor pode ir nela (non é un dato da persoa; garda o `.env` coma os contrasinais). Proba unha tesela con `curl -I https://reservas.concello.gal/tiles/14/7905/5878.png` (200 e `image/png`). Se o servidor responde outra cousa (HTML, SVG, un erro de cota), Caddy dá 502 e o mapa sae gris. Cun provedor contratado, revisa co DPD o contrato de encargo: non lle chega ningún dato da persoa, pero si a IP do servidor e que zonas se miran. Os servidores públicos de OpenStreetMap non admiten o tráfico dun servizo en produción.

**Colacións ICU de PostgreSQL.** As listas alfabéticas ordénanse no servidor coas colacións `gl-x-icu` e `es-x-icu`. Se unha imaxe nova de PostgreSQL trae outra versión de ICU, PostgreSQL avisa nos rexistros (`collation "gl-x-icu" has version mismatch`). Ningún índice usa esas colacións, así que abonda con actualizar a versión gardada (despois dunha copia, §6):

```sh
docker compose exec postgres psql -U postgres -d furar \
  -c 'ALTER COLLATION pg_catalog."gl-x-icu" REFRESH VERSION' \
  -c 'ALTER COLLATION pg_catalog."es-x-icu" REFRESH VERSION'
```

Se o aviso fala da base enteira (`database "furar" has a collation version mismatch`), engade `-c 'ALTER DATABASE furar REFRESH COLLATION VERSION'` e, antes, `REINDEX DATABASE furar` (esa colación si a usan os índices de texto).
