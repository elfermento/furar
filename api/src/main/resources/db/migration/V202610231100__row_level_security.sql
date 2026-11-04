-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Row Level Security en toda táboa do concello (ADR-0006, ARQ3-1/ARQ4-1). Segunda capa, debaixo de
-- @TenantId: tamén filtra o SQL nativo. O concello fíxao TenantJpaTransactionManager ao comezar
-- cada transacción (set_config(..., true)); sen el, a variable é null e non se ve nin se escribe
-- nada. Sen FORCE: o dono (furar_owner, só Flyway e copias) non se ve afectado; furar_app non ten
-- BYPASSRLS. Toda táboa nova con municipality_id tráea da plantilla e SchemaTenancyIT esíxea.

alter table custom_field_definition enable row level security;

create policy custom_field_definition_tenant on custom_field_definition
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
