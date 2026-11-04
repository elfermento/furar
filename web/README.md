<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Furar · web

Angular 22 (standalone, zoneless, signals, `OnPush`), Tailwind 4 + CDK, Transloco (gl/es). Decisións: [ADR-0014](../docs/adr/0014-web.md), [ADR-0009](../docs/adr/0009-contrato-api-cliente-xerado.md), [ADR-0007](../docs/adr/0007-seguridade-pechada.md).

## Comandos

Antes: `source ../scripts/entorno.sh` (Node 24).

| Que | Comando |
|---|---|
| Instalar | `npm ci` |
| Desenvolver (API en :8081 e Keycloak en :8082, perfil `dev`) | `npm start` → http://localhost:4200/naron |
| Cliente da API desde `../api/openapi.json` | `npm run api:generate` (xa o fan `start`, `build`, `test` e `lint`) |
| Lint (TS e modelos, accesibilidade, zonas) e formato | `npm run lint -- --max-warnings 0 && npm run format:check` |
| Textos gl/es e códigos de erro | `npm run i18n:check` |
| Tests con cobertura (umbral 90 % liñas, 87 % ramas) | `npm test -- --watch=false --coverage` |
| Build de produción (orzamento 350 kB) | `npm run build` |
| E2E (perfil `full` levantado, http://localhost:8080) | `../scripts/e2e-web.sh 198` (D34: 0 omitidos e tantos tests como `--list`) |

## Estrutura

```
src/app/
  api/        xerado (non se versiona; só o usan core/ e *.store.ts)
  core/       auth, i18n, erros, tema, concello actual, layout, PWA
  shared/     ui/ (campos, avisos) e custom-fields/ (formulario dinámico e editor)
  features/   municipality/, customfields/ (unha feature non importa outra)
src/i18n/     gl.json, es.json (claves en inglés)
e2e/          Playwright + axe
```

As fronteiras compróbaas `eslint.config.js` (regra `furar/zones`).

## Como funciona

- **Contrato:** `ng-openapi-gen` xera o cliente desde `../api/openapi.json`; con `strictTemplates`, un campo renomeado na API rompe o build, tamén nos modelos (`../scripts/probar-contrato.sh`).
- **Sesión (OIDC código + PKCE):** `oidc-client-ts` cárgase só ao iniciar sesión ou se había sesión. Tokens só en memoria; o `state` e o `code_verifier` de PKCE en `sessionStorage`. Ao recargar, se había sesión, a web vai a Keycloak con `prompt=none` e volve autenticada sen pedir contrasinal; a renovación usa o refresh token (en memoria). Ningún rol sae do token: o de persoal (`GET …/me/staff`) e a administración da plataforma (`GET /api/v1/me/platform-access`, ADR-0028) pídense á API e só serven para a interface: decide a API.
- **Erros:** `problemInterceptor` converte as respostas RFC 9457 en `ApiProblem` e traduce o `code` (`errors.<code>` en gl/es). `i18n:check` esixe un texto por cada código do contrato.
- **Idioma:** preferencia gardada → `navigator.languages` → galego. Non vai no URL. Cambia `<html lang>` e `Accept-Language`.
- **Tema:** o resolver de `/:concello` carga o concello e pon as súas cores como variables CSS antes de pintar. O editor do xestor comproba o contraste en vivo (a API tamén).
- **Campos personalizados:** `<furar-custom-fields-form>` pinta calquera conxunto de definicións (7 tipos, grupos con `fieldset`, axuda e erros ligados con `aria-describedby`, foco ao primeiro erro). O xestor defíneos en `/:concello/xestion/campos` con vista previa en gl e es.
- **PWA:** manifesto e service worker só para a app (nada de `/api` nin `/auth`), aviso de «sen conexión».
- **CSP:** `security.autoCsp` deixa os hashes en `index.html`; `../deploy/caddy/csp.sh` xera a cabeceira no build da imaxe.

## Imaxe

`Dockerfile` (contexto: a raíz do repo): Node 24 compila e Caddy serve, usuario 10001, `HEALTHCHECK` en `/healthz`.
