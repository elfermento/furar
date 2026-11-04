<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Furar

Plataforma libre para que os concellos xestionen e os veciños reserven as instalacións deportivas municipais. En galego e castelán, accesible (obxectivo WCAG 2.1 AA; declaración: parcialmente conforme, [`docs/accesibilidade.md`](docs/accesibilidade.md)) e para varios concellos nunha mesma instalación.

> Estado: en desenvolvemento para o I Certame GOBTEC de Software Libre (AGASOL). Feito: concellos, campos personalizados, instalacións e horario, reservas, avisos por correo, indicadores e persoal (fase 1); estado detallado en [`docs/arquitectura.md`](docs/arquitectura.md#estado).

## Probalo

Precisas Docker (con Compose) e ~4 GB de memoria libre.

Proba local (constrúe as imaxes, unha detrás doutra para caber en memoria):

```sh
git clone https://github.com/elfermento/furar.git && cd furar
for s in api web keycloak; do docker compose --profile full build $s; done
docker compose --profile full up -d --wait
```

Abre http://localhost:8080/naron (só desde este equipo: os contrasinais de demo son públicos). Usuarios de demo en [`deploy/keycloak/README.md`](deploy/keycloak/README.md). Para entrar como faría unha veciña con Cl@ve: «Entrar» → «Cl@ve (simulada, só demo)», NIF `00000000T`, contrasinal `Furar-demo-2026` (IdP SAML 2.0 de demo; a pasarela real dáse de alta como en [`docs/instalacion.md` §12](docs/instalacion.md#12-clve-e-chave365)).

**Cl@ve e Chave365.** Listo: o acceso por un provedor de identidade SAML 2.0 en Keycloak, probado de punta a punta coa «Cl@ve simulada» da demo (sen importar o NIF), e a guía de alta ([ADR-0019](docs/adr/0019-autenticacion-publica-clave.md)). Para a pasarela real, o organismo (o concello ou a Deputación) solicita a adhesión (Cl@ve ao SGAD; Chave365 á Amtega), achega o seu certificado, intercambia os metadatos e dá de alta o provedor en Keycloak ([`docs/instalacion.md` §12](docs/instalacion.md#12-clve-e-chave365)). Falta, e queda como débeda, a extensión de Keycloak que envía os atributos eIDAS e o nivel de garantía que pide Cl@ve; faise co contorno de probas da pasarela.

Instalación nun concello, coas imaxes publicadas e sen compilar: `scripts/instalar.sh` pregunta o dominio, o correo de administración e o SMTP (servidor, cifrado, porto, usuario e remitente), xera os segredos, envía un correo de proba e arrinca. Guía en [`docs/instalacion.md`](docs/instalacion.md).

## Que hai dentro

| Cartafol | Que é |
|---|---|
| `api/` | API en Java 25 + Spring Boot 4 + Spring Modulith; contrato en `api/openapi.json` |
| `web/` | Web en Angular 22 + Tailwind |
| `deploy/` | Keycloak, PostgreSQL e produción |
| `docs/` | [Arquitectura](docs/arquitectura.md), [decisións (ADR)](docs/adr/), [definición de feito](docs/definicion-de-feito.md), [como engadir un módulo](docs/como-engadir-un-modulo.md), [glosario](docs/glosario.md), [instalación](docs/instalacion.md), [reutilización](docs/reutilizacion.md), [datos persoais](docs/datos-persoais.md) |
| `scripts/` | `rapido.sh` (carril rápido), `verificar.sh` (todas as comprobacións), `novo-modulo.sh` (módulo novo), `probar-violacion.sh` (as gardas fallan), `probar-plantilla.sh`, `probar-commits.sh` |
| `templates/` | Plantilla de módulo (API e web) que usa `novo-modulo.sh` |

## Comprobar a calidade

En cada cambio, o carril rápido (sen Docker, < 3 min): `scripts/rapido.sh`. Antes dunha etiqueta, todo:

```sh
scripts/verificar.sh
```

Executa formato, tests con PostgreSQL real, regras de arquitectura, illamento entre concellos, seguridade, contrato da API, lint e tests da web, accesibilidade en gl e es, licenzas e arranque completo. A CI (`.github/workflows/ci.yml` ou `.gitlab-ci.yml`) chama ao mesmo script. Código, *issues* e releases (imaxes en `ghcr.io/elfermento`, asinadas): <https://github.com/elfermento/furar> ([ADR-0016](docs/adr/0016-ci-e-verificacion.md)) ([débeda](docs/definicion-de-feito.md#débeda-coñecida-non-agochada)).

## Desenvolver

Ver [`CONTRIBUTING.md`](CONTRIBUTING.md#comandos) (comandos e convencións) e [`docs/como-engadir-un-modulo.md`](docs/como-engadir-un-modulo.md).

## Licenza

- Código: [EUPL-1.2](LICENSES/EUPL-1.2.txt).
- Documentación e datos de exemplo: [CC0-1.0](LICENSES/CC0-1.0.txt).
- Partes de terceiros: código de conduta ([CC-BY-4.0](LICENSES/CC-BY-4.0.txt), Contributor Covenant), Maven Wrapper e traducións de Keycloak ([Apache-2.0](LICENSES/Apache-2.0.txt)), iconas Material Symbols ([Apache-2.0](LICENSES/Apache-2.0.txt); só os camiños SVG que se usan, en `web/src/app/shared/ui/icon/`), tipografía Atkinson Hyperlegible Next ([OFL-1.1](LICENSES/OFL-1.1.txt); na web chega por npm, `@fontsource/atkinson-hyperlegible-next`, e no tema de Keycloak van os seus `.woff2` en `deploy/keycloak/themes/furar/login/resources/fonts/`). Detalle en [`REUSE.toml`](REUSE.toml) e, na web instalada, en `/3rdpartylicenses.txt`.
- © 2026 Santiago Herrero Padín e colaboradores de Furar. Cumpre [REUSE](https://reuse.software).

Contribucións con [DCO](https://developercertificate.org) (`git commit -s`): [CONTRIBUTING](CONTRIBUTING.md) · [gobernanza](GOVERNANCE.md) · [código de conduta](CODE_OF_CONDUCT.md) · [seguridade](SECURITY.md) · [cambios](CHANGELOG.md) · [`publiccode.yml`](publiccode.yml).
