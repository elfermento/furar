-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Conservación das reservas (ADR-0017 punto 5, revisión do 2026-11-04; docs/datos-persoais.md):
-- a tarefa diaria BookingRetentionService anonimiza as reservas vellas. Unha reserva anonimizada
-- garda o que precisan os indicadores (instalación, uso, franxa, estado, prezo e cobro) e perde a
-- persoa: holder_subject, holder_name e holder_email a nulo (nulo e non un valor común, para que
-- count(distinct holder_subject) non as conte como unha persoa) e anonymized_at co momento. O
-- idioma queda: non identifica a ninguén.
--
-- A historia (booking_event) segue sendo só de engadir, agás quen actuou: as filas que fixo a
-- propia persoa (o seu sub) pasan a 'furar:anonymized'. O rol da API só pode cambiar esa columna
-- e só a ese valor (o trigger comprobao), así que tampouco pode reescribir a historia.

alter table booking add column anonymized_at timestamptz;

alter table booking
    alter column holder_subject drop not null,
    alter column holder_name drop not null,
    alter column holder_email drop not null;

-- Ou a persoa enteira, ou ningún dato dela e a data da anonimización.
alter table booking
    add constraint booking_anonymized_ck check (
        (anonymized_at is null) = (holder_subject is not null)
        and (anonymized_at is null) = (holder_name is not null)
        and (anonymized_at is null) = (holder_email is not null));

-- As candidatas da tarefa: as aínda sen anonimizar, pola fin da franxa.
create index booking_retention_idx on booking (municipality_id, upper(period))
    where anonymized_at is null;

create function booking_event_anonymize_only() returns trigger
    language plpgsql
    set search_path = pg_catalog, public, pg_temp
as $$
begin
    if new.actor_subject <> 'furar:anonymized'
       or (new.id, new.municipality_id, new.booking_id, new.kind, new.at, new.from_period,
           new.to_period, new.channels)
          is distinct from
          (old.id, old.municipality_id, old.booking_id, old.kind, old.at, old.from_period,
           old.to_period, old.channels) then
        raise exception 'booking_event: only the actor of an anonymised booking can change';
    end if;
    return new;
end
$$;

create trigger booking_event_anonymize_only
    before update on booking_event
    for each row execute function booking_event_anonymize_only();

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('grant update (actor_subject) on booking_event to %I', '${approle}');
  end if;
end
$$;
