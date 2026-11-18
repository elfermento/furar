<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0003 · Idioma do código, da documentación e da app

- **Estado:** aceptada (decidida polo mantedor) · 2026-10-22
- **Revisada 2026-11-11 ([ADR-0034](0034-datos-abertos.md)):** excepción consciente: os nomes de columnas e os códigos dos ficheiros de datos abertos van en galego e sen acentos, iguais con `lang=gl` e `lang=es`; os códigos de deportes e servizos son os do dominio.

## Contexto
Os concellos e quen os atende traballan en galego; a app ten que estar en galego e castelán; o código vano ler programadores de calquera sitio e as librerías están en inglés.

## Decisión
- **Código en inglés:** paquetes, clases, táboas, columnas, endpoints, módulos, claves de tradución e códigos de erro (`municipality`, `customfields`, `facility`, `booking`, `municipality.slug-taken`).
- **Documentación do repo en galego:** README, ADR, arquitectura, manual e CONTRIBUTING.
- **Idiomas obrigatorios por instalación**: `furar.languages.required` (`FURAR_REQUIRED_LANGUAGES`, por defecto `gl`). Nunha instalación galega o galego é obrigatorio en todo concello (Lei 3/1983 de normalización lingüística, Lei 5/1997 de Administración local de Galicia): un concello só en castelán → 422 `municipality.language-required`. Outra comunidade pon o seu (`eu`, `ca`, `va`…) ou ningún.
- **Catálogo de idiomas:** hoxe `gl` e `es` (enum `Language`, `check` de `municipality.languages`, `label` das definicións de campos esixe os dous). Engadir un idioma é: valor no enum, `messages_<xx>.properties`, `web/src/i18n/<xx>.json` e unha migración que amplía os `check`; o resto do código non cambia.
- **App en galego (por defecto) e castelán,** textos en ficheiros (`web/src/i18n/{gl,es}.json`, `api/src/main/resources/messages_{gl,es}.properties`); ningún texto visible no código.
- **Commits e CHANGELOG:** Conventional Commits en inglés (`feat(municipality): add theme endpoint`).
- **Glosario** gl/es ↔ en en [`docs/glosario.md`](../glosario.md); un termo novo de dominio entra alí antes que no código.
- **Concello = `municipality`** en código.
- **Rutas visibles da web en galego** (`/naron/instalacions`, `/naron/xestion`): son texto para o veciño, non código. A API segue en inglés.

## Consecuencias
- O código lese sen traducir e encaixa coas librerías; a documentación lese en galego.
- Hai que manter o glosario: revísase en cada PR que engade un termo.

## Alternativas descartadas
- **Código en galego ou castelán:** mestura con inglés das librerías (`getConcello()`), peor para contribuír desde fóra.
- **Documentación en castelán:** os concellos que a usan traballan en galego.
