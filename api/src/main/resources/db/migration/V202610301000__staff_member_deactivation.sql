-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Desactivar en vez de dar de baixa (ADR-0027, revisión do 2026-10-30; especificación, bloque 7,
-- P7): a pertenza desactivada pódese reactivar sen invitación nova, co mesmo rol. O estado REVOKED
-- pasa a DEACTIVATED e revoked_at e revoked_by, a deactivated_at e deactivated_by. Reactivar
-- devolve a fila a ACTIVE e borra deactivated_at e deactivated_by: a conservación (bloqueo ao ano
-- e anonimización aos 3 anos) conta sempre desde a última desactivación. Ningunha fila se borra.
--
-- Separar a conta de traballo da persoal (P1): a función is_active_staff_account di se unha
-- persoa (o seu sub) ten unha pertenza activa en calquera concello. Corre como o dono
-- (security definer), porque a RLS de staff_member só deixa ver o concello da transacción, e
-- devolve só si ou non: nin o concello, nin o rol, nin a fila. Úsaa a reserva da veciñanza.

alter table staff_member rename column revoked_by to deactivated_by;
alter table staff_member rename column revoked_at to deactivated_at;

alter table staff_member drop constraint staff_member_status_ck;
alter table staff_member drop constraint staff_member_revoked_ck;
alter table staff_member drop constraint staff_member_revoked_by_ck;
drop index staff_member_email_uk;

update staff_member set status = 'DEACTIVATED' where status = 'REVOKED';

alter table staff_member add constraint staff_member_status_ck
    check (status in ('INVITED', 'ACTIVE', 'DEACTIVATED'));
-- Desactivada ⇔ quen e cando.
alter table staff_member add constraint staff_member_deactivated_ck check (
    (status = 'DEACTIVATED') = (deactivated_at is not null)
    and (deactivated_at is null) = (deactivated_by is null));
alter table staff_member add constraint staff_member_deactivated_by_ck
    check (deactivated_by is null or length(deactivated_by) between 1 and 255);

-- Unha pertenza non desactivada por concello e correo: volver invitar a alguén desactivado crea
-- outra fila, e entón a vella xa non se pode reactivar (staff.already-member).
create unique index staff_member_email_uk on staff_member (municipality_id, email)
    where status <> 'DEACTIVATED';
-- A función busca a persoa en todos os concellos.
create index staff_member_active_subject_idx on staff_member (subject) where status = 'ACTIVE';

create function is_active_staff_account(account text) returns boolean
    language sql
    stable
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
    select exists (
        select 1 from public.staff_member where subject = account and status = 'ACTIVE');
$$;

revoke all on function is_active_staff_account(text) from public;

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('grant execute on function is_active_staff_account(text) to %I', '${approle}');
  end if;
end
$$;
