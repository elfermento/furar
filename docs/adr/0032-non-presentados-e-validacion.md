<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0032 · Validación do QR e non presentados: asistencia á parte do estado, falta automática ou manual e bloqueo por concello

- **Estado:** aceptada · 2026-11-10 (decidida polo mantedor) · construída o 2026-11-10 (API, web e probas, tamén as de punta a punta no navegador)
- **Fonte de produto:** [especificación](../produto/especificacion.md), bloques 3, 5 e 8 («Validar QR» e «Non presentados»), decidido o 2026-11-10.
- **Revisa:** [ADR-0025](0025-reservas-fase-1.md) puntos 12 e 13 (estados; o QR xa se le) e [ADR-0020](0020-rexistro-de-actividade.md) (o motivo de «Validar igualmente» vai nos detalles).
- **Revisada 2026-11-10 (decisión do mantedor, «a reserva é do correo», [ADR-0031](0031-persoas-a-cargo.md) punto 7):** as faltas e o bloqueo cóntanse por correo, teña conta ou non; as reservas do mostrador con correo tamén contan (puntos 2, 7 e 10).
- **Revisada 2026-11-10 (revisión do deseño antes de construír):** unha reserva con marca non se cancela nin cambia por internet (punto 2); o bloqueo garda que reservas o causaron (punto 7); a tarefa non pisa unha validación simultánea (punto 5); textos da web (punto 14).
- **Depende de** [ADR-0031](0031-persoas-a-cargo.md): a súa migración dos participantes crea `booking.holder_email_key`; as migracións desta ADR van despois.
- **Revisada 2026-11-10 (respostas do mantedor):** os valores propostos nas preguntas abertas quedan decididos e os textos legais adáptanse (punto 13). Non queda ningunha pregunta de produto aberta.

- **Revisada 2026-11-10 (ao construír; correccións técnicas, sen cambiar o decidido):**
  - As migracións son `V202611101800__booking_attendance.sql`, `V202611101810__facility_entry_control.sql`, `V202611101811__municipality_no_show_policy.sql`, `V202611101820__booking_no_show_block.sql` e `V202611101830__notification_no_show.sql`: van despois das de [ADR-0031](0031-persoas-a-cargo.md) (`…1700` e `…1710`), así que as horas do punto 2 non valen.
  - A marca vai no agregado (`Booking.mark()`, `AttendanceMark`): cancelala ou cambiala o persoal bórraa no mesmo `update` que cambia o estado, e deixa unha fila `ATTENDANCE_UNDONE` na historia. Corrixir só o correo non a borra: a falta pasa a contar para o correo novo e, se causara un bloqueo, levántase.
  - A tarxeta «Non presentados» gárdase con `PUT …/municipalities/{slug}/no-show-policy` (co `If-Match` do concello, coma o contacto e as notas de pago); a acción do rexistro é `municipality.no-show-policy`.
  - O control de entrada vai na sección Datos da instalación (`details.entryControl`, un booleano; a data de acendido non sae da API). Viaxa en `furar-export` co resto dos datos da instalación; coma a penalización, ao importar conta desde ese momento.
  - `BookingNoShow.faults` vale 0 coa penalización apagada: o correo sae sen a frase do bloqueo. Cando a persoa xa está bloqueada e chega outra falta, o aviso suma as do bloqueo e as de despois.
  - Un bloqueo só se levanta mentres está vixente: desfacer unha falta dun bloqueo xa cumprido non o toca (as súas faltas xa non contan).
  - Marcar «non presentado» sobre unha reserva que xa o está non escribe nada (nin outra fila nin outro correo).
  - A xanela das faltas empeza ao comezo do día (hora de Galicia) de hai `window_days` días.
  - O aviso do persoal (`noShow`) vai no detalle da reserva sempre que teña «non presentado», correo e a penalización estea acesa: ao marcala e tamén ao reabrir a ficha.
  - `for update skip locked`: a regra A8b lía «skip» como unha táboa; o analizador das probas (`NativeSqlScope`) recoñece agora esa cláusula, coa súa autoproba.
  - Claves da web: `booking.manage.noShow` non pode ser texto e grupo á vez, así que o botón é `booking.manage.noShow.action`; na ficha, desfacer a asistencia di que desfai, para non confundilo co «Desfacer» do cobro: `booking.manage.noShow.remove` («Quitar a falta» / «Quitar la falta») e `.removeValidation` («Quitar a validación» / «Quitar la validación»); en «Validar QR» segue «Desfacer»; os textos da persoa bloqueada van en `booking.reserve.blocked.*`; o singular vai en claves á parte (`countOne`, `blockedOne`, `bodyOne`, `bodyNoPhoneOne`). Textos novos que a táboa non traía: `validate.mode.label`, `validate.row.*`, `validate.at`, `validate.onDay`, `validate.code.placeholder`, `validate.result.already.bodyNoName` e `booking.manage.detail.mail.NO_SHOW` («Aviso da falta enviado o …»).
  - Na ficha, cunha marca de asistencia a etiqueta «Realizada» non sae: queda a do pago xunto a «Validada» ou «Non presentado».
  - No móbil, coa cámara, un resultado sen botóns propios (xa validada, cancelada, inexistente) leva «Validar outra» para volver ler: non hai campo que borrar.
  - Con cinco destinos, a barra inferior da Xestión mostra Instalacións · Reservas · Validar QR e pasa Indicadores e Concello a «Máis» (a regra dos catro sitios que xa había).
  - Probas: a regra da penalización sen base próbase en `NoShowRuleTest` (reservas) e `NoShowPolicyTest` (os rangos do concello); o motivo no rexistro, en `ValidateBookingIT`; a conservación dos bloqueos, en `NoShowPenaltyIT`. As de punta a punta van nun ficheiro propio, `web/e2e/asistencia.e2e.ts` (validar por código en prazo e fóra de prazo co motivo, marcar e quitar unha falta na ficha co seu correo, e a persoa bloqueada, coa resposta de `…/me/booking-block` simulada porque o bloqueo real depende do paso do tempo). Sen probar: os lotes de 500 e a cámara cun fluxo de vídeo simulado.
- **Revisada 2026-11-11 ([ADR-0034](0034-datos-abertos.md)):** a tarxeta «Non presentados» de Concello › Xeral pasa a ser a fila «Penalizar as faltas» da tarxeta «Configuración adicional»; sae `section.noShow.title` e entra `section.noShow.help`. A API e a regra non cambian.
- **Revisada 2026-11-12 ([ADR-0039](0039-tempada-de-entidades.md), punto 14):** as reservas dunha entidade non teñen correo de titular: nunca contan para a penalización nin mandan correo de falta, e `NoShowService` déixaas fóra da falta automática; o persoal pode marcalas a man.

## Contexto
Unha reserva só está confirmada ou cancelada e o QR só leva o código: ninguén o le. O concello non sabe se a persoa veu, e quen reserva e non vén quítalle a hora a outra persoa sen consecuencia. Hai instalacións con alguén na porta e outras sen ninguén, e concellos que queren penalizar as faltas e outros que non.

## Decisión
1. **A asistencia vai á parte do estado, coma o pago.** `status` non cambia (`CONFIRMED`, `CANCELLED_*`): unha reserva validada ou con falta segue ocupando a súa hora, contando no límite por persoa e nos indicadores. Columna nova `booking.attendance`: `NONE`, `USED` (validada) ou `NO_SHOW` (non presentado). «Realizada» segue calculándose.
2. **Modelo** (a hora das migracións axústase á do día en que se creen).
   - **Unha reserva con marca e a cancelación** (confirmado polo mantedor o 2026-11-10): unha reserva **validada** ou con **«non presentado»** xa non se cancela nin se cambia por internet (409 `booking.attendance-marked`). Se a cancela ou a cambia o persoal, ou a cancela o concello (bloqueo, peche), a operación **borra a marca** na mesma transacción (`attendance = 'NONE'`, con quen e cando) e, se era unha falta que causara un bloqueo, levántao (punto 7). Así o `check (attendance = 'NONE' or status = 'CONFIRMED')` nunca se viola.
   - `V202611101300__booking_attendance.sql`: `attendance text not null default 'NONE'`, `attendance_at timestamptz` e `attendance_by text` (o último cambio da marca, tamén un «Desfacer»; o `sub` do persoal ou `furar:no-show`), con `check (attendance in ('NONE', 'USED', 'NO_SHOW'))`, `check (attendance = 'NONE' or status = 'CONFIRMED')` e `check ((attendance_at is null) = (attendance_by is null) and (attendance = 'NONE' or attendance_at is not null))`. Índice parcial para a tarefa: `(municipality_id, facility_id, upper(period)) where status = 'CONFIRMED' and attendance = 'NONE' and attendance_by is null`. `booking_event.kind` gaña `VALIDATED`, `NO_SHOW` e `ATTENDANCE_UNDONE`. Ao anonimizar a reserva, `attendance_by` queda (é o persoal ou a tarefa, coma `booked_by`).
   - `V202611101310__facility_entry_control.sql`: `facility.entry_control_since timestamptz` (nulo = sen control de entrada). O interruptor garda o momento en que se acende: só as reservas que empezan despois poden pasar soas a falta.
   - `V202611101311__municipality_no_show_policy.sql`, columnas de `municipality` (a táboa dos concellos non leva RLS, `TenancyAllowlist`): `no_show_penalty_since timestamptz` (nulo = apagada, o valor por defecto), `no_show_threshold int not null default 3` (1–20), `no_show_window_days int not null default 30` (1–365) e `no_show_block_days int not null default 15` (1–365). Van en `MunicipalityResponse` (`noShowPolicy {enabled, threshold, windowDays, blockDays}`) e en `furar-export` como campo opcional do concello (sen el, apagada; a versión do formato non sobe).
   - `V202611101320__booking_no_show_block.sql`, táboa do concello con RLS e a política `booking_no_show_block_tenant` na mesma migración: `booking_no_show_block(id, municipality_id, holder_email_key, starts_at, ends_at, lifted_at, lifted_by, created_at)` e a súa filla `booking_no_show_block_fault(municipality_id, block_id, booking_id)` (clave primaria as tres, FK compostas ao bloqueo e á reserva, RLS e política `booking_no_show_block_fault_tenant`): **que reservas causaron cada bloqueo**. Unha táboa filla e non un `uuid[]`, porque a base garante que cada falta é unha reserva do concello e a conta faise cun `not exists`. O bloqueo leva `unique (municipality_id, id)`, `check (ends_at > starts_at)` e índice `(municipality_id, holder_email_key, ends_at)`; o correo vai normalizado, coma `booking.holder_email_key`.
   - `V202611101330__notification_no_show.sql`: `notification_delivery_kind_ck` admite `NO_SHOW`.
3. **Marxe de validación:** desde 45 minutos antes do inicio ata 45 minutos despois do final (20:00–21:00 → 19:15–21:45). É unha duración (`furar.booking.validation-margin`, `PT45M`) sobre os `Instant` da franxa: non depende da zona horaria.
4. **Validar.** `POST /api/v1/municipalities/{slug}/bookings/validations` con `{code, force?, reason?}`, baixo o bloqueo da fila da reserva (`for update`; non toca a ocupación, así que non precisa o bloqueo da instalación). Responde 200 con `{outcome, booking, attendanceAt, attendanceBy}`: a pantalla precisa os datos da reserva en todos os casos.

   | `outcome` | Cando | Escribe |
   |---|---|---|
   | `VALIDATED` | Confirmada, sen validar e dentro da marxe; ou fóra da marxe con `force` e motivo | `attendance = USED`, fila `VALIDATED`, rexistro |
   | `OUT_OF_WINDOW` | Confirmada e fóra da marxe, sen `force` | Nada |
   | `ALREADY_VALIDATED` | Xa estaba validada (cando e quen) | Nada |
   | `CANCELLED` | Cancelada (quen e cando) | Nada |

   Un código que non existe no concello dá 404 `booking.not-found`. `force` sen motivo (1 a 200 caracteres), 422 `booking.validation-reason-required`; `force` sobre unha cancelada, 409 `booking.not-validatable`. Validar unha reserva marcada «non presentado» quita a falta (e o bloqueo que causase, punto 7). Repetir a mesma lectura non cambia nada.
5. **Falta, segundo a instalación.**
   - **Con control de entrada** (`entry_control_since` con valor): tarefa `NoShowService` (`@PlatformTask`, `@Scheduled(cron = "${furar.booking.no-show-cron:0 */10 * * * *}")`, coma `BookingRetentionService`): en cada concello, con `TenantContext.runAs` e a súa RLS, en lotes de 500 e unha transacción curta por lote, marca `NO_SHOW` as reservas confirmadas con `attendance = 'NONE'` e `attendance_by is null`, da instalación, que empezaron despois de `entry_control_since` e cuxa fin máis a marxe xa pasou. O lote escolle as filas con `for update skip locked` e o `update` repite `status = 'CONFIRMED' and attendance = 'NONE' and attendance_by is null`: unha validación ou cancelación que chega á vez non se pisa (quen ten a fila gaña e a tarefa sáltaa). `booking` non le a táboa de `facility`: pídelle as instalacións con control a `FacilityQueries.entryControl()` (id e desde cando). Un fallo nun concello non para os demais; é idempotente.
   - **Sen control de entrada:** márcaa o persoal co botón «Non presentado» da ficha dunha reserva xa rematada: `PUT …/bookings/{id}/attendance` con `{attendance: NO_SHOW}` e `If-Match`. Antes da fin, 422 `booking.no-show-too-early`; xa validada, 409 `booking.already-validated`; cancelada, 409 `booking.attendance-cancelled`.
   - **Desfacer** (validación ou falta, por erro ou xustificada): `DELETE …/bookings/{id}/attendance` con `If-Match` → `NONE`, con `attendance_at` e `attendance_by` de quen desfai. Por iso a tarefa non a volve marcar (`attendance_by is null` só nas nunca tocadas); o botón «Non presentado» volve saír. Sen nada que desfacer, 409 `booking.attendance-not-marked`.
   - Só conta non presentarse: non hai cancelación tardía por internet.
6. **Permiso** `booking.attendance` (`BookingPermission`, fila en `RolePermissions`): MANAGER e STAFF. Cobre validar, marcar a falta e desfacer; é o interruptor «Ler o QR e marcar usada ou non presentado» do bloque 7, que na fase 1 é fixo. A plataforma non o ten (non ve reservas). O interruptor da instalación gárdase co permiso da ficha e a tarxeta «Non presentados» con `municipality.update`.
7. **Penalización, por concello e apagada por defecto.** Cos tres números (previsto 3 / 30 / 15). Unha falta nun concello non conta noutro: todo vai en táboas do concello, coa súa RLS.
   - **Que faltas contan:** contan **por correo** (`holder_email_key`), teña conta ou non: as reservas `NO_SHOW` dese correo cuxa franxa rematou nos últimos `window_days` días naturais (hora de Galicia), despois de acender a penalización, e que non están entre as que causaron un bloqueo **sen levantar** (`booking_no_show_block_fault`). Tamén as que fixo o persoal no mostrador, se levan correo. Unha reserva de N prazas é unha falta, do titular ([ADR-0031](0031-persoas-a-cargo.md)). As reservas sen correo márcanse, pero non contan nin bloquean.
   - **Bloqueo:** ao marcar unha falta (a tarefa ou o persoal), na mesma transacción, se o correo chega ao límite créase o bloqueo, desde agora ata o final do día (Europe/Madrid) de hoxe máis `block_days`, coas faltas que o causaron na táboa filla. Os números conxélanse: cambialos despois non o move.
   - **Cumprido ou levantado:** un bloqueo **cumprido** empeza a conta de cero (as súas faltas xa non contan). Desfacer unha das faltas que o causaron (ou validala, cancelala o persoal ou corrixirlle o correo) **levántao** (`lifted_at`, `lifted_by`); un bloqueo levantado **non** pon a conta a cero: as demais faltas seguen contando na súa xanela e a seguinte pode bloquear de novo.
   - **Efecto:** `POST …/me/bookings` dá 403 `booking.blocked-no-show` (`params`: `until`, `faults`, `windowDays`) se a penalización está acesa e hai un bloqueo vixente sen levantar para o correo verificado do token (quen crea a conta despois de faltar no mostrador tamén queda bloqueado). Só as reservas novas por internet: as feitas mantéñense e a persoa pode cambialas e cancelalas; o persoal pode reservar para ela no mostrador. Nunca diñeiro.
   - **Para a pantalla:** `GET …/me/booking-block` (`@PersonUseCase`) → `{blocked, until, faults, windowDays}`. A resposta de marcar a falta leva `noShow {faults, windowDays, threshold, blockDays, blockedUntil}` para o aviso do persoal.
8. **Erros** (`BookingError` e `MunicipalityError`):

   | Código | HTTP | Galego | Castelán |
   |---|---|---|---|
   | `booking.validation-reason-required` | 422 | Escribe o motivo para validala. | Escribe el motivo para validarla. |
   | `booking.not-validatable` | 409 | Unha reserva cancelada non se valida. | Una reserva cancelada no se valida. |
   | `booking.no-show-too-early` | 422 | A reserva aínda non rematou. | La reserva aún no ha terminado. |
   | `booking.already-validated` | 409 | Esta reserva xa está validada. Desfai a validación antes. | Esta reserva ya está validada. Deshaz la validación antes. |
   | `booking.attendance-not-marked` | 409 | Esta reserva non ten nada que desfacer. | Esta reserva no tiene nada que deshacer. |
   | `booking.attendance-cancelled` | 409 | Unha reserva cancelada non se marca. | Una reserva cancelada no se marca. |
   | `booking.attendance-marked` | 409 | Esta reserva xa está validada ou anotada como falta: xa non se pode cambiar nin cancelar por internet. | Esta reserva ya está validada o anotada como falta: ya no se puede cambiar ni cancelar por internet. |
   | `booking.blocked-no-show` | 403 | Tes {faults} faltas nos últimos {windowDays} días. Non podes reservar por internet ata o {until}. | Tienes {faults} faltas en los últimos {windowDays} días. No puedes reservar por internet hasta el {until}. |
   | `municipality.no-show-policy-invalid` | 422 | Revisa os números: de 1 a 20 faltas e de 1 a 365 días. | Revisa los números: de 1 a 20 faltas y de 1 a 365 días. |
9. **Rexistro de actividade** ([ADR-0020](0020-rexistro-de-actividade.md)), con éxito e fallo:
   - `booking.validate`: `bookingId` e `forced`; se foi fóra da marxe, tamén `reason`, o motivo tal como se escribiu (ata 200 caracteres). É a excepción decidida a «os motivos libres non van nos detalles», coma a referencia da exportación dos bloqueados: o motivo ten que quedar con quen e cando.
   - `booking.no-show`: a manual, `bookingId` e `blocked`; a automática, un evento por lote coa tarefa como quen actúa (`furar:no-show`) e só cantas e cantos bloqueos.
   - `booking.attendance-undo`: `bookingId`, `from` (`USED` ou `NO_SHOW`) e `blockLifted`.
   - As lecturas dun código que non validan nada non se rexistran. O interruptor da instalación e a tarxeta do concello saen nos `changes` das súas escrituras.
10. **Correo de falta** (o quinto correo das reservas; `MailKind.NO_SHOW`). `booking` publica `BookingNoShow(municipalityId, bookingId, eventId, faults, threshold, windowDays, blockDays, blockedUntil)` dentro da transacción e `notification` envía despois do commit, coma os demais ([ADR-0025](0025-reservas-fase-1.md) punto 15); a clave única (`booking_id`, `kind`, `event_id`) impide repetilo. Mesma plantilla, sen QR, sen `.ics` e sen prazo de cancelación; botón «Ver as miñas reservas» só se a reserva ten conta ligada (nunha do mostrador sen conta non sae). A toda reserva con correo, tamén ás do mostrador; sen correo non hai aviso. Texto fixo (`mail/notification_{gl,es}.properties`):

    | Clave | Galego | Castelán |
    |---|---|---|
    | `subject.NO_SHOW` | Non te presentaches: {0}, {1} ás {2} | No te presentaste: {0}, {1} a las {2} |
    | `title.NO_SHOW` | Non te presentaches | No te presentaste |
    | `lead.NO_SHOW` | Non usaches a túa reserva en {0}. | No usaste tu reserva en {0}. |
    | `noShow.count` | Levas {0} faltas en {1} días. Con {2} non poderás reservar por internet durante {3} días. | Llevas {0} faltas en {1} días. Con {2} no podrás reservar por internet durante {3} días. |
    | `noShow.count.one` | Levas 1 falta en {1} días. Con {2} non poderás reservar por internet durante {3} días. | Llevas 1 falta en {1} días. Con {2} no podrás reservar por internet durante {3} días. |
    | `noShow.blocked.one` | Levas 1 falta en {1} días: non podes reservar por internet ata o {2}. No mostrador si podes, e as reservas que xa tes mantéñense. | Llevas 1 falta en {1} días: no puedes reservar por internet hasta el {2}. En el mostrador sí puedes, y las reservas que ya tienes se mantienen. |
    | `noShow.blocked` | Levas {0} faltas en {1} días: non podes reservar por internet ata o {2}. No mostrador si podes, e as reservas que xa tes mantéñense. | Llevas {0} faltas en {1} días: no puedes reservar por internet hasta el {2}. En el mostrador sí puedes, y las reservas que ya tienes se mantienen. |
    | `noShow.mistake` | Se é un erro, chama ao {0}. | Si es un error, llama al {0}. |

    O asunto leva o día sen o mes («luns 2 ás 19:00») e `lead.NO_SHOW`, o nome do concello. `noShow.count` e `noShow.blocked` só saen coa penalización acesa `noShow.mistake`, se o servizo de deportes ten teléfono.
11. **Horas.** Todo se garda en `Instant`/`timestamptz` (UTC). A marxe é unha duración. En hora de Galicia (Europe/Madrid) só se calculan a xanela dos días que contan e o final do día en que remata o bloqueo; a web mostra «ata o 24 de novembro» co último día bloqueado.
12. **Lector de QR na web.** A cámara só se ofrece no móbil; no ordenador, só o código. Criterio: hai `navigator.mediaDevices.getUserMedia`, o punteiro principal é táctil (`matchMedia('(pointer: coarse)')`) e a pantalla é a estreita da barra inferior; se algo falla, ou se a persoa nega o permiso, queda o código. O QR leva só o código ([ADR-0025](0025-reservas-fase-1.md) punto 13): o que non cumpra `FUR-XXXX` descártase sen chamar á API. Lese co `BarcodeDetector` do navegador se existe; se non, cun descodificador cargado só nesa pantalla (proposta: `jsQR` 1.4.0, Apache-2.0 e sen dependencias; é de 2021 e non ten versións posteriores, así que cumpre os 14 días de [ADR-0016](0016-ci-e-verificacion.md) pero non recibe mantemento: acéptase por ser pequena e só de lectura, e compróbase a licenza ao engadila). A cabeceira `Permissions-Policy` de Caddy pasa de `camera=()` a `camera=(self)`. Sen conexión non se valida.
13. **Datos persoais** (fila en [`datos-persoais.md`](../datos-persoais.md)): a marca de asistencia, quen a puxo e cando van na reserva e conservan o que ela; os bloqueos (correo, datas e as reservas que os causaron; antes, o número de faltas) bórraos `BookingRetentionService` cando pasa `furar.booking.retention` desde a súa fin. Base 6.1.e. O bloqueo aplica unha regra fixa e publicada, sen perfís, só quita a canle de internet e desfaino o persoal: o mantedor confirma que a «Privacidade» e o aviso legal se adaptan (hoxe as condicións de uso non falan de faltas). **Textos que cambian ao construír** (`web/src/i18n/{gl,es}.json`, rexistro formal; pendentes da revisión legal xa anotada):

    | Clave | Galego | Castelán |
    |---|---|---|
    | `legalNotice.conditions.attendanceTitle` (nova) | Asistencia e faltas | Asistencia y faltas |
    | `legalNotice.conditions.attendance` (nova) | O persoal pode validar a reserva co seu código cando chegue á instalación. Se non se presenta, a reserva queda anotada como falta: nas instalacións con control de entrada, cando remata sen validarse; nas demais, cando o persoal o anota. Recibirá un correo por cada falta. Se {{owner}} activou a penalización, ao chegar ao número de faltas fixado non poderá facer reservas novas en liña durante os días establecidos; poderá reservar no mostrador e as reservas xa feitas mantéñense. As faltas non teñen consecuencias económicas. Se a falta é un erro ou ten xustificación, diríxase ao persoal para que a retire. | El personal puede validar la reserva con su código cuando llegue a la instalación. Si no se presenta, la reserva queda anotada como falta: en las instalaciones con control de entrada, cuando termina sin validarse; en las demás, cuando el personal lo anota. Recibirá un correo por cada falta. Si {{owner}} ha activado la penalización, al llegar al número de faltas fijado no podrá hacer reservas nuevas en línea durante los días establecidos; podrá reservar en el mostrador y las reservas ya hechas se mantienen. Las faltas no tienen consecuencias económicas. Si la falta es un error o tiene justificación, diríjase al personal para que la retire. |
    | `privacy.residents.purpose` (engádese antes de «xestionar o cobro») | anotar a asistencia e as faltas e, se a entidade o estableceu, suspender temporalmente a reserva en liña; | anotar la asistencia y las faltas y, si la entidad lo ha establecido, suspender temporalmente la reserva en línea; |
    | `privacy.residents.purpose` (lista de correos) | (confirmación, cambio, cancelación ou falta dunha reserva) | (confirmación, cambio, cancelación o falta de una reserva) |
    | `privacy.residents.dataAttendance` (nova) | Da asistencia: se a reserva se validou ou quedou como falta, quen do persoal o anotou e cando, e, se procede, o período no que non pode reservar en liña. A suspensión aplícase segundo unha regra fixa e pública, sen elaboración de perfís, e o persoal pode retirala. | De la asistencia: si la reserva se validó o quedó como falta, quién del personal lo anotó y cuándo, y, en su caso, el periodo en el que no puede reservar en línea. La suspensión se aplica según una regla fija y pública, sin elaboración de perfiles, y el personal puede retirarla. |
14. **Web.**

    | Onde | Que cambia |
    |---|---|
    | `features/booking/validate/` (páxina nova `/:concello/xestion/validar`: `validate.page`, `validate.store.ts`, `qr-scanner.component`) | Sempre nesta orde: código (ou QR) → datos da reserva → aviso → motivo → botóns. Valida soa ao ler ou teclear; «Validar outra» e «Desfacer»; fóra da marxe, «Validar igualmente» co motivo e «Non validar» |
    | `core/layout/manage-links.ts`, `staff-pages.ts` e `bottom-nav.component.ts` | «Validar QR» na barra da xestión e, no móbil, na barra inferior xunto a «Reservas»; Xestión e Recepción |
    | `features/booking/manage/booking-detail.component` | Chip «Validada» ou «Non presentado»; nunha reserva rematada e sen marca, botón «Non presentado» (sen control de entrada ou cunha marca desfeita); despois, aviso coas faltas que leva a persoa e «Quitar a falta» (nunha validada, «Quitar a validación») |
    | `features/booking/manage/booking-rows.component` e `calendar/calendar-peek.component` | O mesmo chip |
    | `features/booking/mine/booking-card.component` | Chip «Non presentado» no historial |
    | `features/booking/reserve/reserve.page` | Bloqueada: aviso coa data de fin, o motivo e o teléfono do servizo, sen «Continuar» |
    | `features/facility/data-card` | Interruptor «Con control de entrada: valídase o QR ao chegar» |
    | `features/municipality/section/general.tab` | Tarxeta «Non presentados»: interruptor «Penalizar as faltas» e os tres números |

    Chips coa paleta da app e de `tag.css`: «Non presentado» en vermello e «Validada» en gris.
    **Textos da web** (`web/src/i18n/{gl,es}.json`).

    | Clave | Galego | Castelán |
    |---|---|---|
    | `nav.validate` · `validate.title` | Validar QR | Validar QR |
    | `validate.mode.camera` · `validate.mode.code` | Cámara · Código | Cámara · Código |
    | `validate.camera.hint` | Apunta ao QR da reserva | Apunta al QR de la reserva |
    | `validate.camera.notFurar` | Ese QR non é dunha reserva. | Ese QR no es de una reserva. |
    | `validate.code.label` | Código da reserva | Código de la reserva |
    | `validate.result.validated` | Reserva validada | Reserva validada |
    | `validate.result.already.title` · `.body` | Xa estaba validada · {{date}}, por {{name}}. | Ya estaba validada · {{date}}, por {{name}}. |
    | `validate.result.cancelled.title` · `.byPerson` · `.byMunicipality` | Reserva cancelada · Cancelouna a persoa o {{date}}. · Cancelouna o concello o {{date}}. | Reserva cancelada · La canceló la persona el {{date}}. · La canceló el ayuntamiento el {{date}}. |
    | `validate.result.notFound` | Non hai ningunha reserva con ese código | No hay ninguna reserva con ese código |
    | `validate.result.outOfWindow.title` · `.body` | Fóra do horario da reserva · Esta reserva é para {{when}}. Só se valida soa desde 45 minutos antes ata 45 minutos despois. | Fuera del horario de la reserva · Esta reserva es para {{when}}. Solo se valida sola desde 45 minutos antes hasta 45 minutos después. |
    | `validate.reason.label` · `.required` | Motivo · Escribe o motivo para validala. | Motivo · Escribe el motivo para validarla. |
    | `validate.force` · `validate.skip` | Validar igualmente · Non validar | Validar igualmente · No validar |
    | `validate.next` · `validate.undo` | Validar outra · Desfacer | Validar otra · Deshacer |
    | `booking.attendance.USED` · `.NO_SHOW` (chips) | Validada · Non presentado | Validada · No presentado |
    | `booking.manage.noShow` (botón) | Non presentado | No presentado |
    | `booking.manage.noShow.count` | {{name}} leva {{faults}} faltas en {{days}} días. Con {{threshold}} non poderá reservar por internet durante {{blockDays}} días. | {{name}} lleva {{faults}} faltas en {{days}} días. Con {{threshold}} no podrá reservar por internet durante {{blockDays}} días. |
    | `booking.manage.noShow.blocked` | {{name}} leva {{faults}} faltas en {{days}} días: non pode reservar por internet ata o {{until}}. | {{name}} lleva {{faults}} faltas en {{days}} días: no puede reservar por internet hasta el {{until}}. |
    | `reserve.blocked.title` | Non podes reservar por internet ata o {{until}} | No puedes reservar por internet hasta el {{until}} |
    | `reserve.blocked.body` · `.bodyNoPhone` | Tes {{faults}} faltas nos últimos {{days}} días. No mostrador si podes reservar: {{phone}}. · Tes {{faults}} faltas nos últimos {{days}} días. No mostrador si podes reservar. | Tienes {{faults}} faltas en los últimos {{days}} días. En el mostrador sí puedes reservar: {{phone}}. · Tienes {{faults}} faltas en los últimos {{days}} días. En el mostrador sí puedes reservar. |
    | `facility.data.entryControl` | Con control de entrada: valídase o QR ao chegar | Con control de entrada: se valida el QR al llegar |
    | `section.noShow.title` | Non presentados | No presentados |
    | `section.noShow.penalty` | Penalizar as faltas | Penalizar las faltas |
    | `section.noShow.threshold` · `.windowDays` · `.blockDays` | Faltas · En cantos días · Días de bloqueo | Faltas · En cuántos días · Días de bloqueo |

    Cunha soa falta, os textos con `{{faults}}` levan a forma en singular («1 falta»).
15. **Probas previstas.** `BookingAttendanceTest` (transicións e marxe nos bordos, ao segundo); `NoShowPolicyTest` (que faltas contan, xanela en hora de Galicia co cambio de hora, bloqueo e conta de cero); `ValidateBookingIT` (os catro resultados, código doutro concello → 404, forzar con e sen motivo, repetir, desfacer, permisos de Xestión, Recepción e plataforma); `NoShowIT` (manual, antes da fin, sobre unha cancelada, desfacer, a tarefa e unha validación simultánea da mesma reserva: gaña unha soa e a marca final é coherente; cancelar por internet unha reserva con marca → 409 e cancelala o persoal borra a marca; a tarefa só marca con control de entrada e desde que se acendeu, non volve marcar o desfeito, lotes); `NoShowPenaltyIT` (faltas por correo entre mostrador e internet, sen correo non conta, quen crea a conta despois segue bloqueado, límite, bloqueo ao reservar, mostrador si, outro concello non, levantar ao desfacer calquera das faltas que o causaron, un bloqueo levantado non pon a conta a cero e un cumprido si, penalización apagada); `BookingAttendanceSqlTenantIT` e `NoShowBlockSqlTenantIT`; `MunicipalityNoShowPolicyIT` e a ida e volta de `MunicipalityPortabilityIT`; `NoShowMailIT` e `NotificationMailTextsTest`; `ActivityIT` (o motivo queda); `RolePermissionsTest`; `validate.store.spec.ts`, `validate.page.spec.ts`, `booking-detail` e `reserve.page.spec.ts` (bloqueo), `general.tab.spec.ts`, `bottom-nav.spec.ts`; e2e `xestion-reservas.e2e.ts` (validar por código, fóra da marxe con motivo, marcar e desfacer unha falta) e `reservas.e2e.ts` (persoa bloqueada), con axe en `gl`, `es` e `gl-mobile` (a cámara, cun fluxo de vídeo simulado).

## Consecuencias
- O QR deixa de ser decorativo e o concello sabe quen veu; os indicadores de «uso real» seguen sendo capa posterior, pero xa teñen o dato.
- A falta automática depende de que a porta valide: unha instalación con control de entrada que deixa de ler o QR enche de faltas (e de correos) a quen si veu. O interruptor está apagado por defecto e «Desfacer» levanta a falta e o bloqueo.
- Unha tarefa máis cada 10 minutos, lixeira (un índice parcial por concello).
- O rexistro de actividade garda, só neste caso, un texto libre do persoal: a pantalla pide o motivo, non datos da persoa.
- A web pide a cámara por primeira vez: cambia a cabeceira de Caddy e a táboa de medidas de [`ens.md`](../ens.md), e entra unha dependencia na web se o navegador non trae lector.
- A persoa bloqueada ve por que, ata cando e a que teléfono chamar; o correo de cada falta avísaa antes.

## Preguntas abertas
Ningunha de produto.

## Decidido o 2026-11-10
Respostas do mantedor ás preguntas abertas da primeira versión; xa van na decisión.

| | Pregunta | Decisión |
|---|---|---|
| PB1 | Quen valida e quen marca ou desfai faltas | Xestión e Recepción, cun só permiso (`booking.attendance`) |
| PB2 | Motivo de «Validar igualmente»: libre ou lista pechada | Texto libre de 1 a 200 caracteres, gardado no rexistro |
| PB3 | Cada canto corre a tarefa das faltas | Cada 10 minutos |
| PB4 | Reservas anteriores a acender o control de entrada ou a penalización | Non contan: só desde que se acende |
| PB5 | Correo de falta coa penalización apagada, e nas reservas sen conta | Con ela apagada envíase sen a frase do bloqueo. Contan e avísanse todas as reservas con correo, tamén as do mostrador; sen correo, nin unha cousa nin a outra |
| PB6 | A persoa bloqueada pode cambiar unha reserva xa feita | Si: o bloqueo só impide reservas novas |
| PB7 | Cando remata o bloqueo e que pasa despois | Ao final do día (hora de Galicia) de hoxe máis os días de bloqueo; despois a conta empeza de cero |
| PB8 | Desfacer unha falta que causou un bloqueo | Levanta o bloqueo, sen poñer a conta a cero; só un bloqueo cumprido empeza de cero |
| PB9 | Validar unha reserva xa marcada «non presentado» | Dentro da marxe valídase soa e fóra pide o motivo; nos dous casos quita a falta |
| PB10 | Nome do chip dunha reserva validada e se o ve a persoa | «Validada», só na xestión; a persoa segue vendo «Realizada» |
| PB11 | Rangos dos tres números | Faltas de 1 a 20; días de 1 a 365 |
| PB12 | A marxe de 45 minutos, fixa ou por concello | Do despregue (`furar.booking.validation-margin`) |
| PB13 | «Desfacer» ten prazo | Non |
| PB14 | Filtro por asistencia na lista e columna no CSV | Sen filtro; o CSV gaña a columna «Asistencia» |
| PB16 | Unha reserva validada ou con «non presentado» pódese cancelar ou cambiar | Por internet, non; o persoal e o concello si, e a operación borra a marca (punto 2) |
| PB15 | Biblioteca do lector onde o navegador non o trae | `jsQR` 1.4.0 |

## Alternativas descartadas
- **`USED` e `NO_SHOW` como valores de `status`:** as exclusións, o cupo, o límite por persoa, os indicadores e as regras do pago miran `status = 'CONFIRMED'`; habería que tocalas todas para unha marca que non libera a hora.
- **Gardar no bloqueo só o número de faltas:** non se pode saber se a falta que se desfai é unha das que o causaron.
- **Calcular o bloqueo ao reservar, sen táboa:** non se pode saber se unhas faltas xa «pagaron» un bloqueo anterior nin conxelar os números cos que se bloqueou.
- **Bloqueo na conta, para todos os concellos:** unha falta nun concello non conta noutro, e cada concello decide se penaliza.
- **Tarefa diaria, coma a conservación:** a falta e o correo chegarían ata un día despois, e a persoa podería seguir reservando mentres.
- **Validar con `GET` (mirar) e `POST` (confirmar) en dúas chamadas:** a decisión é que se valide soa ao ler; unha chamada evita que dúas persoas na porta validen a mesma reserva sen sabelo.
- **O motivo só na historia da reserva (`booking_event`):** a decisión é que quede no rexistro de actividade, que é onde a xestión mira quen fixo que.
- **Marcar tamén «Usada» á man:** dous botóns por reserva para un dato que xa dá o QR; escolleuse só a falta.
- **Descodificar o QR no servidor** (a API xa trae un lector): subir fotogramas da cámara é lento e manda imaxes da porta ao servidor sen necesidade.
- **Lector sen conexión ou cunha aplicación á parte:** fóra de alcance.
