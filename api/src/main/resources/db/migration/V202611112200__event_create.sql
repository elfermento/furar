-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Eventos do concello (ADR-0038): o concello publica eventos deportivos puntuais e a veciñanza
-- apúntase. Dúas táboas do concello, coa súa RLS (ADR-0006):
--
--   * event: o evento (configuración do concello, entra en furar-export). Lugar: unha instalación
--     (facility_id, sen FK: outro módulo, fóra da lista pechada CrossModuleForeignKeys; o caso de
--     uso compróbaa pola API pública de facility) ou un texto libre («Outro sitio»). Prazas, prezo
--     e «Apuntarse ata» opcionais (nulo: sen límite, gratis, ata que empeza).
--   * event_registration: quen se apuntou ou pediu praza (datos da persoa copiados do token, coma
--     as reservas), o estado da solicitude e o do pago. Fóra de furar-export, coma as reservas
--     (ADR-0018). As prazas confírmanse baixo o bloqueo da fila do evento (for update): dúas
--     persoas non ocupan a última. Unha persoa, unha inscrición por evento.

create table event (
    id                    uuid primary key,
    municipality_id       uuid not null references municipality (id),
    name_gl               text not null,
    name_es               text not null,
    description_gl        text,
    description_es        text,
    starts_at             timestamptz not null,
    facility_id           uuid,
    place                 text,
    capacity              integer,
    price_cents           integer,
    registration_deadline timestamptz,
    published             boolean not null,
    approval              boolean not null,
    cancelled_at          timestamptz,
    created_at            timestamptz not null,
    updated_at            timestamptz not null,
    version               bigint not null,
    constraint event_tenant_uk unique (municipality_id, id),
    constraint event_name_ck check (length(name_gl) between 1 and 100 and length(name_es) between 1 and 100),
    constraint event_description_ck check ((description_gl is null) = (description_es is null)
        and (description_gl is null or (length(description_gl) <= 2000 and length(description_es) <= 2000))),
    -- Unha instalación ou outro sitio, nunca os dous.
    constraint event_place_ck check (num_nonnulls(facility_id, place) = 1
        and (place is null or length(place) between 1 and 200)),
    constraint event_capacity_ck check (capacity is null or capacity between 1 and 10000),
    constraint event_price_ck check (price_cents is null or price_cents between 1 and 100000),
    constraint event_deadline_ck check (registration_deadline is null or registration_deadline <= starts_at)
);

create index event_municipality_idx on event (municipality_id, starts_at);

alter table event enable row level security;

create policy event_tenant on event
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table event_registration (
    id               uuid primary key,
    municipality_id  uuid not null references municipality (id),
    event_id         uuid not null,
    status           text not null,
    person_subject   text,
    person_name      text,
    person_email     text,
    person_language  text not null,
    payment_status   text,
    decided_at       timestamptz,
    anonymized_at    timestamptz,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    version          bigint not null,
    constraint event_registration_tenant_uk unique (municipality_id, id),
    constraint event_registration_event_fk foreign key (municipality_id, event_id)
        references event (municipality_id, id) on delete cascade,
    constraint event_registration_status_ck check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    constraint event_registration_payment_ck
        check (payment_status is null or payment_status in ('PENDING', 'PAID', 'WAIVED')),
    constraint event_registration_language_ck check (person_language in ('gl', 'es')),
    constraint event_registration_name_ck check (person_name is null or length(person_name) <= 200),
    constraint event_registration_email_ck check (person_email is null or length(person_email) <= 254),
    -- Só unha solicitude decidida (aceptada ou rexeitada) ten data de decisión.
    constraint event_registration_decided_ck check (status <> 'REJECTED' or decided_at is not null),
    -- Unha anonimizada xa non ten persoa.
    constraint event_registration_anonymized_ck check (anonymized_at is null
        or (person_subject is null and person_name is null and person_email is null))
);

create unique index event_registration_person_uk
    on event_registration (municipality_id, event_id, person_subject)
    where person_subject is not null;
create index event_registration_event_idx on event_registration (municipality_id, event_id, created_at);
create index event_registration_person_idx on event_registration (municipality_id, person_subject)
    where person_subject is not null;

alter table event_registration enable row level security;

create policy event_registration_tenant on event_registration
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
