-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Unha reserva con N prazas e os seus participantes, e «a reserva é do correo» (ADR-0031).
--
--   1. booking.seats e booking_occupancy.seats: nun uso por prazas a reserva leva de 1 a 11
--      persoas; use_slot.taken sobe e baixa seats dunha vez e o seu CHECK segue gardando o cupo.
--   2. booking.holder_email_key: o correo do titular normalizado pola aplicación (EmailKey.of).
--      O límite por persoa e a praza cóntanse por el, teña conta ou non. holder_subject pasa a
--      ser «a conta ligada»: unha reserva do persoal pode tela (bórrase
--      booking_booked_by_holder_ck) e queda nula mentres o correo non teña conta.
--   3. booking_participant: unha fila por persoa que ocupa praza. O titular só leva fila se a
--      ocupa. «Unha praza por persoa» pasa de booking (booking_one_seat_per_person, por conta) a
--      booking_participant_one_seat (por persoa): person_key é 'e:<correo do titular>',
--      'd:<id da ficha>', 'w:<id da fila>' (sen correo ou acompañante) ou 'x:<id da fila>'
--      (anonimizada).
--   4. A historia gaña LINKED (a reserva pasou á conta do seu correo) e HOLDER_CHANGED (o persoal
--      corrixiu o correo).
--   5. dependent_has_bookings: se unha ficha figura nalgunha reserva de calquera concello (só si
--      ou non, coma has_resident_bookings). close_account: o paso da baixa dunha conta, que só
--      pode executar o dono da base.

-- 1. Prazas ------------------------------------------------------------------------------------
alter table booking add column seats int not null default 1;
alter table booking
    add constraint booking_seats_ck
        check (seats between 1 and 11 and (rental_mode = 'SEATS' or seats = 1));

alter table booking_occupancy drop constraint booking_occupancy_seats_ck;
alter table booking_occupancy
    add constraint booking_occupancy_seats_ck
        check (seats between 1 and 11 and (rental_mode = 'SEATS' or seats = 1));

-- 2. A reserva é do correo -----------------------------------------------------------------------
alter table booking add column holder_email_key text;
update booking set holder_email_key = lower(btrim(holder_email)) where holder_email is not null;
alter table booking
    add constraint booking_holder_email_key_ck
        check ((holder_email_key is null) = (holder_email is null));
alter table booking drop constraint booking_booked_by_holder_ck;

-- O límite por persoa e as reservas dun correo aínda sen conta (as que se ligan ao entrar).
create index booking_holder_email_key_idx
    on booking (municipality_id, holder_email_key, lower(period));
create index booking_unlinked_email_idx
    on booking (municipality_id, holder_email_key) where holder_subject is null;

-- 3. Participantes -------------------------------------------------------------------------------
alter table booking drop constraint booking_one_seat_per_person;
alter table booking add constraint booking_use_uk unique (municipality_id, id, use_id);

create table booking_participant (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    booking_id      uuid not null,
    -- Copia da reserva, para a exclusión: use_id por FK composta; period e active escríbeos
    -- booking na mesma transacción en que reserva, cambia ou cancela.
    use_id          uuid not null,
    period          tstzrange not null,
    kind            text not null,
    person_key      text not null,
    -- A mesma persoa a cargo en todas as súas reservas: FK a unha táboa publicada de people
    -- (CrossModuleForeignKeys). Impide borrar unha ficha con reservas, en calquera concello.
    dependent_id    uuid references account_dependent (id),
    -- Copia do nome e do ano: DEPENDENT e COMPANION, ata a anonimización. O do titular sae de
    -- booking.holder_name.
    name            text,
    birth_year      int,
    position        int not null,
    -- false ao cancelar ou cambiar. replaced: un cambio da reserva substituíuna por outras; as
    -- dunha reserva cancelada sen substituír son as que levaba ao cancelar.
    active          boolean not null default true,
    replaced        boolean not null default false,
    constraint booking_participant_tenant_uk unique (municipality_id, id),
    -- Ao cambiar de uso, as filas vellas (xa inactivas) seguen a reserva.
    constraint booking_participant_booking_fk foreign key (municipality_id, booking_id, use_id)
        references booking (municipality_id, id, use_id) on update cascade,
    constraint booking_participant_kind_ck check (kind in ('HOLDER', 'DEPENDENT', 'COMPANION')),
    constraint booking_participant_key_ck check (person_key ~ '^[edwx]:.+$'),
    constraint booking_participant_dependent_key_ck
        check ((dependent_id is not null) = (person_key like 'd:%')),
    constraint booking_participant_dependent_kind_ck
        check (dependent_id is null or kind = 'DEPENDENT'),
    constraint booking_participant_name_ck check (
        (kind <> 'HOLDER' and person_key not like 'x:%')
        = (name is not null and birth_year is not null)),
    constraint booking_participant_name_length_ck check (length(name) between 1 and 200),
    constraint booking_participant_birth_year_ck check (birth_year between 1900 and 2200),
    constraint booking_participant_anonymized_ck check (person_key not like 'x:%' or not active),
    constraint booking_participant_position_ck check (position >= 0),
    constraint booking_participant_replaced_ck check (not (replaced and active)),
    constraint booking_participant_period_ck check (
        not isempty(period) and lower(period) is not null and upper(period) is not null
        and lower_inc(period) and not upper_inc(period)),
    -- Unha praza por persoa: a mesma persoa, titular ou a cargo, non ten dúas no mesmo uso e
    -- franxa.
    constraint booking_participant_one_seat
        exclude using gist (use_id with =, person_key with =, period with &&) where (active)
);

create index booking_participant_booking_idx
    on booking_participant (municipality_id, booking_id, position);
create index booking_participant_dependent_idx on booking_participant (dependent_id);

alter table booking_participant enable row level security;

create policy booking_participant_tenant on booking_participant
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

-- As reservas por prazas xa feitas e sen anonimizar: o titular ocupaba a praza. Co correo, 'e:';
-- sen el, 'w:'. Se dúas confirmadas do mesmo correo se pisan (unha do mostrador e outra de
-- internet), a máis antiga leva 'e:' e as outras 'w:'.
with fresh as (
    select b.*, uuidv7() as participant_id from booking b
    where b.rental_mode = 'SEATS' and b.anonymized_at is null
)
insert into booking_participant
    (id, municipality_id, booking_id, use_id, period, kind, person_key, position, active)
select f.participant_id, f.municipality_id, f.id, f.use_id, f.period, 'HOLDER',
       case
           when f.holder_email_key is null then 'w:' || f.participant_id
           when f.status = 'CONFIRMED' and exists (
               select 1 from booking o
               where o.municipality_id = f.municipality_id and o.use_id = f.use_id
                 and o.rental_mode = 'SEATS' and o.status = 'CONFIRMED'
                 and o.anonymized_at is null and o.holder_email_key = f.holder_email_key
                 and o.period && f.period and (o.created_at, o.id) < (f.created_at, f.id))
               then 'w:' || f.participant_id
           else 'e:' || f.holder_email_key
       end,
       0, f.status = 'CONFIRMED'
from fresh f;

-- 4. Historia --------------------------------------------------------------------------------------
alter table booking_event drop constraint booking_event_kind_ck;
alter table booking_event
    add constraint booking_event_kind_ck check (kind in (
        'CREATED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY', 'KEPT', 'PAID',
        'REFUNDED', 'PAYMENT_UNDONE', 'LINKED', 'HOLDER_CHANGED'));

-- 5. Funcións --------------------------------------------------------------------------------------
-- Corre como o dono (security definer): a RLS de booking_participant só deixa ver o concello da
-- transacción. Devolve só si ou non.
create function dependent_has_bookings(dependent uuid) returns boolean
    language sql
    stable
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
    select exists (select 1 from public.booking_participant where dependent_id = dependent);
$$;

revoke all on function dependent_has_bookings(uuid) from public;

-- Baixa dunha conta (docs/datos-persoais.md, «Dereitos»): execútaa quen opera o despregue, como
-- dono da base, antes de borrar a conta no sistema de acceso. Borra a fila da conta en
-- account_email e as fichas que nunca se usaron, e desactiva as que figuran nalgunha reserva, que
-- se borran soas cando se anonimiza a última. O rol da API non a pode chamar.
create function close_account(account text) returns void
    language sql
    set search_path = pg_catalog, public, pg_temp
as $$
    delete from public.account_email where account_subject = account;
    delete from public.account_dependent d
     where d.account_subject = account
       and not exists (select 1 from public.booking_participant p where p.dependent_id = d.id);
    update public.account_dependent
       set deactivated_at = now(), updated_at = now(), version = version + 1
     where account_subject = account and deactivated_at is null;
$$;

revoke all on function close_account(text) from public;

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('grant execute on function dependent_has_bookings(uuid) to %I', '${approle}');
  end if;
end
$$;
