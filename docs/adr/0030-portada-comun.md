<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0030 · Portada común: as instalacións de todos os concellos na raíz, sen escoller concello

- **Estado:** aceptada · 2026-11-10 (decidida polo mantedor); revisada o 2026-11-11.
- **Revisa:** [ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 5 (que mostra a raíz con varios concellos) e [ADR-0006](0006-multiconcello.md) (unha lectura máis que percorre os concellos, sen cambiar ningunha garantía).
- **Revisada 2026-11-11 (decisión do mantedor):** no móbil, o mapa arriba e a lista debaixo, os dous á vista e sen alternar (substitúe o «No móbil alterna unha e outro» do punto 7); os filtros, nunha folla «Filtros» que sobe desde abaixo. «Preto de min», no mapa: co permiso do navegador, ordena a lista pola distancia e centra o mapa onde está a persoa; a ubicación queda no navegador, non se manda á API nin se garda (por iso a `Permissions-Policy` pasa a `geolocation=(self)`, [ADR-0007](0007-seguridade-pechada.md)). As marcas próximas agrúpanse nun círculo co número de instalacións; picalo achega o mapa.
- **Revisada 2026-11-12 ([ADR-0040](0040-buscar-xente-para-xogar.md)):** terceira ruta sen concello, `GET /api/v1/catalog/open-matches`, cos concellos que teñen a función acesa, lidos un a un.
- **Revisada 2026-11-18 (decisión do mantedor):** a folla «Filtros» do móbil leva «Mostrar» (despregable): as instalacións ou, se hai algunha, as partidas abertas (na portada dun concello, tamén os eventos). Lístase só o escollido; «Data» e «Deporte» saen só coas instalacións, que son o que filtran, e buscar volve a elas. «Quitar filtros» vai na cabeceira da folla, á dereita. A portada común non ofrece eventos: non hai ruta de eventos sen concello.

## Contexto

Unha instalación con varios concellos (unha Deputación, unha mancomunidade) abría en «Escolle o teu concello»: unha lista de nomes. Quen chega quere unha pista, non un concello: ten que saber antes onde hai o que busca, entrar, mirar, e volver se non o atopa. O que xa hai en cada concello (listado público, ocos do día, mapa) serve igual para todos xuntos.

Dúas cousas que non poden cambiar: os datos dun concello só se len dentro dese concello (`@TenantId`, RLS e FK compostas, ADR-0006), e un concello a medio configurar non debe verse.

## Decisión

1. **A raíz (`/`) con varios concellos é a portada común.** «Reserva a túa pista» (o mesmo título e a mesma marca xenérica ca a portada de exemplo, sen nome de concello), o buscador e, debaixo, as instalacións publicadas de **todos** os concellos. Non hai paso previo de escoller concello. Con un só concello, a raíz segue levando á súa portada; sen ningún, a de exemplo (ADR-0028 punto 5, sen cambios).
2. **Un concello sae só, sen botón nin estado.** Sae na portada común cando ten **os datos legais completos** (entidade, NIF, domicilio, correo xeral e correo do DPD: os que usan os textos legais por defecto) **e polo menos unha instalación activa que vexa a veciñanza**. É a mesma condición ca «Para abrir as reservas». Se deixa de cumprila, deixa de saír. Dun concello que sae vese todo o seu listado público (tamén as pechadas temporalmente, coma nel).
3. **Decídeo a API, non a web.** Dous endpoints públicos sen concello no URL, por páxinas de concellos (por nome; 20 por defecto, 50 como moito, con `hasNext`):
   - `GET /api/v1/catalog/facilities` (`facility`): cada concello que sae, co seu slug, nome e as instalacións do seu listado público (`PublicFacilityResponse`, coas rutas das imaxes xa no seu concello).
   - `GET /api/v1/catalog/availability?date=` (`booking`): os ocos libres do día por instalación que se reserva por internet (`FacilityFreeSlotsResponse`), dos concellos cos datos legais completos.

   A web non pode pedir «todos»: só recibe o que a API decide publicar. Son os mesmos datos que cada concello xa dá, sen sesión, no seu listado.
4. **Ler todos os concellos sen debilitar o illamento.** Non hai consulta que vexa dous concellos á vez. O porto `shared.tenancy.CatalogMunicipalities` (impleméntao `municipality` sobre a súa táboa, que é da plataforma) dá unha páxina de concellos cos datos legais completos (`LegalDetails.readyToOpen()`, no dominio). Despois, `facility` (`FacilityCatalogService`) e `booking` (`CatalogAvailabilityService`) percorren eses concellos e, en cada un, chaman ao caso de uso de sempre dentro de `TenantContext.runAs(concello)` e **nunha transacción propia**: o mesmo camiño, o mesmo `@TenantId` e a mesma RLS ca unha petición do concello (o patrón do «Rexistro da plataforma»). Sen `runAsPlatform`, sen `BYPASSRLS`, sen SQL nativo novo e sen táboas novas. `facility` segue sen depender de `municipality` (arrinca só, `FacilityModuleIT`).
5. **Custo acoutado.** Un número fixo de lecturas por concello, sexan cantas sexan as súas instalacións: o listado público xa lía por lote, e os ocos da portada común teñen unha lectura propia (`ReadAvailabilityUseCase.ofAllFacilities`: usos e tramos abertos por lote e a ocupación nunha consulta), co mesmo cálculo ca a páxina de 12 do listado. Os concellos van por páxinas, así que unha petición sen sesión nunca percorre máis de 50.
6. **Buscador.** O de sempre (`furar-facility-search`), ampliado: o texto busca por instalación **ou concello** («Instalación ou concello») e suxire tamén concellos; o día; e un filtro **«Deporte»** dentro da barra, xunto ao día, que só se desprega ao pulsalo, con selección múltiple e «Cuberta» ao final (`furar-multi-select`; péchase picando fóra e con Esc). Nesta portada non hai chips de deportes á vista. Os filtros viven no URL (`deporte`, `cuberta`, `data`, `concello`).
7. **Lista e mapa.** Desde 900 px, as tarxetas á esquerda e o mapa á dereita, fixo baixo a barra ao baixar. Picar unha marca resalta a súa tarxeta e leva a lista ata ela; pasar o rato ou o foco por unha tarxeta marca o seu punto. O conmutador «Lista / Mapa» de sempre quita e pon o mapa («Lista»: só tarxetas a todo o ancho). No móbil alterna unha e outro, e a tarxeta da marca picada sae debaixo do mapa. O mapa vese ao entrar no escritorio; a elección da persoa gárdase no seu navegador (`localStorage`, sen datos persoais) e, se o navegador non deixa, vale só para esa visita. Sen mapa no despregue, só lista.
8. **Tarxetas.** A do listado de cada concello, co concello diante do enderezo («Narón · Rúa do Alto, 12»), o chip de ocos e as primeiras horas libres. A tarxeta entra nese concello (`/<slug>/instalacions/<id>`) e as horas levan a reservar nel.
9. **Accesibilidade.** O mapa non é parada de tabulador nin a única vía: a lista ten as mesmas instalacións. O filtro é un `listbox` de selección múltiple con nome, e o botón di o que hai marcado.
10. **Volver á portada común.** Na portada dun concello e no seu «Instalacións», enriba do título, «‹ Todos os concellos» (`furar-all-municipalities-link`): unha ligazón a `/`, sen levar os filtros, só cando a raíz é a portada común (varios concellos; a mesma lista ca `severalMunicipalitiesMatch`, lida unha vez se se entrou directo no concello).

## Consecuencias

- Quen entra ve desde o primeiro momento onde pode reservar, en todos os concellos.
- «Publicar» un concello non é unha acción: é rematar a súa configuración. Un concello que borra un dato legal ou retira todas as súas instalacións desaparece da portada común na seguinte lectura; as súas páxinas (`/<slug>/…`) seguen onde estaban.
- A portada común custa unha transacción curta por concello e petición. Para un despregue con centos de concellos, o seguinte paso sería unha caché de poucos segundos na API; non fai falta na fase 1.
- «Escolle o teu concello» desaparece (pantalla e textos). A administración da plataforma segue chegando a cada concello e á alta desde «Concellos ›» no seu menú.
- Dúas rutas máis na lista pechada das que non levan concello (`/catalog/facilities`, `/catalog/availability`). Desde ADR-0034, tamén `/catalog/open-data` e os seus ficheiros.
- Subdominios e dominio propio non cambian: resólveos o proxy (ADR-0006).
- Probas: `CatalogIT` (a condición, que deixa de cumprirse, e que cada concello trae só o seu), `FacilityCatalogServiceTest`, `CatalogAvailabilityServiceTest`, `LegalDetailsTest`, `common-home.page.spec.ts`, `multi-select.component.spec.ts`, `facility-search.spec.ts`, `common-home.match.spec.ts` e `publico.e2e.ts`.

## Alternativas descartadas

- **Escoller concello primeiro** (o de ata agora, ou tarxetas de concellos na raíz e as instalacións só ao buscar): un paso máis para todos, e obriga a saber de antemán en que concello está o que se busca.
- **Un botón «Publicar o concello»** (ou un estado novo): outro paso que esquecer e outro dato que pode quedar desacompasado co real (publicado sen datos legais, ou completo e sen publicar). A condición calculada non se pode esquecer nin mentir.
- **Decidir na web que concellos saen** (pedir todos e filtrar): a API daría datos de concellos sen rematar e a regra viviría onde non se pode garantir.
- **Unha consulta sobre todos os concellos** (plataforma sen filtro, un rol con `BYPASSRLS` ou táboas agregadas): máis rápida, pero abriría un camiño que le datos de varios concellos á vez; percorrelos un a un mantén as tres capas de ADR-0006 intactas.
- **Que `facility` dependa de `municipality`:** rompería que o módulo arrinque só; o porto en `shared` é o mesmo patrón ca o resolvedor de slugs e o nome do concello.
- **Que a web pida os ocos concello a concello** cos endpoints de cada un: unha petición (ou varias) por concello desde o navegador.
- **Gardar a vista (lista ou mapa) no URL**, coma no listado dun concello: aquí é unha preferencia da persoa, non un filtro que compartir.
