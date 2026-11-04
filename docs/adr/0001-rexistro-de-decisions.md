<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0001 · Rexistro de decisións en ADR curtos

- **Estado:** aceptada · 2026-10-22 · revisada o 2026-11-04
- **Revisada 2026-11-04:** as ADR revísanse no sitio cunha nota datada, en vez de substituílas.

## Contexto
Furar preséntase a un xurado que valora comprensión, viabilidade e reutilización, e vano estender outros concellos e equipos. Unha decisión sen o seu porqué refaise ou rómpese.

## Decisión
- Cada decisión de arquitectura, un ficheiro `docs/adr/NNNN-titulo-curto.md`, en galego e compacto: o que se decide e o porqué, sen historia que xa estea no código ou no git.
- Seccións obrigatorias: **Contexto**, **Decisión**, **Consecuencias**, **Alternativas descartadas** (comprobado por D44 en [`definicion-de-feito.md`](../definicion-de-feito.md)).
- Estado: proposta · aceptada · substituída por ADR-NNNN.
- **Revisión no sitio:** cando a decisión cambia, edítase a mesma ADR e engádese unha liña «**Revisada AAAA-MM-DD:** que cambiou e por que» baixo o estado; o que se deixa de facer pasa a «Alternativas descartadas». Unha ADR nova só se a decisión é outra distinta; entón a vella queda «substituída por ADR-NNNN».
- Decisións que valida o mantedor márcanse «**Valida o mantedor**»; ao validala, unha liña datada co valor aceptado.

## Consecuencias
- O porqué queda xunto ao código e é revisable nun PR.
- Custo: 10–15 minutos por decisión.

## Alternativas descartadas
- **Wiki ou documento aparte:** sepárase do código e desactualízase.
- **Formato MADR completo:** demasiado longo para o ritmo do proxecto.
- **Unha ADR nova por cada cambio (substituír en vez de revisar):** a decisión vixente quedaba repartida entre varias ADR e había que ler a cadea enteira; a historia xa está no git.
