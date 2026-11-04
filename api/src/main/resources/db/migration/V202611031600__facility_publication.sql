-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Asistente «Nova instalación» (revisión do 2026-11-03 de ADR-0022, punto 3): a instalación nace
-- sen publicar e só se publica cun «Publicar» explícito. O borrador gárdase só co nome: o enderezo
-- pode faltar mentres non se publica, e as coordenadas poden faltar sempre (sen mapa no despregue).
-- As que xa había quedan publicadas (o default tamén cobre os datos de exemplo). A táboa xa ten RLS
-- e a política facility_tenant.

alter table facility
    add column published boolean not null default true,
    alter column street drop not null,
    alter column postal_code drop not null,
    alter column locality drop not null,
    alter column ine_code drop not null,
    alter column latitude drop not null,
    alter column longitude drop not null,
    -- O enderezo vai enteiro ou non vai (o número, se o hai, só con vía).
    add constraint facility_address_complete_ck check (
        (street is null) = (postal_code is null)
        and (street is null) = (locality is null)
        and (street is null) = (ine_code is null)
        and (street is not null or street_number is null)),
    -- O punto vai enteiro e só cun enderezo.
    add constraint facility_point_ck check (
        (latitude is null) = (longitude is null)
        and (street is not null or latitude is null)),
    -- Publicada = co enderezo.
    add constraint facility_published_address_ck check (not published or street is not null);
