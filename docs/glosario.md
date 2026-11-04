<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Glosario

Termo de dominio → nome en código ([ADR-0003](adr/0003-idioma.md)). Un termo novo entra aquí antes que no código.

| Galego | Castelán | Código (en) | Nota |
|---|---|---|---|
| Concello | Ayuntamiento | `municipality` | Inquilino: todo dato colga dun (`municipality_id`) |
| Identificador do concello | Identificador del ayuntamiento | `slug` | `naron`; vai no URL |
| Tema | Tema | `theme` | Cores e logo; contraste AA |
| Declaración de accesibilidade | Declaración de accesibilidad | `accessibility` · feature `legal` | RD 1112/2018; ruta `/:concello/accesibilidade` |
| Privacidade | Privacidad | `privacy` · feature `legal` | RGPD art. 13; ruta `/:concello/privacidade` |
| Aviso legal | Aviso legal | `legalNotice` · feature `legal` | LSSI art. 10; ruta `/:concello/aviso-legal`; o texto de Furar é un borrador pendente de revisión legal |
| Datos legais | Datos legales | `legal` (`LegalDetails`) | NIF, domicilio, correo xeral, DPD, sede, INE, DIR3… e o modo de cada páxina legal |
| Modo da páxina legal | Modo de la página legal | `LegalPageMode`: `FURAR`, `TEXT`, `URL`, `NONE` | Páxina desta web · texto propio · páxina propia (as ligazóns de Furar levan a ela) · sen páxina (o pé non a liga; a obriga é do concello) |
| Sección «Concello» | Sección «Concello» | `/:concello/xestion/concello/<pestana>` · `MunicipalitySectionPage` | A configuración do concello en pestanas: Xeral, Aparencia, Servizo, Legal, Persoal, Campos personalizados e Rexistro; cada pestana co seu URL e as de formulario co seu «Gardar» |
| Ámbito do pago e devolución | Ámbito del pago y devolución | `PaymentNotesScope`: `ALL`, `FACILITY` · `payment_notes_scope` | «Igual para todas as instalacións» (os textos do concello) ou «Distinto por instalación» (os de cada ficha, sen herdar; sen texto, os de Furar) |
| Texto propio | Texto propio | `legalTexts` (`LegalTexts`) · `GET/PUT …/legal-texts` | HTML gl/es dunha lista branca, saneado no servidor |
| Editor de texto | Editor de texto | `furar-rich-text-editor` | Títulos, negra, cursiva, listas e ligazón; TipTap en diferido |
| Escribir para confirmar | Escribir para confirmar | `furar-type-to-confirm` · `typeToConfirm`, `acknowledgement` (`furar-confirm`) | Escribir unha palabra («desactivar», «recepción») e, se fai falta, marcar unha casiña co que se entende, para o que custa desfacer; sen distinguir maiúsculas nin tildes |
| Cláusula informativa (primeira capa) | Cláusula informativa (primera capa) | `furar-privacy-notice` · `furar_privacy` | RGPD art. 13 no rexistro (frase xenérica coa ligazón á «Privacidade» do concello) e en «Privacidade»; datos legais de cada concello (ADR-0017, revisión do 2026-11-03). `GET /api/v1/privacy-notice` (do despregue) só devolve os prazos do rexistro de actividade (`activityVisible`, `activityRetention`) |
| Responsable da conta | Responsable de la cuenta | `privacy.controller` | O concello da «Privacidade» (os seus datos legais); a conta común nunha Deputación vai no texto propio do concello (ADR-0017, revisión do 2026-11-03) |
| Encargado do tratamento | Encargado del tratamiento | `processor` | RGPD art. 28; quen aloxa e opera; dato legal opcional do concello (`legal.processor`) |
| Instalación propia / servizo xestionado | Instalación propia / servicio gestionado | — | Modelos de despregue: o concello opera o seu Furar, ou un operador do servizo ofréceo a varios concellos e é o seu encargado (`datos-persoais.md`) |
| Delegado de protección de datos (DPD) | Delegado de protección de datos (DPD) | `dpoEmail`, `dpoPhone` (concello) · `dpoContact` (só ao importar) | Datos legais de cada concello. `dpoContact`, o antigo texto libre, só se le ao importar un ficheiro vello, que o reparte en correo e teléfono |
| Provedor de identidade (Cl@ve, Chave365) | Proveedor de identidad | `identityProvider` (Keycloak) · `clave-simulada` na demo | SAML 2.0 (ADR-0019) |
| Código fonte | Código fuente | `source` (`FURAR_SOURCE_URL`) | EUPL art. 5; `/codigo-fonte` |
| Campo personalizado | Campo personalizado | `custom field` · módulo `customfields` | Definición (`custom_field`) e valores en táboas tipadas (`custom_field_value`), ADR-0011 |
| Definición de campo | Definición de campo | `custom_field` | Código, tipo, nome e axuda gl/es, regras, dato persoal |
| Onde se usa (campo) | Dónde se usa (campo) | `custom_field_usage` · `usages[]` | O sitio do campo (concello, instalación ou reserva) co seu obrigatorio e quen o ve. Un campo, un sitio: con valores, o sitio non cambia (ADR-0011, revisión do 2026-11-04) |
| Campo da reserva | Campo de la reserva | destino `BOOKING` · `BookingCustomFieldTarget` | Campo personalizado que enche quen reserva antes de «Confirmar reserva»; ven a persoa que reserva e o persoal, ou só o persoal, nunca o público |
| Desactivar (campo, opción) | Desactivar (campo, opción) | `INACTIVE` · `inactive` · `POST …/custom-fields/{id}/deactivate` · `…/activate` | Deixa de pedirse e de ofrecerse; os valores gardados consérvanse e séguense vendo; «Activar» devólveo. Substitúe «arquivar» (2026-11-04) |
| Eliminar (campo, opción) | Eliminar (campo, opción) | `DELETE …/custom-fields/{id}` · `customfield.has-values` · `customfield.option-has-values` | Borra un campo ou unha opción só se non ten valores; se os ten, o diálogo ofrece desactivalo |
| Instalación | Instalación | `facility` | O que se reserva: «Pista de pádel 2», «Pabellón de Piñeiros». Un só nivel |
| Estado da instalación | Estado de la instalación | `status`: `ACTIVE`, `TEMPORARILY_CLOSED`, `WITHDRAWN` | Activa · pechada temporalmente · de baixa |
| Borrador | Borrador | `missing` (`SPORTS`, `USES`, `SCHEDULE`) | Instalación á que lle falta algo para publicarse; calcúlase, non se garda |
| Publicar (instalación) | Publicar (instalación) | `published` · `PUT …/publication` | Último paso do asistente: desde entón a veciñanza a ve, se non lle falta nada. A que nace no asistente está sen publicar ata ese «Publicar» |
| Asistente «Nova instalación» | Asistente «Nueva instalación» | `facility wizard` · ruta `instalacions/nova` | Alta paso a paso (8 pasos, ata «Revisar» e «Publicar»); o borrador pode gardarse só co nome |
| Todo o ano | Todo el año | `wholeYear` | Tempada que crea o asistente: desde o 1 de xaneiro do ano en curso e sen data de fin, festivos pechados |
| Tipo de instalación | Tipo de instalación | `type` | Campo, pavillón, pista de pádel, piscina, pista exterior, outro |
| Código RIDEGA | Código RIDEGA | `ridega_code` | Censo deportivo de Galicia |
| Cuberta | Cubierta | `covered` | Para o filtro de días de choiva; nulo = sen dato |
| Reserva por internet | Reserva por internet | `online_booking` · `show_free_slots` | Se non, o persoal reserva e a veciñanza pode ver os ocos |
| Deporte | Deporte | `sport` · `sport_catalog` · `custom_sport` | Catálogo de Furar (plataforma) e os creados polo concello con «+ crear» |
| Servizos e accesibilidade | Servicios y accesibilidad | `amenity` | Catálogo fixo de Furar; textos e iconas na web |
| Foto, portada | Foto, portada | `photo` · `facility image` · `cover` | Ata 12 imaxes por instalación (fotos e debuxos); a primeira é a portada |
| Biblioteca do concello | Biblioteca del ayuntamiento | `photo library` · `library_photo` | As fotos que sube un concello, reutilizables en todas as súas instalacións, co texto alternativo gl/es |
| Debuxo de Furar | Dibujo de Furar | `drawing` | Ilustración propia (CC0) do catálogo da plataforma, que se usa coma unha foto |
| Debuxo suxerido | Dibujo sugerido | `suggested drawing` · `DrawingSuggestion` | O que se ve se a instalación non ten imaxes: polo nome, os deportes ou o tipo, sen IA |
| Subida pendente | Subida pendiente | `pending photo` | Foto acabada de subir que aínda non gardou ningunha instalación; descártase ao cancelar ou ao día |
| Portada do concello | Portada del ayuntamiento | `home` | Páxina de inicio pública (`/:concello`): buscador, instalacións publicadas e «Así de fácil». Non confundir coa foto de portada |
| Instalación publicada | Instalación publicada | `public facility` | A que ve a veciñanza: publicada co «Publicar», nin en borrador nin de baixa (as pechadas temporalmente vense) |
| Complexo | Complejo | `complex` | **Fóra de momento** (2026-10-25); decidirase se fai falta ([especificación](produto/especificacion.md)) |
| Espazo | Espacio | `space` | **Obsoleto** (2026-10-25): a instalación é o que se reserva |
| Uso | Uso | `facility use` | Que se pode alugar nunha instalación e de que forma (`rental_mode`: `WHOLE`, `PARTS`, `SEATS`); un só uso por instalación e franxa |
| Bloquea os demais usos | Bloquea los demás usos | `blocks_others` | Uso con datas que, mentres está vixente, é o único que se reserva |
| Período dun uso | Periodo de un uso | `facility use period` | Rango de datas dun uso «só unhas datas» |
| Parte | Parte | `use part` | Subdivisión con nome dun uso (Campo A, terzo 2) |
| Espazo folla | Espacio hoja | `leaf space` | **Obsoleto** (substituído por usos, ver especificación) |
| Prazas, cupo | Plazas, cupo | `capacity`, `seats` | N persoas por franxa nun uso sen sitio fixo |
| Enderezo | Dirección | `address` | Columnas na instalación (vía, número, CP, localidade, INE, coordenadas) |
| Buscador de enderezos | Buscador de direcciones | `AddressSearch` · `FURAR_GEOCODER` | CartoCiudad (IGN) a través da API; `fixed` sen rede (ADR-0023) |
| Mapa, tesela | Mapa, tesela | `furar-map` · `/tiles` · `FURAR_MAP` | Por defecto o Mapa base do IGN a través de Caddy; opcional (`FURAR_MAP=off`) (ADR-0023) |
| Datos do despregue | Datos del despliegue | `GET /api/v1/deployment-settings` · `DeploymentSettings` | Configuración pública que non depende do concello e que a web le ao usala (hoxe, o mapa e o seu recoñecemento) |
| Catálogo | Catálogo | `catalog` | Lista de plataforma, igual para todos os concellos (deportes, festivos), sen `municipality_id` |
| Franxa | Franja | `slot` · `slot_minutes` · `slots` | Unidade de reserva: dura o que di o uso (30, 60, 90 ou 120 min); unha reserva leva de 1 a N franxas seguidas |
| Oco | Hueco | `availability` · `slots[]` · `freeCount` | Franxa libre que se pode reservar; empeza na apertura de cada tramo, en pasos da duración do uso (ADR-0025) |
| Código de reserva | Código de reserva | `code` (`FUR-XXXX`) | Único por concello; vai no QR, no correo e na busca da xestión |
| Clave de reintento | Clave de reintento | `IdempotencyKey` (cabeceira `Idempotency-Key`) | Texto opaco (un UUID) que manda o cliente ao reservar; a mesma clave coa mesma petición devolve a mesma reserva en vez de crear outra. Única por concello e persoa; dura 24 h ([ADR-0025](adr/0025-reservas-fase-1.md) punto 19) |
| Pegada da petición | Huella de la petición | `fingerprint` | SHA-256 do que se pide ao reservar (instalación, uso, inicio, franxas e parte), sen a clave: distingue un reintento dun uso indebido da clave |
| Cancelar sen custo | Cancelar sin coste | norma `rule_cancel_hours` · `cancel_until` | Ata N horas antes do inicio; a reserva garda a data exacta |
| Estado do pago | Estado del pago | `payment_status`: `NONE`, `PENDING`, `PAID`, `TO_REFUND`, `REFUNDED` | Á parte do estado da reserva: sen cobro · pendente · pagada · a devolver · devolta |
| A devolver | A devolver | `TO_REFUND` | Reserva pagada e cancelada (polo concello, ou pola persoa dentro do prazo): o concello ten que devolver o diñeiro |
| Devolta | Devuelta | `REFUNDED` | O persoal marcou que xa devolveu o diñeiro dunha reserva «a devolver» |
| Estado da reserva | Estado de la reserva | `status`: `CONFIRMED`, `CANCELLED_BY_PERSON`, `CANCELLED_BY_MUNICIPALITY` | Confirmada · cancelada pola persoa · cancelada polo concello; «Realizada» calcúlase (confirmada e xa pasada) |
| Calendario de reservas, vista rápida | Calendario de reservas, vista rápida | `GET …/bookings/calendar` (`view` `DAY`, `WEEK`, `MONTH`) · `peek` na web | Pantalla da xestión con día, semana e mes; a vista rápida é a capa dun día ou dunha hora con ata catro reservas e «Ver o día». As canceladas non ocupan |
| Prezo do uso | Precio del uso | `price_kind` (`NONE`, `FREE`, `PAID`) · `price_cents` | Sen prezo · gratis · de pago, por reserva ou por persoa; conxélase na reserva |
| Normas da instalación | Normas de la instalación | `house_rules` | Texto libre gl/es que se mostra en «Antes de reservar» |
| Caso de uso da persoa | Caso de uso de la persona | `@PersonUseCase` | O que unha persoa fai sobre o seu sen rol no concello: pide sesión e correo verificado (ADR-0007, ADR-0025) |
| Ocupación | Ocupación | `occupancy` | Fila por instalación, uso (e parte ou prazas) e intervalo |
| Horario | Horario | `schedule` (subpaquete de `facility`) | Tempadas, semana, excepcións e festivos. Non hai módulo `calendar` |
| Tempada | Temporada | `season` | Rango de datas coa súa semana; non se solapan. «Ata» é opcional: sen data de fin (`endDate` nulo) cobre todos os días desde o inicio |
| Semana, tramo | Semana, tramo | `season_hours` · `startMinute`, `endMinute` | Unha fila por día (1–7) e a de festivos (8); tramos en minutos 0–1440 |
| Excepción | Excepción | `schedule exception` | Cambio de horario nunha data (aberto cun tramo ou pechado) |
| Festivo | Festivo | `public holiday` · `public_holiday` | Nacionais e autonómicos de Galicia sementados; os locais, como excepción |
| Bloqueo | Bloqueo | `block` | Obras, eventos: un día, desde–ata, motivo |
| Bloqueo da conta | Bloqueo de la cuenta | `bruteForceProtected` · `failureFactor` (Keycloak) | Tras 5 intentos fallidos de acceso, a conta non entra durante 15 minutos; temporal, nunca permanente (`docs/ens.md`, op.acc.6.8) |
| Bloqueo da instalación (concorrencia) | Bloqueo de la instalación | `FacilityLock` · `lockFacility` | Bloqueo de fila que toma toda escritura que cambia a reservabilidade, e `booking` (ADR-0022) |
| Reservas afectadas | Reservas afectadas | `BookingImpact` · `affected[]` · `decisions[]` | Modal ao bloquear, pechar ou dar de baixa: cancelar ou manter e como avisar |
| Norma; Condicións da reserva | Norma; Condiciones de la reserva | `rules` (`UseTerms`) · `rule_*` · módulo `policy` | Regras de reserva; na fase 1, catro no uso (antelación, duración máxima, límite por persoa, cancelar sen custo) e sen módulo `policy` (ADR-0025). No modal do uso, a sección chámase «Condicións da reserva» para non confundila coas «Normas da instalación» |
| Próximos peches | Próximos cierres | `closures` (`PublicClosureResponse`) | Na ficha pública, os días pechados dos sesenta seguintes: excepcións pechadas, festivos que pechan e bloqueos, coa data e o motivo |
| Tarifa | Tarifa | `price` (`UseTerms`) · módulo `pricing` | Na fase 1, o prezo do uso; `pricing` chega cos colectivos e a herdanza |
| Colectivo | Colectivo | `group` (`pricing`) | |
| Exención | Exención | `exemption` | |
| Reserva | Reserva | `booking` | |
| Reserva anonimizada | Reserva anonimizada | `anonymized_at` · `BookingRetentionService` · `booking.anonymize` | A que a conservación deixou sen persoa (2 anos despois da franxa ou do último cambio; as de pago, 5): sen conta, nome, correo nin campos de dato persoal; queda para os indicadores. Na xestión, «Persoa anonimizada» |
| Límite de peticións | Límite de peticiones | `RateLimitFilter` · `common.rate-limited` · `furar.rate-limit.*` | Peticións á API por enderezo IP e minuto; pasado, 429. Non cobre as pantallas de Keycloak |
| Serie | Serie | `booking series` | Reserva que se repite |
| Titular | Titular | `holder` | Persoa ou entidade |
| Veciño, veciña | Vecino, vecina | `resident` | |
| Entidade, club | Entidad, club | `organization` | Club, colexio, asociación; con responsable vinculado |
| Responsable de entidade | Responsable de entidad | `organization manager` | Persoa coa súa conta vinculada pola entidade |
| Modo de rexistro | Modo de registro | `registration mode` | Aberto · só identidade pública · con validación do concello |
| Correo asignado | Correo asignado | `assigned email` | Correo engadido a unha reserva ou serie; pode cancelar con código |
| Aviso pendente | Aviso pendiente | `series conflict` | Data dunha serie que choca, á espera de saltar ou mover |
| Lista de espera | Lista de espera | `waitlist` | «Avísame se se libera» |
| Xestión do concello | Gestión del ayuntamiento | `/:concello/xestion/…` · `session.manage` | Entrada do menú da persoa (Xestión e Recepción, fóra da xestión) que leva á súa xestión; a parte da web onde traballa o persoal. Os roles chámanse «Xestión» (`MANAGER`) e «Recepción» (`STAFF`) |
| Cambiar de concello | Cambiar de ayuntamiento | `GET /api/v1/me/staff-memberships` · `session.switchMunicipality` | Fila «Concello ›» do menú da persoa que é persoal de dous ou máis concellos: lista os seus concellos co rol de cada un e leva á xestión do escollido (ADR-0027 punto 16) |
| Para abrir as reservas | Para abrir las reservas | `section.opening` · `opening-checklist` | Pasos que lle faltan a un concello para abrir as reservas (datos legais e unha instalación activa publicada), en «Xeral» e pregados na portada; avisan sen bloquear nada e desaparecen co todo feito. Só Xestión e a plataforma |
| Persoal | Personal | `staff` · módulo `staff` | Persoas que traballan para o concello en Furar (Xestión e Recepción); non é a veciñanza (`people`). ADR-0027 |
| Xestión | Gestión | rol `MANAGER` | Todo o concello. Nome do rol en toda a interface e nos correos (antes «xestor»; 2026-11-03, maqueta 50) |
| Recepción | Recepción | rol `STAFF` | Atende as reservas (antes «conserxe»; 2026-11-03, maqueta 50). As súas instalacións; na fase 1, todo o concello con permisos fixos (os interruptores son capa posterior) |
| Administración da plataforma | Administración de la plataforma | rol `PLATFORM_ADMIN` · `platform_admin` | Gárdaa Furar, non Keycloak (ADR-0028). Só o que o nomea: alta e importación de concellos, persoal (ver, invitar con calquera rol, cambiar o rol, desactivar e reactivar), configuración do concello (datos e datos legais, instalacións, campos personalizados) e rexistro de actividade (ADR-0028 punto 9). Non ve reservas, indicadores nin datos da veciñanza, e non exporta |
| Primeiro acceso | Primer acceso | `FURAR_ADMIN_EMAIL` · `platform.first-admin` | A conta co correo verificado de `FURAR_ADMIN_EMAIL` pasa a ser a administración da plataforma mentres non hai ningunha; unha soa vez (ADR-0028) |
| Instalación de exemplo | Instalación de ejemplo | `home/examples.ts` | Instalación debuxada coa etiqueta «Exemplo», sen ligazón, que mostra a portada mentres non hai ningunha publicada (ou ningún concello) |
| Pertenza | Pertenencia | `staff_member` · `StaffMember` | Fila que di que unha persoa é persoal dun concello: correo, rol, estado (`INVITED`, `ACTIVE`, `DEACTIVATED`), quen invitou e cando. Unha por concello; os roles saen dela en cada petición |
| Invitación | Invitación | `staff_invitation` · `/:concello/convite` | Correo co que o xestor dá de alta a unha persoa: ligazón cun token dun só uso que caduca aos 7 días; só se garda o seu hash |
| Proba da invitación | Prueba de la invitación | `registrationProof` · `furar_invitation` · `FURAR_INVITATION_PROOF_SECRET` · `furar-invitation-email` | Texto asinado pola API (HMAC co segredo compartido con Keycloak) co correo e a invitación, que a web leva ao rexistro de Keycloak: co mesmo correo, a conta nace co correo verificado, sen outro correo. Caduca aos 30 minutos ou coa invitación e vale unha vez |
| Desactivar (persoal) | Desactivar (personal) | `deactivate` · `DEACTIVATED` · `staff.deactivate` | Retirar o acceso dunha pertenza; vale na seguinte petición. Non se borra nada (nin a conta, nin a fila, nin o rexistro); pódese reactivar. Ata o 2026-10-30, «dar de baixa» (`revoke`, `REVOKED`, `staff.revoke`) |
| Reactivar (persoal) | Reactivar (personal) | `reactivate` · `staff.reactivate` | Devolver o acceso a unha pertenza desactivada que aceptou, co mesmo rol e sen invitación nova; os prazos de conservación contan desde a seguinte desactivación. Non vale coa anonimizada |
| Conta de traballo | Cuenta de trabajo | `workAccount` · `booking.StaffAccounts` · `booking.staff-account` | Conta con pertenza activa ao persoal dalgún concello: non reserva como veciña en ningún. A conta persoal (con reservas) non acepta invitacións (`staff.invitation-personal-account`) |
| Obrigas do persoal | Obligaciones del personal | `legalNotice.staffItems.*` | Texto fixo de Furar (custodia das credenciais, confidencialidade, avisar se se perden) publicado no aviso legal de cada concello; Furar non pide aceptalo (desde o 2026-11-03): a aceptación, se a hai, é un procedemento do concello |
| Lista de contrasinais comúns | Lista de contraseñas comunes | `passwordBlacklist` | Contrasinais de 12 caracteres ou máis que o realm rexeita por seren comúns; lista propia de Furar (CC0) na imaxe de Keycloak |
| Ligazón curta | Enlace corto | `ShortLink` · `/auth/realms/furar/l/{código}` | Baixo o botón dos correos de Keycloak: código de 12 caracteres sen letras que se confundan que redirixe ao URL da acción e caduca con el (extensión de Keycloak, ADR-0007) |
| Último acceso | Último acceso | `last_sign_in_at` · `previousSignInAt` | Data do acceso anterior á xestión, que se lle mostra á persoa («O teu último acceso foi o …») |
| Rexistro da plataforma | Registro de la plataforma | `/plataforma/rexistro` · `audit.platform-view` | Rexistro de actividade da administración da plataforma: os seus propios eventos e os que non teñen concello (ADR-0020) |
| Correo de proba | Correo de prueba | `test-mail` · `scripts/instalar.sh --probar-correo` | Correo que envía o instalador ao de administración polo SMTP do `.env`, co mesmo camiño ca a API, antes do primeiro arranque; di se o servidor o aceptou ou por que non ([instalacion.md](instalacion.md)) |
| Rexistro de actividade | Registro de actividad | `audit_event` · `AuditLog` · `AuditTrail` · `/:concello/xestion/concello/rexistro` | Quen fixo que, cando, con que rol e resultado; só engadir. O xestor le o do seu concello (ADR-0020) |
| Bloqueo de datos (evento bloqueado, persoa desactivada) | Bloqueo de datos | `AuditRetention` (`visible`, `retention`) · `blocked` · `audit.export-blocked` · `POST …/audit-events/blocked-exports` | LOPDGDD art. 32: o dato xa non se usa nin se ve, só se garda para responsabilidades e só se pon á disposición de xulgados, Ministerio Fiscal, autoridade de protección de datos ou dun expediente aberto. No rexistro, de 1 a 3 anos (`FURAR_AUDIT_VISIBLE`, `FURAR_AUDIT_RETENTION`); o xestor expórtao cun motivo. Non confundir co bloqueo dunha instalación (`block`) (ADR-0020) |
| Persoas | Personas | módulo `people` | |
| Aviso | Aviso | `notification` · módulo `notification` | |
| Indicador | Indicador | `indicator` · módulo `indicators` · permiso `indicators.view` | Cifra ou gráfico agregado do panel do xestor; nunca leva nomes (ADR-0026). Non `metrics`, que é o `/actuator/metrics` da operación |
| Horas abertas | Horas abiertas | `openMinutes` | Σ dos tramos abertos das instalacións do filtro no período (tempada, festivos e excepcións, menos bloqueos); denominador da ocupación |
| Horas reservadas | Horas reservadas | `reservedMinutes` · `reservedHours` | Σ duración × peso das reservas confirmadas: 1 enteira, 1/nº de partes por partes, 1/prazas por prazas |
| Ocupación (indicador) | Ocupación (indicador) | `occupancy` (`{reservedMinutes, openMinutes}`) | Horas reservadas / horas abertas, ata o 100 %. Non confundir coa fila de ocupación dunha reserva |
| Persoas distintas | Personas distintas | `people` | Contas distintas que teñen reservas confirmadas no período (o período enteiro, non a suma dos meses) |
| Cancelación en prazo | Cancelación en plazo | `CANCELLED_BY_PERSON` con `cancelled_at < cancel_until` | Cancelada pola persoa antes do límite para cancelar sen custo |
| Cancelación fóra de prazo | Cancelación fuera de plazo | `CANCELLED_BY_PERSON` con `cancelled_at ≥ cancel_until` | Cancelada pola persoa despois do límite; na fase 1 sempre 0 (por internet non se pode) |
| Período, período anterior | Periodo, periodo anterior | `period` (`WEEK`, `MONTH`, `QUARTER`, `YEAR`, `CUSTOM`) · `previous` | Semana ISO, mes, trimestre, ano ou rango libre en hora de Galicia; o anterior é a unidade anterior ou, nun rango, os mesmos días xusto antes |
| Período en curso, ata hoxe | Periodo en curso, hasta hoy | `partial` · `comparison.toDate` · `valueToDate` · `previousToDate` | O período (ou o tramo dunha serie) que contén hoxe. A súa variación compara do primeiro día a hoxe co mesmo tramo do anterior; nas liñas vai discontinuo e nas táboas, «(en curso)» (ADR-0026, revisión do 2026-10-29) |
| Evolución | Evolución | `evolution` · `evolutionUnit` | Serie dunha cifra ata o período actual (12 semanas, 12 meses, 8 trimestres ou 5 anos); a minigráfica son os seis últimos valores |
| Recomendación | Recomendación | módulo `recommendation` | |
| Plataforma | Plataforma | módulo `platform` | Novidades, versión, auditoría |
| Non presentado | No presentado | `NO_SHOW` | |
| Dato persoal | Dato personal | `personalData` | RGPD |
| Permiso | Permiso | `Permission` | Acción permitida; matriz en `RolePermissions` |
| Quen actúa | Quien actúa | `CurrentActor` | Usuario e os seus roles por concello |
| Tarefa de plataforma | Tarea de plataforma | `@PlatformTask` | Único código que pode ler todos os concellos |
| Valores de campos | Valores de campos | `CustomFieldValues` | Mapa validado; só o crea `customfields` |
| Dobre factor | Doble factor | — | Retirado o 2026-11-02 (decisión do mantedor): ningunha conta o usa. Ata entón, OTP ou código de recuperación por autenticación escalonada (`acr_values=furar-2fa`, `auth.step-up-required`) para o persoal e en cada acceso para a plataforma (ADR-0007, ADR-0027) |
| Copia cifrada | Copia cifrada | `FURAR_COPIAS_AGE_DESTINATARIO` | age; a chave privada, fóra do servidor |
| Volume de produción | Volumen de producción | `pgdata-prod` | Distinto do de `full`: a demo nunca pasa a produción |
| Consulta entre módulos | Consulta entre módulos | `XxxQueries` · `XxxSummary` | API pública síncrona dun módulo, con records (ADR-0005, A19) |
| Seguridade a nivel de fila | Seguridad a nivel de fila | Row Level Security · `furar.municipality_id` | Política por concello en PostgreSQL (ADR-0006) |
| Responsable / encargado do tratamento | Responsable / encargado del tratamiento | — | RGPD arts. 4, 26 e 28 (ADR-0017) |
| Exportación do concello | Exportación del ayuntamiento | `furar-export` · `MunicipalityDataSection` · `municipality-imports` | Portabilidade en JSON, versión 3 (le da 1 á 3); as reservas aínda non entran (ADR-0018) |
| Servizo de migración | Servicio de migración | `migrate` · `FurarMigration` | Aplica Flyway como dono do esquema e sae; a API non ten o seu contrasinal (ADR-0015) |
| Forxa | Forja | — | GitHub ou GitLab (A Eira de Mancomún) onde vive o repositorio |
| Modo edición | Modo edición | `EditSession` | «Editar» pon a ficha enteira en edición; a barra fixa garda ou cancela todo |
| Barra de accións | Barra de acciones | `furar-action-bar` | Barra fixa ao pé con «Cancelar» e «Gardar» (modo edición; reserva) |
| Ficha pública | Ficha pública | `PublicFacilityPage` | Páxina dunha instalación para a veciñanza: `/:concello/instalacions/:id` |
| Eliminar instalación | Eliminar instalación | `deleteFacility` | Borrado de verdade, só se nunca tivo reservas; se as tivo, dáse de baixa |
| Acción en curso | Acción en curso | `ActionScope` · `furarAction` (`busy`) | Mentres corre, o resto de accións do mesmo contexto (páxina, capa ou `furarActionScope`) quedan bloqueadas (`deseno.md` §8) |
| Toast de «feito» | Toast de «hecho» | `furar-toast` · `ToastService` | Aviso flotante do que saíu ben: un só á vez, marcha aos 6 s; erros e avisos quedan fixos na páxina (`deseno.md` §8) |
