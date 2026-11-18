-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- «¿Es un concello?» (ADR-0042, revisión do 2026-11-17): as solicitudes de alta cuxo correo non
-- saíu á primeira. A API envía o correo a quen opera o servizo e só se o SMTP falla garda aquí a
-- solicitude para reintentala (JoinRequestRetryService). Bórrase ao enviarse ou, se non sae, aos
-- furar.join-request.retention (P7D por defecto): nunca queda máis dunha semana.
--
-- Táboa da plataforma, sen concello (TenancyAllowlist, coma platform_admin): a solicitude é dun
-- concello que aínda non está na instalación, así que non hai municipality_id que poñer nin RLS
-- que aplicar. Só a len e escriben a API e a tarefa de reintento (JoinRequestSql, @NativeSql
-- GLOBAL); nin a web nin a exportación a ven.
--
-- Os campos son os do formulario, cos seus límites (JoinRequest), e o idioma no que se encheu (o
-- do correo). Nin a IP nin o campo trampa: un envío descartado nunca chega aquí.

create table join_request (
    id               uuid        primary key,
    municipality     text        not null,
    name             text        not null,
    role             text,
    email            text        not null,
    phone            text        not null,
    message          text,
    language         text        not null,
    received_at      timestamptz not null,
    attempts         int         not null,
    next_attempt_at  timestamptz not null,
    constraint join_request_municipality_ck check (length(municipality) between 1 and 160),
    constraint join_request_name_ck check (length(name) between 1 and 120),
    constraint join_request_role_ck check (role is null or length(role) between 1 and 120),
    constraint join_request_email_ck check (length(email) between 3 and 254),
    constraint join_request_phone_ck check (length(phone) between 3 and 30),
    constraint join_request_message_ck check (message is null or length(message) between 1 and 2000),
    constraint join_request_language_ck check (language in ('gl', 'es')),
    constraint join_request_attempts_ck check (attempts >= 1)
);

create index join_request_next_attempt_ix on join_request (next_attempt_at);
