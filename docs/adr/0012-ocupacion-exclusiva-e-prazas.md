<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0012 · Ocupación: un uso por instalación e franxa, enteira, por partes ou por prazas

- **Estado:** aceptada · 2026-10-22 · revisada tras ARQ2-2 e ARQ2-4, o 2026-10-25 e o 2026-10-26
- **Revisada 2026-11-04:** as preguntas abertas que cita (P1, P3) quedan aceptadas co valor por defecto que se describe.
- **Revisada 2026-10-25 (dominio 1):** sen espazos; a ocupación apunta a un uso e, se é por partes, a unha parte; os bloqueos saen da ocupación ([ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md) §5).
- **Revisada 2026-10-26 (dominio 2, [ADR-0025](0025-reservas-fase-1.md)):** o modelo vai nas migracións de `booking`, unha praza por persoa e ocupación ligada á reserva.
- **Validado:** «en cada instalación e franxa só pode haber un uso; dentro do uso, ata o seu cupo» ([especificación](../produto/especificacion.md), bloque 1).

## Contexto
A instalación é o que se reserva («Campo de fútbol 1 de O Alto», «Pista de pádel 2»). Cada instalación ten usos, e cada uso alúgase **enteiro** (1 reserva por franxa), **por partes con nome** (Campo A/B) ou **por prazas** (N persoas). O primeiro que reserva unha franxa fixa o uso: mentres dura, ningún outro uso da mesma instalación se pode reservar nela. Hai que garantilo **na base**, tamén con peticións simultáneas, e sen que `booking` lea as táboas de `facility`.

## Decisión
**Modelo de usos** (módulo `facility`, [plan](../plan/dominio-1-instalacions.md) §2.1): `facility_use(id, municipality_id, facility_id, name, rental_mode WHOLE|PARTS|SEATS, capacity, slot_minutes, availability, blocks_others, archived_at)` con `unique (municipality_id, facility_id, id)` e `unique (municipality_id, id, rental_mode)`; `check ((rental_mode = 'SEATS') = (capacity is not null))`, prazas de 2 a 10 000 (P3). `facility_use_part(id, municipality_id, use_id, name, position, archived_at)` con `unique (municipality_id, use_id, id)`; un uso por partes ten polo menos dúas (dominio). Usos e partes **arquívanse, non se borran**: as reservas apuntan a eles.

**Ocupación** (módulo `booking`), un modelo para os tres modos:

```sql
create table booking_occupancy (
  id uuid primary key, municipality_id uuid not null references municipality (id),
  booking_id uuid not null,
  facility_id uuid not null, use_id uuid not null, part_id uuid,
  rental_mode text not null, period tstzrange not null,               -- [inicio, fin)
  seats int not null default 1 check (seats = 1),                     -- unha persoa = unha praza
  active boolean not null default true,                               -- false ao cancelar ou cambiar
  foreign key (municipality_id, booking_id) references booking (municipality_id, id),
  foreign key (municipality_id, facility_id, use_id) references facility_use (municipality_id, facility_id, id),
  foreign key (municipality_id, use_id, part_id) references facility_use_part (municipality_id, use_id, id),
  foreign key (municipality_id, use_id, rental_mode) references facility_use (municipality_id, id, rental_mode),
  check ((rental_mode = 'PARTS') = (part_id is not null)),
  -- un uso por instalación e franxa
  exclude using gist (facility_id with =, use_id with <>, period with &&) where (active),
  -- enteira: unha reserva por franxa
  exclude using gist (use_id with =, period with &&) where (active and rental_mode = 'WHOLE'),
  -- por partes: unha reserva por parte e franxa
  exclude using gist (part_id with =, period with &&) where (active and rental_mode = 'PARTS')
);
create table use_slot (                              -- só usos por prazas
  municipality_id uuid not null references municipality (id),
  use_id uuid not null, slot_start timestamptz not null,
  rental_mode text not null default 'SEATS' check (rental_mode = 'SEATS'),
  capacity int not null check (capacity between 2 and 10000), taken int not null default 0,
  primary key (municipality_id, use_id, slot_start),
  foreign key (municipality_id, use_id, rental_mode) references facility_use (municipality_id, id, rental_mode),
  check (taken between 0 and capacity)
);
```

- **Un uso por instalación e franxa:** a primeira exclusión impide dúas ocupacións activas solapadas da mesma instalación con uso distinto. As FK compostas fan que `facility_id` sexa de verdade a instalación do uso e `rental_mode` o modo do uso: o código non pode mentir nin nunha nin noutra. `uuid with <>` funciona con `btree_gist` en PostgreSQL 18 (`OccupancyModelIT` próbao). Erro `23P01` → 409 `booking.slot-taken`.
- **Enteira e por partes:** as exclusións segunda e terceira (409 `booking.slot-taken` e `booking.part-taken`). Dúas partes distintas do mesmo uso convén; a mesma parte, non. Unha parte por reserva (P3); o campo enteiro é outro uso «enteira».
- **Por prazas:** na mesma transacción, para cada franxa **en orde (`use_id, slot_start`)**, para evitar interbloqueos: `insert … on conflict do nothing` en `use_slot` (coa `capacity` actual do uso) e `update use_slot set taken = taken + 1`, que bloquea a fila ata o commit. O `CHECK (taken between 0 and capacity)` fai imposible pasarse aínda cun erro no código. Erro `23514` → 409 `booking.slot-taken`. Garda tamén a fila en `booking_occupancy` (historial e a exclusión entre usos).
- **Referencias sempre dentro do concello** ([ADR-0006](0006-multiconcello.md)): todas as FK levan `municipality_id`, e RLS en todas as táboas. `facility_use` e `facility_use_part` son **táboas publicadas** de `facility` na lista pechada `CrossModuleForeignKeys` ([ADR-0005](0005-monolito-modular-hexagonal.md) revisada).
- **Bloqueos, peche, horario e «bloquea os demais» non van na ocupación.** `booking` compróbaos baixo o bloqueo da instalación (`FacilityQueries.lockFacility`, ADR-0022 §4) antes de escribir a ocupación. **Manter** unha reserva afectada é non tocala: segue na ocupación e o bloqueo simplemente non se lle aplica; a decisión, quen e cando, gárdaa `booking`. **Cancelar:** `active = false` e `taken = taken - seats` na mesma transacción.
- **Cambios dun uso con reservas futuras** (modo, duración, baixar prazas, quitar unha parte): `facility` chama a `BookingImpact` (`UseChanged`, `UseArchived`) baixo o bloqueo e, se hai reservas futuras, 409 `facility.use-has-bookings` (P1). Nome e datas si se poden cambiar. A copia de `capacity` en `use_slot` existe porque o `CHECK` atómico a necesita e só a cambia `booking`: cando as prazas dun uso por prazas cambian (suben, ou baixan sen reservas futuras), `facility`, despois de gardar o uso e baixo o mesmo bloqueo e transacción, chama a `BookingImpact.apply` con `SeatsChanged(capacity)` e sen decisións, e `booking` pon a capacidade nova nos contadores das franxas que aínda non empezaron (`BookingSql.resizeSeats`); os das pasadas quedan como estaban, para os indicadores. Baixar por debaixo das prazas tomadas non chega á base: con reservas futuras xa é 409 `facility.use-has-bookings`. `SeatCapacityIT` próbao.
- **Prazas libres** para a pantalla: `capacity - taken` por franxa; sen fila = todas libres. A aliñación das franxas é de `booking` ([ADR-0025](0025-reservas-fase-1.md) punto 6).
- **A ocupación apunta á súa reserva** (`booking_id`, FK composta): cambiar ou cancelar non borra filas, pon `active = false`.

**Unha praza por persoa** (especificación, bloque 5): nos usos por prazas cada reserva ocupa unha. A exclusión vai na táboa `booking`, que ten quen reserva, non na ocupación: `exclude using gist (use_id with =, holder_subject with =, period with &&) where (status = 'CONFIRMED' and rental_mode = 'SEATS')`. Erro `23P01` → 409 `booking.seat-taken-by-you`. Varias partes do mesmo uso á mesma hora pola mesma persoa si valen (P6 do [plan do dominio 2](../plan/dominio-2-reservas.md), pendente de validar).

**Proba do modelo** (`OccupancyModelIT`, D19, D1-02): só SQL e JDBC sobre Testcontainers PG 18 coas migracións de produción (`db/migration`), como `furar_app` con RLS, `CyclicBarrier` para soltar os fíos á vez e repeticións por `-Dfurar.occupancy.reps` (20 en local, 3 na CI): dous usos distintos na mesma franxa → 1 éxito; enteira → 1; dúas partes → 2 e a terceira falla; 30 prazas e 50 fíos → 30; ocupación co uso doutra instalación → `23503`; modo que non casa co uso → `23503`; multifranxa en orde inversa sen interbloqueo (5 s); RLS entre concellos; unha praza por persoa (`samePersonNeverHoldsTwoSeats`). Sen bloqueos (§5 de ADR-0022).

## Consecuencias
- Un só fluxo de reserva para pádel, campo por partes, ximnasio e piscina libre; a garantía de «un uso por franxa e cupo» está na base.
- Sen árbore nin expansión a follas: unha reserva é unha fila (e as súas franxas se é por prazas).
- `use_slot` crece ~1 fila por uso con prazas e franxa reservada; na fase 1 as pasadas non se purgan.
- Dúas cousas que se teñen que reservar á vez sen excluírse son partes do mesmo uso ou instalacións distintas: restrición de produto, xa acordada.
- Bloqueos e horario fóra da base: a súa garantía é o bloqueo da instalación, que toman todas as escrituras e `booking`; un IT de dúas transaccións compróbao (D1-11a).

## Alternativas descartadas
- **Árbore de espazos con «espazos folla» (deseño do 2026-10-22, retirado o 2026-10-25):** `space(parent_id, capacity)`, a reserva dun pai expandíase ás follas, `CapacityChangeGuard` e `FacilityQueries.lockLeaves`. A especificación quita o espazo; partes e usos expresan o mesmo («pabellón enteiro» = uso enteira; «terzos» = uso por partes) sen árbore nin regra «un espazo con prazas é sempre folla».
- **Bloqueos como ocupación (`block_id` en `booking_occupancy`) recortada arredor das mantidas:** obriga a reescribir filas de bloqueo cada vez que se mantén unha reserva e mete `booking` na escritura dos bloqueos ([ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md)).
- **Uso centinela ou filas falsas en `facility_use` para pechar a instalación:** mesturan configuración e ocupación; o bloqueo da instalación chega.
- **Contar con `select count(*)` e illamento `SERIALIZABLE`:** reintentos frecuentes en picos de apertura de reservas.
- **Bloqueo consultivo (`pg_advisory_xact_lock`):** funciona, pero a garantía queda no código, non nun `CHECK` visible.
- **Restrición de exclusión tamén para prazas:** non pode contar.
- **`use_slot` sen `capacity` e trigger que le `facility_use`:** quita a copia, pero o trigger de `booking` lería a táboa de `facility` e a garantía deixaría de ser un `CHECK` visible.
- **Varias prazas nunha reserva (familia):** a especificación do 2026-10-26 decide unha persoa = unha praza; reservar para outras persoas é unha capa posterior.
- **Unha praza por persoa só no código:** dúas peticións simultáneas da mesma persoa pasarían a comprobación; a exclusión na base non depende diso.
- **Varias partes nunha reserva:** complica a exclusión (rango de partes) para un caso que xa cobre un uso «enteira» (P3).
