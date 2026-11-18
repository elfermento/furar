<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0038 · Eventos do concello: a veciñanza apúntase, con prazas, prezo e aprobación opcionais

- **Estado:** aceptada · 2026-11-11 (decidida polo mantedor; construída o 2026-11-11)
- **Constrúese enriba de:** [ADR-0025](0025-reservas-fase-1.md) (a persoa con conta, sen rol, e o pago no mostrador), [ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md) (que fai a plataforma), [ADR-0017](0017-datos-persoais.md) e [ADR-0018](0018-portabilidade.md).
- **Revisada 2026-11-16 ([ADR-0041](0041-actividades-con-horario.md)):** o evento gaña un tipo (dun día, o de sempre, ou con horario: unha actividade con sesións que ocupan a instalación coma unha tempada), persoas a cargo, límite de idade, período do prezo e lista de espera automática sen «Con aprobación»; o permiso `event.attendance` (Xestión e Recepción) para pasar lista; a conservación conta desde que remata o evento (nunha actividade, a última sesión); `event` depende tamén de `booking` e `people`. Punto 2: só as confirmadas ocupan praza e, mentres haxa lista de espera, unha praza libre é para ela. Punto 3: desapuntarse dunha actividade, ata que remata.
- **Revisada 2026-11-12 ([ADR-0040](0040-buscar-xente-para-xogar.md)):** «Futuro: partidas abertas» queda resolto: táboa irmá en `booking` (`booking_open_match_player`), non `event_registration` xeneralizada; a mecánica e as pezas da web reutilízanse.

## Contexto
Ademais das reservas por horas, o concello organiza actividades puntuais (unha carreira, un torneo, unha xornada) con prazas e, ás veces, prezo. Sen un sitio onde publicalas, a inscrición vai por teléfono ou en papel e ninguén ve as prazas que quedan.

## Decidido polo mantedor (2026-11-11)
- O concello publica eventos deportivos puntuais: nome e descrición (gl e es), data e hora, lugar (unha instalación do concello ou «Outro sitio»), prazas (baleiro: sen límite), prezo (baleiro: gratis), «Apuntarse ata» (apagado: ata que empeza), «Publicado» e «Con aprobación» (apagado por defecto). Sen imaxe propia (a da instalación ou unha xenérica) e sen normas.
- A veciñanza ve «Próximos eventos» na portada do concello e a ficha do evento; apúntase con «Apuntarme» ou, «Con aprobación», «Pedir praza», e desapúntase. O evento sae en «As miñas reservas › Próximas».
- Na xestión, «Eventos» é unha pestana de «Instalacións», co número de solicitudes pendentes. O persoal marca o pago de cada persoa coma nas reservas.
- Correos: á persoa cando o concello acepta ou rexeita a súa solicitude, e a todas as apuntadas se o evento se cancela ou cambia de data, hora ou lugar. Apuntarse directamente non manda correo (abonda o aviso en pantalla).

## Decisión
1. **Módulo propio `event`.** O evento é configuración do concello (JPA, táboa `event`, en `furar-export`); as inscricións (`event_registration`) son datos da persoa, en SQL nativo con RLS (`RegistrationSql`, `RegistrationSqlTenantIT`) e fóra de `furar-export`, coma as reservas. Sen FK cara a `facility` (lista pechada `CrossModuleForeignKeys`): o caso de uso comproba a instalación pola API pública. Depende de `facility` (lugar e portada) e de `notification` (`EventMailer`).
2. **Prazas sen carreiras.** Apuntarse e aceptar unha solicitude bloquean a fila do evento (`select … for update`) antes de contar as confirmadas: dúas persoas á vez non ocupan a última praza (a segunda recibe 409 `event.full`). Unha persoa, unha inscrición por evento (índice único parcial). Só as confirmadas ocupan praza; unha solicitude pendente non a reserva, e non se pode aceptar sen praza libre (desde [ADR-0041](0041-actividades-con-horario.md), pedir praza si, aínda que estea completo). Baixar as prazas por debaixo das confirmadas non se permite.
3. **Estados.** `PENDING` (pediu praza), `CONFIRMED` (apuntada ou aceptada) e `REJECTED` (o concello non a aceptou; queda para que non volva pedir e para o seu aviso). Desapuntarse (ou retirar a solicitude) borra a inscrición e é posible ata que empeza o evento. Un evento cancelado ou empezado non se cambia; con persoas non se elimina, cancélase.
4. **Pago.** Nun evento con prezo, cada inscrición confirmada leva o seu estado (Pendente de pago, Pagada, Gratis), que marca o persoal; págase no mostrador, coma as reservas.
5. **Permisos** (revisado en [ADR-0041](0041-actividades-con-horario.md): máis `event.attendance`, pasar lista, para Xestión e Recepción). `event.manage` (alta, edición, publicar, cancelar, eliminar): Xestión e a plataforma, que configura o concello enteiro. `event.view` (a lista e o detalle, só números): tamén Recepción. `event.registrations` (as persoas e o número de pendentes) e `event.payment`: Xestión e Recepción; `event.review` (aceptar e rexeitar): só Xestión. A plataforma non ve persoas ([ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md), punto 9). Proposta conservadora para Recepción: ve os eventos e as persoas e marca o pago, coma no mostrador, pero non decide as solicitudes.
6. **Correos.** No mesmo rexistro de envíos ca o resto (`notification_delivery`, co evento en `municipal_event_id`): `EVENT_ACCEPTED` e `EVENT_REJECTED` unha vez por solicitude; `EVENT_CHANGED` unha vez por cambio e persoa; `EVENT_CANCELLED` unha vez por persoa. Despois do commit e fóra de toda transacción.
7. **Datos persoais.** A inscrición copia do token a conta, o nome, o correo e o idioma, coma a reserva (base 6.1.e). Conservación: 2 anos desde que empezou o evento (`furar.event.retention`, `P2Y`, tarefa diaria `EventRetentionService`), despois sen persoa (queda o estado e o pago). Na baixa da conta, `close_account` borra as inscricións en eventos que aínda non empezaron e quita a persoa das pasadas. Os eventos non van nos datos abertos, polo de agora.

## Futuro: partidas abertas
A mecánica das inscricións (persoas apuntadas, prazas, aprobación opcional e estado do pago) non depende do evento: unha reserva aberta a outras persoas, con mínimo e máximo, pode reutilizala cunha táboa irmá que apunte á reserva no canto do evento, ou xeneralizando `event_registration` cun destino. Non se construíu.

*Resolto o 2026-11-12 en [ADR-0040](0040-buscar-xente-para-xogar.md):* táboa irmá en `booking` (`booking_open_match_player`), non `event_registration` xeneralizada; a mecánica e as pezas da web reutilízanse.

## Consecuencias
- Recepción ve as solicitudes pero non as decide: se o concello quere que o mostrador acepte, é un cambio dunha liña en `EventPermission`.
- Na web, polo de agora, a pestana «Eventos» só se mostra á xestión: Recepción ten os permisos na API pero non a pestana. Mostrarlla é unha decisión pendente.
- `close_account` redefínese outra vez (`V202611112205__event_close_account.sql`): a seguinte migración que a toque ten que partir desta.

## Alternativas descartadas
- **O evento como unha reserva máis (en `booking`):** un evento pode ser fóra das instalacións («Outro sitio») e non ocupa ocos de reserva; mesturalo cambiaría as regras das reservas.
- **Imaxe propia e normas do evento:** quedan fóra; vale a imaxe da instalación ou unha xenérica.
- **Correo ao apuntarse directamente:** abonda o aviso en pantalla; o correo queda para cando decide o concello ou cambia o evento.
