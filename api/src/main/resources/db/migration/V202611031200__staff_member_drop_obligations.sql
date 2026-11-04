-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Sen aceptación das obrigas en Furar (ADR-0027, revisión do 2026-11-03): a persoa invitada
-- acepta a invitación ao volver co correo verificado, sen modal nin casiña. As obrigas do persoal
-- publícanse no aviso legal e a súa aceptación é un procedemento propio do concello (ens.md,
-- op.acc.6 e mp.per.2). A versión das obrigas xa non se le nin se garda: bórrase a columna e as
-- comprobacións que a nomeaban volven sen ela (aceptar garda a persoa e a data á vez). As
-- aceptacións pasadas seguen no rexistro de actividade (staff.accept), cos seus detalles.
-- A táboa segue igual para a RLS (a política staff_member_tenant non cambia).

alter table staff_member drop constraint staff_member_active_ck;
alter table staff_member drop constraint staff_member_accepted_ck;
alter table staff_member drop constraint staff_member_obligations_ck;
alter table staff_member drop column obligations_version;

alter table staff_member add constraint staff_member_active_ck check (
    status <> 'ACTIVE' or (subject is not null and accepted_at is not null));
alter table staff_member add constraint staff_member_accepted_ck
    check ((accepted_at is null) = (subject is null));
