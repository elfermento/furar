<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->
# Notas de cambios no realm

Keycloak só importa `deploy/keycloak/import/furar-realm.json` na primeira instalación (`--import-realm` non sobrescribe un realm que xa existe). Por iso, cando unha versión cambia o realm, cómpre un ficheiro `VERSIÓN.md` aquí (p. ex. `v0.2.0.md`) cos pasos que ten que facer a man quen xa ten Furar instalado: que crear ou cambiar en *Clients*, *Roles*, *Authentication* ou *Realm settings*, e como comprobalo.

`scripts/notas-release.sh` compárao coa release anterior: se `deploy/keycloak/import/` cambiou e falta o ficheiro, non se publica a release. O contido vai nas notas da release, na sección «Cambios no realm de Keycloak». Decisión e alternativa (unha ferramenta que aplica o realm en cada arranque) en [ADR-0015](../adr/0015-despregue.md).

A primeira release, [`v0.1.0.md`](v0.1.0.md), non ten pasos: toda instalación importa o realm desde cero. Desde ela, cada versión que cambie `deploy/keycloak/import/` trae o seu `VERSIÓN.md`.

As notas con data (`AAAA-MM-DD-tema.md`) son probas técnicas feitas antes dun cambio do realm, coa súa evidencia e o JSON validado. Non son notas de versión, `scripts/notas-release.sh` non as le e as anteriores á v0.1.0 xa van no realm que esta importa.
