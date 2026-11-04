-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Contacto do servizo de deportes e textos de pago e devolución de cada concello (plan do dominio
-- 2, §2.1, D2-02; P8): todos opcionais. Saen na ficha pública e no pé dos correos das reservas.
-- Sen nota, Furar usa o seu texto por defecto («No mostrador, ao chegar» e «O concello dirache
-- como cha devolve»). O dominio valida o formato; a base, o tamaño. A táboa dos concellos non leva
-- RLS (TenancyAllowlist).

alter table municipality
    add column sports_service_name text,
    add column sports_address      text,
    add column sports_phone        text,
    add column sports_email        text,
    add column payment_note        jsonb,
    add column refund_note         jsonb,
    add constraint municipality_sports_text_ck check (
        length(sports_service_name) <= 200 and length(sports_address) <= 200
        and length(sports_phone) <= 30 and length(sports_email) <= 200),
    add constraint municipality_sports_email_ck check (sports_email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    add constraint municipality_payment_note_ck check (
        payment_note is null
        or (jsonb_typeof(payment_note) = 'object' and payment_note ? 'gl' and payment_note ? 'es')),
    add constraint municipality_refund_note_ck check (
        refund_note is null
        or (jsonb_typeof(refund_note) = 'object' and refund_note ? 'gl' and refund_note ? 'es'));
