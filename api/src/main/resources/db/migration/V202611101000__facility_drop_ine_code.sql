-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- O enderezo da instalación deixa de levar o código INE (decisión do 2026-11-10): non ten uso. A
-- regra «o enderezo vai enteiro» queda coa vía, o CP e a localidade. A táboa xa ten RLS e a
-- política facility_tenant.

alter table facility
    drop constraint facility_address_complete_ck,
    drop constraint facility_ine_code_ck,
    drop column ine_code,
    -- O enderezo vai enteiro ou non vai (o número, se o hai, só con vía).
    add constraint facility_address_complete_ck check (
        (street is null) = (postal_code is null)
        and (street is null) = (locality is null)
        and (street is not null or street_number is null));
