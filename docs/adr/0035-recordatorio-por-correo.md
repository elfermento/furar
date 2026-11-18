<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0035 · Recordatorio por correo: 24 horas antes do prazo, unha vez, e baixa desde o propio correo

- **Estado:** aceptada · 2026-11-11 (decidida polo mantedor; construída o 2026-11-11)
- **Constrúese enriba de:** [ADR-0025](0025-reservas-fase-1.md) punto 15 (correos das reservas), [ADR-0031](0031-persoas-a-cargo.md) («a reserva é do correo», `holder_email_key`) e [ADR-0032](0032-non-presentados-e-validacion.md) (tarefa con `for update skip locked`).
- **Revisada 2026-11-12 ([ADR-0039](0039-tempada-de-entidades.md)):** as reservas dunha entidade non levan recordatorio (non teñen correo de titular).

## Contexto
Quen reserva con días de antelación esquécese, e unha hora reservada que ninguén usa é unha hora que outra persoa non puido coller. Un aviso antes do prazo para cancelar de balde dálle tempo a soltala.

## Decidido polo mantedor (2026-11-11)
- Envíase 24 horas antes de que acabe o prazo para cancelar sen custo; se o uso non ten prazo, 24 horas antes da reserva. Non se envía se a reserva se fixo despois dese momento.
- Leva a instalación e o uso, o día e a hora, o QR e o código, a frase «Se non podes ir, cancélaa antes do ‹límite› para que outra persoa a poida usar.», o botón «Ver a miña reserva» e a ligazón «Non quero recordatorios». Asunto «Lembra: ‹instalación›, ‹día› ás ‹hora›».
- As reservas do mostrador con correo tamén o reciben. A persoa dáse de baixa desde o correo e volve activalo desde a súa conta.

## Decisión
1. **Cando.** O prazo para cancelar de balde xa vai na reserva (`cancel_until`; sen norma, é o propio inicio). O recordatorio toca en `cancel_until − 24 h` (`Reminder`), unha regra para os dous casos. Sae se a reserva está confirmada, ten correo, se fixo como moito nese momento e o prazo aínda non pasou; nunca dúas veces.
2. **Tarefa.** `ReminderService` (`@PlatformTask`, cada 10 minutos, `furar.booking.reminder-cron`): en cada concello, coa súa RLS e en lotes de 500 nunha transacción curta, colle as reservas que tocan con `for update skip locked` (índice parcial `booking_reminder_due_idx`), márcaas (`booking.reminded_at`, sen subir a versión: non é un cambio da reserva) e publica `BookingReminderDue` (sen datos de persoa) polas que non son dun correo dado de baixa. Dúas instancias á vez non marcan a mesma fila; unha cancelación que ten a fila gaña. A marca queda aínda que o correo estea de baixa.
3. **Correo.** `notification` escoita despois do commit e pide `BookingQueries.reminder`, co estado de agora: se a reserva xa se cancelou, o prazo pasou ou o correo se deu de baixa mentres tanto, non sae. Mesma plantilla e mesmo rexistro de envíos ca os demais (`MailKind.REMINDER`, un por reserva); sen prezo nin adxunto. Sen prazo, a frase queda «Se non podes ir, cancélaa para que outra persoa a poida usar.». Sen conta (reserva do mostrador), sen botón: non ten «As miñas reservas». Reinténtase coma os demais (`MailRetry`).
4. **Baixa sen sesión.** A ligazón leva unha proba asinada, `v1.‹reserva›.‹caduca›.‹HMAC›` (`ReminderOptOutToken`): só o identificador da reserva, sen correo nin nome. HMAC-SHA256 co segredo da pegada do rexistro de actividade (`FURAR_AUDIT_HASH_SECRET`) e un prefixo propio, así que non hai segredo novo que configurar. Vale 60 días. A páxina `/‹concello›/recordatorios/baixa?t=…` mándaa a `POST /api/v1/municipalities/{slug}/public/reminder-opt-out`, que dá de baixa o correo desa reserva nese concello (`booking_reminder_opt_out`, con RLS). Manipulada ou doutro concello: 422 `booking.reminder-link-invalid`; caducada: 410 `booking.reminder-link-expired`. Repetila non cambia nada.
5. **Activar outra vez.** `GET`/`PUT /api/v1/municipalities/{slug}/me/reminders` (`{enabled}`), polo correo verificado da sesión; na web, o interruptor «Recordatorios por correo» ao final de «As miñas reservas». A baixa vai polo correo, non pola conta: vale igual para a reserva do mostrador dun correo sen conta.
6. **Datos persoais.** O correo de baixa (clave normalizada) e desde cando, por concello, ata que a persoa o volve activar; nin o rexistro de actividade nin o técnico levan o correo. Fila en [`datos-persoais.md`](../datos-persoais.md).

## Consecuencias
- Un correo máis por reserva e unha tarefa máis cada 10 minutos, cunha lectura por índice cando non toca nada.
- Rotar `FURAR_AUDIT_HASH_SECRET` invalida as ligazóns de baixa xa enviadas; a persoa pode darse de baixa desde a conta.
- Cambiar a hora dunha reserva xa recordada non manda outro recordatorio («nunca dúas veces»).

## Alternativas descartadas
- **Calcular o momento ao crear a reserva e gardalo:** habería que recalculalo en cada cambio; `cancel_until` xa o di.
- **Un segredo propio para a ligazón:** outra variable obrigatoria no despregue para o mesmo tipo de firma.
- **Poñer o correo (aínda que cifrado) na ligazón:** dato persoal no historial do navegador e nos rexistros de quen sirva a web.
- **Darse de baixa ao abrir a ligazón (GET):** os analizadores de correo abren as ligazóns sós; a páxina manda a baixa ela mesma.
