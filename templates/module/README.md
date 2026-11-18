<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Plantilla de módulo

Non se edita a man nin se compila: `scripts/novo-modulo.sh` cópiaa substituíndo os marcadores e `scripts/novo-modulo.sh --remove` desfaina. Guía: [`docs/como-engadir-un-modulo.md`](../../docs/como-engadir-un-modulo.md).

| Marcador | Exemplo (`facility`) | Uso |
|---|---|---|
| `@module@` | `facility` | paquete Java, prefixo dos códigos de erro |
| `@Name@` / `@name@` | `Facility` / `facility` | clases / variables e claves i18n |
| `@name-kebab@` / `@name_snake@` | `facility` | ficheiros da web / táboa |
| `@NAME@` | `FACILITY` | permiso `@NAME@_MANAGE` |
| `@Names@` / `@names@` | `Facilities` / `facilities` | caso de uso de lista / URL e etiqueta OpenAPI |
| `@route@` | `instalacions` | ruta visible (galego) |
| `@ts@` | `202610221530` | versión Flyway |
| `@TARGET@` | `FACILITY` | código do destino de campos personalizados, rexistrado por `@Name@CustomFieldTarget` (só con `--custom-fields`) |

Variantes (`--custom-fields TARGET`): unha liña que remata en `// @cf` ou `-- @cf` só se xera con campos personalizados, e unha en `// @nocf` só sen eles; os bloques van entre `// @cf{` e `// @cf}` (ou `@nocf{`/`@nocf}`; en HTML, `<!-- @cf{ -->` e `<!-- @cf} -->`), e os ficheiros `*.cf` só se crean con campos (sen a extensión). Pon o elemento opcional **antes** do último dunha lista de parámetros, para que as comas queden ben nas dúas variantes. `scripts/probar-plantilla.sh` xera e proba as dúas.

`fragments/` son os anacos que se insiren en ficheiros existentes (permiso, rutas, textos).
