-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Administración da plataforma de demo (perfil demo, ADR-0028): admin-plataforma (sub fixo …0900
-- do realm de demo, deploy/keycloak/demo/overlay.json) é administración desde esta táboa, non
-- desde un grupo de Keycloak. Como xa hai unha, FURAR_ADMIN_EMAIL non fai nada na demo.
--
-- Idempotente: id fixo e «on conflict do nothing».

insert into platform_admin (id, subject, granted_at, granted_by)
values ('019a0b6e-0000-7000-8000-000000000990', '019a0b6e-0000-7000-8000-000000000900',
        timestamptz '2026-10-01 08:00:00+00', 'furar:demo')
on conflict do nothing;
