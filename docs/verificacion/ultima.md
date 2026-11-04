<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Verificación: última execución

2026-11-04, 22:55–23:57 CET, antes de etiquetar a v0.1.0. Equipo de 8 GB, Colima con 6 GiB e 2 CPU (5.910 MiB para Docker), Java 25.0.2, Node 24.22.1, docker 29.5.2, compose 5.6.1, builder clásico (sen buildx). Todo en serie.

**Resultado: VERIFICADO.** `scripts/verificar.sh --estrito` (62 min 8 s, código 0) pasa as 62 filas da DoD e os auxiliares X01–X11, sen omitidas. Rexistros en `.verificar/logs/<ID>.log`.

## 1. Definición de feito, fila a fila

| ID | Resultado | Tempo | Evidencia |
|---|---|---|---|
| X01, D01, D02, D44, D07, D06, X02, X09, X11, X03, X08, X04 | ✓ | 0–27 s | |
| D54, D39, D43, D08 | ✓ | 2–17 s | actionlint e shellcheck `-S warning` sen achados |
| D21, D03, D04, D05, D49 | ✓ | 3–31 s | |
| D51, D52 | ✓ | 1 min 43 s, 2 min 12 s | Keycloak de usar e tirar |
| D47, D53, D46 | ✓ | 4–28 s | D46: todos os commits de `origin/main..HEAD` con `Signed-off-by` do autor |
| D10, D11 | ✓ | 6 s, 6 min 2 s | 873 unitarios + 592 IT, 0 fallos, erros e omitidos; `All coverage checks have been met` |
| D12–D20, D50 | ✓ | 6–30 s | `--estrito`: comando literal de cada fila |
| D22–D25, D27 | ✓ | 1–16 s | |
| D26 | ✓ | 43 s | 1.223 tests; cobertura 94,0 / 87,7 / 90,5 / 95,7 % (sentenzas, ramas, funcións, liñas) |
| D28 | ✓ | 12 min 5 s | as 26 violacións (a–z) fallan coa súa regra e a árbore queda igual |
| D45 | ✓ | 3 min 24 s | |
| D29 | ✓ | 8 min 28 s | `--no-cache --pull`, en serie |
| D30–D33, X10 | ✓ | 0–45 s | |
| D34 | ✓ | 12 min 45 s | 261 listados = 261 correctos, 0 omitidos, 0 fallidos, 0 intermitentes (mínimo 258) |
| D35 | ✓ | 3 s | uso 1.173 MiB; build: antes 467 · pico 1.839 · despois 478 MiB (227 mostras) |
| D36, X06, D37, D38, D40, D41, D48 | ✓ | 0 s – 2 min 39 s | |
| D42 | **VERIFICADO** | 62 min 8 s | |

## 2. Fallos atopados e arranxados nesta rolda

Desde a anterior execución completa en verde entraran uns 35 commits sen o carril completo. Fallaron e arranxáronse:

| Fallo | Fila | Arranxo |
|---|---|---|
| A marca do pé leva a `/aviso-legal#software`, pero o título perdía o `id` (a lista de anclas permitidas só tiña a de «Persoal») | D34 | o produto: ancla `software` compartida e permitida na páxina legal (`legal-pages.spec.ts` compróbao) |
| `NG0205` sen capturar: a barra cargaba o selector de concello despois de destruírse | D26 | o produto: a barra ignora o módulo se xa non existe |
| `management-styles.spec.ts` dependía da orde (outro spec deixaba a folla no documento compartido) | rápido, D26 | o spec limpa o `<link>` antes |
| Tests con axe e cargas á parte pasaban dos 5 s por test e do 1 s de `vi.waitFor` co equipo cargado; D28 (g) fallaba por tempo, non polo umbral | D28 | marxe en `test-setup.ts`: 20 s por test e 5 s en `vi.waitFor` |
| E2E escritos sen executar contra o comportamento actual: visibilidade por defecto dun campo da reserva («Só o persoal»), un oco de pádel que non existe (10:00), un valor repetido entre proxectos con campos desactivados que seguen á vista, «Ir» leva a `legal/privacidade`, pechar sesión volve á raíz, un nome que coincide co rol «Recepción», unha opción «Personal» que tamén casaba con «Campos personalizados» | D34 | os tests |
| axe medía o contraste dunha capa entrando e un clic polas coordenadas caía fóra do menú | D34 | `settleAnimations` en `fixtures.ts` (agarda ás animacións con fin; non ás infinitas nin ás do scroll) |

## 3. Incidencias do contorno (non son do repositorio)

- O disco da VM de Docker encheuse (40 GB, 100 %) con imaxes sen etiqueta de builds anteriores: a VM parou a metade do e2e e PostgreSQL non arrincaba (`No space left on device`). `docker image prune` deixou 15 GB libres.
- Unha execución de D29 fallou unha vez co builder clásico (`failed to open writer: lease does not exist`); a seguinte pasou sen cambios.
