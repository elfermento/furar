<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Seguridade

## Informar dunha vulnerabilidade

Non abras unha *issue* pública. Usa o aviso privado do repositorio: en <https://github.com/elfermento/furar>, **Security** → **Report a vulnerability** ([ligazón directa](https://github.com/elfermento/furar/security/advisories/new)). Só o ven as persoas mantedoras (mantedor: Santiago Herrero Padín). Nunha copia noutra forxa, o seu canal privado (en GitLab, unha *issue* marcada como **confidencial**).

Indica a versión (`FURAR_VERSION`), os pasos para reproducilo e o impacto. Non accedas a datos doutras persoas nin a servidores dos concellos para probalo: usa a instalación local (`docker compose --profile full up`).

| Prazo | Que |
|---|---|
| 3 días hábiles | Acuse de recibo |
| 10 días hábiles | Avaliación e severidade (CVSS) |
| 30 días | Corrección e release; aviso aos concellos coñecidos antes da publicación |

Publicamos un aviso de seguridade na forxa (en GitHub, GHSA) coa corrección e damos crédito a quen informe, se o desexa.

## Versións con soporte

A última versión menor publicada (`vX.Y.*`). Antes da 1.0, só a última release.

## Que fai Furar

Medidas e estado fronte ao ENS: [docs/ens.md](docs/ens.md). Decisións: [ADR-0007](docs/adr/0007-seguridade-pechada.md) (seguridade) e [ADR-0016](docs/adr/0016-ci-e-verificacion.md) (cadea de subministración: imaxes asinadas con cosign, SBOM, Trivy semanal).
