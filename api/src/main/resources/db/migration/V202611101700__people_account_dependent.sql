-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Persoas a cargo (ADR-0031): fichas gardadas na conta de quen reserva. A conta é común a todos
-- os concellos (ADR-0017 punto 2), así que as dúas táboas son de plataforma, sen municipality_id
-- (TenancyAllowlist, coma platform_admin) e sen RLS: toda lectura e escritura da API leva
-- account_subject = :s (DependentSql).
--
-- account_dependent: nome e apelidos, ano de nacemento e a data da declaración «Esta persoa está
-- ao meu cargo». Sen DNI, parentesco nin datos de saúde. «Eliminar» borra a fila se nunca se usou
-- nunha reserva e, se se usou, deixa deactivated_at: a reserva segue ligada á ficha
-- (booking_participant.dependent_id, na migración seguinte). Non pode haber dúas activas co mesmo
-- nome e ano na mesma conta; o límite de 10 vixíao a aplicación.

create table account_dependent (
    id              uuid primary key,
    account_subject text not null,
    name            text not null,
    birth_year      int not null,
    declared_at     timestamptz not null,
    deactivated_at  timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint account_dependent_subject_ck check (length(account_subject) between 1 and 255),
    constraint account_dependent_name_ck check (length(name) between 1 and 200),
    constraint account_dependent_birth_year_ck check (birth_year between 1900 and 2200)
);

create index account_dependent_account_idx on account_dependent (account_subject);

create unique index account_dependent_person_uk
    on account_dependent (account_subject, lower(name), birth_year)
    where deactivated_at is null;

-- Contas coñecidas polo correo: o sub e o correo verificado tal como chegan no token, para saber
-- se o correo dunha reserva do mostrador ten conta sen preguntarlle ao sistema de acceso. O correo
-- vai normalizado pola aplicación (EmailKey.of); a base non normaliza por si. seen_at é o
-- auth_time da última sesión vista: a purga diaria borra as filas vellas.

create table account_email (
    account_subject text primary key,
    email           text not null,
    seen_at         timestamptz not null,
    constraint account_email_email_uk unique (email),
    constraint account_email_subject_ck check (length(account_subject) between 1 and 255),
    constraint account_email_email_ck check (length(email) between 3 and 254)
);

create index account_email_seen_idx on account_email (seen_at);
