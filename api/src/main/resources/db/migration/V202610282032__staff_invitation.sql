-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Invitación ao persoal dun concello (ADR-0027; plan do dominio 4, §1.2.6, §1.2.7 e §2.1): unha
-- fila por envío (alta ou reenvío) dunha pertenza. Só se garda o SHA-256 do token (32 bytes); o
-- token en claro vai no correo e nunca na base. Caduca aos 7 días, úsase unha vez e un reenvío ou
-- unha baixa substitúena (superseded_at). As filas cóntanse para os límites de envío (30 por
-- concello en 24 h, 10 min e 5 envíos por pertenza: SendLimits), así que os índices empezan polo
-- concello e a data.
--
-- Táboa do concello (ADR-0006): municipality_id con FK, unique (municipality_id, id), FK composta á
-- pertenza e RLS. requested_by é o subject de quen invitou ou reenviou. As non aceptadas e caducadas
-- bórranse aos 30 días (§1.2.14).

create table staff_invitation (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    member_id       uuid not null,
    token_hash      bytea not null,
    requested_by    text not null,
    created_at      timestamptz not null,
    expires_at      timestamptz not null,
    used_at         timestamptz,
    superseded_at   timestamptz,
    delivery        text not null,
    constraint staff_invitation_tenant_uk unique (municipality_id, id),
    constraint staff_invitation_member_fk foreign key (municipality_id, member_id)
        references staff_member (municipality_id, id),
    constraint staff_invitation_token_uk unique (token_hash),
    constraint staff_invitation_token_ck check (octet_length(token_hash) = 32),
    constraint staff_invitation_requested_by_ck check (length(requested_by) between 1 and 255),
    -- 7 días exactos: en horas, porque «7 days» nun timestamptz depende do fuso da sesión e dos
    -- cambios de hora.
    constraint staff_invitation_expires_ck check (expires_at = created_at + interval '168 hours'),
    -- Usada ou substituída, nunca as dúas; usada só mentres valía.
    constraint staff_invitation_closed_ck check (num_nonnulls(used_at, superseded_at) <= 1),
    constraint staff_invitation_used_ck
        check (used_at is null or (used_at >= created_at and used_at < expires_at)),
    constraint staff_invitation_superseded_ck
        check (superseded_at is null or superseded_at >= created_at),
    constraint staff_invitation_delivery_ck check (delivery in ('PENDING', 'SENT', 'FAILED'))
);

-- Como moito unha invitación aberta (nin usada nin substituída) por pertenza: reenviar substitúe a
-- anterior na mesma transacción.
create unique index staff_invitation_open_uk on staff_invitation (municipality_id, member_id)
    where used_at is null and superseded_at is null;
-- Límite do concello (envíos nas últimas 24 h).
create index staff_invitation_created_idx on staff_invitation (municipality_id, created_at);
-- Límites da pertenza e a última invitación de cada unha na lista.
create index staff_invitation_member_idx
    on staff_invitation (municipality_id, member_id, created_at desc);

alter table staff_invitation enable row level security;

create policy staff_invitation_tenant on staff_invitation
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
