<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Gobernanza

## Quen mantén Furar

| Papel | Quen | Que fai |
|---|---|---|
| Mantedor principal | Santiago Herrero Padín | Decide en último termo, revisa e fusiona, publica versións |
| Persoas mantedoras | (aberto) | Revisan e fusionan na súa área; entran por proposta do mantedor e acordo das demais |
| Concellos usuarios | quen o despregue | Piden funcións, informan de erros, validan cambios na súa instalación |

O código é EUPL-1.2: calquera pode facer un fork. O nome «Furar» e a forxa canónica son do proxecto.

## Como se decide

- **Cambios do día a día:** PR con `ci-ok` en verde, Conventional Commits con DCO e revisión de outra persoa mantedora (ou do mantedor principal).
- **Decisións de arquitectura ou de produto que custa desfacer:** ADR curta en [`docs/adr/`](docs/adr/) (Contexto, Decisión, Consecuencias, Alternativas descartadas). Ábrese como PR e discútese alí; acéptase por consenso das persoas mantedoras e, se non o hai, decide o mantedor principal e queda escrito o porqué.
- **Campos personalizados → campo núcleo** ([ADR-0011](docs/adr/0011-campos-personalizados.md)): se tres ou máis concellos definen o mesmo campo, ou un campo se usa en regras de negocio, indicadores ou CSV comúns, proponse promovelo a columna tipada cunha ADR e unha migración que copia os valores do JSON.
- **Seguridade:** [SECURITY.md](SECURITY.md). **Conduta:** [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Versións

[SemVer](https://semver.org/lang/gl/) desde a 1.0; antes, `0.x` pode romper compatibilidade e dío no [CHANGELOG](CHANGELOG.md). Cada versión é unha etiqueta `vX.Y.Z` que pasa toda a verificación (D42) e publica imaxes por digest ([ADR-0016](docs/adr/0016-ci-e-verificacion.md)). Soporte: a última versión menor.

## Reutilización por outras administracións

Como adoptalo, contratalo e rexistralo: [`docs/reutilizacion.md`](docs/reutilizacion.md). Metadatos para catálogos de software público: [`publiccode.yml`](publiccode.yml).
