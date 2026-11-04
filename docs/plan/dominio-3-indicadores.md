<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Plan do dominio 3: indicadores, fase 1

> **Histórico: manda a especificación e as ADR.** Este plan guiou a construción do dominio e non se mantén ao día; se algo non coincide coa [especificación](../produto/especificacion.md) ou coas [ADR](../adr/), valen elas.

**Estado:** executado (histórico). Era a proposta inicial, v1 · 2026-10-28 · do 29 de outubro ao 6 de novembro. **Fonte de produto:** [especificación](../produto/especificacion.md), bloque 10, recortado para a fase 1 coas decisións do 2026-10-28 (maqueta `31-indicadores.html`, opción A), e o bloque 5 para saber que datos hai. Onde este plan e a especificación non coincidan, manda a especificación. O que falta de produto está no §6 e **non se decide ao construír**: constrúese o valor por defecto marcado e cámbiase cando o mantedor responda.

Hai que ler isto, as regras do perfil (§4) e a tarefa propia (§3). As regras comúns son as do [plan do dominio 1](dominio-1-instalacions.md) §2.1 e §3.5: carrís, lock de Docker, ficheiros quentes e rebase das migracións.

---

## 1. Alcance técnico

**Entra:**
- **Pantalla «Indicadores»** en `/:concello/xestion/indicadores`, só para o xestor (P1). Ten unha fila de filtros: período (Esta semana · Mes · Trimestre · Ano · Datas…), instalación, uso e «Exportar».
- **Cinco cifras:** reservas, ocupación media, horas reservadas, persoas distintas e cobrado. Cada unha leva unha minigráfica e a comparación co período anterior.
- **Cada cifra ábrese nun modal** cunha transición desde a tarxeta (View Transitions API). Sen a API ou con `prefers-reduced-motion`, ábrese sen animación. O modal mostra a evolución na unidade do filtro, coa mesma liña con área da miniatura, a etiqueta só no último valor, a táboa e «Exportar».
- **Seis gráficos:** ocupación por instalación (barras); ocupación día a día (liña); horas punta (mapa de calor día × hora); cancelacións por motivo (en prazo, fóra de prazo e polo concello, en barras apiladas); ingresos por instalación (cobrado, pendente, a devolver e devolto); persoas distintas por mes. Cada gráfico leva título, unha frase coa conclusión calculada, «Táboa» e «Exportar».
- **Exportar** abre xusto debaixo un menú pequeno con PDF e CSV. Na cabeceira exporta todo o panel; en cada gráfico, ese gráfico; no modal, esa evolución.
- **Tema claro e escuro.** Os estados levan a paleta de estados da app (verde o positivo, vermello o negativo ou a alerta, ámbar o pendente, azul info, gris só o pechado ou plano) e as cantidades, o `primary` do concello e unha escala secuencial derivada del.
- **Datos de demo históricos** para que o panel luza.

**Non entra:** uso real e non presentados (precisa o QR), demanda non atendida (precisa a lista de espera), sexo e idade para RIDEGA (precisa o perfil), «exento» nos ingresos (precisa os colectivos), indicadores da Deputación entre concellos (ADR-0006 ARQ2-7) e caché.

**Regra transversal:** os datos van sempre agregados. Ningún nome, correo nin `sub` sae de `booking`, nin pola API, nin no CSV, nin no PDF.

### 1.1 Que existe e que se usa

| Peza | Estado a 2026-10-28 | Que pasa |
|---|---|---|
| `booking`: táboa `booking` con `period tstzrange`, `status`, `payment_status`, `amount_cents`, `cancel_until`, `cancelled_at` e `holder_subject` | Feita (D2-03) | É a única fonte. Non hai táboas novas, só un índice (D3-02) |
| `BookingQueries` | Ten `find` e `notice` | Aparece unha interface pública nova, `BookingStatistics`, con agregados (D3-02) |
| `FacilityQueries.openingIntervalsByFacility(null, from, to)` | Feito, sen N+1, ata 400 días e sen as horas dos bloqueos | É o denominador da ocupación; os indicadores usan `longOpeningIntervalsByFacility` (o mesmo ata 2.000 días, nunha lectura) |
| `FacilityQueries.usesById` / `UseSummary` (partes vivas e `capacity`) | Feito | Dá o peso de partes e prazas |
| `FacilityQueries` para listar todas as instalacións | Non existe | Engádese `all()` (D3-04) |
| Módulo `metrics` en `arquitectura.md` §3 («só escoita eventos») | Só previsto | Pasa a chamarse `indicators` e na fase 1 le de forma síncrona (§1.2.1) |
| `shared/ui`: `[furarMenu]`, `[furarTile]`, `furar-dialog`, `[furarSegmented]`, `furar-calendar` con `range`, `money`, `furar-select` | Feitos | Reutilízanse |
| `deseno.md` §8: «Non se fan … gráficos» | Vixente | D3-01 cámbiao |

### 1.2 Decisións técnicas (non son produto)

1. **Módulo novo `indicators`** (`scripts/novo-modulo.sh indicators Indicator indicators indicadores`), reducido como `notification`: sen táboa, sen entidade, sen CRUD. Depende de `shared`, `booking` e `facility`. Non vai en `booking` porque os indicadores crecerán con `people` (RIDEGA), a asistencia e a lista de espera; non se chama `metrics` porque choca co `/actuator/metrics` de ADR-0015 e ADR-0006 xa usa `indicators`. **Lectura síncrona, sen proxección por eventos:** `BookingStatistics` dá agregados xa filtrados polo concello (RLS) e `FacilityQueries` dá as horas abertas; unha proxección pediría reconstruír o histórico e a demo. As táboas agregadas de ADR-0006 chegarán cos indicadores da Deputación.
2. **O SQL vive en `booking`.** `BookingStatsSql` leva `@NativeSql(MUNICIPALITY)`, o seu `BookingStatsSqlTenantIT` e as regras A8b: o concello no nivel 0; os filtros opcionais como `(:all or …)`; sen `case` (as categorías van con `count(*) filter (where …)` e `sum(...) filter (...)`). Devolve só contas, minutos e céntimos por uso, día, hora ou período. O `holder_subject` só aparece dentro de `count(distinct …)`. Índice novo: `booking_start_idx on booking (municipality_id, lower(period))`.
3. **Hora de Galicia.** Días, semanas (de luns a domingo), meses, trimestres, anos, día da semana e hora en Europe/Madrid, tanto en SQL (`at time zone 'Europe/Madrid'`, `date_trunc(:unit, …)`) como en Java. Os límites do período convértense a `Instant` (`[desde 00:00, ata+1 00:00)`). As horas abertas saen dos `Instant` de `OpeningInterval`, así os días de 23 e de 25 horas quedan ben.
4. **Definicións** (os valores marcados con P están pendentes de validar no §6):

   | Indicador | Definición |
   |---|---|
   | Período dunha reserva | O día do seu inicio, `lower(period)` en Europe/Madrid, para todo: tamén o cobrado e as cancelacións (P4) |
   | Reservas | Número de reservas `CONFIRMED` (as realizadas inclúense) que empezan no período. As canceladas quedan fóra (P2) |
   | Horas reservadas | Σ duración × peso das `CONFIRMED`. O peso é 1 se a reserva é enteira, 1/nº de partes vivas por partes e 1/`capacity` por prazas (P3) |
   | Horas abertas | Σ (`end − start`) dos `OpeningInterval` das instalacións do filtro no período (tempada, festivos e excepcións, menos bloqueos). Co filtro de uso, só os días que vale o uso. As instalacións de baixa non entran |
   | Ocupación | Horas reservadas / horas abertas, só coas instalacións que teñen horas abertas e limitada ao 100 % |
   | Ocupación media (cifra) | O mesmo cociente sobre todo o filtro (ponderado polas horas) |
   | Horas punta | Por día ISO e hora, minutos reservados (cortando cada reserva polas horas) / minutos abertos dese día e hora. Cela sen horas abertas: baleira, en gris («pechado») |
   | Persoas distintas | `count(distinct holder_subject)` das `CONFIRMED` do período (o do período enteiro, non a suma dos meses) |
   | Cancelacións | **En prazo:** `CANCELLED_BY_PERSON` con `cancelled_at < cancel_until`. **Fóra de prazo:** `CANCELLED_BY_PERSON` con `cancelled_at ≥ cancel_until`; hoxe sempre 0 pola P1 do dominio 2 (P5). **Concello:** `CANCELLED_BY_MUNICIPALITY` |
   | Cobrado | Σ `amount_cents` con `payment_status = PAID` (P7) |
   | Pendente | Σ `amount_cents` das `CONFIRMED` con `PENDING` |
   | A devolver / Devolto | Σ `amount_cents` con `TO_REFUND` / con `REFUNDED` |

5. **Períodos e comparación.** Cada período é a unidade natural que contén hoxe (semana ISO, mes, trimestre ou ano); con «Datas…», un rango libre de 1 a 366 días (P9). A API admite `date` como ancla. O **anterior** é a unidade anterior (nun rango libre, os mesmos días xusto antes). A **evolución** do modal: 12 semanas, 12 meses, 8 trimestres ou 5 anos rematando no actual; nun rango libre, 12 meses rematando no mes do fin. A **minigráfica**: os seis últimos valores da evolución. Cambio en porcentaxe nas contas, horas e diñeiro; en puntos na ocupación; sen valor anterior, «sen datos do período anterior»; subir en verde, baixar en vermello, igual en gris. Barras das cancelacións e liña día a día: por día na semana, por semana no mes e no trimestre, por mes no ano (nun rango libre: por día ata 31 días, por semana ata 120, por mes despois). Persoas: os seis meses que rematan no do período e os doce no ano (P10). **Período en curso** (revisión do 2026-10-29, ADR-0026): o valor segue sendo o período enteiro (P8), pero o cambio compara do primeiro día a hoxe co mesmo tramo do anterior (a mesma data da unidade anterior, co tope no seu último día; na semana, o mesmo día da semana anterior; nun rango libre, os mesmos días desde o inicio), e a tarxeta dío nunha liña («ata hoxe, fronte ao mesmo tramo de setembro»). Nas liñas, o tramo ata o punto en curso vai discontinuo coa mesma cor; na táboa e no CSV, «(en curso)».
6. **API.** A resposta leva números en bruto (minutos, céntimos, contas). As porcentaxes e as frases de conclusión calcúlaas a web (os textos viven en `gl.json`/`es.json`). Unha soa petición trae todo o panel, coa evolución das cinco cifras.
7. **Gráficos: Apache ECharts 6.1.0 directamente, sen ngx-echarts.** `echarts` 6.1.0 (Apache-2.0), `zrender` 6.1.0 (BSD-3-Clause) e `tslib` 2.3.0 (0BSD): licenzas en `allowed-licenses.txt` e versións de máis de 14 días. Envoltorio propio `furar-chart` (~120 liñas) que carga ECharts só ao aparecer o primeiro gráfico (`import('./echarts-setup')`) e rexistra só `LineChart`, `BarChart`, `HeatmapChart`, `GridComponent`, `TooltipComponent`, `VisualMapComponent` e `CanvasRenderer`. Sen ngx-echarts porque habería que envolvela igual (tokens do tema, redebuxado, CSP, impresión, accesibilidade): unha dependencia máis sen quitar código.
8. **CSP (`style-src 'self'`).** Debuxo en **canvas** (zrender estiliza por CSSOM, que a CSP non bloquea; SVG descártase). Tooltips con **`renderMode: 'richText'`** (o tooltip HTML mete `<span style>` por `innerHTML`). Sen `aria` de ECharts: a accesibilidade dana a táboa e o nome do gráfico. `view-transition-name` e tamaños por CSSOM. Estilos nunha folla á parte (`styles/indicadores.css`, `inject: false`, `lazyStylesheetResolver`). O e2e comproba que non aparece ningún `securitypolicyviolation` tras pasar o rato polos gráficos.
9. **PDF = `window.print()` cunha folla de impresión.** Sen dependencias novas; o PDF leva texto real e táboas (Chromium xera PDF etiquetado; jsPDF e pdfmake darían imaxe sen etiquetas e pesarían 0,4–1 MB; un PDF no servidor non cabe en 3,5 GB). A persoa escolle «Gardar como PDF» (dío a axuda do menú). Alcance: `data-print` en `<html>` (`panel`, `card` ou `dialog`) e `data-print-target`. Cabeceira impresa cos filtros escritos («Concello de Narón · Outubro de 2026 · Todas as instalacións · Todos os usos · xerado o 28/10/2026 13:05»). Antes de imprimir, cada gráfico do alcance cámbiase por un `<img>` de `getDataURL({pixelRatio: 2})` co tema claro e `alt` = título + conclusión. Táboas no PDF dun gráfico e do modal; no do panel, non.
10. **CSV no navegador**, cos datos da táboa: UTF-8 con BOM, `;`, CRLF, comiñas RFC 4180, decimais con coma; protección contra fórmulas (`'` diante de `= + - @`); o do panel é un ficheiro con bloques; descarga por `Blob` e `<a download>`; nome `indicadores-<concello>-<período>[-<gráfico>].csv`.
11. **Cores.** Estados cos tokens de ton (`--f-success`, `--f-danger`, `--f-info`, `--f-warn`, `--f-muted`): cancelacións en prazo verde, fóra de prazo vermello, concello azul; ingresos cobrado verde, pendente ámbar, a devolver vermello, devolto gris. Cantidades con `--f-primary`; meses pasados das persoas co paso 2 da escala. Escala secuencial `sequentialScale(tokens, 6)` nova en `core/theme` (test: monótona, último paso ≥ 3:1 sobre `surface`, distinguible nos dous temas). Daltonismo: verde e vermello teñen case a mesma luminancia, así que as barras apiladas levan separador de 2 px en `surface`, a serie de alerta leva trama (`decal`) e lenda e táboa van en texto.
12. **Accesibilidade.** Gráfico nun `<figure>` co canvas `aria-hidden` e a figura con `role="img"` e `aria-label` = título + conclusión. «Táboa» con `aria-pressed` e `aria-controls`; táboa con `caption`, `th scope` e cifras á dereita. Mapa de calor coa táboa completa e, no móbil, rexión con scroll propio con nome (sen scroll de páxina a 320 px). Cifras como `<button>` con `aria-haspopup="dialog"`. Modal con `furar-dialog` (foco atrapado, Esc, foco de volta á tarxeta, foco inicial no título). Exportar co patrón APG «menu button» (`[furarMenu]`). Os filtros anuncian o resultado en `role="status"`.
13. **Rendemento:** sen caché. 6–9 lecturas de `booking` e as de `facility` por panel, cada unha sobre un índice: 23–26 sentenzas en total segundo a unidade (ADR-0026, punto 14), catro máis no período en curso (as cifras e as persoas dos dous tramos «ata hoxe»). O IT conta as sentenzas e fixa o tope de cada unidade; a medida con `explain analyze` sobre 100.000 reservas vai no PR.
14. **Permiso:** `indicators.view`, só MANAGER (P1). Sen `AuditLog`: só agregados, sen datos persoais, e a descarga faise no navegador.

---

## 2. API e datos

### 2.1 Táboas
Non hai táboas novas. Só `V{AAAAMMDDHHMM}__booking_start_index.sql`: `create index booking_start_idx on booking (municipality_id, lower(period));`, rexistrada con `scripts/migracions.sh fixar`.

### 2.2 API pública de `booking` (raíz, records, A19)
`BookingStatistics`, implementada en `application/service`, co porto de saída `BookingStatsRepository` e o adaptador `BookingStatsSql`:
- `perUseAndDay(q)`: uso, instalación, data, reservas e minutos (`CONFIRMED`).
- `perUseWeekdayHour(q)`: uso, día ISO, hora e minutos cortados por hora (`cross join lateral generate_series`).
- `perBucket(q, unit)`: inicio, uso, reservas, minutos e céntimos pagados.
- `peoplePerBucket(q, unit)` e `people(q)`.
- `cancellations(q, unit)`: inicio, en prazo, fóra de prazo e concello.
- `money(q)`: por instalación, pagado, pendente, a devolver e devolto.
- `useIds(q)`: os usos con reservas, para o filtro.

`StatsQuery(LocalDate from, LocalDate to, @Nullable UUID facilityId, Set<UUID> useIds)` e `enum StatsUnit {DAY, WEEK, MONTH, QUARTER, YEAR}`. Non comproba permisos (faino o caso de uso).

### 2.3 HTTP (prefixo `/api/v1/municipalities/{slug}`)

| Método e ruta | Permiso | Que |
|---|---|---|
| `GET /indicators?period=WEEK\|MONTH\|QUARTER\|YEAR\|CUSTOM&date=&from=&to=&facilityId=&useId=…` | `indicators.view` | O panel |
| `GET /indicators/filters` | `indicators.view` | Instalacións (`id`, `name`, `status`; as de baixa só se teñen reservas) e usos (`id`, `facilityId`, `name`, `archived`) |

O panel devolve `period {kind, from, to, evolutionUnit, breakdownUnit}`, `previous {from, to}`, `comparison {toDate, from, to, comparedFrom, comparedTo}` (os tramos da variación: no período en curso, ata hoxe), `kpis {bookings, occupancy, reservedHours, people, paidCents}` (cada un `{value, previous, valueToDate, previousToDate, evolution[{from, to, value, partial}]}`; a ocupación como `{reservedMinutes, openMinutes}`), `occupancyByFacility[]`, `dailyOccupancy[]`, `peakHours[]`, `cancellations[]`, `revenue[]` e `people[]` (os tramos con data levan `partial`, se conteñen hoxe). `useId` pódese repetir (P6). Caso de uso con `@Transactional(readOnly = true)`.

**Erros** (`messages/indicators_{gl,es}.properties`): `indicators.period-invalid` (falta `from`/`to` en `CUSTOM`, `to < from` ou máis de 366 días) e `indicators.filter-invalid` (instalación ou uso doutro concello).

---

## 3. Tarefas

Tamaño: **S** ≤ ½ día; **M** ≈ 1 día. Un só carril pesado á vez: Docker, `ng build` e Playwright nunca en paralelo.

### 3.1 Docs (carril D)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D3-01** | ADR-0026 «Indicadores, fase 1» (§1.2, cada punto coa alternativa descartada) · `arquitectura.md` §3 (`metrics` → `indicators`) e índice de ADR · ADR-0018 (nome do módulo do CSV) · `glosario.md` (indicador, horas abertas, horas reservadas, ocupación, persoas distintas, cancelación en prazo e fóra de prazo, período anterior) · especificación bloque 10 (recorte da fase 1; ECharts sen ngx-echarts; P1–P10 en «Pendente de decidir») · `datos-persoais.md` (agregados, CSV sen nomes) · `deseno.md` §8 (P2 `furar-chart`, `furar-chart-card`, `furar-stat-tile`, `furar-export-menu`, `furar-data-table`; «gráficos só con `furar-chart`») · ADR-0021, nota da escala secuencial | `docs/` | Ningún documento contradí o plan; `verificar.sh --parte compliance` | S | — | Arquitecto |

### 3.2 API (carril A)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D3-02** | `BookingStatistics` e records (§2.2), `BookingStatsSql` (A8b) e `BookingStatsSqlTenantIT`, migración do índice, servizo de lectura | `gal.furar.booking`, `db/migration`, `migrations.lock` | `BookingStatisticsIT`: 29 de marzo e 25 de outubro; reserva de 90 min parte en dúas horas (60 + 30); canceladas fóra das contas; en prazo, fóra de prazo e concello; os catro cobros, cunha cancelada pendente fóra de «pendente»; persoas distintas do período ≠ suma dos meses; filtros por instalación e varios usos; semana = luns. `BookingStatsSqlTenantIT`; `SchemaTenancyIT`, `NativeSqlRowLevelSecurityIT`, `OccupancyModelIT`, `MigrationsIT` en verde; `rapido.sh --tenancy api` | M | D3-01 | Backend PostgreSQL |
| **D3-03** | Módulo `indicators` con `novo-modulo.sh`, reducido (sen migración, entidade nin CRUD); `package-info`; `IndicatorsPermission.VIEW` (MANAGER); dominio puro: `ReportPeriod`, `UseWeight`, `OpeningMinutes`, `Ratio` (tope 100 %), `Change` (% e puntos), `IndicatorsError`. **Sen Docker** | `gal.furar.indicators`, `ModularityTest`, `RolePermissionsTest`, `messages/indicators_*` | Semanas ISO do 1 de xaneiro e do 31 de decembro, trimestres, bisestos, anterior dun rango libre, 12/8/5 unidades, desagregación por lonxitude; peso con 0 partes e sen capacidade; minutos abertos co cambio de hora; cambio con anterior 0; `ModularityTest` e `RolePermissionsTest` en verde; cobertura ≥ 80 % en `domain` | S | D3-01 | Backend |
| **D3-04** | `ReadIndicatorsUseCase` e `ReadIndicatorFiltersUseCase`; compón `BookingStatistics` e `FacilityQueries`; `FacilityQueries.all()`; controlador, DTO, erros, `openapi.json` | `gal.furar.indicators`, `FacilityQueries` e a súa implementación, `openapi.json` | `IndicatorsIT` con `facility` real (tempada, excepción, festivo e bloqueo baixan as horas abertas; parte de 2 = media hora; praza de 30 = 1/30; uso só os sábados; ocupación ≤ 100 %; anterior e evolución cadran; `CUSTOM` de 367 días → 400; instalación allea → 400; STAFF → 403, sen token → 401, outro concello → 404; número de sentenzas fixo); `EndpointSecurityIT`, `ProblemDetailsIT`, `ErrorCatalogTest`, `OpenApiContractIT` en verde | M | D3-02, D3-03 | Backend |
| **D3-05** | `db/demo/R__demo_naron_history.sql` (CC0, idempotente): tempadas pasadas; ~5.000 reservas deterministas en 18 meses ata onte, con tendencia de subida, tardes e fins de semana cheos, campo baixo no verán, 8 % canceladas en prazo, unha semana con cancelacións do concello e cobros (pagados, algúns pendentes recentes, a devolver e devoltos); ~300 persoas ficticias (`demo-person-hNNN`, `example.org`); só en ocos libres | `db/demo/`, `DemoIndicatorsIT` | Arrinca con `dev,demo` en < 15 s máis ca hoxe; idempotente; o mes pasado de Narón ten os seis gráficos con datos e ocupación entre o 30 e o 80 %; o ano ten 12 meses; `OccupancyModelIT` e `MigrationsIT` en verde | M | D3-04 | Backend |

### 3.3 Web (carril W)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D3-06** | `echarts` 6.1.0 exacto; `shared/ui/chart/`: `furar-chart` (carga diferida, `document.fonts.ready`, tokens das variables CSS, redebuxado ao cambiar o tema, `ResizeObserver`, sen animación con movemento reducido, `dispose`, `image()`, `richText`) e construtores puros (`lineArea`, `sparkline`, `hBars`, `stackedBars`, `heatmap`, `columns`, `decal`); `sequentialScale` en `core/theme`; NOTICE de ECharts en `third-party-notices.mjs`; catálogo | `package*.json`, `shared/ui/chart/`, `core/theme/`, `scripts/third-party-notices.mjs`, catálogo | Specs cun motor falso; construtores con `richText` e sen `style`; escala monótona e ≥ 3:1 nos dous temas; licenzas (D04); orzamento inicial igual (D27, ECharts nun chunk diferido); `3rdpartylicenses.txt` co NOTICE | M | D3-01 | Frontend compoñentes |
| **D3-07** | `furar-stat-tile`, `furar-chart-card`, `furar-export-menu` (`[furarMenu]`, PDF e CSV), `furar-data-table`, `toCsv`/`downloadCsv`, `shared/print/print-scope.ts`, `shared/ui/view-transition.ts` (`withViewTransition`), `styles/indicadores.css` (`inject: false`) | `shared/ui/{stat-tile,chart-card,export-menu,data-table}/`, `shared/print/`, `shared/ui/view-transition.ts`, `styles/indicadores.css`, `angular.json`, `ui.*`, catálogo | Menú (foco, frechas, Esc, pecha ao escoller); táboa conmuta e o foco queda no botón; CSV (BOM, `;`, comiñas, coma decimal, `'=SUM(A1)`); alcance da impresión con `afterprint`; `withViewTransition` sen a API e con `reduce`; axe do catálogo en gl e es; 320 px; sen estilos de compoñente | M | D3-01 | Frontend compoñentes |
| **D3-08** | `features/indicators/`: ruta (`managerGuard`, `layout: 'wide'`), entrada no menú; `indicators.store.ts` (filtros na URL); fila de filtros (`[furarSegmented]`, «Datas…» con `furar-calendar` `range`, `furar-select` para instalación e uso); 5 `furar-stat-tile`; 6 `furar-chart-card`; `indicator-views.ts` e `conclusions.ts` puros; carga, baleiro e erro; `role="status"` | `features/indicators/`, `app.routes.ts`, `core/layout/site-bar.component.ts`, icona, `gl.json`/`es.json` | URL ↔ filtros; cada frase cos datos da maqueta; `Intl` no idioma; mapa de calor con celas pechadas; STAFF sen entrada no menú; axe; 320 px sen scroll; orzamento inicial igual; Ferrol baleiro sen erros | M | D3-04, D3-06, D3-07 | Frontend features |
| **D3-09** | Modal da evolución (`furar-dialog` `lg`) con transición desde a tarxeta; Exportar do panel, gráfico e modal → CSV e PDF | `features/indicators/` | Esc e clic fóra pechan e o foco volve á tarxeta; sen `startViewTransition` abre igual; con `reduce` sen animación; `window.print` co alcance correcto; nome e contido de cada CSV; cabeceira impresa en gl e es; axe no modal | M | D3-08 | Frontend features |

### 3.4 Final (carril E)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D3-10** | `web/e2e/indicadores.e2e.ts`: xestor-naron → Indicadores → períodos, filtros, «Táboa», cifra só con teclado, CSV (panel, gráfico, modal), PDF (`window.print` substituído); Ferrol baleiro; tema escuro con axe; vecino → 403; sen `securitypolicyviolation`; VoiceOver e teclado en `docs/accesibilidade.md`; D34 coa cifra nova | `web/e2e/`, `docs/accesibilidade.md`, `definicion-de-feito.md` (D34) | `gl`, `es` e `gl-mobile` en verde con axe; ningunha petición fóra de localhost; `verificar.sh --estrito` en verde | M | todas | QA accesibilidade |

### 3.5 Orde e paralelismo
- **Desde o primeiro día:** D3-01; D3-03 (sen Docker) e D3-02 (Docker); D3-06 e D3-07 escríbense en paralelo pero `ng test` e `ng build` van por quendas.
- **Camiño crítico:** D3-02 → D3-04 → D3-08 → D3-09 → D3-10. D3-05 despois de D3-04.
- **Ficheiros quentes:** `app.routes.ts`, `site-bar.component.ts`, `gl.json`/`es.json`, `catalog.page.*`, `angular.json`, `package*.json`, `openapi.json`, `migrations.lock`, `FacilityQueries.java`, `ModularityTest`, `docs/deseno.md`. Claves i18n só no seu prefixo (`indicators.*`, `ui.<compoñente>.*`).
- **Poden escorregar sen romper a demo:** a animación da transición, as imaxes de impresión e o rango «Datas…». **Non escorregan:** D3-02, D3-04, D3-05 e D3-08.

---

## 4. Perfís e regras comúns
Os do dominio 2 §4 cambiando «D2» por «D3». Ademais:
- **Arquitecto (D3-01):** le ADR-0005, 0006, 0016, 0018, 0021 e 0025, este plan e a maqueta 31. Non escribe produto novo.
- **Backend PostgreSQL (D3-02):** le A8b, `BookingSearchSql` (patrón `(:all or …)`) e `BookingSqlTenantIT`. Nada de `case`, `or` nin `union` no nivel 0; SQL enteiro nun literal.
- **Backend (D3-03 a D3-05):** `indicators` nunca le táboas, só `BookingStatistics` e `FacilityQueries`. Tempo en `Instant`, unidades en Europe/Madrid, ningún `LocalDateTime` nos DTO.
- **Frontend compoñentes (D3-06, D3-07):** sen estilos de compoñente (CSP), todo en `styles/`, tamaños por CSSOM. ECharts só dentro de `shared/ui/chart/` (regra ESLint `no-restricted-imports`).
- **Frontend features (D3-08, D3-09):** maqueta `31-indicadores.html`, opción A. Frases de conclusión puras con claves i18n. Datas e cifras con `Intl` e `timeZone: 'Europe/Madrid'`.
- **QA (D3-10):** como no dominio 2; descargas co evento `download` de Playwright.

---

## 5. Riscos

| Risco | Mitigación |
|---|---|
| ECharts mete estilos en liña e a CSP bloquéaos | Canvas e `richText`; e2e con `securitypolicyviolation` |
| ECharts entra na carga inicial | Só `import()` dinámico; D27 e `stats-json` |
| O service worker precarga todos os `*.js`: a veciñanza descarga tamén o chunk de ECharts (medido: ~191,5 kB gz; ~211 kB coa páxina) | Aceptado na fase 1; se pesa, os `chunk-*.js` pasan a un grupo `lazy` |
| Definicións mal entendidas | Todo no §1.2.4, un test por regra, P2–P7 abertas |
| O denominador non coñece os peches temporais pasados nin as instalacións xa de baixa | Documentado; recomendar meter os peches como bloqueos |
| Cancelación fóra de prazo sempre 0 | P5 |
| Verde e vermello con igual luminancia | Separadores, trama, lenda e táboa |
| Lentitude cun ano ou cinco | Índice novo, agregados en SQL, sentenzas contadas e medida no PR |
| Anonimizar reservas (ADR-0017) muda as persoas distintas do pasado | Anotado na ADR-0026 |

**Orde de merge:** D3-01 → D3-03, D3-02 → D3-06, D3-07 → D3-04 → D3-05 → D3-08 → D3-09 → D3-10.

---

## 6. Preguntas de produto
Validadas polo mantedor o 2026-10-29: vale a recomendación de cada unha; a 8, co detalle decidido.

1. **¿Ve o conserxe «Indicadores»?** *Recomendación: non, só o xestor.*
2. **«Reservas»: ¿contan as canceladas?** *Recomendación: non; as canceladas teñen o seu gráfico.*
3. **Ocupación con partes ou prazas.** *Recomendación: a súa proporción (unha parte de dúas, media hora por hora; unha praza de 30, 1/30).*
4. **¿Que data manda?** *Recomendación: o día da reserva en todo, tamén no cobrado e nas cancelacións.*
5. **Fóra de prazo** (hoxe sempre 0). *Recomendación: mantelo na lenda e na táboa; a frase non o nomea mentres sexa 0.*
6. **Filtro «Uso» sen instalación.** *Recomendación: agrupar os usos co mesmo nome; cunha instalación escollida, só os seus.*
7. **«Cobrado».** *Recomendación: só o pagado.*
8. **Período en curso.** *Recomendación: contalo enteiro, coas reservas futuras xa feitas.* **Decidido o 2026-10-29:** o valor, enteiro; a variación, ata hoxe fronte ao mesmo tramo do anterior; o punto en curso, discontinuo e con «en curso» en texto (ADR-0026, revisión).
9. **Rango máximo de «Datas…».** *Recomendación: un ano (366 días).*
10. **Persoas por mes.** *Recomendación: os seis meses que rematan no do período (doce no ano).*

---

## 7. Filas da DoD que se tocan
A DoD está conxelada: ningunha fila nova.

| Fila | Que cambia | Tarefa |
|---|---|---|
| D34 | O mínimo de e2e sobe co ficheiro novo | D3-10 |
| D11, D12, D14, D15, D16, D17 | Seguen en verde con `BookingStatsSqlTenantIT`, `ModularityTest`, `RolePermissionsTest`, `EndpointSecurityIT`, os erros novos e o contrato | D3-02, D3-03, D3-04 |
| D03, D04, D05, D49, D37 | `echarts`, `zrender` e `tslib` en licenzas, SBOM e Trivy; NOTICE de ECharts | D3-06 |
| D27, D31 | Orzamento inicial igual e CSP sen `'unsafe-inline'` | D3-06, D3-08 |
| D44 | ADR-0026 | D3-01 |
