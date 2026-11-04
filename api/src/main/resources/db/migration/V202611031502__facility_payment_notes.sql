-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Pago e devolución de cada instalación (D2-11, revisión do 2026-11-03): só se usan se o concello
-- escolle «Distinto por instalación» (municipality.payment_notes_scope = 'FACILITY'). Opcionais;
-- cada nota, se a hai, en gl e es. A táboa xa ten RLS e a política facility_tenant.

alter table facility
    add column payment_note jsonb,
    add column refund_note  jsonb,
    add constraint facility_payment_note_ck check (
        payment_note is null
        or (jsonb_typeof(payment_note) = 'object' and payment_note ? 'gl' and payment_note ? 'es'
            and length(payment_note ->> 'gl') <= 500 and length(payment_note ->> 'es') <= 500)),
    add constraint facility_refund_note_ck check (
        refund_note is null
        or (jsonb_typeof(refund_note) = 'object' and refund_note ? 'gl' and refund_note ? 'es'
            and length(refund_note ->> 'gl') <= 500 and length(refund_note ->> 'es') <= 500));
