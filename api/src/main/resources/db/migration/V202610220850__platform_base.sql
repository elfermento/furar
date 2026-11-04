-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Infraestrutura da plataforma, sen datos de ningún módulo (ADR-0010, ARQ-8): extensión para as
-- restricións de exclusión (ADR-0012) e rexistro de eventos de Spring Modulith (TenancyAllowlist).

create extension if not exists btree_gist;

-- DDL oficial de spring-modulith-events-jdbc 2.1.1 (schemas/v2/schema-postgresql.sql).
create table if not exists event_publication
(
  id                     uuid not null,
  listener_id            text not null,
  event_type             text not null,
  serialized_event       text not null,
  publication_date       timestamp with time zone not null,
  completion_date        timestamp with time zone,
  status                 text,
  completion_attempts    int,
  last_resubmission_date timestamp with time zone,
  primary key (id)
);
create index if not exists event_publication_serialized_event_hash_idx on event_publication using hash(serialized_event);
create index if not exists event_publication_by_completion_date_idx on event_publication (completion_date);
