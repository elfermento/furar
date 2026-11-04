-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Separar a conta de traballo da persoal (ADR-0027, revisión do 2026-10-30; especificación,
-- bloque 7, P1): unha conta que xa reservou como veciña, en calquera concello e en calquera
-- estado, non acepta unha invitación ao persoal. A función has_resident_bookings dio. Corre como
-- o dono (security definer), porque a RLS de booking só deixa ver o concello da transacción, e
-- devolve só si ou non: nin o concello, nin cantas, nin os datos da reserva.

-- A función busca a persoa en todos os concellos.
create index booking_holder_subject_idx on booking (holder_subject);

create function has_resident_bookings(account text) returns boolean
    language sql
    stable
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
    select exists (select 1 from public.booking where holder_subject = account);
$$;

revoke all on function has_resident_bookings(text) from public;

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('grant execute on function has_resident_bookings(text) to %I', '${approle}');
  end if;
end
$$;
