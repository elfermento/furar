<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0034 · Datos abertos: instalacións, peches e ocupación agregada, por concello e de toda a instalación

- **Estado:** aceptada · 2026-11-11 (decidida polo mantedor, sen preguntas abertas; construída o 2026-11-11)
- **Revisada 2026-11-11:** a ocupación publícase en bruto, por tramos fixos de 15 minutos, co uso de cada tramo e só das instalacións que xa mostran os seus ocos; as instalacións van nun só ficheiro (unha fila por uso no CSV, os usos dentro no JSON e no GeoJSON) e levan os campos personalizados públicos; a ocupación descárgase polas datas escollidas na tarxeta; CORS aberto só nos ficheiros e na ficha; ficha DCAT-AP-ES co publicador por DIR3. As columnas finais de cada conxunto están no punto 16. Quedan fóra, por non ser públicas hoxe: o tipo de instalación e a data de fin da tempada; o código RIDEGA tampouco se publica.
- **Fonte de produto:** decisións do mantedor do 2026-11-11 sobre a páxina «Datos abertos» (o que ve calquera, o que ve a xestión e o conxunto de toda a instalación). Na xestión: «Configuración adicional», «Penalizar as faltas», o botón «Vista previa» antes do interruptor e o «?» que remata dicindo que, se non se activa, a páxina non se ve nin está no pé (puntos 25 e 27). Na páxina pública: un ficheiro de instalacións e as datas da ocupación na tarxeta; os textos de «Detalles» e «Columnas», os seus exemplos e a frase de privacidade son os desta ADR (puntos 16 e 27). Pecha o «traballo futuro» de [`reutilizacion.md`](../reutilizacion.md).
- **Constrúese enriba de:** [ADR-0030](0030-portada-comun.md) (rutas públicas sen concello, concello publicado, lectura concello a concello), [ADR-0026](0026-indicadores-fase-1.md) (definición de ocupación), [ADR-0032](0032-non-presentados-e-validacion.md) (tarxeta «Non presentados» de Concello › Xeral) e [ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md) (horas ocupadas á vista).
- **Revisa:** ADR-0032 puntos 2 e 14 (a tarxeta pasa a ser unha fila), ADR-0030 (tres grupos de rutas máis sen concello), ADR-0026 (`indicators` publica unha consulta e deixa de ser «só o panel»), [ADR-0018](0018-portabilidade.md) (un campo opcional máis no concello), [ADR-0006](0006-multiconcello.md) (un slug reservado máis), [ADR-0007](0007-seguridade-pechada.md) (CORS aberto só nos GET dos ficheiros de datos abertos) e [ADR-0021](0021-sistema-de-deseno.md) (interruptor, «?» e pregable).
- **Como ler esta ADR:** «Decidido polo mantedor» é produto e non se toca. A «Decisión» é o deseño técnico.
- **Revisada 2026-11-12 ([ADR-0040](0040-buscar-xente-para-xogar.md)):** as partidas abertas non se publican nos datos abertos nin na ficha DCAT.
- **Revisada 2026-11-12 ([ADR-0039](0039-tempada-de-entidades.md)):** a ocupación publicada inclúe as sesións das tempadas (son reservas); nin as entidades nin as tempadas se publican.

## Contexto

O que un concello sabe do seu servizo de deportes (onde están as instalacións, cando abren, canto se usan) é información pública que hoxe só se pode ler pantalla a pantalla. A Lei 37/2007, de reutilización da información do sector público, pide publicala en formatos abertos e lexibles por máquina, e os portais de datos abertos recóllena cunha ficha de catálogo.

Tres cousas que non poden fallar: nunca sae un dato de quen reserva; os datos dun concello só se len dentro dese concello ([ADR-0006](0006-multiconcello.md)); e unha petición sen sesión non pode custar máis canto máis medre a instalación.

Non é a portabilidade ([ADR-0018](0018-portabilidade.md)): `furar-export` leva **todo** o dun concello, con permiso, para mudarse. Os datos abertos levan **só o público**, sen permiso, para reutilizar.

## Decidido polo mantedor (2026-11-11)

- **Tres conxuntos, nunca nomes nin datos de quen reserva:** (1) instalacións publicadas e á vista da veciñanza, cos seus usos (o que xa mostra a ficha pública: datos, contacto, servizos, deportes, horario e, de cada uso, modo de aluguer, franxa, partes ou aforo, días, datas, prezo e condicións da reserva); (2) peches (festivos e días enteiros pechados); (3) ocupación agregada por instalación, día e tramo de 15 minutos: só a porcentaxe, só de días pasados, dispoñible dos últimos 12 meses, actualizada cada noite co día anterior.
- **Ocupación en bruto e exacta (2026-11-11):** a unidade é un tramo fixo de 15 minutos. Unha fila por instalación, día e tramo no que a instalación estaba aberta, co inicio, a fin, o uso e a ocupación dese tramo; sen fila, pechada ou bloqueada. Sen porcentaxes intermedias que non correspondan a nada real: quen queira saber se algo estivo ocupado de 10:30 a 10:45 veo exacto. Para agrupar polas franxas reais, a franxa e o modo de cada uso van no ficheiro de instalacións.
- **Un só ficheiro de instalacións (2026-11-11):** en CSV, unha fila por uso (os datos da instalación repítense en cada fila); unha instalación publicada sen usos á vista sae nunha fila coas columnas do uso baleiras. En JSON e GeoJSON, cada instalación leva dentro os seus usos. Sen ficheiro «Usos e franxas».
- **Ocupación por datas (2026-11-11):** as datas escóllense na tarxeta da pestana «Ocupación» (o filtro de datas da xestión de reservas), por defecto o último mes enteiro; como moito 12 meses nun concello e un mes no conxunto de todos, sempre dentro dos últimos 12 meses.
- **Pestana «Peches»** (antes «Festivos e peches»): o conxunto leva os festivos e os peches de día enteiro.
- **Formato común (2026-11-11):** listas dentro dunha cela separadas por `|`; decimais sempre con punto; datas `AAAA-MM-DD` e instantes ISO 8601 con desfase, na zona Europe/Madrid, que se declara; os valores fixos (códigos) son iguais en galego e en castelán; os textos libres (nome do uso, nomes das partes, motivo dun peche) poden levar nomes propios e só levan aviso onde xa estaba previsto (punto 9).
- **Aforo público:** o aforo dos usos por prazas publícase (xa se ve na ficha pública). O que nunca sae é quen reserva: ningún ficheiro leva nomes nin datos de quen reserva.
- **Formatos:** CSV e JSON nos tres; GeoJSON ademais nas instalacións. Unha ficha de catálogo DCAT-AP para portais de datos abertos.
- **Licenza:** CC BY 4.0, con atribución ao concello (no conxunto de todos, «o concello de orixe»).
- **Páxina pública «Datos abertos»** en cada concello, ligada desde o pé, e outra de toda a instalación xunto á portada común, cos concellos publicados que o teñan aceso e unha columna máis (`concello`). Estrutura: título; unha frase a todo o ancho; tres pestanas de liña; a frase do conxunto debaixo; unha tarxeta con «Actualizado o…», «Descargar» e dous pregables pechados e independentes («Detalles» e «Columnas»); ao final, á parte e en pequeno, «Sen datos de persoas», «Uso libre» e «Portais de datos abertos». Na pestana «Ocupación», a tarxeta leva ademais o filtro «Datas» xunto a «Descargar». «Descargar» abre un diálogo para escoller formato. No móbil, a data curta («11 nov 2026»).
- **Xestión:** en Concello › Xeral, unha soa tarxeta «Configuración adicional» con dúas filas: «Penalizar as faltas» (cos seus tres números se está acesa) e «Datos abertos», cun botón «Vista previa» antes do interruptor. O «?» de «Datos abertos» remata dicindo que, se non se activa, a páxina non se ve nin aparece no pé.
- **Aceso por defecto.** Ao apagalo: desaparece a ligazón do pé, a páxina deixa de existir (404) e os datos do concello saen do conxunto de todos.
- **Ocupación sen limiar mínimo.** Publícase toda hora aberta das instalacións que entran na ocupación (punto 3), tamén as celas cunha soa reserva. As razóns están no punto 9.
- **Motivo dos peches.** Publícase, con dúas condicións: (a) o «?» do campo «Motivo» das excepcións e dos bloqueos avisa «Vese na web e nos datos abertos; non poñas nomes de persoas», e os demais campos de texto libre que xa son públicos (normas e descrición) levan o mesmo aviso; (b) os peches con data anterior a `open_data_since` saen sen motivo, porque se escribiron sen ese aviso.
- **CORS:** `Access-Control-Allow-Origin: *` só nos GET dos ficheiros e da ficha de catálogo (punto 20).
- **Publicador da ficha de todos:** por variable de despregue; sen ela, non hai ficha de todos (punto 19).
- **O uso en cada tramo:** a ocupación leva tamén o uso que ocupaba a instalación nese tramo (identificador e nome; baleiro se estaba libre).
- **Bloqueos:** un tramo bloqueado polo concello non ten fila, coma un pechado.
- **Campos personalizados nas instalacións:** o conxunto «Instalacións» leva os valores dos campos personalizados da instalación que o concello definiu para «Todo o mundo (web pública)»; nunca os do persoal nin os marcados como dato persoal (punto 16).
- **Tamén decidido:** festivos e peches do 1 de xaneiro do ano anterior ao 31 de decembro do seguinte, só peches de día enteiro; a páxina existe co interruptor aceso aínda que o concello non saia na portada común; «Vista previa» só aceso e gardado; os textos legais do punto 28; no editor de campos personalizados, cada interruptor na súa fila; e o aviso da descrición: «Vese na web; non poñas nomes de persoas.».
- **Campos personalizados:** os públicos da instalación móstranse tamén na ficha pública da instalación (a colocación decídese aparte); no conxunto de todos van só en JSON e GeoJSON; os campos públicos do propio concello non entran nesta fase.
- **Horario por días, sen excepción á regra:** en `day-row`, o interruptor vai á dereita do todo da fila de cada día e as horas só aparecen co día aceso; apagado, a fila queda co nome do día e o interruptor.
- **Regras de interface para toda a aplicación:** no interruptor, o nome á esquerda e o interruptor á dereita, co «?» pegado ao nome; o «?» pasa a 18 px con contorno (círculo con bordo e «?» da cor principal sobre o fondo) e área de pulsación de 24 × 24 px como mínimo; peza nova: o pregable.

## Decisión

### Modelo

1. **Módulo novo `opendata`** (`gal.furar.opendata`), creado con `scripts/novo-modulo.sh` e reducido coma `indicators`: sen entidades JPA, sen CRUD e sen permisos propios (todo é público; o interruptor é do concello). Depende de `shared`, `facility`, `indicators` e `municipality`. Só serializa: os datos son dos seus donos.
   - **Do que xera o script bórrase:** o evento `OpenDataCreated`, `OpenDataPermission`, `OpenDataSummary` e `OpenDataQueries` (ninguén le de `opendata`); en `domain`, `OpenData` e `OpenDataId`; toda a persistencia JPA (`…Entity`, `…JpaRepository`, `…PersistenceAdapter`, `…PersistenceMapper`) e o porto `OpenDataRepository`; os casos de uso e comandos de alta, lectura, lista, edición, importación, exportación e restauración (`Create…`, `Get…`, `List…`, `Update…`, `Import…`, `Export…`, `Restore…`, `OpenDataView`) cos seus servizos; `OpenDataDataSection` e `OpenDataWebMapper`; os DTO de alta e edición; os tests da plantilla (`OpenDataIT`, `OpenDataServiceTest`, `InMemoryOpenDataRepository`, `OpenDataTest`); na web, as páxinas de lista e edición, o seu `store` e o e2e da plantilla; e o fragmento de ruta que engade á xestión. **Quedan e reescríbense:** `package-info.java`, `OpenDataError`, as mensaxes `messages/opendata_{gl,es}.properties`, a migración (punto 3) e o controlador. Créase sen campos personalizados (sen `--custom-fields`).
   - `facility` publica nunha interface propia da súa API, `FacilityOpenDataQueries`, as lecturas novas, con records da raíz (A19) e un número fixo de sentenzas: `publicData()` (as instalacións do listado público co que xa leva a ficha pública, `PublicFacilityDetailResponse`: nome, descrición, enderezo e coordenadas, teléfono, correo, `temporarilyClosed`, `onlineBooking`, `showFreeSlots`, `covered`, servizos (`facility_amenity`), deportes co seu código, a semana da tempada vixente hoxe, as normas, os valores dos campos personalizados públicos (punto 16) e os usos que mostra a ficha, cos mesmos campos de `PublicUseResponse`: id, nome, modo, partes vivas, `capacity`, `slot_minutes`, `weekdays`, períodos, prezo e condicións da reserva), o nome e o modo dos usos que cita a ocupación, tamén retirados ou caducados (a lectura que xa había, `FacilityQueries.usesById`), `publicClosures(from, to)` (un día por fila: excepción pechada ou bloqueo de todo o día, co motivo) e `holidays(from, to)` (o catálogo de festivos) e `archivedParts(useIds)` (as partes retiradas, para contar as vivas de cada día).
   - `indicators` publica a súa primeira API pública, `IndicatorsQueries.quarterOccupancy(from, to, facilityIds)` (punto 4).
   - `municipality` engade `MunicipalityQueries.openData(municipalityId)` (aceso ou non, desde cando, nome, entidade que se cita e DIR3) e o campo `openData` en `CatalogMunicipalities.Listed`.
2. **Activación: `municipality.open_data_since timestamptz`** (`V202611111200__municipality_open_data.sql`): `add column open_data_since timestamptz` e, na mesma migración, `update municipality set open_data_since = now()`. Sen `default` permanente: a alta e a importación póñena co reloxo da aplicación (`Clock`), coma o resto das datas do dominio. Nulo = apagado. Así queda aceso en todos os concellos que xa existen, coa data da migración. Unha data e non un booleano polo mesmo motivo ca `no_show_penalty_since`, porque a ficha de catálogo pide a data de publicación (`dct:issued`) e porque marca que motivos de peche se publican (punto 7). A data non sae da API: `MunicipalityResponse.openData` é un booleano. Volver acendelo pon a data nova.
3. **A ocupación precalcúlase cada noite** nunha táboa do módulo, `open_data_occupancy` (`V202611111210__opendata_create.sql`), **só das instalacións activas que hoxe mostran os seus ocos** (`onlineBooking || showFreeSlots`): son as únicas nas que a web xa ensina sen sesión que horas están libres ([ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md) e `AvailabilityService`). Dunha instalación que se reserva só no mostrador e non mostra ocos non se publica a ocupación.

   | Columna | Tipo | Nota |
   |---|---|---|
   | `municipality_id` | `uuid not null` | FK a `municipality` |
   | `facility_id` | `uuid not null` | FK composta `(municipality_id, facility_id)` a `facility`, `on delete cascade`; entrada nova en `CrossModuleForeignKeys` (`open_data_occupancy` → `facility`, táboa raíz) |
   | `day` | `date not null` | día de Europe/Madrid |
   | `quarters` | `smallint[] not null` | un valor por tramo de 15 minutos do día, contados en tempo real desde a medianoite de Europe/Madrid (96 tramos; 92 o día do cambio de hora de marzo e 100 o de outubro): 0–100 se estaba aberta, `null` se estaba pechada ou bloqueada |
   | `uses` | `uuid[] not null` | os usos distintos que ocuparon a instalación ese día (normalmente un ou dous) |
   | `quarter_uses` | `smallint[] not null` | por tramo, a posición en `uses` do uso que o ocupaba; `null` se estaba libre, pechada ou bloqueada |

   **Un día por fila, non un tramo por fila:** unhas 7.300 filas por concello de 20 instalacións e 12 meses (≈ 4 MB co uso, gardado como posición nun vector curto e non como un UUID por tramo), fronte a máis de 400.000 cun tramo por fila. Os ficheiros despregan o vector en filas ao escribir (punto 16). Gardar os tramos por instante e non por «hora:minuto» evita tamén que o día do cambio de hora de outubro dous tramos teñan a mesma clave. Clave primaria `(municipality_id, facility_id, day)`, índice `(municipality_id, day)`, RLS coa política `open_data_occupancy_tenant`. Sen `id`, sen reserva, sen conta; o uso só como identificador. SQL nativo `@NativeSql(MUNICIPALITY)` (`OpenDataOccupancySql`, co seu `OpenDataOccupancySqlTenantIT`); como non hai entidades `@TenantId`, A21 non pide sección de portabilidade.

   **Por que precalcular e non calcular ao pedir:**
   - *Custo.* Calcular doce meses por petición sen sesión son as horas abertas de 365 días de cada instalación máis un agregado sobre todas as reservas do ano, por concello; no conxunto de todos, multiplicado polos concellos. Precalculado, a descarga é unha lectura por índice e o cálculo faise unha vez, de noite, só do día novo.
   - *Estabilidade.* O denominador de [ADR-0026](0026-indicadores-fase-1.md) é o horario de hoxe: calculado ao pedir, cambiar unha tempada cambiaría a ocupación publicada de meses atrás. A foto de cada noite queda fixa.
   - *Privacidade.* A táboa só garda a porcentaxe. A lectura pública non toca `booking` nunca: un erro nela non pode sacar unha reserva. E cando as reservas se anonimizan ([ADR-0017](0017-datos-persoais.md)) a serie publicada non cambia.
4. **Ocupación por tramos de 15 minutos, en bruto (decisión do mantedor).** A unidade é un tramo fixo de 15 minutos (`10:30-10:45`). Cada quen agrupa despois como queira: por horas, polas franxas de cada uso (`franxa_minutos` e `modo_aluguer` do ficheiro de instalacións, punto 16) ou por días. Así non hai porcentaxes que non correspondan a nada real: coa hora de reloxo, unha pista de franxas de 90 minutos reservada de 17:00 a 18:30 daba un 50 % de 18:00 a 19:00; por tramos, 100 de 17:00 a 18:30 e 0 de 18:30 en diante.
   - **15 minutos é exacto sempre**, comprobado no esquema e no dominio: os tramos do horario (tempadas e festivos, `V202610251157`), as excepcións (`V202610251227`) e os bloqueos (`V202610251317`) esixen `start_minute % 15 = 0 and end_minute % 15 = 0`; as franxas dos usos son de 30, 60, 90 ou 120 minutos (`facility_use_slot_minutes_ck`); e unha reserva só pode empezar na grella do seu uso, que parte do inicio do tramo do horario (`SlotGrid.onGrid`, `OpeningInterval.alignFromMinute`) e avanza en franxas enteiras. Todo inicio e toda fin de reserva, apertura, peche e bloqueo cae nun múltiplo de 15 minutos, tamén os días de cambio de hora (o desfase é dunha hora enteira). Ningún tramo queda partido.
   - **Valor do tramo**, coa ponderación dos indicadores (`UseWeight.of(modo, partes, aforo)`, ADR-0026 punto 5), **co aforo e as partes que había ese día, non cos de hoxe**: enteira, 100 se hai unha reserva `CONFIRMED` que o cubre e 0 se non; por partes, partes reservadas / partes vivas ese día (as non retiradas antes dese día, `facility_use_part.archived_at`); por prazas, a suma das prazas reservadas (`booking.seats` das reservas confirmadas, o mesmo que `use_slot.taken`) / o aforo desa franxa (`use_slot.capacity`, que se conxela por franxa; sen fila en `use_slot`, ninguén reservou e o valor é 0). **Un non presentado conta como reservado** (a reserva segue `CONFIRMED`, ADR-0032): a ocupación é «reservado», non «usado». **As canceladas non contan.** **Límite coñecido:** a parte non garda cando se creou, así que na primeira carga dos 12 meses (punto 5) unha parte engadida despois conta tamén nos días anteriores; cada noite, co día de onte, o valor é exacto. **Un só uso por tramo:** en cada instalación e franxa só pode haber un uso ([ADR-0012](0012-ocupacion-exclusiva-e-prazas.md)), e a base garánteo (`booking_occupancy`, `exclude using gist (facility_id with =, use_id with <>, period with &&) where (active)`). `blocks_others` non o cambia: é un uso que ese día deixa fóra os demais, non dous usos á vez. Así cada tramo ocupado ten un único uso e a porcentaxe dese uso é tamén a da instalación (unha das dúas metades da pista, 50, co uso «Media pista»): quen queira a ocupación total da instalación non ten que sumar nada. O tope de 100 (`Ratio`) queda como garda. Redondeo ao enteiro máis próximo (a metade, cara arriba).
   - **Só a porcentaxe** en cada fila, un único número comparable entre modos. O aforo e as partes de hoxe están no ficheiro de instalacións; os dun día pasado poden ser outros, por iso a porcentaxe se calcula cos dese día. Ningunha fila leva quen reservou.
   - **Filas:** unha por instalación, día e tramo no que estaba aberta, tamén se a ocupación é 0. **Pechada ou bloqueada polo concello, sen fila:** un bloqueo non é uso da veciñanza, senón tempo que non se podía reservar; coma un peche, quita o tramo do que se publica. Dino «Columnas» («Sen fila: pechada ou bloqueada»).
   - **Que se comparte cos indicadores e que non:** `IndicatorsQueries.quarterOccupancy` reutiliza `UseWeight` (o peso de cada modo), `Ratio` (o tope e o redondeo) e os tramos abertos do horario que xa le o panel (tempada, festivos e excepcións, menos bloqueos). Non reutiliza a consulta por hora (`perUseWeekdayHour`) nin `OpeningMinutes` por hora: a consulta nova de `booking`, `BookingStatistics.confirmedIntervals(from, to, facilityIds)`, devolve por reserva confirmada o uso, a parte, o inicio, a fin e as prazas, e por franxa dos usos por prazas o aforo de `use_slot` (records da raíz de `booking`, sen persoa; `BookingStatsSql`, A8b e RLS non cambian de forma); as partes vivas de cada día saen de `facility` (`FacilityQueries`, coa súa `archived_at`); e o dominio de `indicators` (`QuarterOccupancy`) córtaas en tramos de 15 minutos. Unha definición, unha soa vez: se cambia o peso dun modo, cambia no panel e nos datos abertos.
5. **Tarefa nocturna `OpenDataRefreshService`** (`@PlatformTask`, `@Scheduled(cron = "${furar.opendata.refresh-cron:0 15 3 * * *}")`, o patrón de `NoShowService`: os concellos con `MunicipalityDirectory`, cada un co seu `TenantContext.runAs` e as súas transaccións; un fallo nun concello non para os demais e o rexistro técnico leva só o tipo do erro).
   - **Datas fixas ao empezar:** `hoxe = LocalDate.now(clock.withZone(Europe/Madrid))` e `onte = hoxe − 1`, co `Clock` da aplicación, unha vez por execución: unha tarefa que cruce a medianoite non cambia de día a metade.
   - **Apagado:** borra as súas filas das dúas táboas do módulo. Non se garda o que non se publica.
   - **Aceso:** `open_data_dataset` garda, na fila `OCCUPANCY`, **`calculated_through date`** (o último día calculado; nulo antes da primeira vez). A tarefa calcula desde `max(calculated_through + 1, onte − 12 meses + 1)` ata `onte`: sen valor, os 12 meses enteiros, unha soa vez. Cada anaco (31 días como moito) vai na súa transacción: `delete` dos días do anaco e `insert`, sen `on conflict` (a garda do SQL nativo non o le), e no mesmo commit sobe `calculated_through` ao último día do anaco. Así un concello sen ningunha fila (porque as súas instalacións non mostran ocos ou non abren) non recalcula 12 meses cada noite: o que avanza é a data, non as filas.
   - **Que instalacións:** as do listado público, activas e con `onlineBooking || showFreeSlots` **no momento da tarefa**. Tamén borra as filas das que hoxe xa non cumpren (de baixa, en borrador, pechadas temporalmente ou sen ocos visibles).
   - **Ocos declarados:** unha instalación que entra máis tarde (sae do borrador, volve da baixa ou acende «ocos visibles») empeza a súa serie a noite seguinte; os días anteriores non se enchen cara atrás, porque neses días os seus ocos non eran públicos. Por iso unha instalación pode ter menos de 12 meses. Dino «Detalles» da ocupación (punto 27).
   - Despois borra os días de hai máis de 12 meses.
   - **Custo (estimación para un concello de 20 instalacións):** cada noite, 20 filas e as reservas confirmadas dun día: milisegundos. A primeira carga, 7.300 filas e as reservas dun ano, en 12 anacos: segundos, non minutos. Con tramos de 15 minutos o cálculo é o mesmo traballo ca por horas (as mesmas reservas e os mesmos horarios); só cambia en canto se corta.
   - **Dúas instancias á vez:** coma o resto das tarefas, cada instancia corre a súa. Se as dúas collen o mesmo anaco, a segunda bate coa clave primaria das filas da primeira, a súa transacción vólvese atrás e queda no rexistro técnico só o tipo do erro; as filas e o `calculated_through` da primeira quedan. O choque é inocuo: o resultado é o mesmo ca cunha soa.
   - Así unha noite sen tarefa recupérase soa e acender o interruptor enche os 12 meses a noite seguinte. **Límite coñecido:** os días que se calculan con atraso usan o horario e as marcas de hoxe (o mesmo límite de ADR-0026).
6. **«Actualizado o…»: táboa `open_data_dataset`** (`municipality_id`, `dataset` ∈ `FACILITIES`, `CLOSURES`, `OCCUPANCY`, `checksum text`, `changed_on date`, `calculated_through date` (só en `OCCUPANCY`); clave `(municipality_id, dataset)`; RLS). A mesma tarefa calcula a pegada de cada conxunto (SHA-256 do modelo de instalacións e de peches; `calculated_through` e número de filas na ocupación) e, se cambiou, pon `changed_on` ao **día da tarefa** (`hoxe` do punto 5): a ocupación «actualizada o 11» leva ata o día 10. A páxina mostra esa data.
   - **Ata a primeira noite despois de despregar ou de acender:** non hai fila, así que a liña «Actualizado o…» non sae en ningún conxunto; as instalacións e os peches xa se descargan (xéranse ao pedilos) e a ocupación sae só coa cabeceira.
   - Os ficheiros de instalacións e de peches xéranse ao pedilos, así que un cambio de hoxe xa vai no ficheiro e a data cambia esa noite.
7. **Que entra en cada conxunto.**
   - **Instalacións:** as do listado público (`ListPublicFacilitiesUseCase`): nin en borrador nin de baixa; as pechadas temporalmente e as que non se reservan por internet si. Os usos, os mesmos que mostra a ficha pública (`PublicFacilityDetailService`: non retirados e «sempre» ou con algunha data de hoxe en diante, `stillBookable`); un uso retirado ou caducado deixa de saír aquí, pero a ocupación segue citándoo (punto 16). Os campos personalizados, só os públicos (punto 16). Nunca o código RIDEGA.
   - **Peches:** os festivos do catálogo e, por instalación pública, os días enteiros pechados por unha excepción ou un bloqueo. Os bloqueos dunhas horas non entran (non son «un día que non abre»; xa quitan esas horas da ocupación). Xanela: do 1 de xaneiro do ano anterior ao 31 de decembro do seguinte (decisión do mantedor); con anos naturais o conxunto non cambia cada día. **Motivo:** o que escribiu o persoal, só nos peches con data igual ou posterior ao día (Europe/Madrid) de `open_data_since`; nos anteriores, `motivo` baleiro. Volver acender move esa data e os motivos anteriores deixan de saír.
   - **Ocupación:** as filas da táboa entre as datas pedidas (dentro dos últimos 12 meses, punto 10) das instalacións que hoxe están no conxunto de instalacións, activas e con `onlineBooking || showFreeSlots` (a mesma condición do punto 5, comprobada tamén ao servir: unha instalación que apaga «ocos visibles» deixa de saír ao momento, sen esperar á noite). Todo `instalacion` da ocupación existe no ficheiro de instalacións; non toda instalación ten ocupación.
8. **Concello publicado e conxunto de todos.** A páxina dun concello existe se ten o interruptor aceso (decisión do mantedor). No conxunto de todos entra o concello que, ademais, sae na portada común (datos legais completos e algunha instalación activa á vista, ADR-0030 punto 2). Lese coma a portada común: `CatalogMunicipalities` por páxinas de 50, filtrando `openData`, e cada concello no seu `runAs` e na súa transacción. Sen `runAsPlatform`, sen `BYPASSRLS`, sen consulta que vexa dous concellos.

### Privacidade

9. **Salvagardas.**
   - **Ningún campo de quen reserva, por construción.** Instalacións e peches saen de datos que a web xa mostra sen sesión (ficha pública, «Próximos peches»); a ocupación, dunha táboa que só ten instalación, día e as porcentaxes dos seus tramos. `OpenDataSchemaIT` falla se unha táboa `open_data_*` ten columnas de persoa, reserva, conta, correo, nome ou texto libre (o uso vai só como identificador).
   - **A ocupación só das instalacións que xa mostran os seus ocos** (punto 3). Nelas, ata que pasa o día, calquera ve sen sesión que horas están libres e, se se reservan por internet, as «Ocupada» ([ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md)). O conxunto publica ese mesmo dato, cos tramos de 15 minutos que xa se deducían dos ocos e das horas ocupadas, e consérvao 12 meses. Das instalacións que non mostran ocos non se publica nada que hoxe non se vexa.
   - **Sen limiar mínimo (decisión do mantedor).** [ADR-0026](0026-indicadores-fase-1.md) xa advirte que as celas pequenas poden identificar se se publican fóra. A ocupación é unha agregación cuxo resultado non identifica a ninguén por si mesmo: a unidade é o tempo da instalación, non unha persoa, e o ficheiro non leva quen. Cos tramos de 15 minutos dun uso enteiro vese a que hora empezou e rematou cada reserva: é o mesmo que mostraba a web antes de pasar o día. O risco que queda é a identificación indirecta: nunha instalación cun único usuario habitual, quen xa sabe por outra vía quen é podería ligar a el uns tramos ao 100 %. Redúceno tres cousas: só se publica das instalacións cuxos ocos xa son públicos (punto 3), só a porcentaxe e só de días xa pasados. Suprimir as celas baixas non protexería máis (unha cela que falta delata o mesmo ca un 100 %) e estragaría a serie; e un limiar de persoas distintas obrigaría a contar persoas para publicar, que é xusto o que se evita.
   - **Por prazas** sae a parte do aforo reservada. O aforo é público (xa o mostra a ficha), así que dun día con aforo igual ao de hoxe pódese deducir cantas prazas se reservaron; nunca quen. Non se publica a asistencia: un non presentado conta como reservado.
   - **Con atraso:** só días rematados, calculados de noite; sen cancelacións, pagos nin tipo de persoa.
   - **Campos personalizados:** só os que o concello definiu para «Todo o mundo (web pública)», que nunca poden ser dato persoal (ADR-0011, na base); o editor avisa de que se ven na web e nos datos abertos (punto 16).
   - **Textos libres** (`nome`, `descricion`, `normas`, `uso`, `uso_nome`, `partes`, `motivo`): escríbeos o concello e xa se ven na ficha pública. Poden levar un nome propio se o persoal o escribe (o nome dun uso ou dunha parte, o motivo dun peche). Só levan aviso onde xa estaba previsto, sen avisos novos nos usos nin nas partes: o «?» do motivo avisa de que se ve na web e nos datos abertos e de que non se poñan nomes de persoas, e os peches de antes de `open_data_since`, escritos sen ese aviso, saen sen motivo (punto 7). O mesmo aviso vai nas normas e na descrición.

### API

10. **Rutas por concello** (`opendata`, todas `@PublicEndpoint` cun caso de uso `@PublicUseCase`):

    | Ruta | Resposta |
    |---|---|
    | `GET /api/v1/municipalities/{slug}/open-data` | Resumo para a páxina: os tres conxuntos con `key`, `updatedOn` (data ou nulo) e `formats`; na ocupación, ademais, `availableFrom` e `availableTo` (o primeiro e o último día que se poden pedir), `defaultFrom` e `defaultTo` (o último mes enteiro), `maxMonths` (12) e `bytesPerDay` (`{ "csv", "json" }`: a media dos últimos 30 días calculados, para mostrar o tamaño aproximado no diálogo); `catalog` (booleano: hai ficha) |
    | `GET …/open-data/facilities.{csv\|json\|geojson}` | Instalacións, cos seus usos |
    | `GET …/open-data/closures.{csv\|json}` | Peches |
    | `GET …/open-data/occupancy.{csv\|json}?desde=AAAA-MM-DD&ata=AAAA-MM-DD` | Ocupación entre esas datas, as dúas incluídas |
    | `GET …/open-data/dcat.rdf` | Ficha de catálogo (404 sen DIR3, punto 19) |

    **Datas da ocupación** (días de Europe/Madrid): `desde` e `ata` van as dúas ou ningunha. Sen ningunha, o último mes enteiro (o 11 de novembro, do 1 ao 31 de outubro). Teñen que cumprir: `desde ≤ ata`; `desde ≥ hoxe − 12 meses` e `ata ≤ onte` (dentro dos 12 últimos meses; o 11 de novembro de 2026, do 11 de novembro de 2025 ao 10 de novembro de 2026); e `ata < desde + 12 meses` (como moito 12 meses). Se non, 400 `common.invalid-parameter`. É a mesma regra ca o filtro da páxina (punto 24). Co interruptor apagado, todas as rutas responden 404 `opendata.not-published`.
11. **Rutas de toda a instalación, sen concello:** `GET /api/v1/catalog/open-data`, `/api/v1/catalog/open-data/{facilities|closures|occupancy}.{formato}` e `/api/v1/catalog/open-data/dcat.rdf`. **Na ocupación de todos, como moito un mes** (`ata < desde + 1 mes`; o resumo leva `maxMonths: 1`); sen datas, tamén o último mes enteiro. Os 12 meses de todos os concellos serían centos de MB (punto 20). Amplían a lista pechada de rutas sen concello, no mesmo grupo `/catalog/…` de ADR-0030; a lista vive en `CONTRIBUTING.md` («Convencións») e en `arquitectura.md` §4. `TenantFilter` non cambia (só mira o prefixo `/api/v1/municipalities/`). O resumo leva tamén `municipalities` (cantos concellos entran): con 0, a web mostra o estado baleiro (punto 24).
12. **Gardar o interruptor** (`municipality`): `PUT /api/v1/municipalities/{slug}/open-data-publication`, corpo `{ "enabled": true }`, co `If-Match` do concello, coma `no-show-policy`. Permiso `municipality.update` (Xestión e plataforma). Devolve `{ "enabled": … }` e o `ETag` novo.
13. **Idioma dos ficheiros:** parámetro `lang=gl|es` (por defecto `gl`; outro valor, 400 `common.invalid-parameter`). Nunca `Accept-Language`: o mesmo URL dá sempre o mesmo ficheiro. Cambian só os textos que teñen tradución: `descricion`, `normas` e `deportes` das instalacións e o `motivo` dos festivos (o seu nome); a base de datos xa esixe os dous idiomas neles (`facility_description_texts_ck`, `facility_house_rules_ck`, `sport_catalog_label_ck`, `custom_sport_label_ck`, `public_holiday_name_ck`), así que nunca falta un. **Os textos sen tradución** (nome, enderezo, teléfono e correo da instalación, nome do uso e das partes, motivo dos peches) escríbense nun só idioma e saen tal cal en `gl` e en `es`. **Os códigos nunca cambian co idioma** (punto 14). Se algún día un texto traducible chegase sen o idioma pedido, sae o galego; nunca a cela baleira.
14. **Nomes de columnas e códigos: en galego e sen acentos, iguais en CSV, JSON e GeoJSON e iguais con `lang=gl` e `lang=es`.** O vocabulario fixo: `si`, `non`; `festivo`, `peche`; `pechado`; `enteira`, `partes`, `prazas`; `franxa`, `persoa`; `dia`, `semana`, `mes`; `lu`, `ma`, `me`, `xo`, `ve`, `sa`, `do`, `fe`. Os códigos de deportes son os de `sport_catalog.code` (`football_7`, `padel`…) e os de servizos, os de `facility_amenity.code` en minúsculas (`changing_rooms`, `step_free_access`…): códigos estables do dominio, que non se traducen. É unha excepción consciente a «claves en inglés» ([ADR-0003](0003-idioma.md)): non é o contrato da API senón o que le unha persoa nunha folla de cálculo, coma os URL visibles, que xa van en galego. As rutas, os DTO do resumo e o código seguen en inglés. Fronte ao CSV das reservas (cabeceiras traducidas ao idioma de quen descarga), aquí o esquema é un só: un conxunto publicado non pode ter dous nomes para a mesma columna nin dous valores para o mesmo código. «Columnas» dío nunha liña (punto 27).
15. **Formato.**
    - **CSV, coma os demais do proxecto** (`BookingCsv`, ADR-0026 punto 11): UTF-8 con BOM, `;` entre celas, CRLF, comiñas RFC 4180 e `'` diante do que unha folla de cálculo tomaría por fórmula. A función das celas (`text`) sae de `BookingCsv` a `shared` (`shared.web.CsvCells`) e úsana os dous.
    - **Decimais sempre con punto** (decisión do mantedor), en CSV e JSON: coordenadas (`43.509123`, 6 decimais), euros (`6.00`, 2 decimais) e números dos campos personalizados. Función nova `CsvCells.decimal(BigDecimal, escala)`, sen agrupación de miles. Os demais números son enteiros. Os lectores automáticos e as ferramentas de mapas esperan punto; unha folla de cálculo en galego ou castelán pode pedir indicar o separador ao importar, e «Columnas» dío.
    - **Listas dentro dunha cela, separadas por `|`** sen espazos (`padel|tenis`). Un valor que leve `|` ou `\` escríbese con `\|` ou `\\`. No JSON e no GeoJSON as listas son listas.
    - **Datas e horas ISO 8601:** un día, `AAAA-MM-DD`; un período de días, `AAAA-MM-DD/AAAA-MM-DD`; un instante, con desfase, `2026-10-15T10:30:00+02:00`, na zona Europe/Madrid. A zona declárase: no JSON e no GeoJSON, `"zona_horaria": "Europe/Madrid"` no envoltorio; no CSV, en «Columnas» e na descrición da ficha de catálogo. As horas do `horario` semanal (`09:00-14:00`) son hora local de Europe/Madrid, sen desfase, porque non son un instante senón un patrón.
    - **Baleiro** (CSV) ou `null` (JSON) = sen dato ou non se aplica; nunca `0` nin `non` no seu lugar.
16. **Esquema de cada conxunto.** Columnas finais, na orde do CSV; nada máis. No conxunto de todos, a primeira columna é `concello` (o slug). Cada columna sae dun campo que a ficha pública xa mostra (`PublicFacilityDetailResponse`, `PublicUseResponse`, «Próximos peches») ou do propio cálculo da ocupación.

    **Instalacións.** **CSV:** unha fila por uso que mostra a ficha pública, por nome da instalación e nome do uso; as columnas da instalación repítense en cada fila; unha instalación publicada sen usos á vista sae nunha fila coas columnas do uso baleiras. **JSON e GeoJSON:** un obxecto por instalación coas súas columnas e `usos`, a lista dos seus usos (baleira se non ten), cada un coas columnas do uso.

    *Da instalación:*

    | Columna | Orixe | CSV | JSON |
    |---|---|---|---|
    | `id` | `facility.id` | UUID | texto |
    | `nome` | `facility.name` | texto | texto |
    | `descricion` | `facility.description` (gl/es) | no idioma pedido; baleiro se non ten | texto ou `null` |
    | `enderezo` | `street`, `street_number`, `postal_code`, `locality` | `Rúa do Alto, 12, 15570 Narón` (o número, se o hai) | texto |
    | `rua` | `facility.street` | texto | texto |
    | `numero` | `facility.street_number` | texto; baleiro se non o ten | texto ou `null` |
    | `codigo_postal` | `facility.postal_code` | cinco cifras, como texto (`15570`; o cero inicial non se perde) | texto |
    | `localidade` | `facility.locality` | texto | texto |
    | `latitude`, `lonxitude` | `facility.latitude`, `longitude` | WGS84, 6 decimais, punto; baleiras sen punto no mapa | número ou `null` |
    | `telefono` | `facility.phone` | tal cal; baleiro se non o ten | texto ou `null` |
    | `correo` | `facility.email` | tal cal; baleiro se non o ten | texto ou `null` |
    | `url` | — | a ficha pública: `${FURAR_PUBLIC_URL}/<slug>/instalacions/<id>` | texto |
    | `deportes` | `facility_sport` → `sport_catalog.label` ou `custom_sport.label` | nomes no idioma pedido, na orde da ficha, con `\|` | lista de textos |
    | `deportes_codigos` | `facility_sport.sport_code` | os códigos do catálogo, na mesma orde, con `\|`; os deportes propios do concello non teñen código e non saen aquí (por iso pode ter menos elementos ca `deportes`) | lista de textos |
    | `cuberta` | `facility.covered` | `si`, `non` ou baleiro (sen dato) | `true`, `false` ou `null` |
    | `servizos` | `facility_amenity.code` | códigos en minúsculas, con `\|`: `changing_rooms`, `showers`, `covered_stands`, `cafe`, `parking`, `lighting`, `step_free_access`, `accessible_toilet`, `accessible_changing_room`, `hearing_loop` | lista de textos |
    | `pechada_temporalmente` | `facility.status = 'TEMPORARILY_CLOSED'` | `si` / `non` | booleano |
    | `reserva_internet` | `facility.online_booking` (o que fai que a ficha leve «Reservar») | `si` / `non` | booleano |
    | `horario` | a semana da tempada vixente hoxe | as oito entradas en orde, con `\|`: `lu 09:00-14:00,16:00-22:00\|ma 09:00-22:00\|…\|do pechado\|fe pechado`. Baleiro sen tempada vixente | obxecto coas claves `lu`…`do` e `fe`; cada unha, lista de `{ "de": "09:00", "a": "14:00" }` (baleira = pechado), ou `null` sen tempada |
    | `normas` | `facility.house_rules` (gl/es) | no idioma pedido; baleiro se non ten | texto ou `null` |
    | `campo_<código>` | campos personalizados públicos | véxase máis abaixo | en `campos` |

    *De cada uso* (só os que mostra a ficha pública, punto 7):

    | Columna | Orixe | CSV | JSON |
    |---|---|---|---|
    | `uso_id` | `facility_use.id` | UUID estable; é o que leva a ocupación | texto |
    | `uso` | `facility_use.name` | texto: `Pista enteira`, `Media pista` | texto |
    | `modo_aluguer` | `facility_use.rental_mode` | `enteira` (`WHOLE`), `partes` (`PARTS`) ou `prazas` (`SEATS`) | texto |
    | `franxa_minutos` | `facility_use.slot_minutes` | `30`, `60`, `90` ou `120` | número |
    | `partes` | `facility_use_part.name` (vivas, por `position`) | nos usos por partes, os nomes con `\|` (`Metade A\|Metade B`); baleiro nos demais | lista de textos (baleira nos demais) |
    | `prazas` | `facility_use.capacity` | nos usos por prazas, o aforo por franxa; baleiro nos demais | número ou `null` |
    | `dias` | `facility_use.weekdays` (bit 0 = luns) | os días da semana do uso, con `\|`: `lu\|ma\|me\|xo\|ve` | lista de textos |
    | `datas` | `facility_use_period` (só con `availability = 'DATES'`) | os períodos dos usos de só unhas datas, por orde, con `\|`: `2026-07-01/2026-08-31\|2026-12-20/2027-01-06`; baleiro nos usos de sempre | lista de `{ "desde", "ata" }` ou `null` |
    | `prezo_euros` | `facility_use.price_kind`, `price_cents` | `PAID`: euros con punto e dous decimais (`6.00`); `FREE`: `0.00`; `NONE` (sen prezo posto): baleiro, que non quere dicir gratis | número ou `null` |
    | `prezo_por` | `PricePerValue` (`PERSON` nos usos por prazas, `BOOKING` nos demais) | `persoa` ou `franxa`; baleiro se `prezo_euros` está baleiro | texto ou `null` |
    | `antelacion_minima_horas` | `rule_min_advance_hours` | horas antes como mínimo; baleiro sen norma | número ou `null` |
    | `antelacion_maxima_dias` | `rule_max_advance_days` | días antes como máximo; baleiro sen norma | número ou `null` |
    | `franxas_maximas` | `rule_max_slots` | franxas seguidas por reserva, como moito; baleiro sen norma | número ou `null` |
    | `limite_por_persoa` | `rule_cap_count` | reservas por persoa no período; baleiro sen norma | número ou `null` |
    | `limite_periodo` | `rule_cap_period` | `dia`, `semana` ou `mes`; baleiro sen norma | texto ou `null` |
    | `cancelar_sen_custo_horas` | `rule_cancel_hours` | horas antes ata as que se cancela sen custo; baleiro sen norma | número ou `null` |

    Non saen, a propósito: o código RIDEGA (`ridega_code`); `show_free_slots`, `blocks_others` e `entry_control_since`, que non mostra a ficha; as fotos. **Non saen por non ser públicos hoxe** (a ficha pública non os mostra nin os leva o seu DTO): o tipo de instalación (`facility.type`) e a data de fin da tempada vixente (`season.end_date`). Se algún día a ficha os mostra, engádense ao final como `tipo` e `horario_ata`.

    **Campos personalizados da instalación** (decisión do mantedor do 2026-11-11; [ADR-0011](0011-campos-personalizados.md)). Entran os campos do destino `FACILITY` cuxa visibilidade é `PUBLIC` («Todo o mundo (web pública)»), activos (`ACTIVE`). Nunca os `OWNER` nin `STAFF`, e nunca un dato persoal: a base xa impide que un campo `personal_data` sexa `PUBLIC` (`check (not personal_data or visibility <> 'PUBLIC')`). Lense coma na API: `CustomFieldValueStore.loadAll(FACILITY, ids)` e `CustomFieldProjector.projectAll(FACILITY, valores, anónimo)`, unha vez por ficheiro, así que a regra de quen ve que é a mesma. Un campo desactivado deixa de saír. **Hoxe a ficha pública non mostra estes valores** (`PublicFacilityDetailResponse` non leva `customFields`; só a xestión os ve, en `FacilityDetailsResponse`), aínda que o editor os chame «web pública». **Decisión do mantedor:** móstranse tamén na ficha pública, na mesma tanda, para que o que sae nos datos abertos se vexa na web. Contrato: `PublicFacilityDetailResponse` leva `customFields` (só os `PUBLIC` activos, co mesmo `projectAll` anónimo) e as súas definicións (código, etiqueta en gl e es, tipo e opcións). Esta ADR fixa só o contrato; a colocación na ficha decídese aparte. As definicións de `BOOKING` nunca poden ser públicas; as de `MUNICIPALITY` públicas quedan fóra desta fase (decisión do mantedor). Os usos (`facility_use`) non teñen campos personalizados (non son un destino rexistrado).

    | Formato | Como viaxan |
    |---|---|
    | CSV | Unha columna por campo, despois de todas as fixas (as da instalación e as do uso), ordenadas polo código do campo: `campo_<código>` (o código é inmutable, en minúsculas e sen acentos, `^[a-z][a-z0-9_]{1,39}$`; o prefixo evita chocar coas columnas fixas). Texto e texto longo, tal cal (os valores escríbense nun só idioma); número, con punto; data, `AAAA-MM-DD`; si/non, `si` ou `non`; selección, o código da opción; selección múltiple, os códigos separados por `\|`. Sen valor, baleiro. As columnas fixas nunca se moven; un campo novo pode quedar no medio das columnas `campo_…`, así que hai que lelas polo nome da cabeceira |
    | JSON | En cada instalación, `"campos": { "<código>": valor }` con tipo: texto, número, `true`/`false`, data `"AAAA-MM-DD"`, o código da opción ou a lista de códigos. No envoltorio, `"campos": [ { "codigo", "nome", "tipo", "opcions": [ { "codigo", "nome" } ] } ]`, cos nomes no idioma de `lang` (o único que cambia co idioma) |
    | GeoJSON | En `properties`, `campos` coma no JSON; as definicións, no `FeatureCollection` |
    | De todos | **Só en JSON e GeoJSON** (cada concello ten campos distintos): cada instalación co seu `campos` e as definicións dentro de cada concello do envoltorio (`"concellos": [ { …, "campos": [ … ] } ]`). O CSV de todos leva só as columnas fixas (decisión do mantedor) |

    Selección e selección múltiple levan o código da opción, non a etiqueta, en todos os formatos: un valor fixo é igual en galego e en castelán (punto 14); a etiqueta no idioma de `lang` está nas definicións do JSON e en «Columnas».

    Aviso no editor: co sitio «Instalación» e «Todo o mundo (web pública)», o editor do campo mostra «Vese na web e nos datos abertos; non poñas nomes de persoas.» (`customFields.editor.publicTip`).

    **Peches** (unha fila por día; por data e instalación):

    | Columna | Contido |
    |---|---|
    | `data` | `AAAA-MM-DD` |
    | `tipo` | `festivo` (do calendario de festivos) ou `peche` (a instalación pecha ese día enteiro por unha excepción ou un bloqueo) |
    | `instalacion` | baleiro nos festivos (valen para todas: cada instalación abre ou non segundo a entrada `fe` do seu `horario`); o `id` nos peches |
    | `motivo` | o nome do festivo, no idioma pedido; nos peches, o motivo que escribiu o concello, se o hai e se o peche non é anterior á publicación (punto 7) |

    **Ocupación** (unha fila por instalación, día e tramo de 15 minutos no que estaba aberta; por instalación, data e inicio):

    | Columna | Contido |
    |---|---|
    | `instalacion` | `id` da instalación (o `id` do ficheiro de instalacións) |
    | `uso_id` | `id` do uso que ocupaba a instalación nese tramo (o `uso_id` do ficheiro de instalacións); baleiro se estaba libre |
    | `uso_nome` | nome actual dese uso; baleiro se estaba libre |
    | `modo_aluguer` | `enteira`, `partes` ou `prazas`, o modo dese uso; baleiro se estaba libre |
    | `data` | `AAAA-MM-DD`, o día de Europe/Madrid |
    | `inicio` | instante ISO 8601 con desfase: `2026-10-15T10:30:00+02:00` |
    | `fin` | 15 minutos despois, igual: `2026-10-15T10:45:00+02:00` (no último tramo, o día seguinte ás `00:00`) |
    | `ocupacion` | enteiro de 0 a 100: a parte da instalación reservada nese tramo, co aforo e as partes que había ese día (punto 4). Nos usos enteiros, 0 ou 100 |

    `uso_nome` e `modo_aluguer` van en cada fila para que o ficheiro se entenda só: un uso retirado ou caducado xa non está no ficheiro de instalacións, pero segue na ocupación co seu nome e o seu modo (`FacilityQueries.usesById`, punto 1; o modo dun uso non cambia). Sen fila: a instalación estaba pechada ou bloqueada polo concello (decisión do mantedor). O día do cambio de hora de outubro, os tramos da hora repetida saen dúas veces co mesmo `data` e distinto desfase (`+02:00` e `+01:00`), na orde en que pasaron.

    **Envoltorio do JSON:** `{ "conxunto": "instalacions", "concello": "naron", "licenza": "CC BY 4.0", "atribucion": "Concello de Narón", "actualizado": "2026-11-10", "zona_horaria": "Europe/Madrid", "datos": [ … ] }`; na ocupación, ademais `"desde"` e `"ata"`. No de todos, `"concello": "todos"`, `"atribucion": "O concello de orixe de cada fila"` e `"concellos": [ { "concello", "nome", "atribucion" } ]`. A atribución é a entidade responsable dos datos legais ou, sen ela, «Concello de ‹nome›» (`PlaceName`).
17. **GeoJSON** (RFC 7946, `application/geo+json`): un `FeatureCollection`; cada instalación, un `Feature` co seu `id`, `geometry` `Point` `[lonxitude, latitude]` (ou `null` sen punto no mapa) e en `properties` o mesmo obxecto ca no JSON (cos seus `usos` e `campos`), menos `latitude` e `lonxitude`. `licenza`, `atribucion`, `zona_horaria` e as definicións dos campos van como membros do `FeatureCollection`.
18. **Cabeceiras.**

    | Que | Valor |
    |---|---|
    | `Content-Type` | `text/csv; charset=utf-8` · `application/json` · `application/geo+json` · `application/rdf+xml` |
    | `Content-Disposition` | `attachment; filename="<slug>-instalacions.csv"`, `<slug>-peches.json`, `<slug>-ocupacion-2026-10-01-2026-10-31.csv` (coas datas pedidas ou as do último mes enteiro); no de todos, `todos-…`; a ficha, `<slug>-datos-abertos.rdf` |
    | `Cache-Control` | `public, max-age=300` (ocupación, `max-age=3600`). Un concello que apaga o interruptor desaparece das cachés en cinco minutos (unha hora na ocupación) |
    | `ETag` | forte. Nas instalacións, nos peches e na ficha, a pegada do corpo (caben en memoria): o corpo xérase igual e un `If-None-Match` que coincide só aforra envialo (304). **Só na ocupación dun concello** o `ETag` se calcula antes de ler as filas (`calculated_through`, número de filas, instalacións á vista, `desde`, `ata`, formato e idioma), e aí o 304 sae sen xerar o corpo e sen ocupar praza do tope do punto 20. A ocupación de todos non leva `ETag` |
    | `Access-Control-Allow-Origin` | `*`, só nos GET dos ficheiros e da ficha, tamén nas súas respostas de erro (punto 20) |

    Sen `Vary` propio: o idioma e as datas van no URL. Caddy comprime (`encode zstd gzip`). **Comprobación:** detrás do proxy, unha segunda petición co `ETag` recibido en `If-None-Match` ten que dar 304. Se o proxy cambia o `ETag` ao comprimir (sufixo do codificador ou paso a débil), o servidor compara sen ese sufixo; esa proba vai no e2e contra o despregue completo (punto 29).
19. **Ficha de catálogo DCAT-AP-ES.** RDF/XML escrito co `XMLStreamWriter` do JDK (sen dependencias novas), segundo **DCAT-AP-ES 1.0.0**, o perfil español baseado en DCAT-AP 2.1.1. Non desenvolve a norma técnica de reutilización vixente: é o modelo da nova norma técnica de interoperabilidade de reutilización, aínda en tramitación. Cúmprese porque é o que xa piden os portais que federan e o que pedirá a norma nova; se a versión final cambia, cambia `DcatCatalogWriter` e os seus tests.
    - **O publicador identifícase co URI oficial do organismo a partir do seu código DIR3** (`municipality.dir3_code`, que xa existe e xa ten o mesmo patrón que pide o perfil, `^[A-Z][A-Z0-9]{8}$`). O perfil esixe ese URI no `dct:publisher` do catálogo e de cada conxunto. **Sen DIR3 non hai ficha:** `dcat.rdf` responde 404, o resumo leva `catalog: false` e a páxina non mostra o bloque «Portais de datos abertos». Complétase en Concello › Legal.
    - **Tres `dcat:Dataset`:** instalacións (cos seus usos), peches e ocupación. A descrición de cada un leva a zona horaria (Europe/Madrid) e as regras de formato do punto 15; a da ocupación, ademais, os parámetros `desde` e `ata` e os seus límites.
    - **Ficha de todos:** un catálogo cos catro conxuntos de todos, publicado por quen opera a instalación. Variables novas de despregue `FURAR_OPEN_DATA_PUBLISHER` (nome) e `FURAR_OPEN_DATA_PUBLISHER_DIR3` (DIR3 do organismo). Sen as dúas, non hai ficha de todos (404, `catalog: false`) nin bloque «Portais de datos abertos» nesa páxina.
    - **Clases e propiedades** (da guía publicada do perfil, dos seus exemplos e formas SHACL 1.0.0, e da especificación DCAT do W3C; os conceptos dos vocabularios europeos comprobáronse un a un no seu rexistro):

    | Onde | Propiedade | Valor | Comprobado |
    |---|---|---|---|
    | Espazos de nomes | `dcat:` · `dct:` · `foaf:` · `rdf:` · `xsd:` | `http://www.w3.org/ns/dcat#` · `http://purl.org/dc/terms/` · `http://xmlns.com/foaf/0.1/` · `http://www.w3.org/1999/02/22-rdf-syntax-ns#` · `http://www.w3.org/2001/XMLSchema#` | si |
    | Clases | — | `dcat:Catalog`, `dcat:Dataset`, `dcat:Distribution` | si |
    | Catálogo | `dct:title`, `dct:description` | literais con `xml:lang="es"` (obrigatorio polo menos en castelán) e `xml:lang="gl"` | si |
    | Catálogo e conxunto | `dct:publisher` | `http://datos.gob.es/recurso/sector-publico/org/Organismo/{DIR3}` | si |
    | Catálogo | `dct:issued` | `open_data_since`, `xsd:dateTime` | si |
    | Catálogo | `dct:modified` | o `changed_on` máis recente, `xsd:date` (se o validador só acepta `xsd:dateTime`, a medianoite de Europe/Madrid) | sen verificar (o tipo `xsd:date`) |
    | Catálogo | `foaf:homepage` | `${FURAR_PUBLIC_URL}/<slug>/datos-abertos` | si |
    | Catálogo | `dcat:themeTaxonomy` | `http://datos.gob.es/kos/sector-publico/sector` | si |
    | Catálogo | `dct:language` | `http://publications.europa.eu/resource/authority/language/SPA` e `…/language/GLG` | si |
    | Catálogo e distribución | `dct:license` | `http://publications.europa.eu/resource/authority/licence/CC_BY_4_0` | si |
    | Catálogo | `dcat:dataset` | os catro conxuntos | si |
    | Conxunto | `dct:title`, `dct:description` | es e gl | si |
    | Conxunto | `dcat:theme` | `http://datos.gob.es/kos/sector-publico/sector/deporte` | si |
    | Conxunto | `dct:identifier` | URL da páxina co conxunto (`…/datos-abertos?conxunto=…`) | sen verificar no perfil |
    | Conxunto | `dcat:keyword` | literais es e gl | sen verificar no perfil |
    | Conxunto | `dct:issued`, `dct:modified` | `open_data_since`; `changed_on` do conxunto | si (propiedades); o tipo, coma no catálogo |
    | Conxunto | `dct:accrualPeriodicity` | `http://publications.europa.eu/resource/authority/frequency/DAILY` (ocupación) · `…/frequency/IRREG` (os outros dous) | si |
    | Conxunto (ocupación) | `dct:temporal` | un `dct:PeriodOfTime` con `dcat:startDate` e `dcat:endDate` (`xsd:date`): o primeiro e o último día que se poden pedir | sen verificar no perfil |
    | Conxunto | `dcat:distribution` | unha por formato; na ocupación (dun concello e de todos), o URL sen datas, que dá sempre o último mes enteiro, e a descrición explica `desde` e `ata` | si (a propiedade); a páxina como `accessURL`, sen verificar no perfil |
    | Distribución | `dcat:accessURL` (obrigatoria) | o URL do ficheiro con `lang=gl` | si |
    | Distribución | `dcat:downloadURL` | o mesmo URL | sen verificar no perfil |
    | Distribución | `dct:format` | `http://publications.europa.eu/resource/authority/file-type/CSV` · `…/file-type/JSON` · `…/file-type/GEOJSON` | si |
    | Distribución | `dcat:mediaType` | `http://www.iana.org/assignments/media-types/text/csv` · `…/application/json` · `…/application/geo+json` | o patrón si; os tres valores, sen verificar |
    | Ficha | (o propio ficheiro, se se describe) | `…/file-type/RDF_XML` | si |

    `dct:spatial` non se pon: o concello xa non garda o código INE. `DcatCatalogTest` fixa estes URI.
    - **Validación:** ademais de `DcatCatalogTest`, antes de cada etiqueta que toque a ficha, unha ficha de exemplo (un concello con DIR3 e a de todos) pásase a man polo validador SHACL público que enlaza a guía do perfil (<https://datosgobes.github.io/DCAT-AP-ES/>), coas formas SHACL 1.0.0 do perfil («caso 1», sen conxuntos de alto valor). Sen violacións; os avisos anótanse no PR.
20. **Límites e abuso.**
    - **Sen paxinación; a ocupación, por datas.** Tamaños (estimación cunha instalación aberta 14 horas ao día, 56 tramos): instalacións (unha fila por uso) e peches, decenas de kB por concello. Ocupación: unhas 20.400 filas por instalación e ano; un concello de 20 instalacións, unhas 410.000 filas: **≈ 55 MB en CSV e ≈ 95 MB en JSON os 12 meses** (≈ 4,5 MB e ≈ 8 MB un mes), cos instantes ISO e o uso, o seu nome e o seu modo nas filas ocupadas (supoñendo un 40 % de tramos ocupados). Comprímese moito (o `id`, a data e o desfase repítense en cada fila): o proxy envíao con `zstd` ou `gzip` e por rede quedan uns poucos MB. Por iso:
      - **Sen ficheiro comprimido aparte** (`.zip`): a compresión de transporte xa o fai, e un formato máis sería outra distribución que manter.
      - **Descarga por datas** (`desde`, `ata`), por defecto o último mes enteiro; ata 12 meses nun concello. **Na de todos, como moito un mes:** con 10 concellos coma o do exemplo, os 12 meses serían ≈ 550 MB en CSV; un mes, ≈ 45 MB (≈ 80 MB en JSON).
      - O custo da lectura non cambia de orde: son as mesmas 7.300 filas da táboa por concello (punto 3), despregadas en tramos ao escribir; o nome e o modo de cada uso saen dunha soa lectura por ficheiro (`FacilityQueries.usesById`, cos usos que aparecen nas filas).
    - **Descarga síncrona, por tramos, no fío da petición.** O patrón da exportación das reservas (`BookingExportController` e `BookingExportService`): o controlador escribe directamente en `HttpServletResponse` mentres se len as filas en tandas, sen ter todo en memoria e sen fíos asíncronos. Non se usa `StreamingResponseBody` (non existe no repositorio): escribiría noutro fío, e `TenantContext` é un `ThreadLocal`, así que ese fío perdería o concello e a RLS devolvería 0 filas sen erro. Unha diferenza coa exportación das reservas, que le todo nunha transacción: aquí **cada tramo (un mes natural, ou o que caiba del entre as datas pedidas, dun concello) lese na súa transacción curta** dentro do `runAs` dese concello (`readOnly`, `timeout = 10`), a transacción pecha e despois escríbese o tramo. Ningunha conexión fica aberta mentres a rede vai lenta.
    - **Tope común a toda descarga de ocupación**, dun concello ou de todos: como moito `furar.opendata.occupancy-downloads` (por defecto 2) á vez en cada instancia, cun semáforo que se colle antes da primeira lectura e se solta ao rematar ou fallar. A seguinte, 429 `common.rate-limited` con `Retry-After: 60`. Un 304 non colle praza. Instalacións, peches e ficha non teñen tope: son pequenos e en memoria.
    - **Encaixe co límite de peticións que xa hai** (`RateLimitFilter`, `furar.rate-limit.requests-per-minute`, 3.000 por IP e minuto): vai antes e conta cada descarga coma unha petición máis; protexe dunha IP que pide moito. O tope protexe o servidor de moitas descargas longas de IP distintas. Os dous responden co mesmo erro e `Retry-After`, así que a web e un cliente automático trátanos igual.
    - **Límite coñecido:** con máis de dez concellos grandes, un mes da ocupación de todos pasa dos 50 MB sen comprimir (en JSON, xa con dez). O paso seguinte sería deixar os ficheiros mensuais feitos cada noite; non fai falta agora.
    - **CORS (decisión do mantedor):** `Access-Control-Allow-Origin: *` só nos GET dos ficheiros (`facilities`, `closures`, `occupancy`) e da ficha (`dcat.rdf`), por concello e de todos, tamén nas súas respostas 304, 404 e 429. Non no resumo da páxina nin en ningunha outra ruta, e sen `Access-Control-Allow-Credentials`. Ponse na resposta desas rutas; a configuración de CORS de Spring Security segue apagada e non se responden preflights `OPTIONS`: un GET sen cabeceiras propias non os pide. **Non abre nada:** a API non ten estado nin cookies de sesión; a autenticación vai nun token Bearer que unha web allea non ten e que o navegador non engade só; e esas rutas xa son públicas para calquera programa. O único que cambia é que unha web de terceiros pode ler eses ficheiros desde o navegador, que é o que busca a reutilización.
21. **Rexistro de actividade.** Acender ou apagar queda en `audit_event` coa acción `municipality.open-data` e o cambio `openData` (`MunicipalityAuditSnapshots`), co éxito na mesma transacción e o fallo co seu código, coma o resto dos axustes do concello. As descargas e a tarefa nocturna non se rexistran: non hai datos persoais nin unha persoa que actúe.
22. **Portabilidade.** O axuste viaxa en `furar-export` como campo opcional do concello, `openData` (booleano). Sen el, aceso. Ao importar conta desde ese momento. A versión do formato non sobe (o mesmo criterio ca `noShowPolicy`). As táboas `open_data_*` non viaxan: son derivadas e a tarefa vólveas encher; como as reservas tampouco viaxan, o histórico de ocupación empeza de novo.
23. **Erros.**

    | Código | Estado | Galego | Castelán |
    |---|---|---|---|
    | `opendata.not-published` (novo, `OpenDataError`) | 404 | Este concello non publica datos abertos. | Este ayuntamiento no publica datos abiertos. |
    | `common.invalid-parameter` | 400 | (o de sempre: `lang`; na ocupación, `desde` sen `ata` ou ao revés, unha data mal escrita, fóra dos 12 últimos meses, `desde` despois de `ata`, ou máis de 12 meses nun concello ou dun mes no de todos) | |
    | `common.rate-limited` | 429 | (o de sempre) | |

    Un conxunto ou un formato que non existe (`occupancy.geojson`) ou unha ficha sen DIR3 son un 404 normal. Na web, un 404 do resumo leva á páxina de erro 404.

### Web

24. **Páxinas.** Feature nova `features/opendata/`, unha soa páxina (`open-data.page`) para as dúas rutas: `/:concello/datos-abertos` e `/datos-abertos` (só con varios concellos, coa mesma condición ca a portada común; `severalMunicipalitiesMatch`). `datos-abertos` entra na lista de slugs reservados (`Slug.RESERVED`; `SlugReservedRoutesTest` esíxeo).
    - Estrutura do punto 2, con pezas que xa hai: `furar-page-header`, `furar-tabs` de liña, `furar-card`, `furar-value-list` («Detalles»), `furar-callout` (privacidade, só na ocupación), `furar-data-table` («Columnas»), `furar-dialog` pequeno con `furar-radio-cards`, e a peza nova `furar-disclosure`.
    - A pestana vai no URL (`?conxunto=peches|ocupacion`), coma os demais filtros; as datas da ocupación non (sempre empezan no último mes enteiro).
    - **Un ficheiro por pestana; as datas da ocupación, na tarxeta.** As tres pestanas validadas quedan, sen pestana nova e sen grupo «Ficheiro» no diálogo: «Instalacións» descarga as instalacións cos seus usos (CSV, JSON ou GeoJSON). Na de «Ocupación», a tarxeta leva «Actualizado o…» e, debaixo, o filtro «Datas» (obrigatorio) á esquerda e «Descargar» á dereita (no móbil, «Descargar» a todo o ancho debaixo). O filtro é o da xestión de reservas (`furar-date-filter`, antes `furar-booking-date-filter`, chip `f-mdate` co aspa dentro, atallos, calendario e «Aplicar»), que **pasa a `shared/ui/date-filter/`** (unha feature non importa outra) con tres entradas novas sen cambiar o que fai nas reservas: `presets` (a lista de atallos), `min` e `max` (días fóra, desactivados). Na ocupación: atallos «Onte», «O mes pasado» e «Últimos 12 meses» (este, só nun concello); `min` e `max`, `availableFrom` e `availableTo` do resumo; por defecto, `defaultFrom` e `defaultTo` (o último mes enteiro). Erros debaixo do filtro, en `f-field-error`: sen as dúas datas, ao premer «Descargar», «Escolle o primeiro e o último día.»; máis de `maxMonths`, ao momento, «Como moito, 12 meses. Escolle un período máis curto.» («Como moito, un mes.…» na de todos), e «Descargar» non abre o diálogo. Na páxina de todos, o rótulo «Datas» leva un «?»: «Para todos os concellos á vez, como moito un mes. Para máis, descárgao concello a concello.» O diálogo da ocupación leva no subtítulo o período («Ocupación · do 01/10 ao 31/10 · Narón») e, no pé, «Tamaño: ≈ 4 MB» (días × `bytesPerDay` do formato escollido). «Columnas» das instalacións leva dúas táboas, «Da instalación» e «De cada uso», e entre as dúas os campos personalizados públicos do concello (unha fila por campo: `campo_<código>` e a súa etiqueta no idioma da páxina, co tipo). Na páxina de todos, unha liña: «Os campos propios de cada concello van só en JSON e GeoJSON.»
    - **Descarga.** «Descargar» abre o diálogo co CSV marcado. Ao aceptar, a web pide o ficheiro con `HttpClient` (`responseType: 'blob'`, `lang` = idioma da páxina) e, se chega ben, gárdao cunha ligazón temporal `download` co nome de `Content-Disposition` e pecha o diálogo. Mentres, o botón «Descargar» do diálogo está en espera. **A persoa nunca sae da web nin ve un JSON de erro:** o erro queda no diálogo, nun `furar-callout` de erro, e o diálogo segue aberto:
      - **404** (o concello acaba de apagar): «Este concello xa non publica datos abertos.» e, ao pechar, a páxina recárgase (e dá o 404 normal).
      - **429:** «Agora hai moitas descargas. Volve tentalo nun minuto.»
      - **5xx ou sen rede:** «Non se puido descargar. Volve tentalo.»
      O URL directo do ficheiro segue sendo o mesmo para quen o reutiliza desde un programa; a ficha DCAT apunta a el. **Límite coñecido:** o blob ten o ficheiro enteiro en memoria do navegador; cos tamaños do punto 20 (ata ≈ 95 MB nos 12 meses en JSON dun concello grande; un mes de todos, ≈ 45 MB en CSV e ≈ 80 MB en JSON por cada 10 concellos) abonda nun ordenador, e o paso seguinte do límite da ocupación de todos resolve tamén isto. No móbil, o tamaño á vista no diálogo avisa antes de descargar.
    - O diálogo péchase con Esc e picando fóra.
    - A data, longa no escritorio («11 de novembro de 2026») e curta no móbil («11 nov 2026»), con `Intl.DateTimeFormat` no idioma da páxina.
    - **Páxina de todos sen concellos:** se ningún concello publicado ten o interruptor aceso (`municipalities: 0`), a páxina mostra o título, a frase e un estado baleiro («Aínda ningún concello publica datos abertos.»), sen pestanas nin tarxeta nin «Portais de datos abertos».
    - **Pé** (`core/layout/shell.component`): «Datos abertos» despois de «Cookies» se `MunicipalityResponse.openData`; na raíz con varios concellos, a mesma condición ca a ruta `/datos-abertos`, coa lista de concellos que a raíz xa leu (`MunicipalityDirectory`, sen petición nova). A lista pública non di que concellos teñen o interruptor aceso: se ningún o ten, a ligazón leva ao estado baleiro da páxina de todos.
25. **Xestión** (`features/municipality/section/general.tab`). A tarxeta «Non presentados» desaparece. No seu sitio, a tarxeta «Configuración adicional» con dúas filas de interruptor:
    - **«Penalizar as faltas»**, co seu «?» e, acesa, os tres números debaixo. Garda coma ata agora (`no-show-policy`).
    - **«Datos abertos»**, co seu «?», o botón «Vista previa» (`furarButton` pequeno coa icona `visibility`, o de Aparencia e Legal) e o interruptor. Garda con `open-data-publication` dentro do «Gardar» da pestana.
    - «Vista previa» abre a páxina pública nunha pestana nova. Vese só co interruptor aceso **e gardado**: apagado, a páxina non existe (decisión do mantedor).
    - Os textos son os do punto 27 (véxase «Fonte de produto»).
26. **Regras de interface para toda a aplicación.** Fanse nun cambio propio, **xusto antes** desta función, para que as capturas e os e2e das demais pantallas cambien unha soa vez.
    - **`furar-switch`:** o nome á esquerda e o interruptor á dereita, ocupando a fila; o «?» pegado ao nome. Vale tamén cando á vista vai o estado (`onText`/`offText`, `exception-dialog`): o estado fai de nome. Cambian o modelo e `switch.css`; a API do compoñente non.
    - **Tres contextos nos que «ocupar a fila» rompería a composición,** coa solución mínima coherente coa regra:

      | Contexto | Onde | Solución |
      |---|---|---|
      | Varios interruptores xuntos | `custom-field-editor` («Obrigatorio» e «Dato persoal», hoxe en liña), `uses-step` («ocos visibles» e «control de entrada»), `data-card` (tres, cada un nun `furar-edit-field`) | Filas apiladas, unha por interruptor, cada unha a todo o ancho do seu bloque. En `uses-step` e `data-card` xa van unha debaixo doutra; en `custom-field-editor` deixan de ir en liña (decisión do mantedor) |
      | Fila con máis cousas á vista | `day-row` (hoxe: interruptor sen rótulo, nome do día e horas na mesma fila) | **Sen excepción á regra (decisión do mantedor):** o nome do día é o rótulo do interruptor (sae `hideLabel`) e vai á esquerda; o interruptor, á dereita do todo da fila; as horas, entre os dous, só co día aceso. Apagado, a fila queda co nome do día e o interruptor |
      | Sen rótulo á vista (`hideLabel`) | `season-list` (xunto ao rótulo «Ata» do campo) | O interruptor non ocupa a fila: vai á dereita do texto que o nomea á vista, co seu tamaño. O modificador sae do propio compoñente cando `hideLabel` está aceso, sen clases nas pantallas |

    - **`furar-help-tip`:** círculo de 18 px con contorno (bordo e «?» da cor principal sobre o fondo da superficie, sen recheo; 3:1 de contraste, WCAG 1.4.11) dentro dun botón de 24 × 24 px como mínimo (WCAG 2.5.8). Cambian `help-tip.css` e `switch.css` (a separación e o aliñamento do «?» dentro do interruptor).
    - **`furar-disclosure`** (`shared/ui/disclosure/`): pregable segundo o patrón «disclosure» das prácticas de accesibilidade ARIA: un botón co título, `aria-expanded` e `aria-controls`, a frecha á dereita e o contido debaixo. Pechado por defecto; cada un independente dos demais; sen animación con `prefers-reduced-motion`. Entra no catálogo de compoñentes e en [`deseno.md`](../deseno.md) §8. O `<details>` de `theme-editor.component` pasa a usalo.
    - **Aviso nos textos libres públicos:** o campo «Motivo» de `exception-dialog` e de `block-dialog` leva un `tip` co aviso (o exemplo segue como `placeholder`); as axudas das normas e da descrición engaden o aviso ao final (punto 27).

    **Pantallas afectadas polo interruptor** (onde hoxe hai `furar-switch`):

    | Ficheiro | Interruptores |
    |---|---|
    | `features/facility/data-card/data-card.component.html` | 3 (reserva por internet, ocos visibles, control de entrada) |
    | `features/facility/schedule/day-row.component.html` | 1 (aberto ou pechado; pasa a levar o nome do día como rótulo, á dereita do todo) |
    | `features/facility/schedule/exceptions/exception-dialog.component.html` | 1 (co estado á vista) |
    | `features/facility/schedule/season-list.component.html` | 1 (sen rótulo á vista) |
    | `features/facility/uses/use-when.component.ts` | 1 |
    | `features/facility/wizard/steps/uses-step.component.ts` | 2 |
    | `features/municipality/section/general.tab.html` | 1 → 2 |
    | `shared/custom-fields/editor/custom-field-editor.component.html` | 2 |
    | `dev/catalog-edit.component.ts`, `dev/catalog-overlays.component.ts` | 4 (catálogo de compoñentes) |

    **Pantallas afectadas polo «?»** (`furar-help-tip` directo ou polo `tip` de `furar-field`, `furar-bilingual-field`, `furar-switch` e `furar-edit-section`):

    | Onde | Ficheiros |
    |---|---|
    | Uso directo | `features/facility/blocks/block-dialog`, `features/facility/uses/use-dialog`, `features/facility/wizard/steps/use-editor`, `features/municipality/section/legal.tab`, `features/staff/activity/export-blocked-dialog` |
    | Polo `tip` | `features/booking/manage/book-for-seats` e `book-for-who`; `features/facility/data-card/data-card` e `sports-field`; `features/facility/description/description-section`, `house-rules-section` e `payment-notes-section`; `features/facility/uses/use-when`; `features/facility/wizard/steps/data-step`, `rules-step` e `uses-step`; `features/municipality/legal-fields`, `municipality-create.page` e `section/general.tab`; `shared/custom-fields/editor/custom-field-editor`; e, novos, `features/facility/schedule/exceptions/exception-dialog` |
    | Pezas que o levan dentro | `shared/ui/field.component`, `shared/ui/bilingual-field`, `shared/ui/edit-section`, `shared/ui/switch` |
    | Catálogo | `dev/catalog.page` |
27. **Textos da web** (`web/src/i18n/{gl,es}.json`).

    | Clave | Galego | Castelán |
    |---|---|---|
    | `titles.openData` · `openData.title` · `footer.openData` | Datos abertos | Datos abiertos |
    | `openData.intro.municipality` | O Concello {{ofName}} publica aquí os datos do seu servizo de deportes para que calquera os poida descargar e reutilizar: estudos, prensa ou outras aplicacións. | El Ayuntamiento {{ofName}} publica aquí los datos de su servicio de deportes para que cualquiera pueda descargarlos y reutilizarlos: estudios, prensa u otras aplicaciones. |
    | `openData.intro.all` | Os concellos desta web publican aquí os datos dos seus servizos de deportes para que calquera os poida descargar e reutilizar: estudos, prensa ou outras aplicacións. | Los ayuntamientos de esta web publican aquí los datos de sus servicios de deportes para que cualquiera pueda descargarlos y reutilizarlos: estudios, prensa u otras aplicaciones. |
    | `openData.empty` | Aínda ningún concello publica datos abertos. | Todavía ningún ayuntamiento publica datos abiertos. |
    | `openData.tabs.label` | Conxuntos de datos | Conjuntos de datos |
    | `openData.tabs.facilities` · `.closures` · `.occupancy` | Instalacións · Peches · Ocupación | Instalaciones · Cierres · Ocupación |
    | `openData.facilities.description` | As instalacións deportivas que se ven na web e o que se pode reservar en cada unha: onde están, horarios, prezos e condicións. | Las instalaciones deportivas que se ven en la web y lo que se puede reservar en cada una: dónde están, horarios, precios y condiciones. |
    | `openData.closures.description` | Os días nos que as instalacións non abren: festivos e peches por mantemento ou competicións. | Los días en los que las instalaciones no abren: festivos y cierres por mantenimiento o competiciones. |
    | `openData.occupancy.description` | Canto se usa cada instalación que mostra na web as súas horas libres: cada fila é un cuarto de hora no que estaba aberta e di que parte dela estaba reservada e para que uso. | Cuánto se usa cada instalación que muestra en la web sus horas libres: cada fila es un cuarto de hora en el que estaba abierta y dice qué parte de ella estaba reservada y para qué uso. |
    | `openData.details.updates` · `.formats` · `.since` · `.rows` · `.calc` · `.group` | Actualízase · Formatos · Abrangue · Cada fila · Como se calcula · Para agrupar | Se actualiza · Formatos · Abarca · Cada fila · Cómo se calcula · Para agrupar |
    | `openData.facilities.updates` | Cando cambia unha instalación | Cuando cambia una instalación |
    | `openData.facilities.rows` (en «Detalles», rótulo «Cada fila») | En CSV, un uso dunha instalación (os datos da instalación repítense). En JSON e GeoJSON, unha instalación cos seus usos dentro. | En CSV, un uso de una instalación (los datos de la instalación se repiten). En JSON y GeoJSON, una instalación con sus usos dentro. |
    | `openData.closures.updates` | Cando se engade un festivo ou un peche | Cuando se añade un festivo o un cierre |
    | `openData.occupancy.updates` | Cada noite, co día anterior | Cada noche, con el día anterior |
    | `openData.occupancy.since` | Os últimos 12 meses, ou desde que a instalación mostra as súas horas libres | Los últimos 12 meses, o desde que la instalación muestra sus horas libres |
    | `openData.occupancy.rows` | Unha instalación, un día e un cuarto de hora no que estaba aberta. Se pechou ou o concello a bloqueou, non hai fila. | Una instalación, un día y un cuarto de hora en el que estaba abierta. Si cerró o el ayuntamiento la bloqueó, no hay fila. |
    | `openData.occupancy.calc` | A parte reservada, co aforo e as partes que tiña ese día. Nos usos por prazas, as prazas reservadas entre o aforo. Conta aínda que a persoa non viñese; as canceladas non. | La parte reservada, con el aforo y las partes que tenía ese día. En los usos por plazas, las plazas reservadas entre el aforo. Cuenta aunque la persona no viniese; las canceladas no. |
    | `openData.occupancy.group` | Por horas, por días ou polas franxas de cada uso: a franxa e o modo de cada uso están no ficheiro de Instalacións. | Por horas, por días o por las franjas de cada uso: la franja y el modo de cada uso están en el fichero de Instalaciones. |
    | `openData.formats.three` · `.two` | CSV, JSON e GeoJSON · CSV e JSON | CSV, JSON y GeoJSON · CSV y JSON |
    | `openData.privacy.title` | Privacidade | Privacidad |
    | `openData.privacy.body` | Só que parte de cada cuarto de hora estaba reservada, e só de días xa pasados: o mesmo que a web mostraba antes. | Solo qué parte de cada cuarto de hora estaba reservada, y solo de días ya pasados: lo mismo que la web mostraba antes. |
    | `openData.dates.label` | Datas | Fechas |
    | `openData.dates.tipAll` (o «?» de «Datas», só na páxina de todos) | Para todos os concellos á vez, como moito un mes. Para máis, descárgao concello a concello. | Para todos los ayuntamientos a la vez, como mucho un mes. Para más, descárgalo ayuntamiento a ayuntamiento. |
    | `openData.dates.preset.yesterday` · `.lastMonth` · `.last12` | Onte · O mes pasado · Últimos 12 meses | Ayer · El mes pasado · Últimos 12 meses |
    | `openData.dates.required` | Escolle o primeiro e o último día. | Elige el primer y el último día. |
    | `openData.dates.tooLong` · `.tooLongOne` | Como moito, {{n}} meses. Escolle un período máis curto. · Como moito, un mes. Escolle un período máis curto. | Como mucho, {{n}} meses. Elige un periodo más corto. · Como mucho, un mes. Elige un periodo más corto. |
    | `openData.dialog.size` | Tamaño: ≈ {{size}} | Tamaño: ≈ {{size}} |
    | `openData.columns.name` · `.content` | Nome · Contido | Nombre · Contenido |
    | `openData.columns.facility` · `.use` (títulos das dúas táboas das instalacións) | Da instalación · De cada uso | De la instalación · De cada uso |
    | `openData.columns.format` (liña ao final de «Columnas», nas tres pestanas) | Listas separadas por «\|»; decimais con punto; datas e horas en ISO 8601, na zona Europe/Madrid. Os códigos (si, non, enteira…) son iguais en galego e en castelán. | Listas separadas por «\|»; decimales con punto; fechas y horas en ISO 8601, en la zona Europe/Madrid. Los códigos (si, non, enteira…) son iguales en gallego y en castellano. |
    | `openData.column.customField` (contido da fila de cada campo propio; `{{type}}` é o tipo do campo, de `customFields.type`) | Campo propio do concello: {{type}} | Campo propio del ayuntamiento: {{type}} |
    | `openData.column.customFieldsAll` (só na páxina de todos) | Os campos propios de cada concello van só en JSON e GeoJSON. | Los campos propios de cada ayuntamiento van solo en JSON y GeoJSON. |
    | `openData.column.concello` | Identificador do concello: ferrol, naron, verin | Identificador del ayuntamiento: ferrol, naron, verin |
    | `openData.column.id` | Identificador estable | Identificador estable |
    | `openData.column.nome` | Pavillón municipal de O Alto | Pavillón municipal de O Alto |
    | `openData.column.descricion` | Texto da ficha | Texto de la ficha |
    | `openData.column.enderezo` | Rúa, número, código postal e localidade | Calle, número, código postal y localidad |
    | `openData.column.enderezoPartes` (fila «rua, numero, codigo_postal, localidade») | O enderezo por partes | La dirección por partes |
    | `openData.column.coordenadas` (fila «latitude, lonxitude») | Coordenadas WGS84 | Coordenadas WGS84 |
    | `openData.column.contacto` (fila «telefono, correo») | Contacto da instalación | Contacto de la instalación |
    | `openData.column.url` | Ficha da instalación na web | Ficha de la instalación en la web |
    | `openData.column.deportes` | Pádel\|Tenis | Pádel\|Tenis |
    | `openData.column.deportesCodigos` | padel\|tennis; os deportes propios do concello non teñen | padel\|tennis; los deportes propios del ayuntamiento no tienen |
    | `openData.column.cuberta` · `.pechada` · `.reservaInternet` (filas «cuberta», «pechada_temporalmente», «reserva_internet») | si / non | si / non |
    | `openData.column.servizos` | Servizos e accesibilidade: changing_rooms, step_free_access… | Servicios y accesibilidad: changing_rooms, step_free_access… |
    | `openData.column.horario` | Tramos de cada día da semana e dos festivos (fe) | Tramos de cada día de la semana y de los festivos (fe) |
    | `openData.column.normas` | Normas da instalación | Normas de la instalación |
    | `openData.column.usoId` | Identificador estable do uso | Identificador estable del uso |
    | `openData.column.uso` | Pista enteira, Media pista… | Pista enteira, Media pista… |
    | `openData.column.modo` | enteira / partes / prazas | enteira / partes / prazas |
    | `openData.column.franxaMinutos` | 30, 60, 90 ou 120 | 30, 60, 90 o 120 |
    | `openData.column.partes` | Nomes das partes (só por partes) | Nombres de las partes (solo por partes) |
    | `openData.column.prazas` | Aforo por franxa (só por prazas) | Aforo por franja (solo por plazas) |
    | `openData.column.dias` | lu\|ma\|me… | lu\|ma\|me… |
    | `openData.column.datas` | Os períodos dos usos de só unhas datas | Los periodos de los usos de solo unas fechas |
    | `openData.column.prezoEuros` · `.prezoPor` | 6.00; 0.00 se é gratis; baleiro sen prezo · franxa / persoa | 6.00; 0.00 si es gratis; vacío sin precio · franxa / persoa |
    | `openData.column.antelacion` (fila «antelacion_minima_horas, antelacion_maxima_dias») | Cando se pode reservar | Cuándo se puede reservar |
    | `openData.column.franxasMaximas` | Franxas seguidas por reserva | Franjas seguidas por reserva |
    | `openData.column.limite` (fila «limite_por_persoa, limite_periodo») | Reservas por persoa e dia / semana / mes | Reservas por persona y dia / semana / mes |
    | `openData.column.cancelar` | Horas antes para cancelar sen custo | Horas antes para cancelar sin coste |
    | `openData.column.data` | AAAA-MM-DD | AAAA-MM-DD |
    | `openData.column.tipo` | festivo / peche | festivo / peche |
    | `openData.column.instalacionPeche` | Baleiro: todas | Vacío: todas |
    | `openData.column.motivo` | Mantemento, competición… | Mantenimiento, competición… |
    | `openData.column.instalacion` | Identificador da instalación | Identificador de la instalación |
    | `openData.column.usoOcupacion` (fila «uso_id, uso_nome, modo_aluguer») | O uso que a ocupaba; baleiro se estaba libre | El uso que la ocupaba; vacío si estaba libre |
    | `openData.column.inicio` · `.fin` | 2026-10-15T10:30:00+02:00 · 15 minutos despois | 2026-10-15T10:30:00+02:00 · 15 minutos después |
    | `openData.column.ocupacion` | De 0 a 100: parte reservada. Se só se reserva enteira, 0 ou 100 | De 0 a 100: parte reservada. Si solo se reserva entera, 0 o 100 |
    | `openData.updated` | Actualizado o {{date}} | Actualizado el {{date}} |
    | `openData.download` | Descargar | Descargar |
    | `openData.fold.details` · `openData.fold.columns` | Detalles · Columnas | Detalles · Columnas |
    | `openData.dialog.title` | Descargar | Descargar |
    | `openData.dialog.subtitle` · `.subtitleAll` | {{dataset}} · {{name}} · {{dataset}} · todos os concellos | {{dataset}} · {{name}} · {{dataset}} · todos los ayuntamientos |
    | `openData.dialog.period` (vai despois de {{dataset}} na ocupación: «Ocupación · do 01/10 ao 31/10 · Narón») | do {{from}} ao {{to}} · o {{from}} | del {{from}} al {{to}} · el {{from}} |
    | `openData.dialog.format` | Formato | Formato |
    | `openData.dialog.gone` | Este concello xa non publica datos abertos. | Este ayuntamiento ya no publica datos abiertos. |
    | `openData.dialog.busy` | Agora hai moitas descargas. Volve tentalo nun minuto. | Ahora hay muchas descargas. Vuelve a intentarlo en un minuto. |
    | `openData.dialog.failed` | Non se puido descargar. Volve tentalo. | No se pudo descargar. Vuelve a intentarlo. |
    | `openData.format.csv` | Ábrese con follas de cálculo | Se abre con hojas de cálculo |
    | `openData.format.json` | Para programas e aplicacións | Para programas y aplicaciones |
    | `openData.format.geojson` | Para mapas | Para mapas |
    | `openData.more.label` | Información adicional | Información adicional |
    | `openData.more.people.title` · `.body` | Sen datos de persoas · Ningún ficheiro leva nomes nin datos de quen reserva. | Sin datos de personas · Ningún fichero lleva nombres ni datos de quien reserva. |
    | `openData.more.reuse.title` | Uso libre | Uso libre |
    | `openData.more.reuse.body` (a licenza é ligazón) | Citando {{owner}} (licenza CC BY 4.0). | Citando la fuente: {{owner}} (licencia CC BY 4.0). |
    | `openData.more.reuse.bodyAll` | Citando o concello de orixe (licenza CC BY 4.0). | Citando el ayuntamiento de origen (licencia CC BY 4.0). |
    | `openData.ownerDefault` (sen entidade responsable) | o Concello {{ofName}} | Ayuntamiento {{ofName}} |
    | `openData.more.portals.title` | Portais de datos abertos | Portales de datos abiertos |
    | `openData.more.portals.body` («Ficha de catálogo» é ligazón) | Ficha de catálogo (formato DCAT-AP). | Ficha de catálogo (formato DCAT-AP). |
    | `section.extraSettings.title` | Configuración adicional | Configuración adicional |
    | `section.noShow.penalty` (xa existe) | Penalizar as faltas | Penalizar las faltas |
    | `section.noShow.help` (nova) | Quen reserva e non se presenta acumula unha falta. Ao chegar ás faltas que indiques, non pode reservar por internet durante os días de bloqueo; no mostrador si. | Quien reserva y no se presenta acumula una falta. Al llegar a las faltas que indiques, no puede reservar por internet durante los días de bloqueo; en el mostrador sí. |
    | `section.openData.label` | Datos abertos | Datos abiertos |
    | `section.openData.help` | O concello publica na web, para que calquera os descargue e reutilice, as instalacións, os peches e a porcentaxe de ocupación dos días pasados. Nunca datos de quen reserva. Se non se activa, a páxina non se ve nin aparece no pé da web. | El ayuntamiento publica en la web, para que cualquiera los descargue y reutilice, las instalaciones, los cierres y el porcentaje de ocupación de los días pasados. Nunca datos de quien reserva. Si no se activa, la página no se ve ni aparece en el pie de la web. |
    | `section.openData.preview` · `section.openData.previewOf` (a segunda, só para lectores de pantalla) | Vista previa · de «Datos abertos» | Vista previa · de «Datos abiertos» |
    | `facility.schedule.exceptions.reasonTip` · `facility.blocks.reasonTip` (novas) | Vese na web e nos datos abertos; non poñas nomes de persoas. | Se ve en la web y en los datos abiertos; no pongas nombres de personas. |
    | `facility.houseRules.help` (cambia) | Opcional. Se as escribes, van nos dous idiomas, ata {{max}} caracteres cada unha. Vense na web e nos datos abertos; non poñas nomes de persoas. | Opcional. Si las escribes, van en los dos idiomas, hasta {{max}} caracteres cada una. Se ven en la web y en los datos abiertos; no pongas nombres de personas. |
    | `facility.description.help` (cambia) | Opcional. Se a escribes, vai nos dous idiomas, ata {{max}} caracteres cada un. Vese na web; non poñas nomes de persoas. | Opcional. Si la escribes, va en los dos idiomas, hasta {{max}} caracteres cada uno. Se ve en la web; no pongas nombres de personas. |
    | `customFields.editor.publicTip` (nova; no editor, co sitio «Instalación» e «Todo o mundo (web pública)») | Vese na web e nos datos abertos; non poñas nomes de persoas. | Se ve en la web y en los datos abiertos; no pongas nombres de personas. |
    | `activity.actions.municipality.open-data` | Publicar ou retirar os datos abertos | Publicar o retirar los datos abiertos |
    | `activity.done.municipality.open-data` | Cambiou a publicación dos datos abertos | Cambió la publicación de los datos abiertos |
    | `activity.fields.openData` | Datos abertos | Datos abiertos |
    | `errors.opendata.not-published` | Este concello non publica datos abertos. | Este ayuntamiento no publica datos abiertos. |

    Sae `section.noShow.title` («Non presentados»): xa non hai tarxeta con ese título. A descrición non entra nos datos abertos, así que o seu aviso só di «na web» (decisión do mantedor). A ligazón da licenza é a oficial de Creative Commons Recoñecemento 4.0 Internacional (`https://creativecommons.org/licenses/by/4.0/`), nunha xanela nova.
28. **Textos legais de Furar** (os concellos con texto ou páxina propios revisan o seu).
    - **Aviso legal, «Contidos»** (`legalNotice.openData`, nova; só co interruptor aceso): «Os conxuntos da páxina «Datos abertos» pódense reutilizar para calquera fin baixo a licenza Creative Commons Recoñecemento 4.0 Internacional (CC BY 4.0), citando a fonte ({{owner}}), conforme á Lei 37/2007, do 16 de novembro, sobre reutilización da información do sector público.» / «Los conjuntos de la página «Datos abiertos» pueden reutilizarse para cualquier fin bajo la licencia Creative Commons Reconocimiento 4.0 Internacional (CC BY 4.0), citando la fuente ({{owner}}), conforme a la Ley 37/2007, de 16 de noviembre, sobre reutilización de la información del sector público.»
    - **Privacidade** (`privacy.residents.openData`, nova; só co interruptor aceso): «Os datos abertos que publica esta web non levan datos de quen reserva: a ocupación das instalacións que mostran as súas horas libres publícase só como a parte reservada de cada cuarto de hora e de días xa pasados. O motivo dun peche é un texto do concello que se ve na web.» / «Los datos abiertos que publica esta web no llevan datos de quien reserva: la ocupación de las instalaciones que muestran sus horas libres se publica solo como la parte reservada de cada cuarto de hora y de días ya pasados. El motivo de un cierre es un texto del ayuntamiento que se ve en la web.»
    - Os dous textos, aceptados polo mantedor o 2026-11-11.

### Probas

29. **Probas previstas.**
    - **`*Test`:** `OpenDataCsvTest` (BOM, `;`, CRLF, comiñas, fórmulas, **punto decimal** en coordenadas e euros, listas con `|` e o escape de `\|`, as columnas exactas e na orde do punto 16, unha fila por uso, a instalación sen usos nunha fila coas columnas do uso baleiras, `prezo_euros` baleiro con `NONE` e `0.00` con `FREE`, `prezo_por`, `partes` e `prazas` só no seu modo, `dias` desde a máscara, `datas` só nos usos de datas, as condicións baleiras sen norma, `deportes_codigos` sen os propios, `servizos` en códigos, `codigo_postal` como texto, a serialización de `horario`, e que non saen `ridega_code` nin o tipo); `OpenDataJsonTest` e `OpenDataGeoJsonTest` (envoltorio con `zona_horaria`, tipos, `usos` dentro de cada instalación e baleira sen usos, `geometry` nula, `properties` igual ao obxecto do JSON); `OpenDataCodesTest` (con `lang=es`, os códigos e os nomes das columnas son os mesmos ca con `gl`); `OpenDataLanguageTest` (`lang=es` cambia deportes, descrición, normas e festivos; nome, enderezo, nome do uso, partes e motivo saen iguais nos dous idiomas; un texto traducible sen o idioma pedido sae en galego); `ClosureReasonTest` (motivo nun peche do día de `open_data_since` e posteriores; baleiro nos anteriores; volver acender move o corte); `DcatCatalogTest` (as propiedades obrigatorias do perfil e os URI do punto 19, es e gl, publicador co URI do DIR3, sen DIR3 non hai ficha, a de todos sen variables non existe); `QuarterOccupancyTest` (unha reserva enteira de 17:00 a 18:30 dá 100 nos seis tramos e 0 desde as 18:30; partes: 1 de 2 = 50, 1 de 3 = 33, coas partes vivas ese día e non coas de hoxe; prazas: suma de `seats` sobre o `capacity` da franxa en `use_slot`, tamén se o aforo do uso cambiou despois; un non presentado conta; unha cancelada non; un só uso por tramo, co seu identificador; redondeo; tramo bloqueado ou pechado sen valor; 92 e 100 tramos os días de cambio de hora; ningunha reserva, apertura nin bloqueo parte un tramo); `OpenDataOccupancyCsvTest` (columnas `instalacion`, `uso_id`, `uso_nome`, `modo_aluguer`, `data`, `inicio`, `fin`, `ocupacion`; as tres do uso baleiras nos tramos libres; un uso retirado ou caducado segue co seu nome e o seu modo; sen fila nos tramos `null`; `inicio` e `fin` ISO 8601 con desfase, `+01:00` no inverno e `+02:00` no verán; `fin` do último tramo, o día seguinte ás 00:00; o tramo repetido do cambio de hora de outubro, cos dous desfases); `OccupancyDatesTest` (sen datas, o último mes enteiro; só unha, 400; fóra dos 12 últimos meses, `ata` hoxe, `desde` despois de `ata`, máis de 12 meses nun concello ou dun mes no de todos, 400); `PublicFacilityCustomFieldsIT` (a ficha pública leva só os campos `PUBLIC` activos e as súas definicións; anónimo, sen `OWNER` nin `STAFF`); `OpenDataCustomFieldsTest` (os sete tipos en CSV, JSON e GeoJSON; `campo_<código>` por orde de código; selección e selección múltiple coa etiqueta en `lang` no CSV e o código no JSON; número con coma decimal; sen valor, baleiro; o de todos sen columnas `campo_…` no CSV e con `campos` no JSON); `OpenDataRefreshServiceTest` (`hoxe` e `onte` co `Clock` en Europe/Madrid, tamén cruzando a medianoite; desde `calculated_through`; un concello sen filas non recalcula 12 meses a noite seguinte; instalación que entra máis tarde empeza ese día; anacos; borrado aos 12 meses; apagado borra; instalación que apaga «ocos visibles» borra; pegada e `changed_on` = día da tarefa); `MunicipalityTest` (aceso por defecto, apagar, volver acender); `CsvCellsTest`; `SlugTest` (`datos-abertos` reservado).
    - **`*IT`:** `OpenDataIT` (os tres ficheiros e os seus formatos sen token; `desde` e `ata` válidos, fóra dos 12 meses → 400; só instalacións públicas; un borrador ou unha baixa non sae en ningún; **unha instalación activa sen `onlineBooking` nin `showFreeSlots` sae en instalacións e non na ocupación, nin na táboa nin no ficheiro**; **só saen os campos personalizados `PUBLIC` e activos: un `OWNER`, un `STAFF` e un desactivado non saen en ningún formato**; apagado → 404 en todo; `ETag` e 304; `Content-Disposition`; `lang`; **`Access-Control-Allow-Origin: *` nos ficheiros, na ficha e nos seus 404, e ausente no resumo e no `PUT`**; o tope de descargas de ocupación dun concello → 429 con `Retry-After`; un 304 non colle praza); `OpenDataCatalogIT` (de todos: ocupación de máis dun mes → 400; sen datas, o último mes enteiro; só publicados e acesos, cada fila co seu concello, un que apaga sae; o tope común → 429; a lectura en tramos devolve as filas de cada concello, non 0); `OpenDataRefreshIT` (a tarefa con reservas reais; idempotente; dous concellos; dúas execucións á vez deixan o mesmo resultado ca unha); `MunicipalityOpenDataIT` (permiso de Xestión e plataforma, Recepción non; `If-Match`; rexistro de actividade); a ida e volta de `MunicipalityPortabilityIT` con e sen `openData`; `IndicatorsQueriesIT` (`quarterOccupancy` con reservas reais de enteira, partes e prazas e un bloqueo); `OpenDataSchemaIT` (ningunha columna de persoa nas táboas `open_data_*`); `FacilityQueriesOpenDataIT` (`publicData()` leva os mesmos usos ca a ficha pública, sen os retirados nin os caducados; `useLabels` dá tamén os retirados).
    - **`*TenantIT`:** `OpenDataOccupancySqlTenantIT` e `OpenDataDatasetSqlTenantIT` (estenden `AbstractTenantIT`); `BookingStatsSqlTenantIT` cobre a consulta nova. `SchemaTenancyIT` e `NativeSqlRowLevelSecurityIT` pasan coas dúas táboas e a FK nova.
    - **As de sempre que cambian:** `ModularityTest` (módulo e dependencias), `SlugReservedRoutesTest`, `OpenApiContractIT`, os tests que percorren todos os endpoints sen token.
    - **Web, `*.spec.ts`:** `open-data.page.spec.ts` (pestanas, pregables pechados e independentes, columna `concello` só na de todos, data curta no móbil, sen «Actualizado» sen data, diálogo con dous ou tres formatos, «Columnas» cos campos propios do concello, un só ficheiro na pestana Instalacións, o filtro «Datas» só na Ocupación co último mes enteiro por defecto (na de todos, sen «Últimos 12 meses» e co «?»), os erros «Escolle o primeiro e o último día» e «Como moito…» sen abrir o diálogo, o tamaño aproximado no diálogo, a descarga pide o URL do ficheiro co `desde` e o `ata` escollidos, 404, 429 e 5xx quedan no diálogo sen saír da páxina, estado baleiro da de todos, sen «Portais» con `catalog: false`), `disclosure.component.spec.ts`, `date-filter.component.spec.ts` (xa en `shared/ui`: atallos por entrada, días fóra de `min`/`max` desactivados, o mesmo chip co rango e cunha data; os casos das reservas seguen pasando), `switch.component.spec.ts` (orde nova, os tres contextos do punto 26) e `help-tip.component.spec.ts` (18 px con contorno, área de 24 px), `general.tab.spec.ts` (unha tarxeta, dúas filas, «Vista previa» só aceso e gardado, garda os dous axustes), `exception-dialog` e `block-dialog` (o «?» do motivo co aviso), `custom-field-editor` (o aviso con «Instalación» e «Todo o mundo (web pública)», e non noutros), `day-row` (interruptor á dereita; horas só co día aceso), `shell.component.spec.ts` (ligazón do pé, tamén na raíz só con varios concellos).
    - **E2E con axe** (`web/e2e/datos-abertos.e2e.ts`, proxectos `gl`, `es` e `gl-mobile`): a páxina desde o pé; as tres pestanas; abrir e pechar os pregables co teclado; o diálogo e a descarga (nome do ficheiro); apagar en Concello › Xeral → sen ligazón no pé e 404; a páxina de todos coa columna `concello`. Contra o despregue completo (detrás do proxy, con compresión), unha segunda petición dun ficheiro co seu `ETag` dá 304. `concello.e2e.ts` cambia pola tarxeta nova.
    - **Á man, antes de etiquetar:** a ficha dun concello con DIR3 e a de todos, no validador SHACL do perfil (punto 19).

## Preguntas abertas

Ningunha: todas están decididas e levadas ao texto.

Fóra, por se o mantedor as quere: as horas nos peches de parte do día; e o tipo de instalación e a fin da tempada, que hoxe non mostra a ficha pública (punto 16). Non se constrúen.

## Consecuencias

- Calquera pode descargar e reutilizar os datos do servizo de deportes, tamén desde unha web no navegador, e un portal de datos abertos pode recollelos coa ficha, sen pedirlle nada ao concello.
- Un módulo, dúas táboas pequenas con RLS, unha tarefa nocturna e unha entrada máis en `CrossModuleForeignKeys`. `indicators` deixa de ser só o panel: publica unha consulta que outro módulo usa.
- Tres grupos de rutas máis sen concello (`/catalog/open-data…`) e un slug reservado máis (`datos-abertos`). Un concello que xa tivese ese slug quedaría tapado pola páxina de todos: non hai ningún e a alta xa non o deixa.
- **Ao despregar, todos os concellos que xa existen pasan a publicar** (aceso por defecto): vai no `CHANGELOG.md` e avísase a quen opera cada instalación antes de actualizar. Os motivos dos peches de antes dese día non se publican.
- A ocupación publicada é a que constaba cada noite. Non se corrixe cara atrás se despois cambia unha reserva ou o horario. As instalacións que non mostran os seus ocos non teñen ocupación publicada.
- Un concello sen DIR3 publica os ficheiros pero non a ficha de catálogo.
- A ocupación en cuartos de hora é exacta e agrupable, pero grande: decenas de MB os 12 meses dun concello grande e, na de todos, como moito un mes por descarga (punto 20).
- O CSV de instalacións repite os datos da instalación en cada uso: quen queira unha fila por instalación quédase coa primeira de cada `id`, ou usa o JSON.
- O aforo dos usos por prazas é público: dun día co mesmo aforo ca hoxe pódese deducir cantas prazas se reservaron en cada cuarto de hora; nunca quen.
- Os nomes de columnas en galego son un contrato: cambiar un é romper a quen reutiliza. Unha columna nova engádese ao final. As columnas `campo_<código>` dependen do concello: aparecen e desaparecen cos seus campos públicos e lense polo nome.
- O interruptor e o «?» cambian en toda a aplicación: hai que refacer as capturas e os e2e que dependen da orde ou do tamaño.
- Sen código INE (`dct:spatial`) e sen os bytes das fotos: dous límites coñecidos para unha fase posterior.

## Alternativas descartadas

- **Calcular a ocupación ao pedila:** cara nunha ruta sen sesión, cambia o pasado cando cambia o horario e fai que a lectura pública toque as reservas (punto 3).
- **Publicar a ocupación de todas as instalacións:** das que non mostran os seus ocos sacaría un dato de uso que hoxe non é público (punto 3).
- **Táboa agregada sen RLS en `TenancyAllowlist`** (o previsto en ADR-0006 para os indicadores entre concellos): a lectura de todos sería unha soa consulta, pero abriría un camiño que le varios concellos á vez; percorrelos un a un mantén as tres capas.
- **Meter os datos abertos en `indicators` ou en `facility`:** o primeiro é o panel da xestión, con permiso; o segundo non coñece a ocupación. Un módulo que só serializa deixa cada dato co seu dono.
- **Duplicar a fórmula da ocupación en `opendata`:** dúas definicións que acabarían diverxendo; por iso a publica `indicators`.
- **Un booleano para o interruptor:** non dá a data de publicación que pide a ficha nin o corte dos motivos.
- **Partir da última fila gardada na tarefa nocturna:** un concello sen filas recalcularía 12 meses cada noite; por iso `calculated_through`.
- **Escribir en fluxo noutro fío (`StreamingResponseBody`):** perde o `TenantContext`, que é dun fío, e a RLS devolvería 0 filas sen erro (punto 20).
- **Xerar os ficheiros cada noite e gardalos:** as instalacións e os peches teñen que reflectir ao momento unha baixa ou un borrador; só a ocupación é de onte.
- **«Actualizado o…» calculado en cada visita** (comparando a pegada en vivo): xerar os conxuntos en cada carga da páxina por unha data que só ten precisión de día.
- **Limiar ou supresión de celas baixas:** non protexe máis e obriga a contar persoas (punto 9).
- **Agrupar a ocupación en tramos máis anchos** (horas, mañá, tarde, noite, ou só por día) para baixar o detalle: dá porcentaxes que non corresponden a nada real (unha franxa de 90 minutos partida en dúas horas), impide agrupar polas franxas de cada uso, e a protección xa a dan as tres medidas do punto 9.
- **Tramos de 5 ou 10 minutos:** ningunha reserva, apertura nin bloqueo cae fóra dos múltiplos de 15 (punto 4); un tramo máis curto só multiplicaría as filas.
- **Publicar o numerador e o denominador de cada tramo:** dúas columnas que só teñen sentido nalgúns modos; a porcentaxe é un número comparable entre modos e o aforo de hoxe xa está nas instalacións (punto 4).
- **Dous ficheiros nas instalacións (instalacións e «Usos e franxas») ou unha fila por instalación cos usos nun texto:** o primeiro obriga a cruzar dous ficheiros para saber que se aluga e onde; o segundo non se pode filtrar nin agrupar nunha folla de cálculo (decisión do mantedor).
- **Ocupación por meses naturais nun selector:** o filtro de datas xa existe e deixa pedir unha semana ou un trimestre; os meses son un dos seus atallos.
- **Calcular a ocupación co aforo e as partes de hoxe:** cambiaría o pasado cando cambia un uso; por iso se usan os de cada día (punto 4).
- **Unha fila por tramo na táboa:** máis de 400.000 filas por concello e ano, fronte ás 7.300 cun vector por día (punto 3).
- **Unha fila por uso e tramo na ocupación:** en cada tramo só pode haber un uso (ADR-0012), así que basta unha columna `uso` na fila da instalación.
- **Campos personalizados en columnas por concello no CSV de todos:** centos de columnas baleiras; van no JSON (decisión do mantedor).
- **Non publicar o motivo dos peches:** o motivo xa se ve na ficha e explica o peche; abonda co aviso e co corte en `open_data_since`.
- **Columnas en inglés ou traducidas segundo o idioma:** o primeiro afasta o ficheiro de quen o abre nunha folla de cálculo; o segundo dá dous esquemas ao mesmo conxunto.
- **Coma decimal, coma nos outros CSV do proxecto:** rompe os lectores automáticos e as ferramentas de mapas, e un conxunto publicado non pode depender do idioma de quen o abre.
- **`;` como separador de listas dentro dunha cela:** é o separador das celas do CSV; con `|` a cela non necesita comiñas.
- **Etiquetas traducidas nos valores fixos** (`entera`, `sí`): dúas versións do mesmo dato segundo `lang`; os códigos son os mesmos sempre.
- **Publicar o tipo de instalación, a fin da tempada ou o código RIDEGA:** os dous primeiros non os mostra hoxe a ficha pública; o RIDEGA é un código interno do censo deportivo.
- **Idioma por `Accept-Language`:** o mesmo URL daría ficheiros distintos e as cachés servirían o que non é.
- **Paxinar os ficheiros:** ninguén reutiliza un CSV por páxinas; o tamaño acóutase cos 12 meses, a descarga por datas, a lectura por tramos e o tope de descargas á vez.
- **Ligazón directa ao ficheiro desde o diálogo:** un 404, 429 ou 5xx sacaría a persoa da web a un JSON de erro.
- **JSON-LD ou Turtle para a ficha, cunha libraría de RDF:** unha dependencia nova para un documento pequeno e fixo; RDF/XML escríbese co JDK.
- **Publicador da ficha sen DIR3, só co nome:** o perfil esixe o URI do organismo; unha ficha sen el non valida.
- **Publicar tamén os bloqueos dunhas horas ou a asistencia:** non está decidido e achega detalle sobre o uso real que hoxe non se publica. (O aforo si se publica: xa o mostra a ficha.)
- **Deixar a tarxeta «Non presentados» e engadir outra «Datos abertos»:** dúas tarxetas dunha liña cada unha; unha tarxeta con filas medra sen encher a pestana.
