<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Como adopta Furar un concello ou unha Deputación

Unha páxina para quen decide. Detalle técnico en [`instalacion.md`](instalacion.md); datos persoais en [`datos-persoais.md`](datos-persoais.md).

## Modelos de despregue

| Modelo | Para quen | Quen opera | Datos |
|---|---|---|---|
| **A. Concello só** | Concellos medianos e grandes con servizo TIC | O concello ou unha empresa contratada | Unha instalación, un concello |
| **B. Deputación para varios concellos** | Concellos pequenos (a maioría en Galicia) | A Deputación, como asistencia aos municipios | Unha instalación, cada concello illado (`/<concello>/…`) |
| **C. Servizo dunha empresa** | Quen non quere operar nada | Unha empresa que o aloxa na UE | Igual ca B; a empresa é encargada do tratamento |

**Deputación para varios concellos (B): que ve e como.** Non hai un rol propio da Deputación: quen opera a instalación é a súa administración da plataforma. Con ela dá de alta os concellos e configúraos enteiros (datos, aparencia, datos legais, instalacións, campos e persoal), sen ver as reservas nin os datos da veciñanza ([ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9). Ve as cifras de todos os concellos en «Indicadores da plataforma» (reservas, ocupación media, horas reservadas e cobrado, en total e por concello, coas persoas distintas de cada un; exportable en CSV e PDF; sen datos de ningunha persoa, [ADR-0026](adr/0026-indicadores-fase-1.md)). Se ademais leva a xestión diaria dalgún concello pequeno, faise xestora del desde «Persoal» con «Engadirme como xestor»: desde ese momento ve as súas reservas coma calquera xestor, e queda anotado no rexistro do concello e no da plataforma ([ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 10). Unha mesma conta pode ser xestora de varios concellos e cambia entre eles no menú da conta.

**Servizo dunha empresa ou servidor compartido (C, e tamén B): como entra un concello.** Un concello **non pode darse de alta só**: a plataforma non sabe se quen enche un formulario fala polo concello, e antes de tratar datos da súa veciñanza ten que estar asinado o encargo de tratamento (RGPD art. 28). Por iso o circuíto é sempre este ([ADR-0042](adr/0042-es-un-concello.md)):

1. **O concello solicítao** desde «¿Es un concello?» (a franxa do final da portada común ou a ligazón do pé da portada común, se o despregue ten `FURAR_JOIN_REQUEST_EMAIL`, [instalación §4](instalacion.md#4-configuración-avanzada)) ou por correo. A solicitude chega por correo a quen opera o servizo, no idioma no que se encheu; na plataforma só queda se o correo non sae, para reintentala, e como moito 7 días.
2. **Quen opera o servizo comproba** que a solicitude é do concello (respondendo ao correo ou por outra canle oficial) e **asínase a adhesión co encargo de tratamento** (RGPD art. 28; modelo en [`datos-persoais.md`](datos-persoais.md)).
3. **A administración da plataforma dá de alta o concello** (ou importa o seu ficheiro, se vén doutra instalación, [ADR-0018](adr/0018-portabilidade.md)) e **invita a súa xestión** desde «Persoal» ([ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md)).
4. **A xestión do concello configura o resto:** datos, aparencia, datos legais, instalacións, campos e persoal.

En todos o software é o mesmo e sen licenzas que pagar (EUPL-1.2). Un concello pode pasar de B a A levando os seus datos ([ADR-0018](adr/0018-portabilidade.md)).

## Que custa

- **Servidor:** 2 vCPU, 4 GB de RAM e 40 GB de disco abondan para varios concellos pequenos (a instalación completa cabe en 3,5 GB, D35). Orde de magnitude: decenas de euros ao mes nun provedor da UE, ou un servidor virtual xa existente da Deputación.
- **Posta en marcha:** instalación, dominio, correo, temas e formación do persoal. É un traballo de días, non de meses: a instalación é un comando e un `.env` (D40, D48).
- **Mantemento:** actualizar a cada versión (`git checkout` + `docker compose pull`, con copia antes), vixiar as copias e atender incidencias.

## Como se contrata

- **Concello só:** contrato de servizos de instalación e mantemento (contrato menor se non supera 15.000 € sen IVE, Lei 9/2017 art. 118) ou dentro dun contrato TIC existente. O software non se compra.
- **Deputación:** servizo de asistencia técnica aos municipios (Lei 7/1985, art. 36) mediante convenio ou adhesión do concello; a Deputación pode operalo con medios propios ou contratalo.
- O contrato inclúe sempre o **encargo de tratamento** (RGPD art. 28; modelo en [`datos-persoais.md`](datos-persoais.md)) e a obriga de publicar as modificacións (EUPL-1.2).

## Quen mantén o software

O proxecto ([GOVERNANCE.md](../GOVERNANCE.md)): versións etiquetadas que pasan toda a verificación, imaxes por digest, avisos de seguridade ([SECURITY.md](../SECURITY.md)) e cambios no [CHANGELOG](../CHANGELOG.md). Un concello pode corrixir ou ampliar pola súa conta (é software libre) e propoñer o cambio para todos.

**Contribucións upstream:** Keycloak non trae galego; Furar mantén a tradución completa das pantallas de acceso, conta e correos (`deploy/keycloak/themes/furar/*/messages/messages_gl.properties`). Pendente: propoñela a Keycloak polo seu Weblate, para que calquera administración galega a teña sen Furar e para deixar de mantela nós.

## Rexistro en catálogos públicos

- **Centro de Transferencia de Tecnoloxía (CTT) da Administración Xeral do Estado:** as administracións que teñan aplicacións propias poden declaralas para a súa reutilización (Lei 40/2015, arts. 157 e 158). O concello ou a Deputación que o despregue dáo de alta como solución reutilizada; o proxecto achega `publiccode.yml` cos metadatos.
- **Mancomún (Xunta, Amtega):** publicación en A Eira (espello da v1 pública) e no catálogo de software libre de Galicia ([ADR-0016](adr/0016-ci-e-verificacion.md)).
- **Metadatos:** [`publiccode.yml`](../publiccode.yml) (estándar europeo para catálogos de software público).

## Interoperabilidade (ENI)

O Esquema Nacional de Interoperabilidade (RD 4/2010) e a súa Norma Técnica de Catálogo de estándares piden estándares abertos. Así os usa Furar:

| Estándar | Uso en Furar | Norma ou referencia |
|---|---|---|
| OpenID Connect / OAuth 2.0 | Acceso de persoal e veciñanza (Keycloak) | ENI, NTI Catálogo de estándares |
| SAML 2.0 | Cl@ve e outras pasarelas de identidade ([ADR-0019](adr/0019-autenticacion-publica-clave.md)) | eIDAS (Reg. UE 910/2014), Cl@ve |
| OpenAPI 3 + JSON | Contrato público da API `/api/v1` | NTI Catálogo de estándares |
| RFC 9457 (Problem Details) | Formato dos erros, con código traducible ([ADR-0008](adr/0008-erros-rfc9457.md)) | IETF |
| JSON `furar-export` | Exportación e importación de todos os datos dun concello ([ADR-0018](adr/0018-portabilidade.md)) | ENI art. 17 (reutilización), RGPD art. 20 |
| CSV e iCalendar (RFC 5545) | Indicadores (CSV que xera a web desde o módulo `indicators`, [ADR-0026](adr/0026-indicadores-fase-1.md)) e horarios e reservas en calendario (`facility` e `booking`) | NTI Catálogo de estándares |
| CSV, JSON, GeoJSON (RFC 7946) e DCAT-AP-ES 1.0.0 | Datos abertos de cada concello e de toda a instalación ([ADR-0034](adr/0034-datos-abertos.md)) | Lei 37/2007; DCAT-AP-ES é o modelo da nova norma técnica de reutilización, aínda en tramitación |
| Código DIR3 | Identificación común de cada concello ([ADR-0017](adr/0017-datos-persoais.md) punto 8) | ENI, Directorio Común DIR3 |
| UTF-8, HTML5, WCAG 2.1 AA / EN 301 549 | Web pública | RD 1112/2018 |

