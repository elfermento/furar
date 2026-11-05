<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Verificación: última execución

2026-11-05, 08:01–08:45 CET, antes de etiquetar v0.1.1: despois do día de hoxe do calendario en hora de Madrid, dos proxectos de Compose separados (`furar-local` e `furar`), da clonación da etiqueta nun paso e das imaxes multiarquitectura coa espera do primeiro arranque en `instalar.sh`. Equipo de 8 GB, Colima con 6 GiB e 2 CPU, Java 25.0.2, Node 24.22.1, docker 29.5.2, compose 5.6.1, builder clásico (sen buildx). Todo en serie.

**Resultado: VERIFICADO.** `scripts/verificar.sh` (43 min 44 s, código 0) pasa as 62 filas da DoD e os auxiliares X01–X11, sen omitidas. Rexistros en `.verificar/logs/<ID>.log`.

## 1. Definición de feito, fila a fila

| ID | Resultado | Tempo | Evidencia |
|---|---|---|---|
| X01, D01, D02, D44, D07, D06, X02, X09, X11, X03, X08, X04 | ✓ | 0–10 s | |
| D54, D39, D43, D08 | ✓ | 0–9 s | actionlint e shellcheck `-S warning` sen achados |
| D21, D03, D04, D05, D49 | ✓ | 1–12 s | |
| D51, D52 | ✓ | 55 s, 25 s | Keycloak de usar e tirar |
| D47, D53, D46 | ✓ | 1–5 s | D46: todos os commits de `origin/main..HEAD` con `Signed-off-by` do autor |
| D10, D11 | ✓ | 5 s, 3 min 12 s | 894 unitarios + 599 IT, 0 fallos, erros e omitidos; `All coverage checks have been met` |
| D12–D20, D50 | ✓ | 0 s | lidos dos informes de D11 |
| D22–D25, D27 | ✓ | 0–17 s | |
| D26 | ✓ | 39 s | 1.242 tests; cobertura 93,9 / 87,5 / 90,5 / 95,7 % (sentenzas, ramas, funcións, liñas) |
| D28 | ✓ | 8 min 18 s | as 26 violacións (a–z) fallan coa súa regra e a árbore queda igual |
| D45 | ✓ | 2 min 14 s | |
| D29 | ✓ | 5 min 4 s | `--no-cache --pull`, en serie |
| D30–D33, X10 | ✓ | 0–44 s | |
| D34 | ✓ | 13 min 1 s | 261 listados = 261 correctos, 0 omitidos, 0 fallidos, 0 intermitentes (mínimo 258) |
| D35 | ✓ | 4 s | uso 1.191 MiB; build: antes 493 · pico 1.848 · despois 479 MiB (172 mostras) |
| D36, X06, D37, D38, D40, D41, D48 | ✓ | 0 s – 2 min 43 s | D40 50 s, D41 2 min 43 s, D48 1 min 35 s |
| D42 | **VERIFICADO** | 43 min 44 s | |

Ademais: `scripts/sen-marcadores.sh --release` sen marcadores, e a exportación pública en seco (`scripts/publicar-v1.sh` cunha etiqueta temporal) pasa as gardas de rutas e termos e para, como se espera, na comprobación de `CHANGELOG.md` dunha etiqueta que non saíu de `scripts/preparar-release.sh`.

## 2. Fallos atopados e arranxados nesta rolda

- **D28, caso g (umbral de cobertura ao 99 %)**: dúas execucións seguidas fallaron por unha proba web e non polo umbral. A primeira foi unha proba con axe que pasou dos 20 s baixo carga (intermitente). A segunda, `readChartTokens` en `chart-options.spec.ts`, leu `#123456` en vez da cor por defecto: as probas unitarias corren sen illamento (un só documento por traballador) e o tema que `current-municipality.spec.ts` aplica a `<html>` chegaba, segundo a orde, á seguinte. Arranxo: `test(web): clear the theme on <html> after each unit test` (`web/src/test-setup.ts` quita o estilo de `<html>` despois de cada proba). Despois, a execución completa desde o principio, a desta táboa.
