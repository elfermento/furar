<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Guía de deseño da web

Sistema de deseño de Furar: tokens, tema por concello, compoñentes xenéricos e como se re-estilan as pantallas que xa hai. Decisión en [ADR-0021](adr/0021-sistema-de-deseno.md); base técnica en [ADR-0014](adr/0014-web.md); criterios de accesibilidade en [`accesibilidade.md`](accesibilidade.md).

**Procedencia.** O sistema escríbese de cero en Angular, CSS e Tailwind, sen copiar código nin ficheiros doutras librarías de compoñentes. As súas regras (tokens por función, escala de aire, anatomía dos compoñentes) xustifícanse pola accesibilidade (WCAG 2.1 AA), pola coherencia entre pantallas e polas gardas do repositorio.

**Fóra do alcance (é produto):** campos, fluxos e pantallas que non estean acordados; logo; modo escuro elixido polo concello; campos novos no tema. Os compoñentes de prioridade 2 e 3 só se fan cando unha pantalla acordada os pida. **Xa acordadas (2026-10-25):** listado e ficha das instalacións, horario e bloqueos ([especificación](produto/especificacion.md), bloques 1, 1b e 2, sen complexo); por iso as instalacións constrúen os xenéricos do §8 («Os que piden as instalacións»). O mesmo co panel de indicadores (2026-10-28) e os xenéricos dos indicadores.

## 1. Principios

1. **Compoñentes sen dominio.** En `shared/ui/` nada sabe de concellos, instalacións nin reservas; as features compóñenos.
2. **Cor, medidas, sombras, radios, curvas e duracións só de tokens.** Nada de hex, `px` soltos nin `gray-*` nas plantillas. As clases de Tailwind que levan cor só poden ser as do sistema (`bg-surface`, `text-ink`…).
3. **Un tema = só variables.** O concello pon cor; as medidas son as mesmas en todos.
4. **AA garantido, non probado á sorte.** As cores derivadas calcúlanse con contraste comprobado para *calquera* tema que admita a API, non só para Narón e Ferrol (§4).
5. **Máis aire, pouca tinta.** Columna estreita, ocos xenerosos, unha acción principal por pantalla. O aire súbese nos tokens, non compoñente a compoñente.
6. **Nada de recheos grises.** O que se distingue vai por bordo ou por un tinte da cor do concello. O estado nunca vai só pola cor (1.4.1): leva texto, icona ou bordo.
7. **Primeiro o nativo.** `<button>`, `<a>`, `<input>`, `<select>`, `fieldset`/`legend`. Un compoñente propio só cando o nativo non chega, e entón con CDK (`A11yModule`, `Dialog`, `Overlay`, `Listbox`, `ListKeyManager`). **Excepción, o despregable (2026-10-28):** non se usa o `<select>` nativo senón `furar-select` (§8). A lista do nativo píntaa o navegador e non se pode estilar: cada navegador e cada móbil a amosan distinta e non casa cos outros campos que abren capa (hora, data). `furar-select` dá o mesmo aspecto e o mesmo comportamento en toda a web e en todos os navegadores, co mesmo teclado ca o nativo, e segue sendo un botón e un `listbox` do CDK.
8. **Pezas pequenas.** Páxina fina que compón; ningún compoñente mestura vista e edición nin pasa dos límites de lint (250 liñas `.ts`, 200 `.html`).
9. **Sen texto no código.** Todo rótulo, tamén os xenéricos dos compoñentes («Pechar», «Cargando»), é unha clave de `gl.json`/`es.json` (`ui.*`) ou un `input()`.

## 2. Onde vive cada cousa

| Peza | Sitio | Notas |
|---|---|---|
| Entrada do tema | `--furar-*` en `:root` (`styles.css`) e en `<html>` (ThemeService) | os 5 nomes non cambian: os usan `theme.spec.ts` e `publico.e2e.ts` |
| Derivación das cores | `core/theme/theme-tokens.ts` (`deriveTokens(theme)`) | función pura, con spec de temas límite (§4.3) |
| Tokens | `src/styles/tokens.css` | `--f-*`; valores por defecto = `deriveTokens(tema por defecto)` |
| Utilidades de Tailwind | `@theme inline` en `styles.css` | expón os `--f-*` como `bg-*`, `text-*`, `gap-*`, `rounded-*`… |
| Base e preferencias | `src/styles/base.css` | foco, `prefers-*`, `forced-colors` |
| Estilos dos compoñentes | `src/styles/components/<nome>.css`, en `@layer components` | CSS global: a CSP (`style-src 'self'`) e `NO_COMPONENT_STYLES` impiden `styles` en `@Component` ; os que só usa a xestión impórtanse en `src/styles/xestion.css` (folla á parte que a ruta de xestión carga e agarda, `managementStylesResolver`); os das reservas, o despregable, o mapa e a ficha pública, en `src/styles/reservas.css` (`bookingStylesResolver`); os das portadas (`home.css`), en `src/styles/portada.css` (`homeStylesResolver`); o resto en `styles.css` |
| Compoñentes | `shared/ui/<nome>/` | `furar-*` (elementos) ou `[furar*]` (directivas sobre o nativo), OnPush, signals, con spec |
| Iconas | `shared/ui/icon/` | unha soa fonte (§8, P1-10) |

Os estilos van en `@layer components`, así calquera utilidade de Tailwind de quen chama manda sobre eles sen subir a especificidade (o mesmo fin ca os selectores de peso cero). Cada compoñente pinta con clases `f-<nome>` e variantes por atributo (`data-variant="primary"`), nunca con estilos en liña.

## 3. Tokens

Prefixo **`--f-`** para os tokens do sistema; `--furar-*` queda só como entrada do tema. En Tailwind exponse sen prefixo (`--color-ink` → `text-ink`). **Non se redefinen as utilidades de Tailwind** (`text-sm`, `text-lg`, `rounded-lg`…): os tokens novos levan nomes novos.

### 3.1 Cor (derivada do tema, §4)

| Token | Tailwind | Función | Mínimo garantido |
|---|---|---|---|
| `--f-page` | `bg-page` | fondo da páxina: o fondo do concello cun lavado do `primary` (4 % nominal) | ink sobre el ≥ 4,5 |
| `--f-surface` | `bg-surface` | card, campo, capa: o fondo do concello tal cal | — |
| `--f-tint` | `bg-tint` | seleccionado, hover, cabeceira de grupo: tinte do `primary` (10 % nominal) | ink, accent e estados sobre el ≥ 4,5 |
| `--f-ink` | `text-ink` | texto | ≥ 4,5 (valida a API) |
| `--f-ink-soft` | `text-ink-soft` (alias `text-muted`) | axuda, meta, secundario | ≥ 4,5 sobre page, surface e tint |
| `--f-divider` | `border-divider` | separadores e bordo de card (decorativos) | sen mínimo (non é compoñente) |
| `--f-line` | `border-line` | bordo de control: campo, casilla, botón secundario | ≥ 3 sobre page, surface e tint (1.4.11) |
| `--f-primary` / `--f-on-primary` | `bg-primary` / `text-on-primary` | recheo da acción principal e da cabeceira; a súa tinta | on-primary/primary ≥ 4,5 (valida a API) |
| `--f-primary-text` | `text-primary` | ligazóns e texto da marca | ≥ 4,5 sobre page, surface e tint |
| `--f-accent` | `text-accent` | segunda cor do concello, como texto ou icona | ≥ 4,5 sobre page e surface |
| `--f-focus` | `outline-focus` | aro de foco fóra das superficies `primary` | ≥ 3 sobre page e surface |
| `--f-danger`, `--f-success`, `--f-warn`, `--f-info` | `text-danger`… | estados (texto, icona, bordo) | ≥ 4,5 sobre page, surface e tint |
| `--f-muted` | — | estado apagado (Devolta, de baixa) como texto | ≥ 4,5 sobre page, surface e tint |
| `--f-danger-fill`… `--f-muted-fill` / `--f-on-danger`… `--f-on-muted` | — | recheo dos estados (chips `solid`, chip de filtro marcado) e a súa letra: o ton da paleta tamén en escuro, con letra branca | on/fill ≥ 4,5 |
| `--f-chip-mix` | — | porcentaxe do ton no fondo pálido do chip de filtro sen marcar (9 % nominal; baixa ata que a letra no ton dea 4,5) | ton/fondo pálido ≥ 4,5 |
| `--f-scrim` | `bg-scrim` | velo baixo unha capa: negro ao 40 % (fixo: escurece igual con fondo claro ou escuro) | — |
| `--f-glass` | — | superficie translúcida (§6) | ou cumpre ou vale `--f-surface` |
| `--f-shadow-color` | — | negro ao 12 % (fixo, non sae da tinta) | — |

Compatibilidade: `--color-line` segue sendo o bordo dos campos e pasa a valer `--f-line` (comprobado); `--color-muted` pasa a ser alias de `--f-ink-soft`. Así os ~100 usos de hoxe (`border-line`, `text-muted`) quedan ben sen tocalos.

Cores dos estados: **paleta única para toda a app** (2026-10-26, [ADR-0021](adr/0021-sistema-de-deseno.md) revisada), tons de partida sen tema, todos con letra branca a ≥ 4,5:1; se un fondo do concello non lles dá 4,5:1 como texto, a derivación achégaos á tinta, e en escuro os recheos escurécense ata manter a letra branca a 4,5:1:

| Estado | Ton | Branco sobre el | Exemplos (reservas) |
|---|---|---|---|
| `success` | `#2a7d3f` | 5,1:1 | Confirmada, Pagada |
| `warn` | `#a05d0e` | 5,2:1 | Pendente |
| `danger` | `#c9372c` | 5,2:1 | A devolver, Cancelada |
| `info` | `#2c64cc` | 5,5:1 | Gratis |
| `muted` | `#5f646b` | 6,0:1 | Devolta, de baixa |

Un ton por estado dentro de cada grupo (os estados dunha mesma lista nunca comparten ton).

**Escala secuencial para cantidades (2026-10-28, [ADR-0026](adr/0026-indicadores-fase-1.md)):** nos gráficos, unha cantidade pinta con `--f-primary`; se fan falta varios pasos (mapa de calor, meses pasados), `sequentialScale(tokens, n)` en `core/theme/` deriva a escala do `primary`: monótona, distinguible nos dous temas e co último paso ≥ 3:1 sobre `surface`. Os estados nunca usan a escala: van coa paleta de arriba.

**Período en curso nos gráficos (2026-10-29, [ADR-0026](adr/0026-indicadores-fase-1.md), revisión):** nunha liña (`lineArea`, `sparkline`), o tramo desde o punto anterior ao en curso vai **discontinuo** coa mesma cor, ancho e área da serie: patrón de trazo, nunca unha cor nova, así que se distingue tamén con cores forzadas e no papel. O punto en curso di «en curso» no tooltip, na etiqueta (se é o último), na táboa e no CSV. A variación dunha tarxeta no período en curso compara ata hoxe e dío nunha liña: «ata hoxe, fronte ao mesmo tramo de setembro» (verde se sobe, vermello se baixa, como sempre).

**Non hai terceiro nivel de texto** (un `text-faint` daría 4,4:1 e falla) nin texto desactivado máis claro ca `ink-soft`: un control desactivado vese con bordo descontinuo e `cursor: not-allowed`, non con opacidade 0,4. **Os botóns non** (2026-11-17): nunca bordo descontinuo; e un botón non se desactiva porque falte un dato (§8, «Botóns sempre activos»). **Excepción, os días do calendario que non se poden escoller** (2026-10-26): vense apagados, en gris claro (`ink-soft` mesturado ao 50 % coa superficie), sen riscar nin bordo descontinuo. Cumpre AA porque 1.4.3 exime o texto dos compoñentes inactivos; o estado vai en `aria-disabled` (anúnciase e non se escolle) e, con cores forzadas, en `GrayText`. Antes ían riscados por 1.4.1, pero a grella xa distingue o día dispoñible polo rótulo e polo estado, e o riscado cargaba a vista nos meses con moitos días pasados.

### 3.2 Espazos e aire

Rede de 4 px (a de Tailwind, `--spacing: 0.25rem`). Para separar pezas escóllese **a relación**, non o número:

| Token | Tailwind | Valor | Relación |
|---|---|---|---|
| `--spacing-unit` | `gap-unit` | 0,5rem (8) | título e a súa meta |
| `--spacing-item` | `gap-item` | 1rem (16) | irmáns: campos dunha fila, filas, botóns; tamén do título ao seu contido |
| `--spacing-form` | `gap-form` | 1,25rem (20) | entre campos dun formulario |
| `--spacing-card` | `p-card` | 1,5rem (24) | recheo dentro da card (1rem a menos de 360 px) |
| `--spacing-group` | `gap-group` | 1,75rem (28) | entre grupos (cards) |
| `--spacing-section` | `gap-section` | 2rem (32) | entre seccións da páxina |
| `--spacing-gutter` | `px-gutter` | 1rem (16) | marxe lateral no móbil |
| `--container-col` | `max-w-col` | 35rem (560) | columna estreita centrada: formularios longos dunha columna e tarxetas soltas |
| `--container-prose` | `max-w-prose` | 70ch | columna centrada de texto para ler (páxinas legais) |
| `--container-bar` | `max-w-bar` | 76rem (1216) | **o contedor de toda a app**: barra superior, contido (`.f-main`) e pé, cos mesmos bordos (§7) |

### 3.3 Tipografía

Inter 4.1 autoaloxada (paquete npm `inter-ui`, subconxunto latin, que conserva as funcións OpenType; o de `@fontsource` quítalle o cero con barra), **só 400, 600 e 700** (os pesos que se cargan, `styles/fonts.css` e `fields.css`; o 600, só para os rótulos dos campos, P1-3; nada de 500 nin pesos sintéticos). Todo en `rem`, para que o zoom de texto ao 200 % funcione (1.4.4). Desde o 2026-11-17 ([ADR-0021](adr/0021-sistema-de-deseno.md) revisada); antes, Atkinson Hyperlegible Next.

- **Cero con barra en toda a app** (`font-feature-settings: 'zero' 1` no `body`): horas, prezos e códigos non confunden 0 e O. Vai en `font-feature-settings` e non en `font-variant-numeric: slashed-zero`, porque o `tabular-nums` das cifras pisaría este último.
- **Cifras tabulares** (`tabular-nums`, función `tnum`) só onde se aliñan cifras: táboas (`furar-data-table`), calendarios, horas das listas e contadores. No texto corrido, as proporcionais. Os identificadores técnicos do rexistro de actividade e os hex do tema seguen en monoespaciada do sistema.
- **Ollo ao 0,5 do tamaño** (`font-size-adjust: 0.5` no `body`, tamén no tema de Keycloak): Inter ten a altura da x un 10 % maior ca a letra anterior; así os tokens de tamaño, as alturas de liña e os anchos non cambian. A monoespaciada vai sen axuste. Os gráficos (canvas) non o aplican.

| Token | Tailwind | Tamaño | Uso |
|---|---|---|---|
| `--text-display` | `text-display` | 1,75rem (28) | `furar-page-header` con `size="display"` |
| `--text-title` | `text-title` | 1,5rem (24) | `h1` de pantalla |
| `--text-head` | `text-head` | 1,25rem (20) | `h2`, título de card |
| `--text-body` | `text-body` | 1rem (16) | texto, campos (≥ 16 para que iOS non faga zoom) |
| `--text-support` | `text-support` | 0,875rem (14) | axuda, meta, pé; **mínimo absoluto** |

Interliñado 1,5 no corpo e 1,25 nos títulos (1.4.12). Prosa (páxinas legais) a 70ch como máximo. As etiquetas non baixan de 14 px. Peso 700 para títulos, rótulos de campo e botóns; 400 para o resto.

### 3.4 Radios, bordos e medidas

| Token | Valor | Uso |
|---|---|---|
| `--radius-field` | 0,75rem (12) | campo, select, área de texto |
| `--radius-row` | 0,875rem (14) | fila solta, aviso, peza interior |
| `--radius-card` | 1,25rem (20) | card, lista de filas |
| `--radius-sheet` | 1,75rem (28) | capa, barra fixa |
| `--radius-pill` | 999px | botón, etiqueta |
| `--f-border` / `--f-border-state` | 1px / 2px | bordo en repouso (tamén dos controles) / foco, erro ou seleccionado |
| `--f-touch` | 2,75rem (44) | obxectivo mínimo (2.5.5 recomendado; Furar obrígao) |
| `--f-control-h` | 3rem (48) | alto de campo e de botón normal |
| `--f-sheet-end` | `--spacing-section` + área segura | aire debaixo de toda folla do móbil (campo con panel e diálogo con `sheet`) |
| botóns | 2,75 / 3 / 3,5rem (44 / 48 / 56) | sm / md / lg; recheo lateral 1,5 / 1,75 / 2rem |
| `--f-row-h` | 3,25rem (52) | fila de lista |

Os controles levan sempre bordo `--f-line`, tamén en repouso: un campo sen bordo sobre fondo lavado falla 1.4.11. En repouso é de 1px (para 1.4.11 conta o contraste 3:1 da cor, non o grosor) e pasa a 2px no foco e co erro; o padding resérvalle ese px para que o contido non salte. Con `prefers-contrast: more` todos os bordos son de 2px.

### 3.5 Sombras, movemento e capas

- **Sombras:** `--shadow-raised` (card: 0 1px 2px), `--shadow-overlay` (capa e menú: dúas capas, ata 0 8px 24px), `--shadow-float` (aviso flotante). Cor desde `--f-shadow-color`. Nunca levan significado.
- **Curvas:** `--ease-smooth` cubic-bezier(.22, 1, .36, 1) para case todo; `--ease-balanced` (.45, 0, .55, 1) para ir e volver; `--ease-overshoot` (.35, 1.55, .65, 1) só en pezas pequenas. Nunca `ease`/`linear` do navegador nin `transition-all`.
- **Duracións:** `--f-dur-fast` 160ms (hover, prema), `--f-dur-med` 280ms (aparecer), `--f-dur-slow` 420ms (capa). Prema: `scale(.98)`.
- **`prefers-reduced-motion: reduce`:** sen desprazamentos nin escalas; só fundidos de ≤ 160ms (a regra global de hoxe segue). Hover só dentro de `@media (hover: hover)`.
- **Lista → detalle (`shared/ui/motion/motion.ts`):** o detalle ao lado (reservas do concello, vista previa do tema) usa a mesma curva e 320 ms: `slideFrom` (a sección entra desde onde estaba, un 35 % do camiño), `slideOut`/`fadeIn` e, para un panel que sae dun botón, `growFrom`/`shrinkInto` (FLIP desde e cara ao rectángulo do botón). Sen movemento se a persoa o pide.
- **Capas:** `--f-z-sticky` 50, `--f-z-modal` 100, `--f-z-popover` 150, `--f-z-toast` 200.

## 4. Tema por concello

### 4.1 Que pode cambiar un concello

Exactamente os 5 campos que xa hai (`ThemeRequest`): `primary`, `onPrimary`, `accent`, `background`, `text`. A API e o editor (`contrast.ts`) esixen hoxe:

| Par | Mínimo |
|---|---|
| `text` / `background` | 4,5 |
| `onPrimary` / `primary` | 4,5 |
| `accent` / `background` | 4,5 |
| `primary` / `background` | 3 |

Iso non cambia: **o sistema de deseño non engade campos ao tema nin endurece a validación**. Tipografía, medidas, radios e sombras non son do concello.

### 4.2 Por que non chega con `color-mix` en CSS

Un tema no límite que a API admite (texto `#767676` sobre branco, 4,54:1) perde o AA en canto se mestura calquera tinte no fondo: o texto sobre `color-mix(primary 4 %, branco)` dá 4,39:1. E hoxe, con ese tema, `--color-line` (texto ao 55 %) dá 2,07:1 nos bordos dos campos e falla 1.4.11. CSS non sabe se o fondo do concello é claro ou escuro, así que non pode corrixilo.

### 4.3 Derivación con contraste comprobado

`deriveTokens(theme)` (TypeScript, puro, mesmo cálculo de `contrast.ts`) calcula as cores do §3.1 e ThemeService escríbeas en `<html>` por CSSOM xunto ás 5 de sempre (compatible coa CSP); `reset()` bórraas todas. Regras:

1. **Tintes (`page`, `tint`, `glass`):** parten do valor nominal (4 %, 10 %, 90 % de opacidade) e baixan 1 punto ata que `ink` e `accent` dean 4,5 e `primary` 3 sobre eles. No peor caso valen o fondo do concello (0 %), que xa cumpre.
2. **Textos e bordos derivados (`ink-soft`, `line`, `primary-text`, `focus`, estados):** parten do nominal e **achéganse á tinta do concello** (`text`) ata cumprir o seu mínimo sobre `page`, `surface` e `tint`. Como `text` xa dá 4,5 sobre o fondo, sempre hai solución; funciona igual con fondo claro ou escuro.
3. **`color-scheme`** segue á luminosidade do fondo (`dark` se é escuro), para que os controles nativos casen.
4. Con fondo claro, `--f-on-primary` e `--f-primary` son os do concello sen tocar (a API xa os valida).
5. **Letra sobre o recheo dos estados (`--f-on-danger`, `-success`, `-warn`, `-info`):** branca ou negra, a que dea máis contraste (sempre ≥ 4,5, porque o ton xa dá 4,5 sobre a superficie); coa paleta do 2026-10-26 é sempre branca. É a dos chips `solid` (P1-9).
6. **Mestura dos avisos (`--f-soft-mix`):** a porcentaxe do ton no fondo suave (8 % nominal); baixa ata que a tinta siga a 4,5:1 e a icona a 3:1 en todos os tons. No tema límite queda en 0 %.

#### Fondo escuro (2026-10-24: «os azuis confúndense co negro»)

Cando o fondo do concello é escuro (luminancia < 0,18), a derivación cambia de estratexia:

- **Escalóns de superficie case negros (2026-11-03):** `page` é o fondo do concello e as cards (`surface`) soben só un 4 % cara ao branco (menos se a tinta deixase de dar 4,5:1). En escuro non se ven as sombras; separan a card da páxina ese escalón pequeno e as divisorias e liñas. O `tint` mestura un 6 % do `primary` sobre a card. Antes (8 % e 14 %) as superficies quedaban grisáceas.
- **Texto suave:** `ink-soft` parte do 60 % da tinta sobre a card (no claro, 72 %), para que se distinga da tinta case branca; sobe cara á tinta se non dese 4,5:1.
- **Tons con luz, non grises:** `primary-text`, `accent`, `focus` e os estados non se achegan á tinta (iso apagábaos nun gris azulado); **soben de luz en HSL mantendo o matiz** ata 6:1 sobre `page`, `surface` e `tint` (máis folgado que o 4,5 mínimo: un azul escuro sobre negro cumpre e cústalle ler).
- **Botón principal que destaca:** o recheo `primary` sobe de luz ata 4,5:1 sobre o fondo (non 3:1). Se cambia, `on-primary` pasa a branca ou negra segundo o contraste.

Proba: ademais da grella, `theme-tokens.spec.ts` comproba en todos os temas escuros que o `primary` dá ≥ 4,5 sobre os fondos, os tons ≥ 6 e a card sobe sobre a páxina.

Proba (`theme-tokens.spec.ts`): unha grella de temas límite (texto a 4,5:1 exactos, `primary` a 3:1, fondo escuro, accent no mínimo) e os de exemplo; para cada un, todos os pares da táboa §3.1 cumpren. Outro test comproba que os valores por defecto de `tokens.css` son os de `deriveTokens(tema por defecto)`.

### 4.4 Contraste verificado

Calculado coa fórmula de WCAG 2.1 (a de `contrast.ts`) sobre os valores derivados. «Límite» = texto e accent `#767676`, primary `#949494`, onPrimary `#000`, fondo `#fff` (o tema máis xusto que admite a API).

| Par | Defecto | Narón | Ferrol | Límite | Fondo escuro¹ |
|---|---|---|---|---|---|
| ink / page | 16,32 | 16,32 | 14,80 | 4,50 | 17,39 |
| ink / tint | 14,96 | 15,75 | 13,32 | 4,50 | 14,72 |
| ink-soft / page | 6,47 | 6,47 | 5,90 | 4,50 | 7,01 |
| ink-soft / tint | 5,93 | 6,24 | 5,31 | 4,50 | 5,94 |
| line / page (bordo de control) | 3,65 | 3,65 | 3,45 | 3,01 | 6,11 |
| primary-text / page | 6,71 | 7,88 | 9,13 | 4,50 | 7,15 |
| focus / page | 6,71 | 7,88 | 9,13 | 3,01 | 6,11 |
| on-primary / primary | 7,15 | 8,40 | 10,04 | 6,92 | 6,39 |
| accent / page | 6,38 | 4,68 | 5,85 | 4,50 | 10,31 |
| danger / page | 7,27 | 7,27 | 7,05 | 7,69 | 7,13 |
| success / tint | 5,65 | 5,94 | 5,37 | 6,51 | 6,14 |
| warn / tint | 5,89 | 6,20 | 5,60 | 6,79 | 6,20 |
| info / tint | 5,80 | 6,11 | 5,52 | 6,69 | 6,09 |

¹ Tema escuro de referencia (catálogo, tests e tema de Keycloak): fondo `#0d1117` case negro, texto `#f0f6fc` case branco, primary `#4493f8`, onPrimary `#0a0a0a`, accent `#f0b46a`. Columna calculada coa derivación do §4.3 «Fondo escuro» do 2026-11-03, comprobada nos tests.

Lecturas: en Narón o `tint` baixa ao 6 % porque o seu accent (`#b3541e`, 5,0:1 sobre branco) non aguanta o 10 %. No límite os tintes quedan nun 1 % e `ink-soft` iguala `ink`: perde xerarquía de cor (queda a de tamaño e peso), pero non o AA.

### 4.5 Recomendación para o editor do tema

O editor xa mostra os 4 pares. Sen cambiar a validación, pode avisar en texto (non bloquear) cando a derivación tivo que apagar os tintes («o fondo e o texto teñen pouco contraste: a web verase sen tintes»). Isto é un texto novo nunha pantalla existente: **non se fai na fase 1** (decidido o 2026-11-04, [ADR-0021](adr/0021-sistema-de-deseno.md)); a derivación xa garante o AA.

## 5. Accesibilidade de base (WCAG 2.1 AA)

| Criterio | Como o cumpre o sistema |
|---|---|
| Foco visible (2.4.7, 1.4.11) | `outline: 3px solid var(--f-focus); outline-offset: 2px` con `:focus-visible`, sen depender de JS; sobre `primary` (cabeceira, botón principal enfocado desde dentro) usa `--f-on-primary`. **Con `outline`, non con `box-shadow`**, porque sobrevive a `forced-colors`. Un aro de acento ao 35 % (1,7:1) non abonda. |
| Contraste (1.4.3, 1.4.11) | §4. Todo control ten bordo ≥ 3:1. Placeholder: non se usa para dar información; se aparece, `ink-soft` (≥ 4,5). |
| Teclado (2.1.1, 2.4.3) | nativo primeiro; grupos compostos (radiogroup, tira de días, calendario) cunha parada de tabulador e frechas (`ListKeyManager`); capas con trampa e retorno do foco (CDK). |
| Obxectivo (2.5.5) | 44 px de toque mínimo, tamén se a peza vista é menor (área ampliada sen medrar). |
| Reflow (1.4.10) | todo a 320 px sen scroll horizontal da páxina; ningún carril que se desliza de lado para opcións (Segmented con máis de 5 → baixa de liña); táboas que pasan a filas. **Excepción, as pestanas** (revisión do 2026-11-03): van sempre nunha fila e, se non caben, desprázanse **dentro da súa franxa** (a páxina non), cun fundido no bordo que ten máis pestanas, a activa centrada á vista e as frechas do teclado; nunca baixan de liña. |
| Espazado de texto (1.4.12) | alturas mínimas, nunca fixas; nada se corta con `line-height` 1,5 e `letter-spacing` 0,12em. |
| Movemento (2.3.3) | §3.5. |
| Estado sen cor (1.4.1) | seleccionado = bordo 2px + ✓; erro = bordo + icona + texto; ligazóns subliñadas. |
| Mensaxes (4.1.3) | `role="status"` para avisos, `role="alert"` só para erros que o piden; mensaxes de campo ligadas por `aria-describedby`. |
| `prefers-contrast: more` ou «Alto contraste» | `ink-soft` → `ink`, `line` → `ink`, tintes → `surface` con bordo, bordos de 2px tamén nas cards, sen cristal; textos, foco, botón principal e chips coas cores `--f-hc-*` (7:1 co mesmo matiz, `core/theme/high-contrast.ts`) e foco de 4px. |
| Preferencias da persoa | botón «Accesibilidade» na barra: tamaño do texto (100 %, 112,5 % ou 125 % do que xa ten o navegador), «Alto contraste» e «Menos movemento». Sen elección propia seguen o sistema; a elección da persoa gaña. Aplícanse en `<html>` (`data-text-size`, `data-contrast`, `data-motion`) antes do primeiro pintado (script de `index.html`) e os estilos len eses atributos, non as media queries (`core/preferences/`). Gárdanse no navegador. |
| `forced-colors: active` | sen fondos con significado; todo botón ten bordo (transparente en normal) para que se vexa; iconas con `currentColor`; seleccionado por bordo e ✓. |

## 6. Cristal (translucidez)

Parte da identidade, pero **con moderación**: nunca cristal por todas partes nin sen alternativa.

- **Só no marco e en pezas fixas sobre o contido:** a barra superior flotante, o pé (tarxeta co mesmo ancho, radio, bordo e sombra ca ela; 2026-11-03), a barra inferior do móbil (`core/layout`, clase `.f-glass` en `styles/components/site.css`) e a barra de accións fixa (`furar-action-bar`). Nunca en cards, campos (tampouco no buscador da portada) nin capas.
- **Valor:** `--f-glass` = `surface` ao 90 % + `backdrop-filter: blur(16px) saturate(140%)`, bordo `--f-divider` e `--shadow-overlay` (as dúas barras flotantes, arriba e abaixo, levan a mesma).
- **AA no peor caso:** a derivación comproba o texto sobre `glass` mesturado co contido máis escuro posible (a tinta). Se non dá 4,5, `--f-glass` vale `surface` sólido (no tema límite pasa).
- **Fondo sólido, sen `backdrop-filter`,** baixo `prefers-reduced-transparency: reduce`, `prefers-contrast: more`, `forced-colors: active` e `@supports not (backdrop-filter: blur(1px))`.
- Unha barra fixa non pode tapar o foco: o contido leva `scroll-padding-bottom` co alto da barra inferior e `scroll-padding-top` co da superior (medido en `--f-topbar-h`). A superior escóndese ao baixar pasados 80 px e volve ao subir ou en canto algo dentro recibe o foco (`ScrollHide`); con movemento reducido, sen transición.

## 7. Composición das pantallas

- **Marco:** saltar ao contido → **unha soa** barra superior de cristal (marca do concello unha vez: nome e «Deportes municipais», que leva á portada pública; menú; idioma; «Entrar» ou o menú da persoa) → `main#contido` → pé (franxa a todo o ancho co fondo da páxina e unha liña fina enriba: columnas «Legal», «Servizo» e, co correo de soporte, «Contacto», e o nome do servizo como texto; abaixo de todo aínda que a páxina sexa curta; 2026-11-16) → barra inferior de cristal no móbil (< 900 px, os mesmos destinos). Sen sesión do persoal, o menú é o público (co inicio na barra inferior); co persoal, o de xestión (Instalacións · Concello · Campos personalizados) — nunca dúas filas (2026-10-26). O menú da persoa (`furar-user-menu`) leva «A miña conta» (a pantalla da app: quen é, os seus concellos co rol, o último acceso, «Seguridade da conta» e «Eliminar a miña conta»), «Idioma» e «Pechar sesión» (a web pública é o escudo); o persoal de dous ou máis concellos ten enriba «Concello ›», que abre no mesmo panel a lista dos seus concellos e, mentres non escolleu ningún, un botón «Concello ▾» na barra (`furar-pick-menu`: menú no escritorio, folla desde abaixo no móbil); cárgase con `@defer` para que o CDK Menu non pese na carga inicial. Fondo `page`; contido no contedor da barra con `px-gutter`, salvo as rutas con `data: { layout: 'bleed' }` (portada), que van a todo o ancho e cada sección pon o contedor (`.f-wrap`, cos mesmos bordos). O menú só leva o que existe (hoxe «Instalacións», á sección da portada): nada de «Reservar» nin «As miñas reservas» ata que existan.
- **Ancho (2026-11-03):** un só contedor (`--container-bar`) para a barra superior, o contido e o pé, cos mesmos bordos esquerdo e dereito; non hai outro ancho de páxina.
  - O que se percorre coa vista ocupa o contedor enteiro: táboas, listas, reixas de tarxetas, calendarios e paneis (Persoal, Reservas, Calendario, Indicadores, Rexistro, Campos personalizados, Instalacións de xestión e a súa ficha, As miñas reservas, o listado e a ficha públicos). As cabeceiras de pantalla con accións (`furar-page-header`) tamén, para que os botóns queden ao bordo dereito da barra.
  - O que se le ou se enche vai nunha columna estreita **centrada** dentro del: o texto longo a `max-w-prose` (páxinas legais) e os formularios longos dunha columna a `max-w-col` (páxina de reservar, datos do concello, alta dun concello).
  - Centrado e estreito, coma sempre: o acceso e o rexistro (Keycloak), a tarxeta solta do convite, o selector de concello, os estados baleiros e de erro, os diálogos e os toasts.
  - No móbil só cambia a marxe (`px-gutter`); sen scroll horizontal a 320 px.
- **Cabeceira de pantalla:** `h1` (`text-title`) e unha liña de introdución; a acción principal da pantalla («+ Novo») á dereita en escritorio e debaixo no móbil, co texto completo (sen deixala só en icona).
- **Formulario en cards por pregunta:** unha card por bloque (`fieldset` + `legend` como título de card), campos con `gap-form`, `gap-group` entre cards. Botóns ao pé: unha acción → á dereita, á súa medida; con dúas, a principal a última. Resumo de erros arriba e foco ao primeiro erro (como hoxe).
- **Listas:** filas en card con divisores (`furar-row-list`). A fila con frecha é só para o que leva a outra pantalla; se a acción se fai aí, é un botón. Toda a fila clicable sen aniñar interactivos: ligazón estirada.
- **Grupos dunha lista:** título de sección; para «activos / desactivados», divisor rotulado.
- **Baleiro, cargando, erro:** `furar-status-state` no sitio do contido; cargando con esqueleto da forma da lista mellor ca un spinner.
- **Avisos:** `furar-callout` enriba do que afecta, só para erros e avisos (`danger`, `warn`, `info`), que quedan fixos ata que se arranxan. O que sae ben vai nun toast (`furar-toast`, «Toast de feito e de erro» no §8), e tamén o erro dunha acción solta sobre unha lista ou tarxeta (toast de erro, que non marcha só).
- **Ficha con modo edición global (especificación, bloque 1; 2026-10-26):** «← Volver», fotos, cabeceira co nome e o chip de estado na mesma liña (`pageHeadingAside`), o enderezo coa icona de ubicación (`introIcon`) e, á dereita, «Editar» e «Eliminar»; dúas columnas con **Datos primeiro no DOM** (no móbil, enriba; en escritorio, ao lado e fixa ao baixar). Fóra da edición todo é texto, sen lapis; «Editar» pon a ficha enteira en edición (`EditSession`) e aparece a barra fixa (`furar-action-bar`) con «Cancelar» (sae sen gardar nada) e «Gardar» (garda cada parte con cambios co seu `If-Match`). As listas con versión por elemento (usos, excepcións, bloqueos) son tarxetas (`[furarTile]`) que abren un modal; «Aceptar» no modal só deixa o cambio pendente. Os textos en dous idiomas, nun só campo con GL/ES (`furar-bilingual-field`).
- **Listados con imaxe:** a portada e o listado de xestión usan a mesma tarxeta (`furar-media-card`): imaxe 3:2, lugar coa icona de ubicación, etiquetas `soft` e, en xestión, o chip de estado nas accións.
- **Non repetir:** un dato vese unha vez por pantalla (o enderezo vai na cabeceira; a sección Localización só leva o mapa). Nada de textos que expliquen o evidente.

## 8. Catálogo de compoñentes

Todos en `shared/ui/`, xenéricos, OnPush, con signals (`input()`, `output()`, `model()`), sen `styles`, con textos só por `input()` ou claves `ui.*`, e cun spec (cobertura ≥ ao umbral de `angular.json`). Directiva (`[furarX]`) cando a peza é un elemento nativo con aspecto; compoñente (`furar-x`) cando hai estrutura.

### Prioridade 1: para re-estilar o que xa existe

**P1-1 · Tokens e derivación** (`core/theme/theme-tokens.ts`, `styles/tokens.css`, `styles/base.css`). §3–§6. Primeiro de todo: só con isto e coas utilidades compatibles (P1-2…) xa cambia toda a web.

**P1-2 · `[furarButton]`** (directiva en `<button>` e `<a>`).
- Variantes: `primary` (recheo `primary`, tinta `on-primary`; unha por pantalla), `secondary` (surface + bordo 1px `line` + `shadow-raised` + texto `primary-text`; en hover o bordo pasa a `primary`, sen tinte), `danger` (surface + bordo e texto `danger`), `quiet` (só texto `primary-text`, subliñado; para accións terciarias).
- Tamaños `sm` / `md` / `lg` (44 / 48 / 56), cápsula; `block` para ocupar o ancho no móbil.
- Estados: hover (só con `hover: hover`: bordo `primary` no secundario, `primary-hover` no principal, subliñado máis groso no `quiet`; nunca tinte co texto da mesma cor), prema (`scale(.98)`), foco, desactivado (texto `ink-soft`, bordo `line` continuo e `cursor: not-allowed`, sen opacidade nin bordo descontinuo; só mentres agarda ao servidor ou nun límite, véxase «Botóns sempre activos»), `busy` (`aria-busy="true"`, aro que xira —quedo e punteado con movemento reducido— e o rótulo en xerundio que pasa quen chama: «Gardando», como hoxe `common.saving`). `busy` fai do botón a acción en curso do seu contexto: véxase «Accións en curso» máis abaixo.
- Accesibilidade: `type` sempre explícito (lint `button-has-type`); nome accesible = texto; se só leva icona, `label` obrigatorio.
- Compatibilidade: as `@utility` `btn`, `btn-primary`, `btn-secondary`, `btn-danger` pasan a pintar igual ca a directiva, para non reescribir os usos nin `templates/module/web/` dunha vez.

**P1-3 · `furar-field` (o de hoxe, re-estilado) e `[furarInput]`**.
- **Sistema de campos (2026-11-12, decisión do mantedor; substitúe a anatomía de antes, rótulo enriba e control co seu marco).** Estilos en `styles/components/fields.css`, que cargan as follas das rutas con formularios (non a carga inicial).
  - **Cápsula só nos campos** (texto, número, texto longo, bilingüe, despregable, data e hora): un marco (radio `card`) co rótulo **dentro**, arriba, e o valor debaixo. Rótulo en minúsculas, seminegra (600) e 14 px; valor a 17 px e peso normal; aire entre os dous; alto compacto, uns 62 px (tokens `--f-fld-*`). O rótulo segue sendo un `<label for>` visible e fixo (non flotante) e os ids `-help`/`-error` non cambian. Picar en calquera punto da cápsula leva ao control ou abre o despregable; os que abren capa levan a frecha `chevron-down` á dereita, centrada. `furar-field` e `furar-bilingual-field` herdan a cápsula; un `.f-field` feito a man, se o control é fillo directo.
  - **Campos relacionados nunha cápsula** con liñas entre eles (`furar-field-group`); os campos curtos (desde e ata, teléfono e correo) comparten fila e baixan se non caben (`cells` ou `.f-field-cells`, ancho mínimo `--f-cell`).
  - **Filas soltas, sen caixa**, para o interruptor (`furar-switch`), o chip de estado (`furar-chip-menu`) e o selector de dúas opcións (`[furarSegmented]` radio), dentro de `furar-field-line`: o texto á esquerda, ao tamaño e peso do valor (17 px, normal) e aliñado co **texto** dos campos (o seu sangrado, `--f-fld-in`, nunca co bordo da cápsula), e o control á dereita. Si/non é sempre interruptor, nunca casilla (salvo as casillas de aceptación); varios si/non, un interruptor por fila; un campo que depende dun interruptor só aparece con el aceso.
  - **Despregable único** (`furar-select`, tamén con `multiple`): máis de dúas opcións é un despregable, que é un campo. Dentro, só unha lista vertical de texto coa letra do valor; a escollida leva ✓ á esquerda nun oco fixo (todas as opcións aliñadas) e sen fondo nin negra; en múltiple non se pecha ao escoller. Pecha co clic fóra e con Esc; `listbox`/`option`, `aria-selected`, `aria-multiselectable`. A mesma lista no filtro dunha barra (`furar-multi-select`) e igual no móbil: nada de botóns, chips ou tarxetas no canto do despregable (o «Que» e a «Parte» da reserva, o «Deporte» da folla de filtros).
  - **Erro fóra:** anel vermello na cápsula (coma o do foco, en `danger`) e a mensaxe debaixo, fóra da cápsula, a todo o ancho; nun grupo, cada mensaxe vai xusto debaixo do seu campo, dentro do grupo, e o seguinte campo baixa; nunha fila de dous campos, cada mensaxe debaixo da súa metade; sen o rótulo diante, que xa se ve enriba (2026-11-16; *antes*, debaixo do grupo co nome do campo diante). O control leva `aria-invalid` e `aria-describedby`.
  - **Foco ao erro:** ao fallar un gardado (validación no navegador ou erro do servidor), o foco vai ao primeiro campo con erro na orde de lectura, que queda á vista coa súa mensaxe (ábrese o pregable que o tapa) e o lector anúncia; se o erro non é dun campo, ao aviso de erro. Peza común `shared/ui/focus-error/`: herdana `furar-dialog` e `furar-edit-section`; nun formulario de páxina, `furarFocusError` no `<form>`.
  - Non cambian: os selectores de vista (pestanas, Día/Semana/Mes), os días da semana e as casillas de aceptación; a data e a hora conservan o seu panel, cun disparador de campo.
- **Tres regras de todos os formularios (2026-11-03, decisión do mantedor):**
  1. **Obrigatorio:** un «*» xusto despois do texto do rótulo, na cor `danger` e sen negra (claro pero sen berrar), nunca «(obrigatorio)» á vista. O «*» vai `aria-hidden` e o rótulo leva «(obrigatorio)» só para o lector (`furar-required-mark`), así o nome accesible o segue dicindo; o control leva ademais `aria-required="true"` (os botóns —`furar-select`, `furar-date-field`, `furar-time-picker`, `furar-field-popover`— non o admiten: co seu `required` levan «(obrigatorio)» no nome). Sen lenda «* Campo obrigatorio» enriba do formulario: o «*» vermello no rótulo xa o di e non desprazar a primeira card deixa as cabeceiras e as columnas aliñadas.
  2. **Axuda nun «?» tras o rótulo** (`tip`, `furar-help-tip`) ou, se é un exemplo, como texto de exemplo do campo; se aínda hai algunha visible, vai debaixo da cápsula (2026-11-12). *Antes* — **axuda entre o rótulo e o control** (2026-11-04, decisión do mantedor; substitúe a do 2026-11-03, que a puña debaixo): rótulo → axuda → control → erro, na vista e na orde do DOM, en todos os formularios (tamén os campos personalizados e a súa vista previa); quen le a axuda faino antes de escribir. Nunca no rótulo nin ao lado; o rótulo vai nunha liña e non se trunca: se é longo, o deseño dálle sitio. O `aria-describedby` do control segue apuntando á axuda e ao erro. Os campos que comparten fila (vía e número, código postal e localidade, desde e ata) van nunha `.f-field-row`: cada campo é unha grella de catro filas (rótulo, axuda, control, erro) que colle as da fila con `subgrid`, así os controis quedan á mesma altura aínda que un rótulo ou unha axuda ocupen máis ou só un dos campos teña axuda. Un campo feito a man (sen `furar-field`) é un `<div class="f-field">` cos mesmos fillos; un control sen rótulo nesa fila (a casiña «Obrigatorio», «Eliminar») vai nun `.f-field` só con el, á altura dos controis.
  3. **O control enche a súa columna** (`furar-field` e `.f-field` estíranos, tamén `furar-date-field` e `furar-select`): nada de anchos arbitrarios (`max-w-*`, `w-64`) nos campos. Só van curtos os que o son por deseño: `furar-time-picker` nunha fila de horas, os números dentro dunha frase (regras do uso) e o importe co «€»; unha columna estreita por deseño (código postal, número: 9rem) xa os deixa curtos.
- `[furarInput]` en `input` e `textarea` fóra dun campo (buscadores, filtros): alto 48, radio `field`, bordo 1px `line` (2px no foco e co erro); dentro dun campo, sen marco propio (o marco é a cápsula); foco co aro común e bordo `primary`; `aria-invalid="true"` → bordo `danger` (o estilo vai polo atributo, como hoxe). `textarea` crece ata un máximo. O despregable non é un `select` nativo senón `furar-select` (§8), co mesmo aspecto de campo.
- **Casillas e radios redondos, os dous** (2026-10-24): `input` nativo con `appearance: none`, círculo de 24 px, bordo 2px `line` (≥ 3:1) e, marcado, recheo `primary`. **Distínguense polo de dentro**: a casilla leva un check e o radio un punto (ambos en `on-primary`). Erro: bordo `danger`. Con cores forzadas, `Highlight`/`HighlightText`. Segue sendo o control nativo: teclado, lectores e formularios igual (`base.css`).
- Compatibilidade: `@utility input`, `label`, `help`, `field-error` apuntan aos mesmos estilos.

**P1-4 · `furar-card` e `[furarFieldset]`**.
- `furar-card`: surface, bordo `divider`, radio `card`, recheo `card`, `shadow-raised`. Opcións: `heading` + `headingLevel` (2 ou 3; a card é `section` con `aria-labelledby` se ten título), `tone="tint"` para destacar.
- `[furarFieldset]` en `<fieldset>`: o mesmo aspecto de card, `legend` como título de card; campos con `gap-form`.

**P1-5 · `furar-page-header`**.
- Anatomía: `h1` (`text-title`), introdución opcional, ranura de accións á dereita (debaixo a < 640 px), ligazón «‹ volver» opcional antes do `h1`.
- Accesibilidade: o `h1` é o destino do foco ao navegar (o shell búscao como hoxe); só un por pantalla.

**P1-6 · `furar-callout`**.
- **Decidido (2026-10-24): aspecto A, tinte suave, sen barra lateral.** Descartados no catálogo: a barra lateral, B (card co círculo da icona) e C (cápsula dunha liña).
- Anatomía: fondo co tinte suave do ton (`--f-soft-mix`, 8 % nominal), bordo fino do ton mesturado (28 %), radio `row`, sen sombra; icona do ton; título (negriña, opcional) e texto **sempre na tinta**; texto ou lista, acción opcional ao pé.
- Tons: `info`, `warn`, `danger`, `success`. A palabra do estado vai no texto ou nun rótulo oculto, non só na cor. Desde o 2026-11-02 `success` xa non se usa como aviso fixo nas pantallas: o que sae ben vai en toast.
- `live`: ningún (por defecto), `status` ou `alert`. `furar-problem-alert` e `furar-form-errors-summary` pasan a compoñelo (manteñen `role="alert"`, `traceId` e textos); a banda sen conexión tamén.

**P1-7 · `furar-row-list` e `furar-row`**.
- `furar-row-list`: `<ul>` en card, divisores entre veciñas con sangría (16 a cada lado); a fila non ten radio propio, só a primeira e a última seguen as esquinas da card; `label` para a lista (a lista do picker conserva `picker.listLabel`).
- `furar-row`: icona inicial opcional, rótulo (1–2 liñas), `detail` debaixo (`ink-soft`), valor á dereita, e **ou** ligazón (fila enteira, frecha, ligazón estirada co foco na fila) **ou** ranura de accións (botóns ao final; a fila deixa de ser ligazón). Alto mínimo 52, recheo 16.
- A < 640 px as accións baixan debaixo do texto.

**Tarxeta de cousas con estado e lista de accións (2026-11-12, decisión do mantedor).**
- `furar-status-card`: unha tarxeta por elemento (reservas, solicitudes…), pulsable enteira e co chevron á dereita, **sen botóns dentro**. Arriba o nome destacado (e o importe á dereita, que nunca empuxa o nome); debaixo, as liñas de datos con aire (os anacos unidos por « · » e cada un sen partir); ao final, a fila de chips (`[cardChips]`) aliñada co texto. O título da sección vai fóra das tarxetas. Un chip nunca vai á altura dun botón. Ao premela abre o detalle.
- `[furarActionList]` con `furar-action-item`: as accións dese detalle, unha por fila nunha caixa con bordo (icona, rótulo e chevron; a fila enteira é o botón ou a ligazón); a destrutiva (`tone="danger"`) vai a última. Como moito dúas accións á vista nunha tarxeta ou fila; o resto, no detalle.

**P1-8 · `furar-status-state`**.
- Modos `empty`, `loading`, `error`: icona nun círculo de 60 con tinte (en `error`, o tinte do propio ton: `danger` ao 12 % sobre surface, ≥ 3:1 coa icona), aire `item` (16) entre pezas, título, descrición, acción opcional centrada. En `error` a icona é sempre a mesma (distinguir «rompeu» de «aínda non hai nada»).
- `loading` leva `role="status"` e texto (`common.loading`). O modo `skeleton` (**feito**) tamén é `role="status"`, co texto oculto, e pinta `rows` bloques decorativos coa forma do contido (`shape` = `row` ou `card`) en vez da icona; brillo lento só sen `prefers-reduced-motion`.

**P1-9 · `furar-tag`**.
- **Regra (2026-10-24):** un chip de cor é **ou** `solid`, recheo do ton con letra branca ou negra segundo o contraste (`--f-on-*`, o habitual), **ou** `outline`, superficie con bordo e letra no ton. **Prohibido** o fondo tinguido coa letra do ton. Trinquete: `shared/ui/tag/tag-color.spec.ts` le os CSS de compoñentes e falla se unha regra de chip o fai.
- Tons: `neutral` (sempre `outline` con bordo `line` e letra na tinta), `primary`, `success`, `warn`, `danger`, `info`, `muted`; `look` = `solid` (por defecto) ou `outline`. O texto parte se non cabe (1.4.10). O ton só se usa cando o produto lle deu significado; se non, `neutral`.
- Só informativa na P1 (sen `role`); para metadatos dunha fila (tipo, visibilidade, obrigatorio). Mínimo 14 px.
- **Paleta de estados (2026-10-26):** os chips de estado usan a paleta de §3.1, `solid` con letra branca: Confirmada e Pagada `success`, Pendente `warn`, A devolver e Cancelada `danger`, Gratis `info`, Devolta `muted`. Sen etiqueta «Realizada» (2026-11-17): unha reserva xa pasada leva a do seu pago, coma calquera outra, e a da asistencia se a ten; que pasou xa o din a data e o historial.
- **Excepción, só o chip de filtro (2026-10-26):** `[furarFilterChip]` é un botón con `aria-pressed`, non unha etiqueta. Sen marcar pode levar o **tinte pálido do ton coa letra no ton** (fondo do 9 % do ton, bordo do 28 %); marcado, recheo do ton con degradado lixeiro, letra branca e sombra suave. 16 px e 40 px de alto mínimo; ≥ 4,5:1 nos dous estados e nos dous temas. O trinquete (`tag-color.spec.ts`) admite esa combinación **só** no selector do chip de filtro e comproba o contraste; as etiquetas de estado seguen `solid` ou `outline`. Non se estende a ningunha outra peza.
- **`look="soft"` (2026-10-26):** fondo `tint` e letra na tinta, sen ton (o ton non se aplica); para etiquetas informativas que se repiten, como os deportes das tarxetas da portada. Non choca coa regra: o tinte vai coa tinta, nunca coa letra do ton (o trinquete admite só esa combinación). Bordo transparente, visible con cores forzadas.
- **Chip de estado da instalación (feito):** `furar-chip-menu` = `furar-tag` dentro dun `<button>` que abre `[furarMenu]` con opcións `menuitemradio` (cada unha co seu punto de cor decorativo, `.f-tag-swatch`), con nome accesible completo («Estado: Activa. Cambiar»). Non cambia o valor: emite `selected` e a feature decide (confirmar «De baixa», reservas afectadas). Ton novo `muted` para `WITHDRAWN` (de baixa) en `tag.css`: recheo `ink-soft` con letra `surface` (ou bordo e letra `ink-soft` en `outline`; desde o 2026-10-26, o `muted` da paleta de §3.1 con letra branca); o estado vai sempre en texto.

**P1-10 · `furar-icon`**.
- SVG de liña desde un rexistro de nomes en código (só as iconas que se usan), trazo 1,75, `currentColor`, 20 ou 24 px.
- Decorativa (`aria-hidden="true"`) por defecto; con `label` pasa a `role="img"` co nome.
- **Unha soa familia (2026-10-26):** Material Symbols de liña (`outlined`, 400) en `material-symbols.ts`, na cor do texto ou do concello (`text-primary-text`), sen círculos de fondo. Nada de iconas de cor nin doutras familias: as que había (chincheta e calendario de cor) quitáronse.
- Fonte: **Material Symbols (Apache-2.0) como SVG empaquetados** no rexistro, só os usados e con anotación REUSE do seu titular (a outra colección avaliada case non tiña deportes nin accesibilidade). **Validada o 2026-11-04.** Non ten nada que ver con Angular Material, que segue fóra (ADR-0014). Nunca emojis nin varios sistemas de iconas.

**P1-11 · `[furarSegmented]` para o selector de idioma**.
- **2026-11-12 (decisión do mantedor):** o idioma vai **dentro do menú da conta** en todos os anchos (non nun segmentado na barra); o segmentado queda para o GL/ES dos campos bilingües e para os selectores de dúas opcións.
- Grupo de botóns con `aria-pressed` (o de hoxe, mesmo marcado). Sobre surface: pista afundida no `tint` e a opción activa en cápsula `surface` con `shadow-raised` e bordo `line` (o estado non depende só da sombra). Sobre `primary` (cabeceira): carril con contorno `on-primary` e a activa rechea. Con cores forzadas a activa vai en `Highlight`. A variante `radio` (radiogroup real, frechas, `ControlValueAccessor`) para escoller un modo dentro dun formulario faise nas instalacións; o modo `aria-pressed` do selector de idioma segue igual.

**P1-12 · `furar-action-bar`** (**feito**, 2026-10-26: modo edición da ficha; tamén servirá na reserva).
- Barra fixa ao pé con botóns de envío en formularios longos (axustes, editor de campo); cristal con fallback (§6); `scroll-padding-bottom`. Opcional: se a pantalla cabe, os botóns quedan no fluxo. **Pendente de validar** se se usa (non cambia o que fai a pantalla, só onde se ve o botón).

### Estado da implementación

Feito en `web/` (P1-1…P1-12). Onde a implementación concreta a guía:

| Peza | Onde | Nota |
|---|---|---|
| Tokens e derivación | `core/theme/theme-tokens.ts` (+ spec cunha grella de temas límite), `styles/tokens.css`, `styles/base.css` | ThemeService escribe os `--f-*` e `color-scheme`; un tema incompleto non deriva. O texto da marca exponse como `text-primary-text` (Tailwind non pode dar dous valores a `primary`); `bg-primary` é o recheo |
| `[furarButton]`, `[furarInput]`, `[furarFieldset]`, `[furarSegmented]` | `shared/ui/{button,input,card,segmented}/` | directivas sobre o nativo; as clases de antes (`btn-*`, `input`, `label`, `help`, `field-error`, `card`) pintan igual |
| `furar-card`, `furar-page-header`, `furar-callout`, `furar-status-state`, `furar-tag`, `furar-icon` | `shared/ui/<nome>/` | a card con título é `role="region"` co título como nome |
| Lista de filas | `ul[furarRowList]` + `<li><furar-row>` | directiva sobre `<ul>` en vez de compoñente: así segue sendo `ul > li` nativo (o que piden axe e os tests) |
| Iconas | `shared/ui/icon/material-symbols.ts` e `lazy-symbols.ts` | unha soa familia: Material Symbols de liña, co nome que usa Furar como clave. Na carga inicial (D27) só as que pinta o marco ao abrir a páxina (`material-symbols.ts`: barra superior e barra inferior pública); o resto vai en `lazy-symbols.ts`, un *chunk* que se pide ao arrancar en paralelo coa primeira páxina e que `furar-icon` tamén pide se lle chega unha icona que aínda non ten (ocupa o seu sitio baleira ata entón). Unha páxina pode rexistralas ao cargarse (`registerSymbols`). Icona nova: en `lazy-symbols.ts`, salvo que a pinte o marco |
| Catálogo | `/catalogo-ui` (`app/dev/catalog.page.ts`) | só en desenvolvemento: a ruta depende de `ngDevMode` e non entra no build de produción; ten os temas defecto, límite e escuro para revisar a derivación |
| Ligazón «← Volver» | ficha da instalación | de momento vai na feature, enriba do `furar-page-header`, como `a[furarButton="quiet"]` con `routerLink`; pasa a P1-5 se outra pantalla a pide |
| Pendente | espazos numéricos de Tailwind | a paleta de Tailwind está quitada (`--color-*: initial`), pero `gap-2`, `p-3`… seguirían compilando: a regra «aire só de tokens» só a fai cumprir a revisión. Quedan desprazamentos ópticos de menos dun `unit` (`mt-0.5`, `mt-1`, `py-1`) |
| Pendente | textos `catalog.*` | o catálogo non entra no build de produción, pero os seus textos si van en `gl.json`/`es.json` |
| Pendente | `furar-status-state` en `loading` | o `role="status"` nace xa co texto; algúns lectores non anuncian unha rexión viva que aparece chea. Aceptado mentres as cargas sexan breves |
| Cristal | `--f-glass`, `.f-glass` (`site.css`) | barras superior e inferior. O peor caso calcúlase coa tinta, o `primary` e o `danger` debaixo; se non dá 4,5:1 vale `surface`. Fondo sólido con `prefers-reduced-transparency`, `prefers-contrast: more`, `forced-colors` e `@supports not (backdrop-filter)` |
| axe nos unitarios | `src/testing/axe.ts` | WCAG 2.1 A/AA en jsdom sen contraste (non hai pintado); o contraste próbase en `theme-tokens.spec.ts` e no e2e |

### Prioridade 2: xenéricos que pedirán os módulos (facelos cando unha pantalla acordada os necesite)

**Os que piden as instalacións** (instalacións, horario e bloqueos). Estado a 2026-10-25: todos **por facer**, na tarefa indicada.

| Compoñente | Onde se usa | Propósito e anatomía | Accesibilidade |
|---|---|---|---|
| `furar-time-picker` | — | botón coa hora en texto que abre en CDK Overlay unha lista cada 15 min, desprazada ata a actual; valor en **minutos enteiros** 0–1440; `min`, `max`, `allowEndOfDay` (ofrece 24:00); `ControlValueAccessor` | `cdkListbox` co foco dentro; frechas, Inicio/Fin, Intro, Esc; o foco volve ao botón; `aria-expanded`; nome con contexto por `input` («Luns, tramo 1, desde, 8:00») |
| `furar-calendar` e `furar-date-field` | — | mes con semana en luns, hoxe con aro, escollido recheo `primary`, días fóra de rango apagados en gris claro (§3.1, sen riscar); `furar-date-field` admite atallos (`shortcuts`, p. ex. «Hoxe»/«Mañá» con `aria-pressed`), `displayWith` e `look="pill"` (botón sen marco dentro dunha barra de busca, icona proxectada con `[furarDateFieldIcon]`); o campo mostra a data en texto («24/12/2026») e abre o panel en Overlay; valor ISO `LocalDate`; **só data simple** nun formulario: un rango son dous campos; nun filtro de días (reservas do concello) `furar-calendar` admite `range` e `rangeEnd` (extremos en recheo, os do medio en tinte) | **rexilla APG**: frechas, Re Páx/Av Páx (mes), Maiús+Re Páx (ano), Inicio/Fin; mes anunciado, día con data completa; mes e días por `Intl` co idioma activo |
| `furar-dialog` e `furar-confirm` | **feito** | capa centrada; título ≤ 5 palabras, mensaxe, cancelar á esquerda e acción á dereita; `danger`, `busy`. Ábrense con `DialogService.open()` / `confirm()` (promesa con `true`/`false`; `action` opcional con `busy`; desde a súa orixe, véxase «Accións en curso e transicións das capas») sobre un contedor propio sen estilos en liña (o do CDK mete `<style>` e a CSP o bloquea); `size` `sm`/`md`/`lg`; a 320 px as accións apílanse | CDK `Dialog`: trampa e retorno do foco, `aria-modal`, `aria-labelledby` (o título), Escape pecha (salvo `busy`); botón de pechar visible; foco inicial no título, e na confirmación (`alertdialog` con `aria-describedby`) en «Cancelar» |
| `[furarMenu]` | **feito** | menú sobre CDK Menu: `[furarMenuTrigger]`, `[furarMenu]`, `[furarMenuItem]` e `[furarMenuItemRadio]` (directivas con `hostDirectives` do CDK); úsao o chip de estado (`furar-chip-menu`) | `menu`/`menuitem`/`menuitemradio` con `aria-checked`, frechas, Inicio/Fin, Esc e Tab pechan e devolven o foco; a marcada leva ✓ e negra |
| `furar-switch` | **feito** | interruptor 50×30 (botón de 44 px); unha fila en toda a aplicación: rótulo e «?» á esquerda, o interruptor á dereita do todo e o proxectado (p. ex. «Vista previa») xusto antes del; os campos que dependen del só existen co interruptor acendido; `ControlValueAccessor` booleano; `hideLabel` cando a fila xa o di; acendido con ✓ na bóla (non só cor) | `button role="switch"` + `aria-checked`, rótulo nun `<label for>` (nomea e, ao picalo, cambia) |
| `furar-radio-cards` | rol ao invitar e ao cambialo (**feito**, 2026-11-03) | tarxetas lado a lado (unha debaixo doutra no estreito): nome, radio e a lista do que inclúe (✓ en `success`) e do que non (✕ apagado); a escollida, bordo `primary`, tinte e o punto do radio; `ControlValueAccessor` ou `[(value)]`; con `description`, unha liña de texto baixo o nome no canto da lista (formato da descarga dos datos abertos) | radios nativos co mesmo `name` nun `radiogroup` nomeado polo rótulo: unha parada de tabulador e frechas; nome = o da tarxeta, descrición = a lista con «Si:»/«Non:» en texto (as iconas son decorativas); foco visible na tarxeta enteira |
| `furar-edit-section` | **feito** | **substitúe a `furar-summary-card`**: `<section>` con `<form>` tipado; valor en repouso como `<button>` (rótulo + valor) que pasa a campo; liña de «cambios sen gardar», «Cancelar»/«Gardar», erros do servidor no campo, «Gardado» uns segundos. **Non decide o control**: pono a feature. Uso: `<furar-edit-section [formGroup]="form" [heading]="…" [save]="fn">` (o `FormGroup` vai no propio compoñente, así o `formControlName` do contido atópao); `save` devolve promesa ou observable e rexeita co `ApiProblem`; `errors[]` ao control pola ruta (`parts[0].name`) ou con `controlFor`; o que non ten campo, a un aviso da sección. Cancelar volve ao último estado limpo (`cancelled` para reconstruír `FormArray`). Cada valor en repouso é un `furar-edit-field` (`label`, `value`, `control`) co control da feature dentro. Garda: `unsavedChangesGuard` e `UnsavedChangesService` en `core/unsaved-changes/` | ao editar, o foco vai ao campo; Esc devolve o foco ao botón; tras gardar, ao título da sección; foco ao primeiro erro; «Gardado» por `role="status"`; garda `CanDeactivate` e `beforeunload` só con sección sucia |
| `[furarSegmented]` (radio) | — | a mesma directiva de P1-11 cun modo radio para 2–5 opcións («Se aluga», «Cada reserva dura») | `role="radiogroup"`, frechas, `ControlValueAccessor`; a 320 px baixa de liña |
| `furar-tabs` | aspecto `line` (2026-11-03) | pestanas (tempadas); as accións («+ Tempada») van fóra do `tablist`. `look="pill"` (cápsulas, por defecto) ou `look="line"` (o das seccións: sen título enriba, 44 px de alto, 0 14px, negra 16 px en `ink-soft`; a activa en tinta cun subliñado de 3 px `primary`; a fila cunha liña `divider` debaixo e 4 px entre pestanas); `size="sm"` (40 px, 15 px) dentro doutra sección. Nunha fila que se despraza dentro da franxa (`furarTabStrip`) | `tablist`/`tab`/`aria-selected`, frechas |
| `furar-tab-nav` | sección «Concello» (**feito**, 2026-11-03) | pestanas que son páxinas: un `nav` de ligazóns `a[furarTabLink]` co mesmo aspecto ca `look="line"`, cada unha co seu URL; marcas dentro da pestana: punto de cambios sen gardar (`.f-tabs__dirty`), triángulo de aviso (`.f-tabs__warn`) e «!» de erros no contido (`.f-tabs__error`, tamén en `furar-tabs`), cada unha co seu texto para lectores; `size="sm"` para subpestanas (as páxinas legais) | `aria-current="page"` (`routerLinkActive` + `ariaCurrentWhenActive`); cada ligazón é unha parada de tabulador e ademais frechas, Inicio e Fin moven o foco sen navegar; ao navegar desde elas o foco queda na pestana (`data-keep-focus`) |
| `furar-color-tile` | Concello › Aparencia (**feito**, 2026-11-03) | unha cor como mosaico: a mostra é o mosaico (sen card), co nome e o código debaixo; bordo fino só nas cores moi claras | o `input type="color"` vai enriba da mostra, transparente, co nome da cor como rótulo; foco visible na mostra |
| `DialogService.unsavedChoice()` | sección «Concello» (**feito**, 2026-11-03) | saír dunha parte con cambios: «Gardar os cambios de …?», Cancelar · Descartar · Gardar (`furar-unsaved-choice`); úsao `leaveTabGuard` | `alertdialog` con descrición; foco inicial en «Gardar»; Esc ou fóra = Cancelar |
| `furar-combobox` | **feito** | campo con suxestións asíncronas (enderezos, deportes): espera de 300 ms (`debounce`), `minLength`, «buscando», «sen resultados», erro (emite `failed` para encher a man) e opción final «Crear «texto»» con `allowCreate` (emite `create`; a feature decide). Valor (`ControlValueAccessor`): o da suxestión escollida ou `null`; escribir despois borra o valor; `clearOnSelect` para engadir varios. Para escoller nunha lista pechada e curta, `furar-select`. Listas ricas (portada): grupos (`group` en todas as suxestións → `role="group"` co título, APG), miniatura (`image`) ou icona (`icon`) decorativas, `highlight` (coincidencia sen acentos marcada con `<mark>`) e `look="pill"` (campo sen marco, capa ancorada en `panelOrigin`) | patrón combobox APG con `aria-activedescendant` no `input` (o foco non sae del); frechas con volta, Alt+abaixo abre e Alt+arriba pecha, Intro, Esc pecha e despois borra, Tab pecha; anuncia «Buscando…», o número de suxestións, «sen resultados» ou o erro nun `role="status"` |
| `furar-carousel` | portada e fotos (**feito**, 2026-10-26) | tira horizontal; cada elemento cun `ng-template furarCarouselItem`; frechas redondas sobre os bordos que desaparecen nos extremos (desactivadas: fóra do tabulador); `reorderable`: arrastrar con vista previa (as demais apártanse, FLIP) e desprazamento automático preto dun bordo ou fóra da tira; emite `reordered` coas claves; `hint` (aviso emerxente) e `hintOpen` (ábreo desde fóra); `scrollFocusable` cando os elementos non teñen nada enfocable; `--f-carousel-item` para o ancho. Nas portadas, tira con frechas en todos os anchos (2026-11-18) | lista con nome; Alt + ← / → move o enfocado e anúnciao (`role="status"`); aviso WCAG 1.4.13 (pódese pasar por riba, Esc péchao, abre co foco); sen animacións con movemento reducido |
| `furar-media-card` | portada e listado de xestión (**feito**, 2026-10-26) | tarxeta con imaxe 3:2 (zoom suave ao pasar), título, lugar coa icona de ubicación, contido (etiquetas `soft`) e `[cardActions]` por riba da ligazón; con `link`, o título é ligazón estirada a toda a tarxeta; `selected` (2026-11-10) ponlle o aro do foco: a tarxeta da marca escollida no mapa da portada común | un só destino por tarxeta; as accións son paradas propias, sen aniñar interactivos; a imaxe co seu `alt` (ou `alt=""` se só decora) |
| `furar-review` | capa pública das valoracións e tarxeta «Valoracións» dos indicadores (**feito**, 2026-11-18) | unha valoración nunha lista de filas (`ul[furarRowList]`, fila `.f-review-row`): arriba, en texto suave, o lugar (só se a lista é de varias instalacións) e o día; debaixo, as estrelas; o comentario no seu parágrafo e, ao final, os chips («Oculto»). Nos indicadores a fila enteira é o botón que abre o detalle, co chevron | só `span` dentro, para poder ir nun botón; as estrelas son unha imaxe co seu nome |
| `[furarTile]` | modo edición da ficha (**feito**, 2026-10-26) | `<button>` nativo co aspecto dunha tarxeta (bordo, radio de card); `data-dashed` para «engadir»; `.f-tile-grid` para a lista | botón nativo; o nome é o seu texto; contido só en `<span>` |
| `furar-bilingual-field` | descrición, alt das fotos, deporte novo (**feito**, 2026-10-26) | un só campo (`input` ou `textarea` con `multiline`) co rótulo e o conmutador GL/ES (`[furarSegmented]`); cada idioma é o seu `FormControl` (`[gl]`, `[es]`); o erro co idioma diante; se o idioma á vista está ben e o outro non, pasa ao outro | `lang` no campo; conmutador `aria-pressed` co nome «Idioma de …»; erros por `aria-describedby`; `hideLabel` cando o título xusto enriba xa o di |
| `[furarLongText]` | textos longos dos formularios do persoal: nome e enderezo do servizo de deportes, datos legais longos, nome, rúa, descrición, normas e notas de pago da instalación (tamén no asistente), axuda dos campos propios (**feito**, 2026-11-18) | directiva sobre o `<input>`/`<textarea>` `furarInput`: no móbil (< 640 px) o campo é de só lectura e tocalo (ou Intro) abre unha folla desde abaixo (`furar-dialog`, alta) co texto enteiro e «Confirmar», que o leva ao control; Pechar, Esc ou picar fóra descartan. Bilingüe: `longText` en `furar-bilingual-field`, e a folla leva o GL/ES e confirma os dous. No escritorio, o campo de sempre. Estilos en `components/long-text.css` (xestion.css e reservas.css) | título da folla = rótulo do campo (ou o texto de `furarLongText`); área co mesmo nome e `lang`; o foco volve ao campo ao pechar |
| `EditSession` e `furar-action-bar` | ficha da instalación (**feito**, 2026-10-26) | modo edición global: as partes (`EditParticipant`: `dirty`, `validate`, `save`, `reset`) dánse de alta; «Gardar» valida todo antes, garda as que teñen cambios unha detrás doutra e só sae se todas foron ben; «Cancelar» desfai todo sen peticións. `furar-edit-section` e `furar-edit-field` entran sós no modo (sen pé propio; fóra da edición, texto sen botón). A barra: cristal e sombra da barra superior, compacta e centrada (ancho do contido; no móbil, enteira), estado á esquerda («Editando a ficha» ou «Cambios sen gardar») e botóns á dereita; no móbil, enriba da barra inferior | a barra é unha rexión con nome; `scroll-padding-bottom` para que non tape o foco; conta para a garda de saída |
| `[furarSegmented]` co fondo que se despraza | todos os selectores (**feito**, 2026-10-26) | ancho xusto ás opcións (tamén en grella) e un fondo (`::before`) que vai á escollida; a directiva mide e escribe `--f-seg-*` por CSSOM | sen movemento con `prefers-reduced-motion`; con cores forzadas, a escollida en `Highlight` |
| `furar-confirm` con `typeToConfirm` e `acknowledgement`; `furar-type-to-confirm` | eliminar unha instalación (**feito**, 2026-10-26); desactivar persoal e pasar unha persoa de Xestión a Recepción (**feito**, 2026-10-30) | campo que pide escribir a frase («eliminar instalación», «desactivar», «recepción»), comparada sen maiúsculas, tildes nin espazos de máis; con `acknowledgement`, ademais unha casiña obrigatoria co texto exacto do que se entende (non é consentimento). A acción segue activa (desde o 2026-11-17, «Botóns sempre activos»): premida antes de que coincida a frase ou coa casiña sen marcar, non fai nada, o erro sae debaixo do que falta («Escribe «desactivar» para confirmar.», «Marca a casiña para confirmar.») e o foco vai ao primeiro. `furar-type-to-confirm` é a peza soa, para diálogos propios («Cambiar o rol»), con `invalid` para o erro | rótulo visible co texto que hai que escribir; o foco entra no campo; o erro, por `aria-invalid` e `aria-describedby` do campo ou da casiña; a casiña, `aria-required` e descrita polo diálogo |
| `furar-confirm` con `details` (estilo A) | cancelar unha reserva (**feito**, 2026-10-26) | no canto da mensaxe, liñas de texto con icona e sen fondos: a cousa sobre a que se pregunta (`title` en negra e o texto) e o que pasará; `tone` só colorea a icona | as liñas son a descrición do `alertdialog` (`aria-describedby`); o significado vai no texto, nunca só na cor |
| `furar-delete-button` e `[furarButton] round` | fotos (**feito**, 2026-10-26) | botón `danger` coa papeleira, redondo (só icona, 44 px) ou con texto; ao pasar, enche de `danger`. `round` vale para calquera botón só de icona. Non pregunta: se borra algo que non se recupera, quen o usa abre `furar-confirm` | nome accesible obrigatorio (`label`, «Eliminar a foto 2»); co texto visible, o nome empeza por el (2.5.3) |
| `furar-chip-list` | deportes da instalación (**feito**, 2026-11-09) | o escollido nun campo de varios valores, debaixo del (p. ex. un `furar-combobox` con `clearOnSelect`): `items` (`{ key, label, outline? }`, xa traducidos), `label` da lista, `status` (anuncio), `fallbackFocus` e `removed` (o índice; non cambia nada, quita a feature). Cada chip co aspecto de `furar-tag` `soft` (fondo `tint`, letra na tinta, 40 px de alto) e un «×» redondo de 40 px; `outline` engade un fino bordo na liña (os deportes do concello). Fronte a `[furarFilterChip]` (só conmuta) e `furar-tag` (sen «×»). Estilos en `styles/xestion.css` | `ul` nomeada; cada «×» é un botón «Quitar …»; Tab percorre os «×», Supr ou Retroceso nun «×» quítao, ← e → móvense entre eles; tras quitar, o foco vai ao «×» seguinte (ao anterior se era o último) e, sen ningún, a `fallbackFocus`; un `role="status"` educado, sempre presente, para cada alta e baixa. Retroceso co campo baleiro (quita o último) faino a feature |
| `furar-multi-combobox` | deportes da instalación e «Instalación» dos filtros e da descarga das reservas (**feito**, 2026-11-17) | o único campo de varios valores con busca: dentro de `furar-field` (rótulo dentro), un `furar-combobox` con `clearOnSelect` e, debaixo, os escollidos en `furar-chip-list`. `selected` (model, na orde en que se escollen), `key`, `display`, `outline`, `source` (as xa escollidas quítanse no compoñente), `limit`, `countText` (total do anuncio), `allowCreate` (emite `create`; a feature crea e chama a `add`), `tip`, `required`, `error`. Para escoller nunha lista curta sen buscar, `furar-select` con `multiple` | o do combobox e o da lista de chips; Retroceso co campo baleiro quita o último; cada alta e baixa anúnciase («Engadiuse Pádel. 2 deportes escollidos.») |
| `furar-user-menu` | barra (**feito**, 2026-10-26; segundo nivel, 2026-11-03) | pastilla só co nome á esquerda (sen o rol, 2026-11-03) e as iniciais nun círculo á dereita, sen chevron; no móbil só o círculo; panel con cabeceira (iniciais, nome, rol · contexto, correo) e entradas con icona (`href`, `link` ou acción `selected`) e separador. Con `switcher`, segundo nivel no mesmo panel: no primeiro, a fila «Concello» (icona, valor actual á dereita e ›) á mesma marxe ca as outras e sen columna de ✓; no segundo, «‹ Concello» e as opcións (`menuitemradio`) cunha columna para a ✓ diante, todas aliñadas, e o detalle (o rol) á dereita; escoller emite `switched` | `[furarMenu]` (APG menu button: Intro/Espazo/frechas abren, frechas e Inicio/Fin, Esc e Tab pechan e devolven o foco); o botón leva `aria-label` co nome e o rol (no móbil o texto non se ve). Segundo nivel con `[furarMenuItemStay]` (un `menuitem` que non pecha): a fila leva `aria-haspopup="menu"`, abre con →, Intro, Espazo ou clic co foco en «‹ Concello»; ← ou «‹ Concello» volven co foco na fila; Esc, Tab ou clic fóra pechan en calquera nivel |
| `furar-more-menu` e `overflow()` | barra inferior do móbil (**feito**, 2026-10-28) | `overflow(items, max)` reparte os destinos dunha barra de `max` sitios: se caben todos, ningún vai a «Máis»; se non, vense os `max − 1` primeiros e o último sitio é «Máis» co resto, na mesma orde (a barra inferior leva 4, para que os nomes non partan a 320 px). `furar-more-menu` é ese último sitio: botón con icona e texto que despregue enriba a lista das ligazóns que non caben; a da páxina actual leva ✓ e `active` marca o botón coma un destino activo. Quen o usa cárgao con `@defer` | APG «disclosure» de navegación (son ligazóns, non accións: sen `role="menu"`): botón con `aria-expanded` e `aria-controls`, `<ul>` de ligazóns que se percorre co Tab; Esc pecha e o foco volve ao botón; pecha ao picar fóra, ao saír co foco ou ao escoller; `aria-current="page"` na ligazón da páxina actual e, se está dentro, o nome do botón remata en «(páxina actual)» |
| `furar-dialog` con `sheet` | **feito** | `DialogService.open(…)`: por baixo de 640 px, toda capa (detalles, formularios, filtros) sae como folla pegada abaixo a todo o ancho (esquinas de arriba redondas, entra desde abaixo), por defecto desde o 2026-11-18; `sheet: false` só para a que teña que ir centrada. As confirmacións (`confirm`, `unsavedChoice`) seguen centradas; `size="full"` segue a pantalla enteira. No escritorio, a capa centrada de sempre | igual ca `furar-dialog` |
| `furar-dialog` `size="full"` e `back` | fotos (**feito**, 2026-10-26) | ata 80 × 54rem, pantalla enteira < 900 px, cabeceira e pé brancos co corpo en `page`; `back` pon «←» antes do título (pecha esta capa e volve á de debaixo); `[dialogStatus]` no pé, á esquerda das accións («2 cambios sen gardar») | igual ca `furar-dialog` |
| `furar-map` | **feito** | mapa con Leaflet 1.9.4 ([ADR-0023](adr/0023-enderezos-e-mapa-sen-terceiros.md)), teselas de `/tiles`: só lectura (`point`, sen arrastre, zoom no centro) ou editable (`editable`, ControlValueAccessor con `MapPoint {latitude, longitude}` ou `null`: chincheta arrastrable, picar para mover, frechas e campos de latitude e lonxitude con seis decimais); botóns de zoom de 44 px con tokens; chincheta propia en CSS; recoñecemento do despregue (`DeploymentSettings`, por defecto «CC BY 4.0 scne.es» con ligazón) sobre fondo sólido; sen roda do rato. Leaflet (`import()`) e `leaflet.css` (`<link>` á mesma orixe, capa `base`) cárganse co primeiro mapa. Sen mapa no despregue (`FURAR_MAP=off`) non se carga Leaflet: só lectura e varias marcas non pintan nada e o editable queda nos campos de latitude e lonxitude; con varias marcas, `activeMarker` (2026-11-10) resalta unha (escura, maior e por riba) sen mover o mapa | só lectura: non é parada de tabulador (canvas e zoom `aria-hidden`) e o texto do enderezo é a alternativa; editable: grupo co nome, chincheta enfocable co nome e as coordenadas, frechas (Maiús: máis lonxe) con anuncio en `role="status"` e campos lat/lon (WCAG 2.5.7); atribución con contraste AA |

**Os que piden as reservas** (fase 1). Estado a 2026-10-26: **feitos**, no catálogo. Para non mover o orzamento inicial (D27), os seus estilos van en follas á parte: `styles/reservas.css` (chip de filtro, campo con panel, QR e lista de valores), que carga `bookingStylesResolver` en cada ruta que os use, e `styles/xestion.css` (días da semana).

| Compoñente | Onde se usa | Propósito e anatomía | Accesibilidade |
|---|---|---|---|
| `[furarFilterChip]` | — | botón de filtro con ton opcional, coa excepción de P1-9 (tinte pálido sen marcar, recheo marcado) | `aria-pressed`; 40 px de alto; ≥ 4,5:1 nos dous estados e temas; con cores forzadas, o marcado en `Highlight` |
| `furar-qr` | — | pinta o QR que dá a API (`size`, `path` SVG), sen dependencia nin `innerHTML` | `role="img"` co nome e o código («Código QR da reserva FUR-…») |
| `furar-field-popover` | — | campo-resumo (rótulo e valor; `placeholder` sen valor) que abre un panel en CDK Overlay co contido proxectado e, por baixo de 640 px, unha folla desde abaixo co velo e botón de pechar; `next()` pecha e abre `nextField`; varios campos seguidos comparten marco dentro de `.f-field-group`; o seu erro vai dentro, debaixo do valor (`<span fieldError class="f-field-error">`) | pecha con clic fóra e Esc; o foco volve ao campo ao pechar; `aria-expanded` |
| `furar-value-list` | **feito**, 2026-10-26 | lista de rótulos e valores (`items: {id, label, value}`): rótulo en gris á esquerda e valor en negra á dereita, liñas enriba e embaixo; se non cabe, o valor baixa de liña. «Reserva confirmada» e o detalle da reserva na xestión; variante `plain` sen liñas nin recheo (campos propios da ficha pública): se unha fila non cabe nunha liña, todas pasan a rótulo enriba e valor debaixo (ResizeObserver) | `dl` con `dt`/`dd` |
| `furar-disclosure` | datos abertos (**feito**, 2026-11-11, [ADR-0034](adr/0034-datos-abertos.md)) | pregable: título como botón a todo o ancho, na cor principal, coa frecha á dereita que vira; pechado por defecto e independente dos demais; sen animación con movemento reducido | patrón «disclosure» das APG: `button` con `aria-expanded` e `aria-controls`, panel `hidden` pechado |
| `furar-date-filter` | xestión de reservas, datos abertos e folla de filtros da portada común (**feito**; en `shared/ui/date-filter/` desde o 2026-11-11, [ADR-0034](adr/0034-datos-abertos.md)) | o único chip de data da app (`f-mdate`, estilos en `components/date-filter.css`, na folla global): sen data, o chip de contorno «Escoller data»; con ela, o chip cheo co aspa dentro. Rango: panel cos atallos, calendario de rango e «Aplicar»; entradas `presets` (os atallos; sen eles, Hoxe · Mañá · Esta semana), `min` e `max` (días fóra, desactivados). `single`: unha data, só o calendario, e escoller pecha. `format` (texto do chip) e `label` (nome do grupo no nome dos botóns) | atallos con `aria-pressed`; Esc pecha só o panel; picar fóra pecha; o foco volve ao chip |
| `furar-weekday-picker` | — | L–D co idioma activo por `Intl`, atallos «Todos», «Luns a venres», «Fins de semana» (sobe da P3); valor (ControlValueAccessor) = máscara de bits do `weekdays` da API (luns = 1 … domingo = 64) | `role="checkbox"` por día; atallos con `aria-pressed` |
| `furar-multi-select` | portada común (**feito**, 2026-11-10, [ADR-0030](adr/0030-portada-comun.md)) | filtro de selección múltiple dentro dunha barra (o «Deporte» do buscador): botón sen marco, coma o do día (`furar-date-field` `look="pill"`), que abre en CDK Overlay a lista de `furar-select` con varias marcadas; segue aberta mentres se marca. O botón di o rótulo, a opción marcada ou «N filtros»; `value` e `changed` | `aria-haspopup="listbox"`, lista `aria-multiselectable`; péchase picando fóra, con Esc (o foco volve ao botón) e con Tab; o nome accesible leva o rótulo e o marcado |

**`furar-select`, o despregable (feito, 2026-10-28).** O único despregable da web: ningunha pantalla usa o `<select>` nativo (excepción ao principio 7, §1).
- **Que é:** un `<button>` co aspecto dun campo (`.f-input`: 48 px, bordo fino que engrosa no foco ou co erro, frecha debuxada) e o texto da opción escollida; ao pulsalo abre en CDK Overlay unha lista (`cdkListbox`) exactamente co ancho do campo (a lista enche o panel do CDK, que leva ese ancho mínimo; 2026-11-03) e máis ancha só se unha opción o pide, sen saír da pantalla; debaixo ou, se non cabe, enriba, e aliñada á dereita do campo se á esquerda sairía. A escollida leva ✓ e negra ademais do tinte (1.4.1). Estilos en `styles/components/select.css`.
- **Uso:** `formControlName`/`formControl` (`ControlValueAccessor`) ou, sen formulario, `[value]` e `(changed)`. Opcións `{ value, label, disabled? }`, cos textos xa traducidos: as dunha lista de claves co pipe `furarOptions` (`tipos | furarOptions: 'prefixo.' : lang()`, cun cuarto argumento opcional cos valores desactivados); `null` vale como opción («Ningún», «Escolle unha opción»). `placeholder` para o baleiro, `selectId` para un `<label for>` externo (`furar-field`), `invalid` e `describedBy` para o erro e a axuda. Non valida: o erro pono quen o usa.
- **Teclado (o do nativo):** no botón, Intro, Espazo, frecha abaixo ou arriba abren. Na lista, frechas, Inicio e Fin moven, e escribir as primeiras letras salta á opción; Intro ou Espazo escollen e pechan; Esc pecha sen cambiar; Tab pecha e segue. O foco volve sempre ao botón; clic fóra pecha.
- **Accesibilidade:** `aria-haspopup="listbox"`, `aria-expanded` e `aria-controls`; o nome accesible é o rótulo e o valor («Idioma por defecto, Galego»), e por iso `label` ten que ser o texto do rótulo visible (2.5.3); un botón non admite `aria-required`: se o campo é obrigatorio, `required` pon «(obrigatorio)» no nome, despois do rótulo. As opcións desactivadas vense riscadas e con `aria-disabled`. Con cores forzadas, a escollida en `Highlight` e as desactivadas en `GrayText`; con movemento reducido, sen animación.
- **Fronte a `furar-combobox`:** `furar-select` para escoller nunha lista pechada e curta que se le dunha ollada (tipo, idioma, período, visibilidade). Se a lista é longa, vén da API ou hai que buscar escribindo (enderezos, deportes), `furar-combobox`.

**`furar-search-bar` e `.f-list-toolbar`, a busca dunha lista (feito, 2026-11-18).** A cápsula das reservas para todas as listas da xestión (Reservas, Incidencias, Rexistro de actividade): lupa e campo (o `<input type="search">` proxéctao a páxina) e, tras un separador, botóns de só icona do mesmo azul (`f-search-bar__tool`: «Filtros», «Calendario»). Con `filters` (un `<ng-template>` cos chips das columnas), no móbil «Filtros» co número de filtros postos abre unha folla con todos eles e «Ver N …»; no escritorio non se ve (fíltrase nas cabeceiras). `.f-list-toolbar` pon na mesma liña a busca (ou o chip do estado, sen busca) e o «+» da lista á dereita (`.f-list-toolbar__end`). Unha cabeceira `quiet` sen accións non ocupa sitio. Estilos en `styles/components/search-bar.css` (folla `reservas.css`).

**`furar-filter-bar`, a fila de filtros (feito, 2026-10-29).** O único patrón das filas de filtros enriba dunha lista, un panel ou unha táboa: «Rexistro de actividade», «Indicadores» e os chips do listado público de instalacións. Compoñentes en `shared/ui/filter-bar/`; estilos en `styles/components/filter-bar.css` (folla `reservas.css`).
- **Anatomía:** `furar-filter-bar` é un `role="group"` co seu nome (`label`). Dentro, campos `furar-filter-field` co **rótulo enriba** (14 px, negra, `ink-soft`; nunca en maiúsculas nin dentro da caixa) e o control debaixo: `furar-select`, `furar-field-popover` con `look="filter"` (o mesmo campo ca o despregable: superficie, bordo `line`, valor en negra e a frecha debuxada; sen valor, o texto do filtro por defecto en tinta, p. ex. «Último ano») ou `[furarSegmented]`. Despois, chips `[furarFilterChip]` e botóns («Quitar os filtros»). `.f-filter-bar__end` leva un control á dereita («Exportar»).
- **Medidas:** todos os controis miden **44 px** e van en **cápsula**, aliñados pola base; separación de 12 px. Baixa de liña se non cabe; por baixo de 640 px cada campo colle o ancho enteiro e os chips seguen ao seu ancho.
- **Chips:** neutros, salvo que o chip sexa un estado: entón levan o ton do estado (§3.1), o mesmo da etiqueta que filtra («Só fallidos», `danger`, coma «Fallou» na táboa; «Pendente», `warn`).
- **Accesibilidade:** o rótulo é un `<label for>` (`controlId`) que leva ao control, e o nome accesible do control é «rótulo, valor» (`furar-select` e o campo con panel, 2.5.3); para un grupo segmentado, o rótulo é un texto con `labelId` e o grupo leva `aria-labelledby`. Teclado, o de cada control.
- **Fóra deste patrón:** a cabeceira do calendario das reservas (vista e instalación xunto ao título) e a barra das reservas do concello (busca, «Filtros» nun diálogo e chips dos filtros postos), que non son filas de filtros.

**Conta atrás dun botón desactivado (feito, 2026-10-29).** Cando unha acción volve estar dispoñible nun momento coñecido («Reenviar» unha invitación, 2 min despois do último envío), o botón desactívase antes de que a API diga que non. `countdownClock()` en `shared/ui/countdown/` é o reloxo: avanza cada segundo só mentres queda algún prazo e párase só (sen intervalos orfos); `formatCountdown()` dá `m:ss`. O botón leva `aria-disabled` (segue no tabulador), o texto á vista («Podes reenviar en 1:45», cifras `tabular-nums`) é a súa descrición (`aria-describedby`) e non se anuncia cada segundo; cando remata, un único aviso en `role="status"`. Nun menú, o texto vai debaixo do rótulo (`.f-menu__item-hint`, `aria-hidden` e referido pola descrición, para que o nome siga sendo o da acción).

**`furar-rich-text-editor`, o editor de texto (feito, 2026-10-29).** O único editor con formato da web (`shared/ui/rich-text/`); hoxe, o «Texto propio» das páxinas legais ([ADR-0017](adr/0017-datos-persoais.md) punto 8).
- **Que é:** barra mínima e área editable. Só títulos (`h2`, `h3`; na barra, unha «T» grande e outra pequena), negra, cursiva, lista, lista numerada e ligazón (https ou correo). Nada de cores, tamaños, imaxes, táboas nin HTML á man. Valor: HTML (`''` baleiro) por `ControlValueAccessor`; **o servidor sanéao sempre** cunha lista branca, así que o editor non é a barreira de seguridade.
- **Motor:** TipTap 3 (MIT, só o núcleo libre: sen extensións de pago nin nube), **en diferido**: só `rich-text-setup.ts` o importa e chega por `import()` co primeiro editor que se pinta. Nin entra na carga inicial nin o precarga o service worker (grupo `lazy`, `rich-text-setup-*.js`; `ngsw-check.mjs` compróbao). Sen estilos inxectados (CSP): os de ProseMirror van en `rich-text.css`, na folla da xestión.
- **Teclado e atallos:** a barra é un `toolbar` cunha soa parada de tabulador (frechas, Inicio e Fin). Ctrl/⌘+Alt+2 e +3 (títulos), Ctrl/⌘+B, +I, Ctrl/⌘+Maiús+8 e +7 (listas) e **Ctrl/⌘+K** (ligazón, que abre un campo baixo a barra: Intro engade, Esc cancela). Cada botón leva o atallo no tooltip (co rato e co foco; Esc agóchao, 1.4.13) e en `aria-keyshortcuts`.
- **Pegar:** só o texto. O formato chega só pola barra.
- **Accesibilidade:** a área é un `textbox` multiliña co nome que pon quen o usa («Texto de privacidade en galego»), `lang` do idioma que se escribe e `aria-describedby` coa axuda e o erro; o estado de cada botón en `aria-pressed`. A área usa `.f-prose`, a mesma tipografía que a páxina pública.
- **Fóra do compoñente:** as pestanas gl/es, a vista previa e «Volver ao texto de Furar» son da pantalla que o usa (`features/municipality/legal-text-editor.component.ts`).

**Non son de `shared/ui`:** a semana do horario (`week-editor`, `day-row`) e a lista de excepcións (`exception-list`) viven en `features/facility/schedule/`, porque saben de tempadas, da fila de festivos e das regras do servidor. Usan `furar-time-picker`, non `input type="time"`. Por iso desaparece o `furar-weekly-slots` desta guía; o mesmo co modal de reservas afectadas (`features/facility/affected-bookings/`), que compón `furar-dialog`, `ul[furarRowList]`, `[furarSegmented]` radio e casillas.

**Os que piden os indicadores** (fase 1). Estado a 2026-10-28: **por facer**. Os estilos van nunha folla á parte, `styles/indicadores.css`, que carga a ruta do panel, para non mover o orzamento inicial (D27).

| Compoñente | Onde se usa | Propósito e anatomía | Accesibilidade |
|---|---|---|---|
| `furar-chart` | — | **o único sitio con gráficos.** Envoltorio de Apache ECharts 6.1.0 ([ADR-0026](adr/0026-indicadores-fase-1.md)): carga ECharts co primeiro gráfico (`import()` diferido) e só liña, barras, mapa de calor, grella, tooltip e `visualMap`, en **canvas**; agarda ás fontes; colle as cores dos tokens (`--f-*` e a escala secuencial) e redebuxa ao cambiar o tema e o tamaño (`ResizeObserver`; as opcións reciben o ancho en px e `height` admite unha función do ancho: nas barras deitadas dos indicadores, o nome de cada instalación vai enriba da súa barra con todo o ancho e cada fila medra coas liñas do nome máis longo, 2026-11-18); sen animación con movemento reducido; `image()` dá o PNG para imprimir. Construtores puros das opcións (`lineArea`, `sparkline`, `hBars`, `stackedBars`, `heatmap`, `columns`, `decal`), sempre con tooltip `richText` e sen `style` | o canvas é `aria-hidden`; o nome e a alternativa danos quen o usa (`furar-chart-card`); nunca só cor: separador nas barras apiladas (un tramo estreito vai sen bordo propio, para que o separador non o coma), unha trama por serie que se confunde polo ton (`patternDecal`: raias diagonais na de alerta, puntos, raias horizontais) e a mesma mostra na lenda; no mapa de calor, as celas pechadas sen recheo e con bordo discontinuo; lenda en texto |
| `furar-chart-card` | — | card dun gráfico: título, frase de conclusión, «Táboa» (conmuta o gráfico e a súa táboa) e «Exportar»; o gráfico vai nun `<figure>` | `figure role="img"` con `aria-label` = título + conclusión; «Táboa» con `aria-pressed` e `aria-controls`, e o foco queda no botón |
| `furar-stat-tile` | — | cifra principal: rótulo, valor, cambio co período anterior (% ou puntos; sobe verde, baixa vermello, igual gris; sen anterior, texto) e minigráfica e «Ver evolución» sempre á vista; é un botón que abre o detalle, con bordo `line` (3:1) | `<button>` con `aria-haspopup="dialog"`; o cambio vai en texto, non só en cor nin en frecha; o nome é rótulo, valor, cambio, comparación e «Ver evolución» (as frechas, `aria-hidden`) |
| `furar-export-menu` | — | botón «Exportar» que abre debaixo un menú con PDF e CSV; a acción dáa quen o usa. CSV con `toCsv`/`downloadCsv` (UTF-8 con BOM, `;`, CRLF, comiñas, coma decimal, `'` diante de `= + - @`); PDF con `window.print()` e o alcance de `shared/print/` | `[furarMenu]` (APG menu button): frechas, Esc e Tab pechan e devolven o foco; pecha ao escoller |
| `furar-data-table` | — | táboa con columnas aliñadas (cifras á dereita, números tabulares) e o mesmo deseño en todos os anchos: se non cabe (o mapa de calor no móbil), móvese dentro da súa rexión con scroll propio; non se parte en tarxetas. `captionHidden` deixa o caption só para os lectores cando repite o título que xa se ve (tarxeta ou modal). No papel, sen scroll e con letra menor | `table` con `caption`, `th scope` (columnas e primeira cela de cada fila); sen scroll de páxina a 320 px; a rexión con scroll, só cando o hai, leva `role="region"`, o caption como nome e `tabindex="0"` |

**Os demais, cando os pida unha pantalla:**

| Compoñente | Propósito e anatomía | Accesibilidade |
|---|---|---|
| `furar-option-list` | a pregunta como título; filas de opción de 60 px, ✓ na escollida (tinte + bordo 2px), detalle; modo única (radiogroup) ou varias (casillas) | unha parada de tabulador e frechas no radiogroup; `fieldset`/`legend` |
| `furar-day-strip` | botóns día da semana + número + mes + punto de «hai contido», fixa baixo a cabeceira | tabulador itinerante, frechas, Inicio/Fin, `aria-pressed`, mes con `aria-live="polite"`; textos ≥ 14 px |
| `furar-value-list` | resumo rótulo → valor, cifras aliñadas, fila de total | `dl`/`dt`/`dd` |
| `furar-skeleton`, `furar-spinner` | esqueletos co tinte da marca (`sm`, `md`, `lg`, `circle`); un só spinner | `aria-hidden`; o anuncio vai no `status` de quen o usa |
| `furar-toast` | **feito (2026-11-02)**, ver «Toast de feito e de erro»; variante de erro (2026-11-16) | `role="status"`; pausa ao enfocar ou pasar o rato; o de erro, `role="alert"` e sen marchar só |
| `furar-progress` | barra con «3 de 10», indeterminada se non se sabe | `role="progressbar"` con valores, ou texto |
| `furar-section-divider` | divisor rotulado entre grupos dunha lista | é un título (`h2`/`h3`), non un `hr` con texto |

### Toast de «feito» e de erro (2026-11-02; erro, 2026-11-16)

**Regra:**
- O que sae ben (gardar, crear, cancelar, invitar, cambiar o rol…) anúnciase nun **toast de feito**.
- O erro dunha **acción solta sobre unha lista ou unha tarxeta** (saír dunha partida, desapuntarse dun evento, gardar os recordatorios, abrir unha entidade), onde non hai formulario no que pintalo, vai nun **toast de erro** que **non se pecha só**: o texto xenérico e o motivo real da API, se o hai («Non se puido saír da partida. A partida xa empezou.»).
- Os erros dun **formulario** e os da **carga dunha páxina** son un **aviso fixo** (`furar-callout`, `danger`) na páxina, onde se poden ler e arranxar. Un aviso que pide facer algo («o correo non se puido enviar, reenvíaa») tampouco é un toast: vai en `warn`.

- **Un só, para toda a app:** `furar-toast` (`shared/ui/toast/`) vive no shell; `ToastService.show(clave, parámetros)` para o que sae ben e `ToastService.error(clave, erro)` para o erro (pásase o erro capturado: del sae o motivo da API; sen motivo concreto, «Téntao de novo.»). O novo substitúe ao anterior, tamén entre navegacións: o toast non depende da páxina, así que «Concello creado» segue ao chegar a «Persoal» e desaparece cando sae «Invitación enviada».
- **Tempo:** o de feito desaparece só aos 6 s; o de erro queda ata pechalo (✕ ou `Esc`) ou ata a seguinte acción que saia ben, que o substitúe, para que non se perda sen ler. A conta párase mentres o rato ou o foco están enriba e, ao saír os dous, segue co que lle quedaba (WCAG 2.2.1). ✕ (nome accesible «Pechar o aviso») e `Esc` pechan; `Esc` fóra do toast só o pecha se non hai ningunha capa aberta, que se pecha primeiro. Se o foco estaba no toast ao pechalo, pasa a `#contido`.
- **Accesibilidade:** a rexión `role="status"` (`aria-live="polite"`) existe sempre, baleira, para que o lector anuncie o texto ao aparecer, coa palabra do ton («Feito:») oculta; nunca colle o foco. O de erro leva «Erro:» oculto e `role="alert"` (a rexión non o repite). A icona vai á altura do texto.
- **Aspecto:** superficie con bordo `divider`, radio `card` e `--shadow-overlay`; texto na tinta en negriña e a cor só na icona (verde no de feito, a icona de erro en `danger` no de erro), así vale en claro e escuro; flotante ao pé, centrado, enriba da barra inferior do móbil e da barra de accións. Ancho ata 32rem e nunca máis ca a xanela menos as marxes: sen scroll horizontal a 320 px. A entrada só se anima con `prefers-reduced-motion: no-preference`.
- **Textos:** os de feito, curtos e **sen punto final** (gl e es), aínda que leven dúas frases («Instalación creada. Complétaa para que a vexa a veciñanza»). O de erro é unha frase con punto («Non se puido saír da partida.») e detrás vai o motivo.
- No catálogo (`/catalogo-ui`), «Avisos en contexto» > «Gardar».

### Accións en curso e transicións das capas

**Regra:** mentres corre unha acción (gardar, cancelar, confirmar, exportar, reenviar, dar de baixa…), o botón premido queda ocupado e o resto de accións do mesmo contexto, bloqueadas ata que remata. Así non hai envíos dobres nin accións contrarias seguidas («Gardar» e «Cancelar» moi rápido). E mentres unha capa se abre ou se pecha, non se abre outra.

**Contexto** (`ActionScope`, `shared/ui/action/action-scope.ts`): un por páxina (`furarActionScope` no `<main>` do shell), un por capa (`DialogService` dálle o seu a cada diálogo e confirmación) e os que se poñan a man con `[furarActionScope]` (unha parte que non debe bloquear nin ser bloqueada polo resto). A barra superior queda fóra.

**Botón** (`furarAction`, que leva todo `[furarButton]`; noutros botóns, como o de `furar-chip-menu`, ponse a man):
- `[busy]="saving()"` marca o dono: `aria-busy="true"`, o aro e o rótulo en xerundio de quen chama. O nome accesible non cambia de significado.
- Mentres o contexto está ocupado, **todos** os botóns del (tamén o dono) levan `aria-disabled="true"` e o clic, o Intro dun formulario e un `requestSubmit` quedan sen efecto antes de chegar á feature. Nunca `disabled`: o foco quedaría no aire (2.4.3). O dono non se pinta como desactivado.
- Un segundo clic antes de que se pinte o `busy` tampouco pasa: o primeiro reserva o contexto ata o seguinte render.
- `[unavailable]` bloquea do mesmo xeito por outro motivo (a conta atrás de «Reenviar»).
- A capa ocupada (`furar-dialog`) non se pecha con Esc, picar fóra nin «Pechar».

**Sen botón** (o chip de estado, unha exportación dun menú): `scope.run(() => traballo)` ocupa o contexto mentres dura e non fai nada se xa hai outra acción. `furar-export-menu` recibe as exportacións como funcións (`[pdf]`, `[csv]`) e queda ocupado mentres corren.

**A feature** só di que botón leva a acción e mantén o seu estado (`saving`, `busy`); non repite o bloqueo nin pon `[disabled]="saving()"` nos outros. O final anúnciase co patrón de sempre: «Gardado» nun toast (`ToastService`) ou o erro no seu aviso fixo.

**Transicións das capas** (`shared/ui/view-transition.ts`):
- Unha capa que se abre desde un elemento (a tarxeta dunha cifra, o botón premido; `from` en `DialogService.open()` ou, se non se di, o botón ou ligazón que se acaba de premer) medra desde el cunha View Transition e, ao pechar por calquera vía, encolle de volta cara a el. Se a orixe xa non se ve, pecha no sitio. `from: null`, sen orixe. A folla do móbil (`sheet`) sae desde abaixo.
- Con `prefers-reduced-motion: reduce` ou sen a API, sen transición: a capa aparece cun fundido curto e pecha sen animación.
- Nada se superpón: a primeira tecla, pulsación ou texto escrito durante a transición remátaa (salta ao estado final) antes de chegar á páxina, e unha transición nova remata a anterior. A animación non bloquea nada máis do que dura. Abrir o menú «Exportar» co teclado coa animación de peche aínda en curso tumbaba o renderizador de Chromium (regresión en `web/e2e/accions-en-curso.e2e.ts`).

### Botóns sempre activos (2026-11-17)

**Regra:** un botón non se desactiva porque falte un dato. Queda activo e, ao premelo sen el, leva á persoa ao que falta: abre o campo (se é un panel, `furar-field-popover`, o calendario) ou pon o foco nel coa mensaxe de erro debaixo (patrón «foco ao primeiro erro», `focusFirstError`/`revealAndFocus` de `shared/ui/focus-error/`). Non se envía nada nin se pecha nada. Ningún botón leva bordo descontinuo.

- **Reservar**: «Continuar» / «Confirmar reserva» sen hora abre directamente a Hora, á vista, como ao pasar dun campo ao seguinte; sen erro. Se faltase outro campo (Que, Data, «Para quen»), abre ese (`ReserveFieldsComponent.openMissing()`, `FieldPopoverComponent.reveal()`). O mesmo en «Reservar para…» do mostrador; sen ninguén nas prazas, o foco vai á lista.
- **Paneis de datas:** «Aplicar» sen ningún día (tempadas, actividades) mostra o aviso no panel e leva o foco ao calendario; cun rango longo de máis (`furar-date-filter`), o aviso xa está á vista e o foco vai ao calendario. «Desde · Ata» do rexistro: o erro debaixo de cada campo baleiro e o foco no primeiro.
- **Listas:** «Cancelar/Manter as marcadas» sen ningunha marcada, o erro debaixo da barra e o foco en «Seleccionar todas»; «Engadir» fotos sen ningunha marcada, o aviso no pé e o foco na primeira foto; con máis das que caben, o foco na bandexa.
- **Publicar** (asistente) e **Crear/Aprobar a tempada** sen sesións: o foco vai ao aviso que di o que falta.
- **Frase e casiña** (`furar-confirm`, «Cambiar o rol»): véxase `furar-type-to-confirm` no catálogo.

**Si se desactivan** (texto `ink-soft`, bordo continuo, `cursor: not-allowed`): mentres se agarda ao servidor (o contexto ocupado de «Accións en curso», que vai con `aria-disabled`; unha foto subindo), nos límites (frechas dun carrusel ou dunha galería no extremo, zoom do mapa) e nos casos que non dependen dun dato que a persoa poida pór: unha conta de traballo que non reserva como veciña, unha instalación sen reserva por internet, «Engadir persoa» coas dez fichas e a conta atrás de «Reenviar».

### Prioridade 3: só se un fluxo acordado o pide

`furar-sheet` (folla inferior no móbil con asa e **botón de pechar visible**, `dirty` pregunta antes de descartar), unha folla de filtros no móbil (botón «Filtrar» con contador que abre folla; filtros activos como etiquetas borrables; a fila de filtros, `furar-filter-bar`, xa existe), `furar-bottom-nav` (3–4 destinos, `aria-current="page"`, área segura), `furar-info-popover`, `furar-stepper` («− N +» con topes). O menú e as pestanas pasaron á P2 coas instalacións, e `furar-weekday-picker` coas reservas.

**Non se fan** pezas de chat, deporte ou iOS (compositor de mensaxes, roda de selección, fila deslizable…), nin un «stepper» visual de pasos. **Gráficos, só con `furar-chart`** (2026-10-28, [ADR-0026](adr/0026-indicadores-fase-1.md)): ningunha feature importa ECharts nin debuxa gráficos pola súa conta.

## 9. Como se re-estilan as pantallas existentes

**Regras:** non cambian textos, claves, ids, roles, `for`/`aria-*`, a orde do foco, a estrutura de títulos nin o que fai a pantalla. Só cambian clases e o envoltorio visual (un `div` pode pasar a `furar-card`, un `ul` a `furar-row-list` se manteñen rol e nome). Ollo cos ficheiros que muta D28 (`custom-fields-admin.page.html`, `custom-field.component.html`…): se cambia o marcado que mutan, actualízase `scripts/probar-violacion.sh` no mesmo cambio.

| Pantalla | Cambio |
|---|---|
| Shell (`shell.component.html`) | fondo `page`; cabeceira `primary` co nome en `text-head`; selector de idioma P1-11; botóns de sesión `secondary` sobre `primary`; navegación de xestión con indicador da activa (bordo inferior 3px + negriña, `aria-current` como hoxe); toast de «feito» (`furar-toast`); pé en `text-support` `ink-soft`, ligazóns de 44 px. **2026-11-12:** o idioma, dentro do menú da conta en todos os anchos; o pé enteiro (marca, ligazóns legais, cookies, datos abertos, código fonte e licenzas) só nas portadas e nas páxinas legais, e nas interiores unha liña discreta coas legais, o código fonte e a licenza. **2026-11-16**: o pé enteiro en tres columnas con título («Legal»: Aviso legal, Privacidade, Cookies, Accesibilidade; «Servizo»: Datos abertos se os hai e «Código e licenzas», á sección «Software» do aviso legal, que enlaza o código, a licenza e as de terceiros; «Contacto»: o correo de soporte, se o hai) e o nome do servizo como texto; nas interiores, os catro legais e o soporte. Ligazóns do pé discretas (cor e peso do texto, subliñadas ao pasar o rato e co foco); cada lista nomeada polo seu título (`aria-labelledby`) |
| Portada común (`/` con varios concellos, [ADR-0030](adr/0030-portada-comun.md)) | o título e o buscador da portada (co filtro «Deporte», `furar-multi-select`). Sen filtros (2026-11-18): un `furar-carousel` por concello co seu nome e as tarxetas da portada do concello (`furar-facility-card`), «Buscan xente para xogar» e «Eventos e actividades»; botón flotante «Mapa» abaixo no medio (no móbil, enriba da barra inferior) que pon o mapa a toda a pantalla, con «Lista» para volver. Cun filtro: as tarxetas do listado público co concello diante do enderezo e, desde 900 px, o mapa ao lado, fixo baixo a barra; «Lista / Mapa» co `furarSegmented` de sempre |
| Inicio do concello | portada (decidida o 2026-10-26): título co concello, buscador (combobox e día), collaxe, tarxetas das instalacións publicadas (`soft`), «Así de fácil» e, debaixo, os datos públicos do concello (P1-4) |
| Alta de concello (`/plataforma/...`) | formulario en cards por pregunta (P1-4), P1-3, botón P1-2 ao pé |
| Axustes (`/xestion`) | P1-5; nome, idiomas, tema e campos en cards por pregunta; o editor do tema mostra os pares con P1-9/P1-6 ademais do número; P1-12 opcional |
| Campos personalizados (lista) | P1-5 coa acción «Novo»; activos e desactivados en P1-7 con meta en P1-9 e accións ao final; baleiro con P1-8; cargando con P1-8 |
| Editar campo | formulario en cards (P1-4); opcións en filas; vista previa en card `tint` |
| Formulario dinámico (`shared/custom-fields`) | só P1-3 (as plantillas están preto das 200 liñas: as clases, ás directivas) |
| Accesibilidade e privacidade | prosa a 70ch en `max-w-col`, títulos co sistema |
| Erro (`/erro/:status`) | P1-8 en modo `error` |
| Login de Keycloak | fóra desta fase; aliñar cores por `furar-tema.css` é opcional e próbase con D51/D52 |

**Orde de traballo** (cada paso deixa `scripts/rapido.sh web` en verde e o build sen avisos de orzamento):

1. P1-1: tokens, `deriveTokens` + spec, `base.css`, preferencias. As `@utility` actuais apuntan aos tokens: toda a web cambia sen tocar plantillas.
2. P1-2, P1-3, P1-10 e utilidades compatibles; `templates/module/web/` co mesmo (D45, `scripts/probar-plantilla.sh`).
3. P1-4…P1-9 e P1-11; pantalla a pantalla segundo a táboa.
4. P1-12 se se aproba.
5. Verificación: e2e en `gl`, `es` e `gl-mobile` (axe, 320 px, CSP), `scripts/probar-violacion.sh` se se tocaron ficheiros que muta, revisión con `prefers-contrast: more`, `forced-colors` e `prefers-reduced-motion` emulados no navegador, e cos temas límite do §4.4 cargados por API nun concello de proba.

## 10. Regras para revisar un PR de interface

- Ningún hex, `rgb()`, `px` de cor ou medida, `gray-*`, `text-[…]` nin `shadow-[…]` nas plantillas; só tokens.
- Ningún compoñente con dominio en `shared/ui/`.
- Toda transición con curva e duración de token e anulada baixo `prefers-reduced-motion`; hover dentro de `@media (hover: hover)`.
- Todo control: bordo ≥ 3:1, 44 px de toque, foco visible, nome accesible.
- Cristal só nas pezas do §6 e co seu fallback.
- Un compoñente novo: con spec, textos por claves `ui.*` en gl e es, e unha entrada no catálogo desta guía.
