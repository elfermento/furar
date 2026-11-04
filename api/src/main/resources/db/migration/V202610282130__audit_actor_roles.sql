-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Roles de quen actúa (ADR-0020, ADR-0027; plan do dominio 4, §1.2.12): os roles efectivos da
-- persoa no concello da acción no momento de facela (os da táboa de pertenzas e o da plataforma).
-- Responde a «con que rol e en que concello» sen depender de que a pertenza siga existindo.
-- Os eventos anteriores quedan sen roles ('{}'). furar_app segue sen UPDATE nin DELETE: a columna
-- nova herda os permisos da táboa (só engadir e ler). Sen índice GIN: o volume é baixo.

alter table audit_event
  add column actor_roles text[] not null default '{}'
    check (actor_roles <@ array ['PLATFORM_ADMIN', 'MANAGER', 'STAFF']::text[]);
