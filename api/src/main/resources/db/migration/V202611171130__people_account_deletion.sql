-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Eliminar a conta desde a web (ADR-0043). Dúas funcións que a API pode chamar e que corren como o
-- dono (security definer), porque a RLS de cada táboa só deixa ver o concello da transacción e a
-- conta é común a todos. Devolven JSON (un só valor), para chamalas sen ler ningunha táboa:
--
-- account_deletion_blockers(sub): o que impide eliminar a conta, por tipo e concello (JSON), sen
-- datos de ninguén: reservas confirmadas que aínda non remataron, partidas abertas que organiza e que
-- aínda non se xogaron, inscricións (pide praza ou ten praza) en eventos e actividades sen
-- cancelar que aínda non remataron (co id e o nome do evento, para ligalo), ser persoal activo
-- dun concello e ser administración da plataforma. As esperas non impiden: bórranse.
--
-- close_account_counted(sub): conta, por concello (JSON), as filas que vai tocar close_account (a última
-- versión, V202611161230__event_activity_close_account.sql) e despois chámaa. Só cifras: o
-- rexistro de actividade de cada concello garda cantas, nunca quen. Un módulo que redefina
-- close_account cunha táboa nova engade aquí a súa conta (CloseAccountIT e AccountClosureIT).

create function account_deletion_blockers(account text)
    returns jsonb
    language sql
    stable
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
    select coalesce(jsonb_agg(jsonb_build_object(
               'kind', x.kind, 'municipalityId', x.municipality_id, 'eventId', x.item_id,
               'nameGl', x.name_gl, 'nameEs', x.name_es, 'count', x.total)), '[]'::jsonb)
      from (select 'BOOKING' as kind, b.municipality_id, null::uuid as item_id,
                   null::text as name_gl, null::text as name_es, count(*) as total
              from public.booking b
             where b.holder_subject = account and b.status = 'CONFIRMED'
               and upper(b.period) > now()
             group by b.municipality_id
            union all
            select 'OPEN_MATCH', m.municipality_id, null::uuid, null::text, null::text, count(*)
              from public.booking_open_match m
              join public.booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where m.organizer_subject = account and m.status = 'OPEN' and upper(b.period) > now()
             group by m.municipality_id
            union all
            select 'EVENT', e.municipality_id, e.id, e.name_gl, e.name_es, count(*)
              from public.event_registration r
              join public.event e on e.municipality_id = r.municipality_id and e.id = r.event_id
             where r.person_subject = account and r.status in ('PENDING', 'CONFIRMED')
               and e.cancelled_at is null and e.ends_at > now()
             group by e.municipality_id, e.id, e.name_gl, e.name_es
            union all
            select 'STAFF', s.municipality_id, null::uuid, null::text, null::text, count(*)
              from public.staff_member s
             where s.subject = account and s.status = 'ACTIVE'
             group by s.municipality_id
            union all
            select 'PLATFORM_ADMIN', null::uuid, null::uuid, null::text, null::text, count(*)
              from public.platform_admin p
             where p.subject = account
            having count(*) > 0) x;
$$;

revoke all on function account_deletion_blockers(text) from public;

create function close_account_counted(account text)
    returns jsonb
    language plpgsql
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
declare
    touched jsonb;
begin
    select coalesce(jsonb_object_agg(c.m::text, c.n), '{}'::jsonb)
      into touched
      from (select x.m, count(*) as n
              from (select w.municipality_id as m
                      from public.booking_wait w
                     where w.account_subject = account
                    union all
                    select r.municipality_id
                      from public.booking_review r
                      join public.booking b
                        on b.municipality_id = r.municipality_id and b.id = r.booking_id
                     where b.holder_subject = account
                    union all
                    select o.municipality_id
                      from public.booking_open_match o
                     where o.organizer_subject = account
                    union all
                    select p.municipality_id
                      from public.booking_open_match_player p
                     where p.account_subject = account
                    union all
                    select g.municipality_id
                      from public.booking_organization_manager g
                      join public.account_email a on g.email_key = lower(btrim(a.email))
                     where a.account_subject = account
                    union all
                    select q.municipality_id
                      from public.booking_series_request q
                     where q.requested_by_subject = account
                    union all
                    select e.municipality_id
                      from public.event_registration e
                     where e.person_subject = account
                    union all
                    select i.municipality_id
                      from public.incident i
                     where i.reporter_subject = account) x
             group by x.m) c;
    perform public.close_account(account);
    return touched;
end
$$;

revoke all on function close_account_counted(text) from public;

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('grant execute on function account_deletion_blockers(text) to %I', '${approle}');
    execute format('grant execute on function close_account_counted(text) to %I', '${approle}');
  end if;
end
$$;
