<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Accesibilidade

Obxectivo: **WCAG 2.1 AA / EN 301 549** (RD 1112/2018 no sector público). Decisións en [ADR-0014](adr/0014-web.md).

## Que se comproba en cada cambio

| Garda | Onde | Que detecta |
|---|---|---|
| Lint de modelos | `web/eslint.config.js` (`templateAccessibility`) | `alt` que falta, `click` sen teclado, elementos interactivos sen foco, ARIA non válido, `label` sen control |
| Textos | `npm run i18n:check` e `npm run lint` (`furar/no-loose-text`) | texto solto nos modelos (árbore do compilador de Angular): entre etiquetas, en atributos de texto (`aria-label`, `title`, `alt`, `placeholder`, `label`…) e literais que chegan á pantalla en *bindings* (`[attr.title]="'…'"`) ou interpolacións; claves inexistentes no pipe `transloco` |
| Tests de compoñente | `*.spec.ts` | etiquetas ligadas, `aria-describedby` de axuda e erro, foco ao primeiro erro |
| E2E con axe | `web/e2e/` (proxectos `gl`, `es`, `gl-mobile`) | 0 violacións `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa` en cada pantalla, cos temas de Narón e Ferrol e na pantalla de login de Keycloak; 320 px sen scroll horizontal; teclado (salto ao contido, Tab ata o campo, Enter envía e o foco vai ao erro) |

## Criterios aplicados no deseño

- Idioma da páxina (`<html lang>`) e de cada idioma no selector (3.1.1, 3.1.2).
- Título por páxina, traducido (2.4.2). O foco vai ao título ao cambiar de páxina (2.4.3) e hai «Saltar ao contido» (2.4.1).
- Etiqueta visible en todos os campos, obrigatorio cun «*» explicado enriba do formulario («* Campo obrigatorio») e en texto para o lector («(obrigatorio)» no nome), grupos con `fieldset`/`legend`, erros en texto ligados ao campo e anunciados (1.3.1, 3.3.1, 3.3.2).
- Contraste: o tema de cada concello valídase (texto 4,5:1, compoñentes 3:1) na web e na API (1.4.3, 1.4.11). O estado non depende só da cor (1.4.1).
- Obxectivos de polo menos 44 px, reflow a 320 px (1.4.10), `prefers-reduced-motion`.
- Avisos con `role="status"` e erros con `role="alert"` (4.1.3).

## Instalacións do xestor (dominio 1, D1-25)

E2E en `web/e2e/instalacions.e2e.ts` (fluxo en serie) e `web/e2e/instalacions-tema.e2e.ts`, en `gl`, `es` e `gl-mobile`, con axe en cada paso. A instalación da proba elimínase ao rematar (e as que deixase unha execución interrompida, ao empezar): a base da demo non garda restos.

| Paso | Que se comproba |
|---|---|
| Listado e «Nova instalación» | diálogo con nome, buscador de enderezos (combobox, `FURAR_GEOCODER=fixed`) e mapa; teselas respondidas pola proba |
| Borrador | aviso co que falta; cada ligazón leva o foco ao `h2` da sección (2.4.3) |
| Modo edición | «Editar» e a barra fixa (rexión con nome); uso no seu modal; deportes; tempada co «+» do selector; luns aberto; «Gardar» sae da edición e o foco volve a «Editar» |
| Datos | erro do teléfono con `aria-invalid` e o foco nel, sen gardar nada (3.3.1); válido, en texto ao saír |
| Tempada que pisa outra | o aviso no diálogo, que non se pecha (3.3.1, 3.3.3) |
| Excepción | día pechado cun motivo no modal; ao aceptar, o foco vai á tarxeta nova; «Gardar» na barra |
| Bloqueo | **só teclado**: Enter en «Editar» e na tarxeta «Bloqueo», data coas frechas do calendario, Enter acepta, «Gardar» na barra con teclado; modal de reservas afectadas (409 simulado, aínda non hai reservas): Confirmar sen decidir anuncia o que falta (`role="alert"`), Espazo e frechas nos `radio`, Confirmar; o foco volve a «Editar» (2.1.1, 2.4.3) |
| Foto e enderezo | subida con texto alternativo en gl e es (un campo con GL/ES); buscador de enderezos no modo edición; a galería mostra o `alt` do idioma (1.1.1) e a cabeceira o enderezo novo |
| Baixa e eliminar | baixa coa confirmación (P17) e o estado en texto no chip (1.4.1); eliminar escribindo a frase de confirmación |
| Ficha pública (`portada.e2e.ts`) | desde a tarxeta da portada: nome, usos, mapa e tarxeta co horario e o contacto; «Reservar» leva á páxina de reservar e «Volver» ao listado; axe e 320 px |
| Temas e zoom | listado e ficha co tema de Ferrol; 640 px CSS (1280 px ao 200 %) sen scroll horizontal (1.4.4, 1.4.10); 320 px en `gl-mobile` |
| Rede e CSP | ningunha petición fóra de localhost (as que haxa abórtanse e fan fallar a proba); sen `securitypolicyviolation` |

Revisión manual (2026-10-25): o percorrido só con teclado e o zoom están automatizados arriba. **VoiceOver + Safari: pendente**; a escoita co lector de pantalla non se automatiza. Guión para facela: (1) listado: o rotor de puntos de referencia e cabeceiras; cada tarxeta lese co nome, o enderezo e «Estado de …: Activa. Cambiar»; (2) diálogo «Nova instalación»: ao escribir no buscador anúnciase «N suxestións» e a opción activa; (3) ficha: o aviso de borrador e as súas ligazóns; ao abrir un valor de Datos anúnciase o control; «Gardado» lese sen mover o foco; (4) semana: «Luns Pechado, editar» (o nome empeza polo texto visible, 2.5.3), o interruptor «Luns aberto» e os selectores de hora co seu nome; (5) modal de reservas afectadas: título co número, cada reserva como grupo, «Por decidir: …» ao cambiar e o aviso ao confirmar incompleto. Anotar aquí o resultado e a data.

## Reservas (dominio 2, D2-17)

E2E en `web/e2e/reservas.e2e.ts` (veciñanza) e `web/e2e/xestion-reservas.e2e.ts` (concello), en `gl`, `es` e `gl-mobile`, con axe en cada paso. As reservas fanse na demo de Narón, en días propios de cada proxecto; ao empezar e ao rematar cancélanse as que non son da demo e bórranse os bloqueos da proba. Os correos lense no Mailpit do compose (`localhost:8025`).

| Paso | Que se comproba |
|---|---|
| Listado e ficha pública | listado `/:concello/instalacions` → tarxeta → ficha cos usos e «Reservar»; temas de Narón e de Ferrol (uso por prazas: cada hora di en texto as prazas libres) |
| Reservar sen sesión | Que · Data · Hora: cada campo é un botón que abre un diálogo co seu nome; escoller a data abre a Hora; «Continuar» co aviso «Pedirémosche entrar» en texto; tras Keycloak volve ao mesmo paso (data e hora no URL) con «Confirmar reserva» |
| Reserva confirmada | título `h1` co foco, QR co código en texto ao lado (1.1.1), prazo para cancelar; correo co código, o QR incrustado e o `.ics` adxunto |
| As miñas reservas, cambiar e cancelar | tarxeta coa hora; «Cambiar» abre o mesmo fluxo («Confirmar o cambio»); «Cancelar» é un `alertdialog` co prazo; a confirmación anúnciase en `role="status"` e a tarxeta queda co estado en texto (1.4.1) |
| Oco collido | outra lapela reserva antes: aviso en `role="status"` coa hora perdida e tres botóns coas horas libres máis próximas; a Hora queda baleira |
| **Só teclado** | Tab ata Data, Enter abre o calendario co foco en hoxe, frechas ata o día, Enter escolle e abre a Hora co foco dentro, Tab ata a hora e Enter; Tab ata «Confirmar reserva» e Enter; o foco vai ao título «Reserva confirmada» (2.1.1, 2.4.3). Escoller nun campo cambia só os parámetros do URL e o foco xa non salta ao título da páxina |
| Xestión: cobros | busca polo código e detalle; pagada, cancelar pagada (aviso «quedará A devolver» e «Por correo» marcado), devolta; cada cambio anúnciase en `role="status"` |
| Xestión: calendario | día, semana e mes; cada bloque leva de nome a reserva enteira e empeza polo texto visible, persoa e uso (2.5.3); a grella empeza na primeira hora de apertura; a vista rápida dunha hora e dun día; Esc pecha o detalle tamén co foco no bloque |
| Bloqueo sobre unha reserva | modal de reservas afectadas coa reserva real; cancelar avisando por correo; chega o correo de cancelación |
| Rede e CSP | ningunha petición fóra de localhost; sen `securitypolicyviolation` |

Revisión manual (2026-10-26): o percorrido só con teclado está automatizado arriba. **VoiceOver + Safari: pendente**; a escoita co lector de pantalla non se automatiza. Guión: (1) páxina de reservar: cada campo lese co rótulo e o valor («Data, xov 29 out»), ao abrir anúnciase o diálogo co nome do campo e o calendario co día enfocado; as horas son botóns con `aria-pressed`; (2) o aviso de oco collido lese sen mover o foco e as tres horas son botóns; (3) «Reserva confirmada»: o título recibe o foco e o código lese despois do QR; (4) as miñas reservas: cancelar anuncia o diálogo de alerta e despois «Cancelaches a reserva de …»; (5) calendario do xestor: cada bloque lese «persoa, uso, instalación, hora, estado». Anotar aquí o resultado e a data.

## Indicadores (dominio 3, D3-10)

Revisión de accesibilidade e QA da pantalla de indicadores (2026-10-28). Decisións que non se ven nos tests:

| Que | Como se resolve |
|---|---|
| Series que só se distinguen polo ton (1.4.1) | Barras apiladas: cada serie que se confunde con outra leva trama propia, a mesma na mostra da lenda. Cancelacións: en prazo (verde, liso), fóra de prazo (vermello, raias diagonais), concello (azul, puntos). Ingresos: cobrado (verde, liso), pendente (ámbar, puntos), a devolver (vermello, raias diagonais), devolto (gris, raias horizontais). Un test comproba que ningunha parella vai só polo ton (trama distinta ou 3:1) en claro e en escuro |
| Mapa de calor: «pechado» fronte a 0 % | As celas pechadas van sen recheo e con bordo discontinuo `line` (3:1); a lenda leva a mesma mostra e os extremos «0 %» e «100 %» |
| Mapa de calor: pasos baixos da escala | Os primeiros pasos da escala secuencial non chegan a 3:1 sobre a superficie (só o último o garante). Compénsano o valor no tooltip («Mar. · 19 h · 85 %»), a táboa «Táboa» co valor de cada cela e a frase de conclusión; o gráfico non é a única vía para ningún dato (1.4.11, como gráfico con alternativa en texto) |
| PDF etiquetado | `window.print()`: Chromium (`page.pdf({ tagged: true })`, o mesmo motor ca «Gardar como PDF» de Chrome, que etiqueta por defecto) dá `Tagged: yes`, as táboas como `Table` con texto real e cada gráfico como `Figure` co `alt` = título + conclusión. A pista do menú di «no diálogo, elixe Gardar como PDF». Outros navegadores (Safari, Firefox) poden xerar un PDF sen etiquetas: recoméndase Chrome ou Edge |
| Período en curso (2026-10-29) | Na liña, o tramo ata o punto en curso vai discontinuo (trazo, non cor): distínguese en cores forzadas, porque o canvas non se forza, e no PDF, que imprime o mesmo canvas. Non vai só no estilo: o tooltip, a etiqueta do último punto, a táboa e o CSV din «en curso», e a tarxeta di «ata hoxe, fronte ao mesmo tramo de …» |
| Cores forzadas | As mostras das lendas (`.f-chart-card__swatch`, `.f-heat-legend__swatch`) levan `forced-color-adjust: none`: o canvas non se forza, así que a lenda garda as súas cores |
| «Máis» da barra inferior | Patrón disclosure (botón con `aria-expanded` e lista de ligazóns), non `role="menu"`; se a páxina actual está dentro, o nome do botón di «(páxina actual)» |
| Modal cunha táboa longa | Se o corpo dun `furar-dialog` hai que desprazalo e non ten controis (a táboa da evolución dunha cifra), o corpo é unha rexión co título de nome e `tabindex="0"` (2.1.1); atopouno axe no e2e |

E2E en `web/e2e/indicadores.e2e.ts` (panel e exportar) e `web/e2e/indicadores-filtros.e2e.ts` (filtros, móbil e permisos), en `gl`, `es` e `gl-mobile`, con axe en cada paso. Non comproban cifras fixas da demo: o esperado sae da resposta da API que pediu a páxina. As reservas dos e2e de reservas fanse a máis de 40 días (`FAR` en `reservas-axudas.ts`) para non ensuciar o mes en curso que ensina o panel.

| Paso | Que se comproba |
|---|---|
| Entrar | `xestor-naron` → menú da xestión → «Indicadores»; as cifras e a táboa de ocupación coinciden coa resposta da API; axe co tema claro e cun tema escuro do concello (resposta do concello cambiada con `page.route`), tamén co modal aberto |
| CSP | o rato polos seis gráficos (tooltips de ECharts): ningún `securitypolicyviolation` |
| **Cinco cifras só co teclado** | Tab ata cada cifra, Intro abre o modal co foco no `h2` (que o nomea), seis Tab quedan dentro, Esc pecha e o foco volve á mesma cifra (2.1.2, 2.4.3); outra vez con `prefers-reduced-motion: reduce` |
| Modal | «Táboa» con `aria-pressed` e `aria-controls`, o foco queda no botón, caption e `th[scope]`; «Exportar»: Intro abre co foco no PDF, frechas, Esc pecha só o menú (o modal segue e o foco volve a «Exportar»); CSV `indicadores-naron-<período>-evolucion-reservas.csv` con BOM |
| Seis gráficos | `figure role="img"` cun nome que empeza polo título e leva a conclusión; «Táboa» (Intro e Espazo) cambia o gráfico pola táboa, co caption e as cabeceiras, e o foco queda no botón |
| **Filtros só co teclado** | período (`aria-pressed`), instalación e uso (`furar-select`: frechas e Intro); cada cambio vai ao URL e anúnciase en `role="status"`; «atrás» restaura cada paso |
| **«Datas…» co teclado** | Esc sen escoller devolve o foco a «Datas…» e segue o mes; frechas, Intro no inicio e no fin, a pista en `aria-live`, «Aplicar» leva o rango ao URL; con máis de 366 días «Aplicar» desactívase e a pista avisa; Esc pecha co foco no campo e o período aplicado non cambia |
| Período en curso | o mes de hoxe compárase ata hoxe: a tarxeta di «ata hoxe, fronte ao mesmo tramo…», a táboa día a día e a da evolución marcan «(en curso)» nunha soa fila, o CSV do panel leva «Valor (en curso)» e «Ata hoxe (…)»; un rango de datas xa pechado non leva ningún «en curso»; axe |
| Exportar | CSV do panel (BOM, `;`, CRLF, as cifras da API) e dun gráfico; PDF do panel e dun gráfico con `window.print` substituído: `html[data-print]`, `data-print-target` na tarxeta, `img.f-chart__print` co `alt` da figura, cabeceira impresa visible en `print`, e todo desfeito tras `afterprint`; `page.pdf({ tagged: true })` en `print` ten `Table` e `Figure` na árbore de estrutura e texto real (título, gráfico e nome dunha instalación, lidos co `/ToUnicode`) |
| 320 px e zoom do 200 % | sen scroll horizontal; a táboa do mapa de calor é unha rexión enfocable co caption de nome que se despraza coas frechas; a barra inferior leva catro destinos (Instalacións, Reservas, Indicadores, Concello), sen «Máis»; as pestanas de «Concello» van nunha fila que se despraza dentro da súa franxa (non a páxina), con fundido no bordo e a activa á vista |
| Ferrol | sen reservas: estado baleiro «Sen reservas neste período»; ao cambiar o período, o anuncio o di |
| Sen permiso | `vecino-naron` e `recepcion-naron`: sen entrada no menú, «Sen permiso» na ruta e 403 na API |
| Ctrl+P | `beforeprint` sen «Exportar» co tema escuro: alcance do panel, as imaxes co fondo do tema claro e, en `print`, os tokens claros; tras `afterprint`, a pantalla segue en escuro |
| Rede | ningunha petición fóra de localhost |

Revisión manual (2026-10-29): o teclado está automatizado arriba. **VoiceOver + Safari: pendente**; a escoita co lector de pantalla non se automatiza. Guión: (1) cada cifra lese «Reservas 1.234 +12 % respecto a … Ver evolución» e anuncia que abre un diálogo; (2) o modal lese polo título e a cifra; «Táboa» anuncia «premido»; (3) cada gráfico lese como imaxe co título e a conclusión; a táboa co caption; (4) cambiar un filtro anuncia «Indicadores actualizados: …» sen mover o foco; (5) «Datas…»: o día lese co «inicio» ou «fin» e a pista ao cambiar; (6) o PDF gardado desde Safari (sen etiquetas, véxase arriba) e desde Chrome cun lector de PDF. Anotar aquí o resultado e a data.

## Persoal e rexistro de actividade (dominio 4, D4-17)

E2E en `web/e2e/persoal.e2e.ts` (invitar, aceptar, desactivar, reactivar, pasar a Recepción, límites e ligazóns), `web/e2e/persoal-contas.e2e.ts` (a última xestora, a conta de traballo e a conta persoal), `web/e2e/persoal-accesos.e2e.ts` (acceso con contrasinal, plataforma e menús) e `web/e2e/rexistro.e2e.ts` (rexistro de actividade), en `gl`, `es` e `gl-mobile`, con axe en cada paso (tamén nas páxinas de Keycloak polas que pasa: rexistro e contrasinal novo), 320 px sen scroll horizontal, ningunha petición fóra de localhost e sen `securitypolicyviolation` en ningún dos contextos (a persoa invitada vai noutro contexto do navegador coas mesmas gardas). Os correos que len as probas en Mailpit bórranse ao lelos e ao rematar. Revisión do 2026-10-29.

| Paso | Que se comproba |
|---|---|
| Invitar | `xestor-naron` → «Concello» → pestana «Persoal»; «Invitar» nun diálogo (rol «Recepción» picando na súa tarxeta: radio nativo marcado, co nome do rol e o que pode facer como descrición; idioma con `aria-checked`); ao enviar, o foco volve a «Invitar» e o resultado sae nun toast (`role="status"`, sen roubar o foco); a fila nova «Pendente» con «Invitación enviada o …» |
| Correo | en Mailpit, asunto no idioma escollido co nome do concello; a ligazón `…/naron/convite#<token>` leva o token só no fragmento (nin na query nin nos rexistros) |
| Crear a conta | a ligazón leva directa ao rexistro de Keycloak co correo invitado xa posto (sen pantalla intermedia en Furar); nome, apelidos e contrasinal («Mínimo 12 caracteres»), a privacidade nunha frase curta (sen casiña), sen correo de verificación (a ligazón da invitación xa o verifica) → de volta en Furar |
| Aceptar | sen modal nin casiña (revisión do 2026-11-03): ao volver de Keycloak, a carga e logo o toast «Xa formas parte do persoal de …» (`role="status"`) e a xestión que lle toca; un erro amosa o seu estado co título enfocado. As obrigas do persoal están no aviso legal |
| Persoal do mostrador | ve «Reservas» no seu menú e entra; non ve «Persoal», «Indicadores» nin «Rexistro de actividade» |
| Desactivar | confirmación (`alertdialog`) que di o que perde e que se pode reactivar; o foco entra no campo «Para confirmar, escribe «desactivar»» (rótulo visible); «Desactivar» queda bloqueado (`aria-disabled`, nunca `disabled`, e descrito por `aria-describedby` co rótulo do campo e o da casiña que faltan) ata escribir a palabra (sen distinguir maiúsculas nin tildes) e marcar a casiña obrigatoria («Entendo que non se borra a conta nin o seu historial…», `aria-required`, descrita polo diálogo); anuncio en `role="status"` e a fila pasa a «Desactivadas»; á seguinte navegación da persoa, «Sen permiso» e 403 na API (revisión do 2026-10-30) |
| Reactivar | desde a fila desactivada, confirmación co rol que recupera e que entra co seu correo e contrasinal; toast en `role="status"`, a fila volve ao persoal en uso e a persoa entra outra vez nas reservas, sen invitación nova |
| Pasar a Recepción | «Cambiar o rol» dunha persoa de «Xestión» a «Recepción»: aparece o campo «Para confirmar, escribe «recepción»» (en castelán, tamén «recepción»), descrito polo texto do que perde; «Gardar» bloqueado (`aria-disabled`, descrito polo rótulo) ata que coincide; ao cambiar de rol e volver, o campo volve baleiro |
| Última xestora | `xestor-ferrol` (a única de Ferrol): desactivarse ou pasarse a Recepción a si mesma → a capa pecha e o erro «O concello ten que ter sempre…» en `role="alert"`; nada cambia |
| Conta de traballo | `recepcion-naron` na páxina de reservar: aviso «Esta é unha conta de traballo…» antes de nada, «Confirmar reserva» desactivado e descrito polo aviso (`aria-describedby`); a API responde 403 `booking.staff-account`. Unha conta con reservas (`vecino-naron`) non acepta unha invitación: 403 `staff.invitation-personal-account` (pola API) |
| Límites e ligazóns | dentro dos 2 min do último envío, «Reenviar» do menú está desactivado (`aria-disabled`) co nome «Reenviar» e a conta atrás «Podes reenviar en m:ss» como descrición, que avanza sen anunciarse; picar dúas veces non pide nada nin mostra alerta, Esc devolve o foco ao botón da fila, e a API, chamada directamente, responde 429 con `Retry-After`; outra conta (`vecino-naron`) abre a ligazón: «Entraches con outra conta» e «Pechar sesión», que pecha; desactivada, a mesma ligazón → «Esta invitación non serve» |
| Caducada | `persoal-antigo@example.org` da demo: «Pendente», «Invitación caducada» en vermello (con icona, non só cor) e «Reenviar» á vista co nome accesible «Reenviar a invitación a …», co tema escuro |
| Último acceso | `xestor-naron` entra co contrasinal; na xestión, «O teu último acceso foi o dd/mm/aaaa, hh:mm» (texto, non alerta) |
| Plataforma | `admin-plataforma`: 403 en reservas e indicadores (API e «Sen permiso» na web); no menú só «Persoal»; ve todo o persoal e ten as mesmas accións ca o xestor (revisión do 2026-11-02): «Cambiar o rol», «Desactivar», «Reactivar» e «Invitar» co selector de rol; «Rexistro da plataforma» desde o seu menú, só co período |
| Rexistro | as accións do persoal (invitar, aceptar, desactivar, entrar; os «staff.revoke» anteriores len como «Desactivar») coa persoa e o resultado; «Cargar máis» co teclado: o foco vai á primeira fila nova e anúnciase canto se cargou e cando non hai máis; tantas filas «Fallou» como na API; «Persoa anonimizada» se a hai; tema escuro; a 320 px as filas apílanse sen scroll horizontal |
| Filtros só co teclado | período co calendario de rango (frechas, Intro no inicio e no fin, a pista en `aria-live`, «Aplicar» e o foco volve ao campo); área e persoa (`furar-select`: frechas e Intro); «Só fallidos» con Espazo (`aria-pressed`); cada filtro vai ao URL, «atrás» desfaino, «Quitar os filtros» volve ao último ano e o URL con filtros ábrese tal cal |
| Menús e pestanas | «Concello» é unha ligazón máis da barra (`aria-current="page"` en calquera das súas pestanas); as pestanas da sección son ligazóns (`furar-tab-nav`, `aria-current="page"`), cada unha unha parada de tabulador e ademais frechas, Inicio e Fin entre elas sen navegar; ao navegar, o foco queda na pestana; saír dunha pestana con cambios pregunta (`alertdialog`, foco en «Gardar»; Cancelar, Descartar, Gardar); tamén co tema escuro |

Atopado e arranxado no e2e:

- **Keycloak, páxina do código TOTP** (ata o 2026-11-02, cando se retirou o segundo factor): o campo co usuario xa escrito (`#kc-attempted-username`) non tiña nome accesible (axe `label`, crítico; WCAG 1.3.1 e 4.1.2); o tema arranxábao en `template.ftl`.
- **Keycloak, a 320 px:** a pista escondida de «Volver empezar» (`.kc-tooltip-text`) abría á dereita do botón e ensanchaba a páxina 132 px (scroll horizontal, 1.4.10). En `furar.css` abre enriba, aliñada co botón, e vese tamén co foco do teclado (1.4.13).

- **Keycloak, rexistro:** desde o 2026-11-02 o contrasinal vai no propio formulario de rexistro, despois do nome e os apelidos (antes, Keycloak 26.8 pedíao despois de verificar o correo); coa invitación do persoal o correo é só de lectura ([ADR-0027](adr/0027-persoal-e-permisos-fase-1.md)). As pantallas de Keycloak seguen o esquema da web: claras, e escuras só se a web gardou o tema escuro dun concello (desde o 2026-11-03 non seguen o esquema do sistema, igual ca a web); D51 pasa axe tamén no acceso e no rexistro escuros. Ata o 2026-11-02 o tema substituía `password-validation.ftl` (daba un erro de JavaScript sen `#password`) por unha lista de requisitos en vivo; desde entón só hai unha liña de axuda e o erro ao enviar ([abaixo](#contrasinal-e-aviso-do-bloqueo-keycloak)). D51 falla se o rexistro ou o cambio de contrasinal dan un erro de JavaScript.

Keycloak 26.8.1 enche o correo do rexistro co `login_hint` (`prompt=create`), así que quen chega da invitación atopa o seu correo xa posto.

Revisión manual (2026-10-29): o teclado está automatizado arriba. **VoiceOver + Safari: pendente**; a escoita co lector de pantalla non se automatiza. Guión: (1) ao volver da invitación, o toast «Xa formas parte do persoal de …» anúnciase sen mover o foco; (2) no aviso legal, «Obrigas do persoal» lese como encabezado e a súa lista como lista; (3) en «Persoal», a táboa co caption e as cabeceiras; o botón de accións de cada fila co nome da persoa; o resultado de invitar e o 429 anúncianse sen mover o foco; (4) a confirmación de desactivar lese como diálogo de alerta co que perde, o campo da palabra co seu rótulo e a casiña co seu texto; «Desactivar» lese como non dispoñible e di que falta; (5) no rexistro, «Cargar máis» anuncia as accións novas e a primeira fila nova lese ao recibir o foco; os filtros anuncian o resultado; (6) ao crear ou cambiar o contrasinal en Keycloak, o campo lese cos requisitos e, ao deixar de escribir, anúnciase unha soa vez o que falta. Anotar aquí o resultado e a data.

### Contrasinal e aviso do bloqueo (Keycloak)

No rexistro, no cambio e no restablecemento do contrasinal (desde o 2026-11-02, decisión do mantedor):

- **Axuda nunha liña (3.3.2):** baixo o campo, «Mínimo 12 caracteres», ligada ao campo con `aria-describedby`. Sen lista en vivo nin JavaScript propio.
- **Erro ao enviar (3.3.1, 3.3.3):** Keycloak valida no servidor (lonxitude, distinto do correo, fóra da lista de contrasinais comúns) e o motivo sae baixo o campo, ligado a el, co campo marcado como non válido.
- **Aviso do bloqueo:** tras un acceso fallido, baixo o contrasinal e antes de «Esqueciches o contrasinal?», unha liña de texto co número de intentos e os minutos do realm e que facer se xa está bloqueada. É o mesmo exista ou non a conta ([ens.md](ens.md#contrasinais-e-bloqueo)).
- **Estilo de Furar:** tipografía, cores (os tokens da web, con contraste AA), botóns e cabeceira como na web, en galego e castelán; sen scroll horizontal a 320 px.
- **Probas:** D51 (axe no rexistro, no cambio do contrasinal e no acceso fallido, o mesmo aviso exista ou non a conta) e o e2e do persoal (crear a conta e o contrasinal).

## Declaración de accesibilidade (RD 1112/2018)

- Publicada en `/:concello/accesibilidade`, ligada desde o pé de todas as páxinas do concello (art. 10.2); privacidade en `/:concello/privacidade`. Texto en `web/src/i18n/{gl,es}.json` (`accessibility.*`), coas seccións do modelo oficial (Decisión de Execución (UE) 2018/1523): compromiso, situación de cumprimento, contido non accesible, preparación, observacións e contacto, e procedemento de aplicación.
- Estado declarado: **parcialmente conforme**, porque a revisión manual con lector de pantalla está pendente. Data de preparación e de última revisión: 2026-10-22, por autoavaliación (axe en cada pantalla e idioma, 320 px, dous temas, teclado).
- **Como actualizala:** tras cada revisión, cambiar en gl e es `accessibility.status`, `accessibility.nonAccessible*` e as datas de `accessibility.preparation`. O e2e comproba que a páxina existe, que é accesible e que mostra o estado.
- **Canle de comunicación e reclamación (arts. 10.2.b, 12 e 13):** se o concello ten os seus datos legais (`legal` en `MunicipalityResponse`), a declaración mostra o correo de accesibilidade (`mailto:`), a sede electrónica, a unidade responsable ante a que se reclama e a ligazón á reclamación; a privacidade mostra o DPD do concello para as reservas (correo e teléfono, ademais do da conta). Sen eses datos, remite ao rexistro e á unidade responsable do concello. Tests en `web/src/app/features/legal/legal-pages.spec.ts`.
- **Tres modos por páxina (2026-10-29, [ADR-0017](adr/0017-datos-persoais.md) punto 8):** a declaración, a privacidade e o aviso legal (`/:concello/aviso-legal`, novo) poden ser a páxina de Furar, un texto propio do concello (co estilo de Furar e no idioma de quen mira; se falta, no outro, co seu `lang`) ou a páxina do concello: entón a ligazón do pé vai a ela, cunha icona e «(páxina do concello)» no nome, e a de Furar deixa a ligazón. Se o concello publica a súa propia declaración, o contido e o estado son responsabilidade súa.

## Datos legais do xestor (2026-10-29, maqueta 33)

En Concello › Legal (maqueta 49), o bloque «Datos que usan os textos legais» co aviso en vivo dos que faltan (`role="status"`) e unha card coas subpestanas Privacidade · Accesibilidade · Aviso legal (`furar-tab-nav` pequeno; cada unha co seu URL, `…/concello/legal/aviso-legal`, e o foco queda na pestana). «Sen páxina» avisa nun `furar-callout` de ton `warn`. E2E en `web/e2e/datos-legais.e2e.ts` en `gl`, `es` e `gl-mobile`, con axe en cada paso; tests en `features/municipality/section/legal.tab.spec.ts` e `rich-text-editor.component.spec.ts`.

| Peza | Que se comproba |
|---|---|
| Modo | `[furarSegmented]` en modo radio (`radiogroup` co nome da páxina: «Páxina de privacidade»); a 320 px, en columna |
| Erros | un só «Gardar» para as tres pestanas: a pestana con erros leva «!» (non só cor) e «, con erros» no nome; o foco vai ao primeiro erro (se está noutra pestana, primeiro vai a ela); aviso en `role="status"` xunto a «Gardar» coas pestanas que revisar (3.3.1, 4.1.3) |
| Páxina propia | rótulo «Enderezo» con «de privacidade» para o lector, axuda ligada, só https |
| Editor de texto | `toolbar` cunha parada de tabulador e frechas; atallos no tooltip e en `aria-keyshortcuts`; tooltip co rato e co foco, Esc agóchao (1.4.13); a área é un `textbox` multiliña co nome da páxina e o idioma, `lang` e a axuda «os datos xa non se insiren sós» en `aria-describedby`; pegar só mete texto |
| Vista previa | diálogo co título da páxina; Esc e «Pechar» volven ao botón |

Revisión manual (2026-10-29): **VoiceOver + Safari e NVDA: pendente**. Guión: (1) as pestanas anúncianse coa posición e «con erros»; (2) o grupo do modo dío ao entrar; (3) no editor, o lector di o nome da área e os botóns din o seu estado («premido») e o atallo; (4) a ligazón do pé á páxina do concello dío.

## Pendente (débeda visible)

- **Revisión manual con lector de pantalla** (NVDA + Firefox, VoiceOver + Safari, TalkBack): sen facer. Data obxectivo: antes da entrega (2026-11-18, 23:59). Despois, actualizar a declaración.
- A páxina de escoller concello (`/`) e `/plataforma` son da plataforma, non dun concello: non levan declaración propia.
