<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Especificación de produto de Furar

**Estado:** acordada o 2026-10-24, bloque a bloque; bloques 1, 1b e 2 revisados o 2026-10-25 (dominio 1); bloques 3, 4 e 5 revisados o 2026-10-26 (fase 1 de reservas); bloque 10 revisado o 2026-10-28 (fase 1 de indicadores); bloque 7 revisado o 2026-10-28 (fase 1 de persoal); preguntas abertas de reservas, indicadores e persoal validadas o 2026-10-29 e o 2026-10-30. É a referencia do produto: ningún módulo de dominio se constrúe sen que o que fai estea aquí. O que non se acordou vai en [«Pendente de decidir»](#pendente-de-decidir); o que non estea aquí non se decide ao construír.

- Termos e nomes en código: [`../glosario.md`](../glosario.md).
- Arquitectura: [`../arquitectura.md`](../arquitectura.md). Deseño visual: [`../deseno.md`](../deseno.md).
- Requisitos do certame (bases v3, 1.1 e 1.2): consulta, reserva, modificación, cancelación, historial e avisos para a cidadanía; alta e mantemento de instalacións, horarios e normas, e indicadores para o concello; multiconcello; autenticación pública; xestión segura de usuarios; WCAG 2.1 AA.

## Principios que atravesan todo

- **Configurable por concello.** Avisos, tempos, modo de rexistro, permisos de Recepción, plantillas, normas e tarifas: cada concello decide sen tocar código.
- **Fase 1 = o básico, ben feito** (2026-10-26): a persoa reserva e o concello pon a pista. Cada bloque di que entra agora e que queda como capa posterior.
- **Herdanza por niveis.** Normas e tarifas pódense poñer en varios niveis; cada nivel herda do anterior salvo que se cambie. Na fase 1 de reservas non hai herdanza: normas e prezo póñense no uso (bloques 3 e 4).
- **Nada a terceiros.** Sen IA externa, sen reCAPTCHA, sen servizos de envío alleos; o buscador de enderezos pasa polo servidor de Furar e é substituíble.
- **Bilingüe.** Todo texto que ve a veciñanza existe en galego e castelán.
- **Todo o que fai o persoal queda no rexistro de actividade** (quen, que, cando).

---

## Bloque 1. Instalacións

**Revisado o 2026-10-25** (substitúe o modelo complexo → instalación → espazo do 2026-10-24). Maquetas navegables: listado e ficha da instalación.

### Obxectivo
Que un concello dea de alta o que se pode reservar e diga de que maneira, coa xestión máis sinxela posible: un só nivel e unha soa páxina por instalación.

### Modelo
- **Só hai instalacións.** A instalación é o que se reserva: «Campo de fútbol 1 de O Alto», «Pista de pádel 2», «Pabellón de Piñeiros». O nome xa di onde está.
- **Sen complexo de momento.** Decidirase máis adiante se fai falta (agrupar no listado, dar permisos sobre varias á vez).
- **Sen espazos.** Desaparecen o espazo, o interruptor «enteira / por espazos» e os espazos ocultos. O que antes eran partes dun espazo vai nos usos.

### Listado
- Tarxetas en orde alfabética no idioma de quen mira; sen orde manual nin filtro por estado.
- Cada tarxeta, coma as da portada (decidido o 2026-10-26): imaxe de portada, nome, enderezo, deportes e chip de estado. Toda a tarxeta abre a ficha; o chip abre un menú para cambiar o estado.
- «Nova instalación» abre un **asistente de 8 pasos** (revisión do 2026-11-03, maqueta 53): Nome e deportes · Ubicación · Contacto e servizos · Fotos · Usos · Horario · Normas (e pago, se o concello o pide por instalación) · Revisar. Gárdase como borrador desde o primeiro paso (só co nome) e pódese saír e volver. A instalación nace **sen publicar**: non a ve a veciñanza ata que alguén preme **«Publicar»** en «Revisar», que lista o que falta (nome, deportes, enderezo, polo menos un uso e horario) con ligazón a cada paso. Unha sen publicar sae na ficha co aviso «Aínda non está publicada» e «Revisar e publicar».

### Ficha da instalación
Unha soa páxina, con «← Volver» (sen ruta de migas). Fotos grandes arriba (unha grande e catro pequenas; no móbil, unha), nome co chip de estado na mesma liña e, ao lado, «Editar» e «Eliminar»; debaixo, o enderezo coa icona de ubicación. Despois, en dúas columnas:

- **Seccións** separadas por unha liña: Usos · Descrición · Horario · Bloqueos · Servizos e accesibilidade · Localización (o mapa grande; o enderezo xa vai na cabeceira).
- **Tarxeta «Datos»** ao lado (no móbil, enriba): en edición, o **nome** é o primeiro campo (decidido o 2026-11-04); deportes, tipo, código RIDEGA, teléfono, correo, campos propios do concello, «a veciñanza reserva por internet» (se non: «mostrar os ocos libres»), cuberta.

**Modo edición (decidido o 2026-10-26; substitúe a edición campo a campo):** fóra da edición a ficha é só lectura, sen lapis. «Editar» pon a ficha enteira en edición e aparece unha barra fixa abaixo (cristal, o ancho da barra superior) con «Cancelar» e «Gardar». **Cancelar sae sen gardar nada.** «Gardar» garda todo o cambiado, cada parte co seu `If-Match`; se algo falla, a ficha segue en edición co aviso onde fallou e o resto queda gardado.
- Datos, Descrición, Servizos e Localización vense directamente como campos (os servizos, casillas; a localización, buscador, campos e chincheta). Os textos en dous idiomas son un só campo con conmutador GL/ES.
- **Usos, excepcións e bloqueos:** fóra da edición, en filas (os usos coas etiquetas das tarxetas e o prezo á dereita: «45,00 € por reserva de 60 min», «Gratis», «Sen prezo»); en edición, tarxetas que ao pulsalas abren un modal editable, e unha tarxeta para engadir. «Aceptar» no modal só deixa o cambio pendente; vai á API co «Gardar» da barra.
- **Horario:** as tempadas son un selector só co nome (en edición, cun «+» dentro para engadir); debaixo, as datas da escollida coa icona do calendario e, en edición, nome, datas e «Eliminar» na mesma liña. Os días, sen chip «Hoxe»; en edición ábrense ao pulsalos.
- **Fotos:** en edición, «Cambiar fotos» abre o modal; «Aceptar» deixa a lista na ficha e a barra gárdaa (as subidas descártanse se se cancela).
- **Eliminar a instalación:** só se nunca tivo reservas; pide escribir «eliminar instalación» e borra a instalación co seu horario, usos, bloqueos e a súa lista de imaxes (as fotos seguen na biblioteca). Se tivo reservas, explica que hai que dala de baixa para conservar o historial e ofrece facelo.
- Todo «quitar» é «Eliminar» coa papeleira.

### Datos

| Dato | Obrigatorio | Nota |
|---|---|---|
| Nome | Si | Escríbese a man |
| Enderezo e punto no mapa | Si | Ver «Enderezos» |
| Estado | Si | Activa · pechada temporalmente · de baixa |
| Deportes | Si (para publicar) | Catálogo de Furar con «+ crear»; serven para buscar |
| Tipo | Non | Campo, pavillón, pista de pádel, piscina, pista exterior, outro |
| Descrición | Non | gl/es |
| Código RIDEGA | Non | Censo deportivo de Galicia |
| Teléfono e correo | Non | Un de cada |
| Reserva por internet | Si | Se non: o persoal xestiona as reservas en Furar; interruptor «mostrar os ocos libres» (a veciñanza ve os ocos sen poder reservar e «Resérvao chamando ao … ou no mostrador») |
| Cuberta | Non | Para o filtro de días de choiva |
| Normas da instalación | Non | Texto libre gl/es que se mostra en «Antes de reservar» (validado de momento o 2026-10-30, P9 do [plan do dominio 2](../plan/dominio-2-reservas.md#6-preguntas-de-produto); constrúese) |

En táboas propias: **imaxes** (ver «Fotos»), **servizos e accesibilidade** (catálogo fixo de Furar, bilingüe), **campos propios** (Bloque 1b).

### Fotos
**Decidido o 2026-10-26** (maquetas aprobadas: modal de fotos a pantalla completa e «Engadir fotos»; [ADR-0024](../adr/0024-biblioteca-de-imaxes.md)).

- **Biblioteca do concello.** As fotos que sube un concello son do concello (só as ve el) e reutilízanse en calquera das súas instalacións. O texto alternativo (gl e es, obrigatorio) vai coa foto: cambialo desde unha instalación cámbiao en todas as que a usan.
- **Debuxos de Furar.** Catálogo da plataforma de ilustracións propias (CC0): pavillón polideportivo, piscina, pistas, campos, salas… Úsanse igual ca as fotos; a súa descrición é fixa.
- **Lista ordenada por instalación,** fotos e debuxos mesturados, ata 12. **A primeira é a portada** (non hai «portada» á parte): é a que sae na tarxeta e no collaxe da portada do concello.
- **Nunca sen imaxe.** Se a instalación non ten ningunha (ao creala ou se se quitan todas), vese o **debuxo suxerido**, sen IA: (1) palabras do nome en galego ou castelán (pavillón/pabellón/polideportivo → pavillón polideportivo; piscina; pádel; tenis; campo de fútbol/fútbol; rugby; atletismo; frontón; squash; bádminton; ximnasio/gimnasio/musculación; rocódromo/escalada; velódromo; skate…); (2) se non, os deportes: un → o seu debuxo; varios de pista → pavillón polideportivo se é cuberta, pista polideportiva exterior se non; (3) se non, o tipo; (4) se non, a instalación xenérica. O xestor pode escoller calquera imaxe aínda que non cadre.
- **Modal de fotos** (ao pulsar «Cambiar fotos»): título co nome da instalación; arriba, a imaxe escollida en grande e unha tarxeta coa súa descrición (conmutador GL/ES), «Subida o …» e **Eliminar** (quita a imaxe desta instalación; segue na biblioteca); debaixo, «N fotos» e a tira, que se ordena **arrastrando** (as demais apártanse; preto dun bordo desprázase soa) ou con **Alt + ← →**; ao final, fóra da tira, **Engadir**. Un aviso ao pasar ou enfocar: «A primeira é a portada · Arrastra para cambiar a orde». **Nada se aplica ata «Gardar»** (o modal acepta e a barra do modo edición garda), tampouco as subidas nin o que se quita; se se cancela, non queda nada (tampouco na biblioteca). Só se mostra «N cambios sen gardar» cando os hai.
- **Engadir** abre a biblioteca: pestanas «Do concello» e «Debuxos de Furar», subir fotos (JPEG ou PNG ata 8 MB e 40 Mpx; quedan na biblioteca ao gardar), marcar varias e «Engadir N». As que xa ten a instalación vense pero non se marcan.
- **Fóra de alcance agora:** borrar fotos da biblioteca do concello e alt distinto por instalación.

### Usos
Edítanse **dentro da ficha**, sen páxina propia: no modo edición, ao pulsar un uso ábrese o seu modal.

| Dato | Nota |
|---|---|
| Nome | Libre: «Fútbol 11», «Pista completa», «Xornadas de Samaín» |
| Alúgase | **Enteira** (1 reserva por franxa) · **por partes con nome** (Campo A/B; Pista, Lonxitude, Céspede) · **por prazas** (N persoas) |
| Cada reserva dura | 30 · 60 · 90 · 120 min (franxa base; a norma fixa o máximo de franxas seguidas) |
| Cando | Sempre · só unhas datas |
| Bloquea os demais usos | Só se «só unhas datas»: mentres está vixente, só se reserva este uso |
| Días | Todos, de luns a venres, fins de semana ou os que se marquen (no «Cando») |
| Prezo e normas | No mesmo modal (bloques 3 e 4) |

Exemplos: campo de fútbol → «Fútbol 11» enteira e «Fútbol 7» en 2 partes · atletismo → un uso coas partes Pista, Lonxitude e Céspede · pádel → «Pista completa».

### Ficha pública
**Decidido o 2026-10-26** (maqueta aprobada «ficha pública»). Cada instalación que ve a veciñanza ten a súa páxina (`/:concello/instalacions/:id`), á que levan as tarxetas e as suxestións da portada. O nome arriba; á esquerda, a imaxe de portada, a descrición no idioma de quen mira, «Que se pode reservar» (cada uso nunha fila co modo e a duración; o prezo do uso), «Antes de reservar» (normas con valor, pago e normas da instalación), os servizos e «Como chegar» co mapa; á dereita, unha tarxeta fixa co enderezo, o horario (aberto ou pechado agora e cando pecha ou abre; os sete días desde hoxe, hoxe en negriña e os días seguidos co mesmo horario xuntos) e o contacto. Debaixo, se hai algún, «Próximos peches» (decidido o 2026-11-04): os dos sesenta días seguintes, excepcións pechadas, festivos que pechan e bloqueos, cada un coa data ou o rango («12–14 dec.»), «Pechado» ou as horas e o motivo; nunca quen o puxo. **Regras confirmadas o 2026-11-04:** un festivo conta como peche só se a fila «Festivos» da súa tempada está baleira e ese día da semana tiña horario; un bloqueo de parte do día sae coas súas horas; e, ao cambiar ou borrar unha tempada sen fin, as reservas afectadas búscanse ata dez anos. Botón «Reservar» na tarxeta, se a instalación se reserva por internet. Só se ven as publicadas (nin borrador nin de baixa); as pechadas temporalmente, cunha etiqueta.

### Regra para que nunca haxa choques
- **En cada instalación e franxa só pode haber un uso.** Fíxao a primeira reserva; dentro do uso, ata o seu cupo (partes ou prazas).
- **Garántea a base de datos** aínda que dúas persoas reserven á vez: exclusión por instalación e intervalo con uso distinto, e cupo con bloqueo de fila.
- Se dúas cousas se teñen que reservar á vez sen excluírse, son partes do mesmo uso ou instalacións distintas.

### Enderezos
- Columnas na instalación: vía, número, CP, localidade, código INE do concello e coordenadas. Non depende de que o buscador siga funcionando.
- Alta: escribir o enderezo → suxestións do concello → encher campos e chincheta → arrastrar a chincheta se fai falta → se non sae, a man e picar no mapa.
- **Buscador:** CartoCiudad (IGN), sempre a través do servidor de Furar e substituíble. **Mapa:** por defecto o Mapa base do IGN (CC BY 4.0), a través do servidor de Furar; o despregue pode quitalo ou usar outro servidor de teselas (decisión do mantedor, 2026-11-03, ADR-0023).

### Estados e baixa
- **Pechar temporalmente ou dar de baixa con reservas futuras** abre o modal de reservas afectadas (Bloque 2).
- **De baixa:** desaparece para a veciñanza e conserva o historial.

### Quen fai que
Xestión dá de alta e mantén instalacións e usos. Recepción, só o que lle permitan os seus interruptores (Bloque 7).

### Fóra de alcance (fase 2 se o piden nas entrevistas)
Complexo/centro, espazos, fotos e equipamento por parte, medidas e superficie, xestión directa/concesión, orde manual.

## Bloque 1b. Campos personalizados

### Obxectivo
Que cada concello engada datos propios ao concello, ás instalacións e ás reservas (e máis adiante a perfís e entidades) sen tocar código.

### Onde
- **Créanse na pestana «Campos personalizados» de Concello** (`/:concello/xestion/concello/campos`). Saen sós na tarxeta «Datos» de cada instalación (ou na ficha do concello) e edítanse igual ca o resto.
- **Un campo, un sitio** (decidido o 2026-11-04, maqueta 57): no editor, arriba, «Concello | Instalación | Reserva»; debaixo, só «Obrigatorio» e «Quen o ve». Se o campo xa ten valores, o sitio non se cambia («Xa ten valores gardados: non se pode cambiar de sitio»). Para pedir o mesmo dato noutro sitio, créase outro campo.
- **Na reserva:** quen reserva encheos no mesmo paso, antes de «Confirmar reserva»; o persoal ve as respostas no detalle da reserva e quen reservou, as súas. «Quen o ve» só ten «A persoa que reserva e o persoal» e «Só o persoal»: un campo da reserva nunca é público.

### Datos (táboas tipadas)

| Táboa | Que garda |
|---|---|
| `campo` | Definición: código, tipo (texto, texto longo, número, data, si/non, selección, selección múltiple), nome e axuda en gl/es, validacións, se é dato persoal |
| `campo_opcion` | Opcións dunha selección, con etiqueta gl/es |
| `campo_uso` | Onde se usa (concello, instalación ou reserva), co seu obrigatorio e visibilidade: **un por campo** |
| `campo_valor` | Un valor por elemento e campo, cunha columna por tipo: a base de datos garante o tipo |
| `campo_valor_opcion` | Opcións marcadas nunha selección múltiple |

### Regras
- Listas en orde alfabética no idioma de quen mira (calcúlaa o servidor). Sen orde manual.
- Os marcados como dato persoal entran na exportación, borrado e anonimización (RGPD).
- **Desactivar e eliminar** (decidido o 2026-11-04; substitúe «arquivar»), para os campos e para cada opción, con dous botóns só con icona e o seu texto ao pasar o rato: «Desactivar» (ollo tachado; desactivado, «Activar» co ollo) e «Eliminar» (papeleira). Desactivar: deixa de pedirse e de ofrecerse, os datos gardados consérvanse e séguense vendo onde se vían. Eliminar: pregunta sempre; sen datos gardados, bórrase; con datos, o diálogo di que non se pode e ofrece desactivar.
- **Opcións dunha selección:** táboa compacta cunha cabeceira (Código · Galego · Castelán) e unha fila por opción, cos botóns ao final; «+ Engadir opción» debaixo.
- **Axuda entre o rótulo e o campo** (decidido o 2026-11-04 para todos os formularios, [deseno P1-3](../deseno.md)): rótulo, axuda, campo e erro.

## Bloque 2. Horarios e bloqueos

### Horario (sección da ficha)
- **Tempadas** como pestanas enriba da semana, co nome e as datas; a vixente leva «Agora». Cada tempada ten a súa semana. «+ Tempada» pide nome, desde, ata e «empezar co horario de» (copia outra ou baleira). **«Ata» é opcional** («Sen data de fin», decidido o 2026-11-04): unha tempada sen fin cobre todos os días desde o inicio e vese «Desde 1 xan. 2026 · sen fin». **Non se solapan:** se coinciden, avisa e suxire unha excepción para días soltos (unha sen fin choca con calquera posterior). Avísase se quedan días sen tempada.
- **Semana:** unha fila por día (luns a domingo e festivos), en repouso como texto resolto («Luns 8:00 – 23:00», «Pechado»; o día de hoxe cun chip «Hoxe»). **Ao pulsar unha fila** pasa a edición: interruptor aberto/pechado, tramos «desde – ata», ✕ para quitar e «+ Tramo». Ao pulsar fóra volve a resolto.
- **Excepcións:** filas con data, aberto/pechado, tramo e motivo; «+ Excepción». No modal, o interruptor di o estado á vista («Pechado» ou «Aberto» e o tramo). Se a data é festivo, unha nota debaixo avisa sen bloquear: «É festivo (nome). Esta excepción cambia o seu horario.»
- **Festivos:** usan o horario da fila «Festivos» (por defecto o do domingo). Nacionais e autonómicos sementados; os locais, como excepcións.
- **Validación** na pantalla e no servidor: fin despois do inicio, sen pasar da medianoite, sen tramos solapados. «Gardar» desactivado mentres haxa erros.

### Bloqueos (sección da ficha)
- Filas resoltas que se editan ao pulsar: data, horas e motivo; «+ Bloqueo».
- **Modal de reservas afectadas** (ao gardar un bloqueo, pechar temporalmente ou dar de baixa): «Seleccionar todas» e «Cancelar/Manter as marcadas»; por reserva, **cancelar ou manter** e **como avisar** (correo, aviso na web, chamar co teléfono á vista). As canceladas pasan a «cancelada polo concello» sen penalización; nas mantidas o bloqueo non se aplica nesa hora e queda anotado quen o decidiu. Rexístrase a quen se avisou, como e cando. Na fase 1, «aviso na web» é o estado «cancelada polo concello» nas súas reservas; a campá chega cos avisos.

### Compoñentes que hai que construír
- **Selector de hora** propio sobre Angular CDK: a hora vese como texto; ao pulsar, lista curta cada 15 min desprazada ata a actual; teclado (frechas, Intro, Esc).
- **Calendario** propio sobre Angular CDK: panel redondeado con sombra, hoxe cun aro, o elixido recheo da cor principal. Sen Angular Material (ADR-0014). Úsase en excepcións, tempadas, bloqueos e reservas.

## Bloque 3. Normas

### Fase 1 (decidido o 2026-10-26)
Catro normas **no uso**, no seu modal («Condicións da reserva», en acordeón; o nome distíngueas das «Normas da instalación», decidido o 2026-11-04). Todas baleiras por defecto: **baleira = non se aplica**; se ten valor, aplícase; baleirala quítaa.

| Norma | Que di | Exemplo |
|---|---|---|
| Cando reservar | Antelación mínima en horas e máxima en días (as dúas opcionais) | De 2 horas a 7 días antes |
| Duración máxima | Cantas franxas seguidas como moito; móstrase o total | Ata 2 reservas seguidas = 2 horas |
| Límite por persoa | N reservas por día, semana ou mes | 2 reservas por semana |
| Cancelar sen custo | Ata N horas antes do inicio | Ata 24 horas antes |

- **Avalíanse ao reservar e ao cambiar;** a de cancelación, ao cancelar. A reserva garda as normas que se lle aplicaron e a data exacta ata a que se cancela sen custo.
- **Ao cambiar** compróbanse como se a reserva vella non existise (o límite por persoa non a conta).
- **Na ficha pública,** «Antes de reservar» mostra só as normas que teñen valor, o pago (texto do concello) e as **normas da instalación** en texto libre (gl/es), se as hai.
- **Fóra do prazo para cancelar** (validado o 2026-10-30, P1 do [plan do dominio 2](../plan/dominio-2-reservas.md#6-preguntas-de-produto); constrúese este valor): non se pode cancelar nin cambiar por internet; desaparecen «Cancelar» e «Cambiar» e sae «Xa pasou o prazo para cancelar. Chama ao ‹teléfono do servizo›». Sen norma de cancelación, pódese ata o inicio.
- **Límite por persoa** (validado o 2026-10-30, P4): conta por uso e por períodos naturais en hora de Galicia (día, semana de luns a domingo, mes), coas reservas confirmadas, pasadas e futuras, que empezan nese período.

### Capas posteriores
Quen pode reservar (todos ou colectivos) · apertura de reservas programada · duración mínima · non presentados e penalizacións · require aprobación · identidade verificada · **herdanza** concello → instalación → uso.

#### Normas previstas despois da fase 1
Acordadas o 2026-10-24; quedan para as capas posteriores.

| Norma | Exemplo |
|---|---|
| Quen pode reservar | Todos · empadroados · colexios · clubs (con **colectivos** que crea cada concello) |
| Antelación | Mínimo 2 h antes · máximo 7 días |
| Apertura de reservas | «Os luns ás 9:00 ábrese a semana seguinte» |
| Duración | Mínimo 1 h · máximo 2 h seguidas |
| Tope por persoa | 2 por semana · 1 á vez |
| Prazo para cancelar | Ata 24 h antes sen penalización |
| Non presentados | 3 faltas en 30 días → 15 días sen reservar por internet |
| Require aprobación | Non: confírmase ao momento · Si: queda «solicitada» ata que o concello aprobe |
| Identidade verificada | O concello pode esixir Cl@ve ou Chave365 para o que queira (p. ex. prezo de empadroado) |

- **Póñense no concello, na instalación ou no uso;** cada nivel herda do anterior salvo cambio.
- **Avalíanse ao reservar, cambiar e cancelar;** a reserva garda que norma se lle aplicou.

## Bloque 4. Tarifas

### Fase 1 (decidido o 2026-10-26)
- **Prezo no uso**, no seu modal («Duración e prezo»): **Sen prezo** (non se mostra prezo nin se leva cobro) · **Gratis** · **De pago**, cun importe **por reserva** da duración do uso («30,00 € por reserva de 60 min») ou, nos usos por prazas, **por persoa**. Se se reservan varias franxas seguidas, o total é o importe por cada franxa.
- **O prezo queda conxelado na reserva:** cambiar o prezo do uso non toca as reservas xa feitas. Ao cambiar unha reserva, aplícase o prezo do oco novo.
- **Pago no mostrador.** Unha reserva de pago queda **pendente**; o persoal márcaa **pagada**. Se se cancela xa pagada (polo concello, ou pola persoa dentro do prazo), queda **a devolver**; o persoal márcaa **devolta** cando a devolve. Furar non move diñeiro.
- **Os textos de pago e devolución** («No mostrador, ao chegar», «O concello dirache como cha devolve») non van fixos en Furar: escríbeos cada concello nos seus axustes (gl/es), con estes valores por defecto. Desde o 2026-11-03 (maqueta 49) o concello escolle «Igual para todas as instalacións» ou «Distinto por instalación»: entón cada ficha leva os seus, sen herdar os do concello.
- Na lista de xestión hai filtro por pago (pendente, pagada, a devolver, devolta, gratis).
- **Validado o 2026-10-30** ([plan do dominio 2](../plan/dominio-2-reservas.md#6-preguntas-de-produto); como se constrúe): «Sen prezo» non mostra prezo e «Gratis» mostra o chip azul «Gratis», os dous sen «Pendente» (P5); cambiar unha reserva xa pagada só a un oco co mesmo prezo; se non, cancelar e reservar de novo (P3).

### Capas posteriores
Por franxa e tipo de día · suplemento de luz · prezo por colectivo · exencións («exento») · compensar con outra data · lista para Tesourería · prazo para pagar e «débeda pendente bloquea» · pasarela de pago (o porto queda preparado) · herdanza uso → instalación → concello.

#### Tarifas previstas despois da fase 1
Acordadas o 2026-10-24; quedan para as capas posteriores.
- **Prezo base por uso:** por hora ou por persoa (pádel 8 €/h; fútbol 7, 30 €; natación libre 2,50 € por persoa).
- **Por franxa e tipo de día** (mañás máis barato; fins de semana e festivos, máis caro).
- **Suplemento de luz.**
- **Prezo por colectivo** (empadroados −50 %, colexios gratis, federados con prezo propio). **Sen consulta ao padrón:** o xestor engade a persoa ao colectivo «empadroados» cando o acredita.
- **Exencións** marcadas polo xestor (actividades do concello, eventos benéficos).
- **Pago:** pendente · pagada · a devolver · devolta (exento, capa posterior); cóbrase no mostrador.

## Bloque 5. Reservas e acceso

### Fluxo da veciñanza (fase 1, decidido o 2026-10-26)
1. **Busca:** portada (buscador con suxestións e calendario; tarxetas co chip «N ocos hoxe») ou listado **«Instalacións»**: busca que + día, chips de filtro (deportes, cuberta), recuento e **Lista · Mapa**.
2. **Ficha da instalación** (só información): prezos en «Que se pode reservar», «Antes de reservar» e o botón **Reservar**.
3. **Páxina de reservar** (propia, non un modal): **Que · Data · Hora** (coa duración) **· Parte**, cada un nunha ventá flotante (no móbil, desde abaixo) que pasa soa ao seguinte; total e «Continuar». Nas partes, «Tanto me ten» (Furar asigna unha libre).
4. **Sen sesión,** «Continuar» pide entrar e volve ao mesmo paso co escollido. **Con sesión,** «Confirmar reserva» reserva ao momento: non hai pantalla de revisar á parte. **Non se reteñen ocos:** se alguén colleu a hora, aviso suave con tres horas próximas.
5. **Reserva confirmada:** a que correo se enviou, instalación, uso, día, hora, parte e prezo («no mostrador»), **QR co código** («Ensínao ao chegar»), data exacta ata a que se cancela sen custo, «Engadir ao calendario» e «Ver as miñas reservas». O mesmo vai por correo (§ Correos).

### As miñas reservas
**Próximas · Historial.** Cada reserva: instalación, data e hora, uso e parte; prezo e estado do pago na súa liña; botóns **Cambiar**, **Cancelar** e **Ver QR** (no historial, «Reservar de novo»).
- **Cambiar:** a outro día, hora, uso ou parte (na mesma instalación; validado o 2026-10-30, P2 do [plan do dominio 2](../plan/dominio-2-reservas.md#6-preguntas-de-produto)). Nunha soa operación: sóltase a vella, compróbanse as normas sen ela e gárdase a nova; se algo falla, a vella queda como estaba.
- **Cancelar:** diálogo curto que di se é sen custo (dentro do prazo) ou, se xa está pagada, que queda a devolver e como o fai o concello.

### Regras da fase 1
- **Os ocos** empezan na apertura de cada tramo do horario, en pasos da duración do uso (abre 9:30 e o uso dura 60 min → 9:30, 10:30…). Pódense reservar de 1 a N franxas seguidas (N = duración máxima).
- **Usos por prazas: unha persoa = unha praza.** Cada reserva é unha praza, e unha persoa non pode ter dúas no mesmo uso e franxa.
- **Toda reserva confírmase ao momento e ten o seu prezo** (sen aprobación nin exentos na fase 1).
- **Reserva quen ten a conta,** maior de 14 anos, no seu nome.

### Estados
| Estado | Cando |
|---|---|
| Confirmada | Ao reservar (fase 1: sempre) |
| Realizada | Confirmada e xa pasou a hora (calculado) |
| Cancelada pola persoa | Desde as súas reservas |
| Cancelada polo concello | Bloqueo, peche, baixa, cambio de horario ou de uso (modal de reservas afectadas) ou desde a xestión |

**Pago, á parte do estado:** pendente · pagada · a devolver · devolta (gratis e sen prezo, sen cobro). Cores: unha paleta única para toda a app (pagada e confirmada en verde, pendente en ámbar, a devolver e cancelada en vermello, gratis en azul, devolta en gris), sempre con letra branca.

### Capas posteriores
Solicitada, aprobada e rexeitada (require aprobación) · usada e non presentado (QR e asistencia) · reservar a nome doutra persoa con conta («persoas ao meu cargo», menores; o persoal no mostrador ou por teléfono xa se fai, decidido o 2026-11-04) · lista de espera e suxestións se non hai oco · participantes e partido aberto.

#### Fluxo e estados previstos despois da fase 1
Acordados o 2026-10-24; quedan para as capas posteriores.
- **Fluxo:** busca por deporte, instalación ou mapa (filtros por día, hora, cuberto, preto de min) · grella de ocos · revisar (que, cando, prezo, normas) · confirmar: sen aprobación, **confirmada**; con aprobación, **solicitada** e outro correo cando o concello responda · se non hai oco, suxestións (Bloque 9).
- **Estados:** solicitada (require aprobación e está pendente) · rexeitada (o concello non a aproba) · cancelada pola persoa fóra de prazo (conta para as normas) · cancelada polo concello por chuvia ou avaría · usada (Recepción le o QR ou marca a asistencia, ou QR fixo, Bloque 8) · non presentado (pasou a hora sen usala; conta para a penalización).

### Xestión de reservas (fase 1)
- **Reservas:** busca por nome ou código; «Últimas reservas»; «Cobros» (pendentes de pago, a devolver, canceladas, coas súas contas); «Próximas» con filtros (orde, datas, instalación, pago, estado). Ao escoller unha, o detalle ao lado: persoa, uso, día, hora, código, correo, total, «Marcar como pagada» ou «Marcar como devolta» e «Cancelar reserva».
- **Calendario:** día (columnas por instalación), semana e mes; selector de instalación; o mesmo detalle.
- **Modal de reservas afectadas** (bloque 2) coas reservas reais. Avísase por correo ou cun aviso na web; sen «Chamar» (decidido o 2026-11-04; tampouco en «Cancelar reserva»). Nunha reserva por outra persoa, só as canles que ten: o correo, se o deu; o aviso na web, nunca (non ten conta).
- **Conservación** (decidido o 2026-11-04): unha reserva anonimízase 2 anos despois da súa franxa ou do último cambio (as de pago, 5 anos): queda para os indicadores sen persoa e na xestión sae «Persoa anonimizada»; os campos da reserva marcados como dato persoal bórranse entón. Detalle en [`datos-persoais.md`](../datos-persoais.md).
- **Descargar CSV** (decidido o 2026-11-04): botón na barra de «Reservas» (no móbil, só a icona), só para Xestión. Abre un diálogo co período (obrigatorio, como moito 12 meses, co mesmo selector de datas ca os filtros), as instalacións e os estados, cubertos cos filtros da lista (sen datas, o mes en curso). Unha fila por reserva, por día e hora: data, inicio, fin, instalación, uso, persoa, correo, teléfono, estado, pago, importe, código, cando se reservou, quen do persoal a fixo (baleiro se a fixo a persoa; o rol se a conta non ten nome) e unha columna por campo da reserva. UTF-8 con BOM, «;» e coma decimal, horas de Europe/Madrid e cabeceiras no idioma da web; o ficheiro chámase `furar-‹concello›-reservas-‹desde›-‹ata›.csv`. A API faino por tandas e cada descarga queda no rexistro de actividade (período, filtros e número de filas).
- Xestión fai todo; Recepción ve, cobra, reserva no nome de alguén e cambia, pero non cancela (os seus interruptores, bloque 7, son capa posterior).
- **Reserva por outra persoa** (decidido o 2026-11-04, maqueta 58, opción A): para quen non ten conta ou chama por teléfono.
  - **Onde empeza:** botón «Nova reserva» xunto ao título de «Reservas», á vista tamén no móbil; xestión e Recepción (`booking.book-for`).
  - **Formulario** (`/:concello/xestion/reservas/nova`): o mesmo ca o da veciñanza (Que · Data · Hora · Parte, cos mesmos ocos e as normas do uso), máis a instalación, «Para quen?» (nome e apelidos, obrigatorio; correo, para a confirmación co código; teléfono; os campos da reserva, tamén os de «Só o persoal») e, se o uso ten prezo, o pago (Pendente ou Pagada). Sen casiñas de consentimento: os datos son para xestionar a reserva (ADR-0017). Sen conta non se conta o límite por persoa nin «unha persoa, unha praza».
  - **Feita:** volve a «Reservas» co detalle aberto. O detalle e as listas din quen a fixo («Reservada por Lucía Rey · Recepción»), o teléfono e o último correo que saíu («Confirmación enviada o …»); sen correo, «Sen correo: dálle o código …». Con correo, a persoa recibe a confirmación de sempre, sen as ligazóns a «as miñas reservas» (sen conta non ten onde vela): queda o código co QR. A reserva non sae en «As miñas reservas» de ninguén.
  - **Cambiar:** «Cambiar» no detalle (xestión e Recepción, mentres está confirmada e por vir) abre o mesmo formulario relleno (`…/reservas/:id/cambiar`): outro día, hora, uso ou parte da mesma instalación, sen o prazo da persoa; para quen é, só nas que fixo o persoal; o pago e os campos. Sen arrastrar no calendario (non vai con teclado nin no móbil). «Avisar a … por correo», marcado, manda o correo de cambio se cambiou o día ou a hora.

### Correos da fase 1 (decidido o 2026-10-26)
Catro correos con **texto fixo de Furar en gl e es** (no idioma de quen reserva) e a mesma plantilla: **confirmada**, **cambiada**, **cancelada pola persoa** e **cancelada polo concello**. Franxa coa cor do concello e, nunha liña, o logo (ou escudo xenérico), o nome e «Deportes municipais»; tarxeta co título e o día e a hora en grande; instalación e enderezo, uso, parte e prezo (co texto de pago do concello); QR co código; prazo exacto para cancelar sen custo; «Ver a miña reserva» e «Engadir ao calendario», co `.ics` adxunto (sen conta, reservada polo persoal no seu nome, sen eses dous botóns nin «Ver as miñas reservas»: o código co QR); pé co nome, enderezo, teléfono e correo do servizo de deportes e a ligazón a Privacidade (sen «darse de baixa»: son transaccionais). Asunto: que, día e hora.

### Conta e acceso
- **Fai falta conta para reservar por internet.** Sen conta, o persoal reserva por teléfono ou mostrador no seu nome.
- **Entrada:** Cl@ve, Chave365 ou correo (Keycloak). Na demo, Cl@ve simulada (ADR-0019).
- **Contra bots nas contas de correo:** correo verificado antes de reservar; bloqueo da conta tras 5 contrasinais malos (Keycloak); límite de peticións á API por enderezo de rede (decidido o 2026-11-04: 3000 por minuto e, delas, 300 que escriben; 429 ao pasalo); e os topes e penalizacións das normas. O límite por enderezo nas pantallas de acceso e rexistro de Keycloak queda para o cortalumes do servidor: a imaxe de Caddy non trae ese módulo ([`ens.md`](../ens.md)). O reto ALTCHA vai ás capas posteriores.
- **Contas da veciñanza** (confirmado o 2026-11-04): unha conta que non é do persoal só ve os datos públicos do concello (os mesmos ca sen sesión); a súa baixa pídese polas canles de «Dereitos» da «Privacidade» e non hai baixa automática por falta de uso.
- **Modo de rexistro, por concello** (capa posterior; na fase 1, modo aberto):

| Modo | Como entra a veciñanza |
|---|---|
| Aberto | Cl@ve, Chave365 ou correo con antibots |
| Só identidade pública | Só Cl@ve ou Chave365 |
| Con validación do concello | Cl@ve e Chave365 entran directamente; con correo, a conta queda «pendente» ata que o concello a valida (mostrador, teléfono, documentos), ou alta asistida desde o mostrador (chega un correo para poñer contrasinal) |

- **Sempre queda rexistrado quen validou cada alta, cando e con que tipo de documento** («DNI», «certificado da asociación»), sen gardar copia.
- Na fase 1, reservar pide o correo verificado (tamén se se entra con Cl@ve ou Chave365).

#### Acceso previsto despois da fase 1
- **Reto ALTCHA** (MIT, autoaloxado; nada de reCAPTCHA) no rexistro e no acceso, e límite de intentos por enderezo de rede nas pantallas de Keycloak (pasado ao 2026-11-04 desde «Conta e acceso»).

### Entidades (clubs, colexios, asociacións) (capa posterior)
- **Alta:** dáa o concello (nome, NIF, número no rexistro de asociacións, correo e teléfono de contacto) ou solicítaa a entidade e o concello valida cos seus documentos.
- **Responsable:** unha persoa coa súa propia conta (entre por onde entre), que o concello **vincula** á entidade; desde aí reserva e solicita series no nome da entidade. Queda rexistrado quen vinculou e cando.

## Bloque 6. Series (capa posterior)

### Obxectivo
Reservas que se repiten: «Club Narón FS, martes e xoves 19–20 h, 1 set–30 xuño, pista polideportiva».

### Fluxo
1. **Quen a crea:** o xestor directamente, ou o responsable dunha entidade que a **solicita** e o xestor aproba.
2. **Que se indica:** uso (e parte, se ten), días da semana, hora, desde–ata, cada semana ou cada N semanas.
3. **Comprobación de todas as datas.** As que chocan (festivos, bloqueos, outras reservas) **quedan como avisos pendentes da serie**; o modal pódese pechar sen decidir.
4. Ao confirmar créanse as reservas soltas, unidas á serie.
5. **Bandexa «Pendentes»** do panel do xestor: «⚠ Club Narón FS · 8 dec (festivo) · [Saltar] [Mover…]». Mentres non se decide, esa sesión non ocupa nada. Se se achega a data sen decidir, recordatorio ao xestor X días antes (configurable).
6. **Despois:** cancelar ou mover unha sesión, todas desde unha data, ou a serie enteira.
7. Se máis adiante un bloqueo ou festivo coincide, aparece no modal de bloqueo coma calquera reserva.

### Correos asignados
- O xestor pode engadir un ou varios correos a unha reserva ou serie (adestrador, club).
- Reciben a confirmación, o QR e o .ics, e poden reenvialos.
- **Se o xestor o permite, cancelan sen conta:** ligazón no correo → «Cancelar» → código dun só uso enviado a ese correo → confirmar. Unha sesión ou a serie enteira, sempre dentro das normas.
- **Todo correo de reserva lembra as normas importantes:** ata cando se pode cancelar e que pasa se non se presentan.

### Regras
- Prezo por sesión, conxelado en cada unha.
- As series non contan para o tope por persoa, salvo límite propio que poña o concello.

### Fóra de alcance
Convocatoria anual con baremo (os clubs piden horas e o concello reparte por puntuación): fase 2.

## Bloque 7. Persoal e permisos

### Roles

| Rol | Alcance | Que pode facer |
|---|---|---|
| Xestión (`MANAGER`) | Todo o concello | Todo. Non se lle pode quitar ningún permiso |
| Recepción (`STAFF`) | Só as súas instalacións | O que marquen os interruptores |
| Responsable de entidade | A súa entidade | Reservar e solicitar series no seu nome; ver as súas reservas |
| Veciña, veciño | Si mesmo | Reservar, ver, cambiar e cancelar as súas reservas; avisos; exportar ou borrar os seus datos |
| Administración da plataforma | A instalación de Furar | Dar de alta concellos e configuralos enteiros (datos, datos legais, instalacións, campos, persoal), rexistro de actividade (revisión do 2026-11-03). **Non ve** reservas, indicadores nin datos da veciñanza |

### Interruptores de Recepción (por concello)

| Permiso | Por defecto |
|---|---|
| Ver a axenda do día | Sempre |
| Ler o QR e marcar «usada» ou «non presentado» | Activado |
| Reservar no nome de alguén (teléfono ou mostrador) | Activado |
| Cancelar reservas | Desactivado |
| Bloquear un espazo | Desactivado |
| Validar altas no mostrador | Desactivado |
| Vincular responsables a unha entidade | Desactivado |
| Aprobar solicitudes | Desactivado |
| Editar horarios e normas das súas instalacións | Desactivado |

### Regras
- Dous roles fixos de persoal; **sen roles propios**.
- O xestor invita o persoal por correo e asígnalle rol e instalacións. Unha persoa pode ter roles distintos en concellos distintos (Deputación).
- O persoal entra co seu correo e contrasinal, sen segundo factor (decisión do mantedor do 2026-11-02, [ADR-0007](../adr/0007-seguridade-pechada.md)).

### Fase 1 (decidido o 2026-10-28)
Plan: [dominio 4](../plan/dominio-4-persoal.md); decisión técnica: [ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md).
- **Dous roles de persoal, Xestión e Recepción, en todo o concello** (nomes decididos o 2026-11-03, maqueta 50: os mesmos en listas, capas, tarxetas, menús, rexistro de actividade e correos; as claves seguen `MANAGER` e `STAFF`). Recepción ten permisos fixos (os de hoxe: ve e cobra reservas); os interruptores e o alcance por instalación son capa posterior.
- **Pantalla «Persoal»** na xestión (pestana de Concello, `/:concello/xestion/concello/persoal`): lista con usuario (o correo), rol, estado (Pendente, Activa, Desactivada) e último acceso; mentres está pendente, esa columna di como vai a invitación («Invitación enviada o …»; en vermello «Invitación caducada» ou «Non se puido enviar», con «Reenviar» á vista) (revisión do 2026-11-02; quen invitou queda no rexistro de actividade); **Invitar** (correo, rol e idioma do correo; o rol escóllese en dúas tarxetas, Xestión e Recepción, co que pode facer e o que non cada un, igual ca en «Cambiar o rol»; revisión do 2026-11-03), **Reenviar**, **Cambiar o rol**, **Desactivar** e **Reactivar** (P7, decidido o 2026-10-30; antes, «Dar de baixa»). Desactivar confirma o que perde, pide escribir «desactivar» e marcar «Entendo que non se borra a conta nin o seu historial: o acceso pódese reactivar e os datos consérvanse segundo a política de conservación»; pasar unha persoa de Xestión a Recepción (tamén a propia) pide escribir «recepción».
- **Invitación por correo:** ligazón dun só uso que caduca aos 7 días; leva a entrar ou crear a conta. Só a acepta quen entra co mesmo correo, xa verificado. O correo é curto: «Crea a túa conta», quen invita, o concello e o rol nunha frase, «Crear a miña conta», o correo que hai que usar e cando caduca (revisión do 2026-11-03). Ao volver de Keycloak coa sesión, a invitación acéptase soa, **sen modal nin casiña**, e a persoa entra na súa xestión (revisión do 2026-11-03). As **obrigas do persoal** (custodia das credenciais, confidencialidade, avisar se se perden) publícanse no **aviso legal** do concello e a información de privacidade do persoal en «Privacidade»; Furar non pide aceptalas: se o concello o quere, faino co seu procedemento.
- **Desactivar, reactivar e o cambio de rol valen ao momento** (na seguinte acción da persoa). Un concello nunca queda sen ninguén activo en Xestión: nin se desactiva nin se baixa a última persoa xestora, tampouco a si mesma.
- **Desactivar non borra nada** (P7): nin a conta de Keycloak, nin a pertenza (cambia de estado), nin o rexistro de actividade. Co tempo só pasa o que di a política de conservación: correo e nome bloquéanse ao ano da desactivación e anonimízanse aos 3 anos. **Reactivar** devolve o acceso co mesmo rol, sen invitación nova e coa mesma conta; podeno facer quen pode desactivar (a xestión e a plataforma). Se se reactiva antes, os prazos contan desde a seguinte desactivación. Unha persoa xa bloqueada pódese reactivar (o bloqueo só a esconde na xestión mentres está desactivada); unha anonimizada, non: invítase de novo. Unha invitada que nunca aceptou tampouco se reactiva: invítase de novo.
- **A conta de traballo vai á parte da persoal** (P1, decidido o 2026-10-30): unha conta con pertenza activa ao persoal de calquera concello non reserva como veciña en ningún («Esta é unha conta de traballo; para reservar usa a túa conta persoal»; a reserva pública avisa antes de que se intente). Unha conta que xa reservou como veciña, en calquera concello e estado, non acepta unha invitación ao persoal nin se reactiva: a persoa invítase co correo do traballo. Vale en todos os concellos porque o que se separa é o traballo da vida privada da persoa, non un concello doutro. **Actividades do concello** nunha instalación (cursos, escolas, mantemento): non se reservan cunha conta; fanse cun **bloqueo** da instalación co seu motivo, que xa existe (bloque 2).
- **Sen segundo factor** (retirado o 2026-11-02, decisión do mantedor): o persoal entra co correo e o contrasinal, coma a veciñanza (que tamén pode entrar con identidade pública).
- **«O teu último acceso foi o …»** na cabeceira da xestión.
- **Cambiar de concello** (persoal de dous ou máis concellos; decidido o 2026-11-03, maqueta 41): no menú da persoa, unha fila «Concello» co actual á dereita e ›; ao activala, o mesmo menú pasa á lista dos seus concellos, co rol de cada un e ✓ no actual, e «‹ Concello» para volver. Escoller un leva á súa xestión (Xestión, aos datos do concello; Recepción, ás reservas). Cun só concello o menú non cambia. Sen pantallas novas. Substitúe a decisión do 2026-11-02 («os meus concellos» e un selector na barra).
- **Barras e menú da persoa** (decidido o 2026-11-03, maqueta 50): no botón do menú, só o nome (o rol e o concello van na cabeceira do menú). Xestión: Instalacións · Reservas · Indicadores · Concello; Recepción: só «Reservas» (a súa entrada); a plataforma: Instalacións · Concello; a barra inferior do móbil, os mesmos destinos. Ningunha conta de traballo nin a plataforma ve «As miñas reservas»; Recepción tampouco «Ver a web pública».
- **Entrada á xestión** (decidido o 2026-11-04, maqueta 56): ao entrar, a plataforma sen ningún concello vai directa a «Novo concello»; Xestión ou a plataforma, desde a web pública dun concello ao que lle falta algún paso de «Para abrir as reservas», á pestana «Xeral» (onde está a lista); sen pendentes, volve onde estaba. Quen ía á xestión ou a unha invitación segue alí. No menú da persoa, primeiro e en negra: a plataforma sen concellos, «Novo concello»; con concellos, «Concellos ›» (todos, o actual con ✓, leva á sección «Concello» do escollido, e «Novo concello» ao final); Xestión e Recepción, fóra da xestión, «Xestión do concello» (á entrada do seu rol) no canto de «Ver a web pública». Na portada, mentres falte algún paso, quen configura o concello (Xestión e a plataforma) ve a lista de «Xeral» en modo compacto enriba do título: unha fila «Para abrir as reservas: <seguinte paso> · paso N de M» con «Ir» ao sitio dese paso, que se desprega (o título é un botón) para ver a lista enteira; no móbil, en dúas liñas. Recepción, a veciñanza e quen non entrou non a ven.
- **Invitación aberta con outra conta** (decidido o 2026-11-03, maqueta 50): outro correo, a conta da plataforma ou a de quen invitou levan á mesma pantalla, «Xa iniciaches sesión con outra conta.», sen mostrar ningún correo, con «Pechar sesión e continuar» (pecha a sesión e volve á invitación, que leva ao rexistro ou a entrar) e «Volver».
- **Correo da invitación segundo o rol:** a Recepción, asunto e preheader «… invítate a atender as reservas das instalacións deportivas do Concello …»; a Xestión, «… invítate a xestionar …». Os textos curtos van centrados, coma nos correos de Keycloak («Crea a túa conta», «Cambia o teu contrasinal»; maqueta 51).
- **Pantalla «Rexistro de actividade»** (pestana de Concello, `/:concello/xestion/concello/rexistro`), só de lectura, para Xestión e a plataforma: data, persoa e rol, acción, sobre que e resultado; filtros por período, área, persoa e «só fallidos». Ve tamén o que fixo a plataforma no seu concello.
- **Administración da plataforma:** dá de alta concellos (tamén desde un ficheiro furar-export), ve o persoal dun concello e fai con el o mesmo ca o xestor: invita con calquera rol, cambia o rol, desactiva e reactiva (revisión do 2026-11-02). Desde o 2026-11-03 ([ADR-0028](../adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9) configura tamén o concello (datos, datos legais, instalacións e campos personalizados) e le o seu rexistro de actividade. Non ve reservas, indicadores nin datos da veciñanza, e non exporta.
- **Demo:** unha persoa de Recepción e exemplos de persoal invitado, activo e desactivado.

### Capas posteriores
Alcance por instalación e interruptores de Recepción · chaves de acceso e WebAuthn · entrada do persoal con Cl@ve ou certificado de empregado público · revisión periódica de permisos (na fase 1 a lista mostra o último acceso como axuda) · segundo factor · texto das obrigas propio de cada concello · acceso temporal de soporte para a plataforma, que concede o xestor e caduca só (na fase 1 a plataforma non ve datos da veciñanza: o soporte faise co xestor e a consola de Keycloak) · responsables de entidade.

## Bloque 8. Avisos, plantillas e QR

### Avisos
**Fase 1:** só os catro correos do bloque 5; o resto (recordatorio, solicitudes, penalización, oco liberado, campá, activar e desactivar cada aviso) é capa posterior.

**Á veciñanza ou ao responsable:** reserva confirmada (QR e calendario) · solicitude recibida, aprobada ou rexeitada · recordatorio X horas antes (a persoa pode desactivalo) · cancelada polo concello · cancelación confirmada · penalización («non podes reservar por internet ata o…») · liberouse un oco · conta validada.
**Ao persoal:** solicitudes pendentes · avisos pendentes de series · altas por validar. Todo na bandexa «Pendentes»; o correo é opcional para cada persoa.

- **Por correo e na web** (campá dentro da conta), **no idioma de cada persoa**.
- **Envío desde o servidor de correo do concello** ou de quen instale Furar; sen servizos de envío de terceiros.
- **Todo configurable por concello:** activar ou desactivar cada aviso, tempos.

### Plantillas de correo (capa posterior)
- Furar trae plantillas cos textos de cada aviso en gl e es.
- **Adaptar as de Furar** nun modal sinxelo: logo (texto alternativo obrigatorio), cores (do tema do concello), sinatura e texto.
- **Subir a súa propia plantilla HTML:** Furar límpaa, comproba que leva os datos precisos ({nome}, {data}…) e amosa vista previa antes de usala.
- **Editor:** esqueleto fixo en MJML compilado a HTML no build e enchido no servidor Java; corpo e sinatura con TipTap (só o núcleo libre, sen extensións de pago nin nube) e botón «Inserir dato» ({nome}, {instalación}, {data}, {hora}); HTML saneado no servidor; vista previa gl/es; «Envíame unha proba»; restaurar o texto orixinal.

### QR
Na fase 1 o QR só leva o código da reserva; ninguén o le aínda.

- **Asistencia:** Recepción escanea o QR da persoa → «usada».
- Serve para non presentados xustos e para os indicadores de uso real.
- **Opción por concello para pistas sen persoal:** QR fixo na pista que a persoa escanea coa sesión iniciada; marca «usada» só dentro da súa franxa. Risco aceptado: unha foto do QR fixo permite marcar sen ir (só evita a penalización); o concello pode cambiar o QR impreso cando queira, e o anterior deixa de valer.

### Fóra de alcance
Tornos ou portas automáticas co QR: fase 2.

## Bloque 9. Lista de espera e recomendación (capa posterior)

### «Avísame se se libera»
- Se a hora está ocupada, a persoa pulsa «Avísame se se libera».
- Se alguén cancela, avísase a todas as que esperan á vez; resérvao a primeira que o faga (sen asignación automática).
- Límite de esperas activas por persoa (p. ex. 3).
- **Ao pasar a hora, a espera bórrase;** só queda un reconto anónimo como indicador de demanda non atendida.

### Recomendación de ocos
- **Por regras, sen IA externa**, detrás dun porto substituíble.
- **Só suxire ocos que a persoa pode reservar** segundo as súas normas (colectivo, antelación, topes).
- Orde por defecto: mesmo sitio ±1 h → outra pista do mesmo uso á mesma hora → outra instalación próxima o mesmo día → mesmo día e hora outra semana.
- **Despois das suxestións:** «Non che encaixa? Que é o máis importante para ti?» con chips (`Pádel` · `Martes 28` · `19:00` · `Pavillón de Narón` · `Preto de min`) que reordenan as suxestións.

## Bloque 10. Indicadores

### Datos

| Indicador | Exemplo |
|---|---|
| Ocupación | Pista 2: 68 % das horas abertas reservadas en outubro |
| Uso real | 61 % usado, 7 % non presentado (QR) |
| Horas punta | Mapa de calor día × hora |
| Demanda non atendida | 12 esperas para pádel os martes ás 19 h (anónimo) |
| Cancelacións | Pola persoa (dentro/fóra de prazo) ou polo concello |
| Usuarios | Persoas distintas que reservan, por instalación e mes |
| Ingresos | Cobrado, pendente e exento, por instalación e uso |
| RIDEGA | Uso por sexo e idade, **só se a persoa o indica no seu perfil, voluntario**, sempre agregado |

### Regras
- **Panel** con filtros por instalación, espazo, uso e datas; cada gráfico ten tamén a súa táboa (accesibilidade).
- **Descarga en CSV** de cada indicador.
- **Sempre agregado:** o panel nunca mostra nomes; para ver quen reservou está a axenda, cos seus permisos.
- Búscase un panel visualmente coidado e rico en información.

### Panel (decidido o 2026-10-24; a fase 1 recórtao abaixo)
- **Gráficas:** Apache ECharts (Apache-2.0), importando só os gráficos usados e cargado só na pantalla de indicadores. **Cambio do 2026-10-28:** directamente, sen ngx-echarts, cun envoltorio propio (`furar-chart`), porque a envoltura habería que facela igual polo tema, a CSP, a impresión e a accesibilidade ([ADR-0026](../adr/0026-indicadores-fase-1.md)).
- **Filtros:** periodo, instalación, espazo, uso.
- **5 cifras principais**, cada unha cunha minigráfica.
- **8 gráficos:** ocupación por instalación · estado das reservas · ocupación día a día (reservado fronte a usado) · mapa de calor das horas punta · demanda sen atender · cancelacións por motivo · ingresos · usuarios por idade e sexo.
- **Por gráfico:** «Ver datos en táboa» e descarga en CSV.
- **Tema claro e escuro;** paleta validada para daltonismo.

### Fase 1 (decidido o 2026-10-28)
Maqueta `31-indicadores`, opción A (panel: cifras arriba e gráficos en dúas columnas). Só cos datos que xa hai (reservas, horario e usos). Plan: [dominio 3](../plan/dominio-3-indicadores.md).
- **Pantalla «Indicadores»** na xestión (`/:concello/xestion/indicadores`), só para o xestor (P1). Fila de filtros: período (Esta semana · Mes · Trimestre · Ano · Datas…), instalación, uso e «Exportar».
- **Cinco cifras:** reservas, ocupación media, horas reservadas, persoas distintas e cobrado. Cada unha coa súa minigráfica e a comparación co período anterior (sobe en verde, baixa en vermello, igual en gris; sen anterior, «sen datos do período anterior»).
- **Cada cifra ábrese nun modal** coa evolución (liña con área, a etiqueta só no último valor), a táboa e «Exportar»; ábrese cunha transición desde a tarxeta, sen animación se a persoa pide menos movemento.
- **Seis gráficos:** ocupación por instalación (barras) · ocupación día a día (liña) · horas punta (mapa de calor día × hora; as horas sen apertura, en gris «pechado») · cancelacións por motivo (en prazo, fóra de prazo e polo concello, barras apiladas) · ingresos por instalación (cobrado, pendente, a devolver e devolto) · persoas distintas por mes. Cada un con título, unha frase coa conclusión, «Táboa» e «Exportar».
- **Exportar** abre un menú pequeno con PDF e CSV: na cabeceira, todo o panel; en cada gráfico, ese gráfico; no modal, esa evolución. O PDF faino o navegador («Gardar como PDF») cunha folla de impresión que leva os filtros escritos.
- **Tema claro e escuro.** Estados coa paleta da app (verde o positivo, vermello o negativo ou a alerta, ámbar o pendente, azul info, gris o pechado ou plano); cantidades co `primary` do concello e unha escala derivada del.
- **Datos de demo históricos** para que o panel se vexa cheo.
- **Sempre agregado:** nin nomes, nin correos, nin identificadores de persoa na pantalla, no CSV nin no PDF.
- **Definicións:** as do [plan](../plan/dominio-3-indicadores.md) §1.2.4 e o [glosario](../glosario.md) (horas abertas, horas reservadas, ocupación, persoas distintas, cancelación en prazo e fóra de prazo, período anterior); as que están por validar, en «Pendente de decidir».

### Capas posteriores
Uso real e non presentados (precisa o QR na entrada) · demanda non atendida (precisa a lista de espera) · sexo e idade para RIDEGA (precisa o perfil) · «exento» nos ingresos (precisa os colectivos) · indicadores da Deputación entre concellos ([ADR-0006](../adr/0006-multiconcello.md)). A fase 1 tampouco leva o gráfico do estado das reservas nin o filtro por espazo (o espazo desapareceu no bloque 1).

---

## Capas despois da fase 1

Orde decidida o 2026-10-26, sen data: require aprobación · exento · colectivos · reservas a nome doutra persoa · apertura programada · non presentados, penalizacións, «usada» co QR e xustificar falta · prezo por franxa e tipo de día, luz · identidade verificada por norma · compensar con outra data e lista para Tesourería · plantillas de correo editables · lista de espera e suxestións · participantes e partido aberto · series.

## Cambios sobre o xa construído

A base técnica (2026-10-23) fíxose antes de acordar esta especificación. Estes cambios xa se fixeron ao construír os dominios 1 a 4; a lista queda como histórico (manda o resto da especificación e as ADR):

- **Campos personalizados (ADR-0011, módulo `customfields`, `docs/arquitectura.md` §8):** **feito** (`V202610250900`, ADR-0011 revisada): de valores en `custom_fields jsonb` e opcións en `options jsonb` a táboas tipadas (`custom_field`, `custom_field_option`, `custom_field_usage`, `custom_field_value`, `custom_field_value_option`). A reutilización en varios destinos e a orde por lista que se pensaran entón **quedaron fóra**: listas alfabéticas e, desde o 2026-11-04, un campo nun só sitio (bloque 1b).
- **Ocupación (ADR-0012, `docs/arquitectura.md` §9):** os «espazos combinables» en árbore (`parent_id`), os «espazos folla» e o propio espazo substitúense polos **usos da instalación** e a regra «un uso por instalación e franxa». A exclusión pasa a ser por instalación e intervalo con uso distinto; o cupo (partes ou prazas) mantense con bloqueo de fila. **Feito:** ADR-0012 revisada o 2026-10-25 e o 2026-10-26, e `OccupancyModelIT` (D19) ao día.
- **Modelo de instalacións (2026-10-25):** desaparecen complexo e espazo; as columnas de reserva por internet, ocos visibles e cuberta pasan á instalación; os deportes van na instalación.
- **Datos legais do concello (ADR-0017 punto 8):** as columnas engadidas (`accessibility_*`, `dpo_contact`, `e_office_url`, `ine_code`, `dir`, `legal_entity_name`, `privacy_url`) decidíronse sen esta especificación; validadas co mantedor o 2026-10-29 coa pantalla de datos legais, que engade o modo de cada páxina, o aviso legal e o DPD en correo e teléfono (no canto de `dpo_contact`).
- **Normas e tarifas (2026-10-26):** na fase 1 viven no uso, dentro de `facility`; os módulos `policy` e `pricing` previstos non se crean ata a herdanza ([ADR-0025](../adr/0025-reservas-fase-1.md)).
- **Glosario:** «espazo folla» e a árbore de espazos quedan obsoletos; engádense complexo, uso, parte, enderezo, catálogo, modo de rexistro, correo asignado, aviso pendente.

## Pendente de decidir

- ~~**Pantalla de datos legais do concello**~~ **(decidido o 2026-10-29, maqueta 33):** edítaos o xestor en «Datos do concello», cunha pestana por páxina legal (Privacidade, Accesibilidade, Aviso legal) e tres modos por páxina: a páxina de Furar cos seus datos, un texto propio ou a páxina propia do concello. Detalle en [ADR-0017](../adr/0017-datos-persoais.md) punto 8. *(Revisión do 2026-11-03, maqueta 49: na pestana «Legal» da sección «Concello», cun cuarto modo «Sen páxina».)*
- **Validar dúas decisións xa escritas:** a Cl@ve simulada na demo (ADR-0019), ~~validada~~ **(validada o 2026-10-30)**; que a Deputación sexa responsable da conta común da veciñanza (ADR-0017) queda **para revisión legal** co DPD, xunto co modelo de encargo e a conservación.
- **Varios responsables por entidade** (o borrador inicial propuña un; non se acordou).
- ~~**Reservas, fase 1**~~ **(validado polo mantedor o 2026-10-30):** P1–P8 como se constrúen; P9–P13 quedan así de momento e revísanse máis adiante. Detalle no [plan do dominio 2](../plan/dominio-2-reservas.md#6-preguntas-de-produto).
- ~~**Indicadores, fase 1**~~ **(validado polo mantedor o 2026-10-29):** todas como se constrúen; a P8 (período en curso), co detalle decidido. Ver o [plan do dominio 3](../plan/dominio-3-indicadores.md#6-preguntas-de-produto).
- ~~**Persoal, fase 1**~~ **(validado polo mantedor o 2026-10-29 e o 2026-10-30):** P1, P2, P3, P5, P7 e P8 decididas como se di no [plan do dominio 4](../plan/dominio-4-persoal.md#6-preguntas-de-produto) e en «Fase 1» (bloque 7); P4, P6, P10, P11 e P12 como se constrúen. Queda a **P9**: texto das obrigas fixo de Furar e versionado; volver aceptalo ao cambiar e un texto propio do concello, despois. *(Revisada o 2026-11-03: as obrigas van no aviso legal, sen aceptación en Furar; o texto propio do concello é o do aviso legal.)*
- **Deseño:** cristal na barra de accións fixa e iconas **(validadas o 2026-11-04: cristal e Material Symbols)**; cor base do tema por defecto, estilo do botón secundario, cor do aviso «datos persoais» ([ADR-0021](../adr/0021-sistema-de-deseno.md)).
