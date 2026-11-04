-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Persoal de demo (perfil demo; plan do dominio 4, D4-12): os roles de Narón e Ferrol saen desta
-- táboa e non do token (ADR-0027). Cada pertenza activa leva o sub fixo do seu usuario do realm de
-- demo (deploy/keycloak/demo/overlay.json; táboa en deploy/keycloak/README.md):
--
--   xestor-naron       …0902  MANAGER de Narón, ACTIVE (invitada pola plataforma)
--   xestor-ferrol      …0903  MANAGER de Ferrol, ACTIVE (invitada pola plataforma)
--   recepcion-naron    …0904  STAFF de Narón, ACTIVE
--   ex-recepcion-naron …0905  STAFF de Narón, DEACTIVATED (desactivada por xestor-naron)
--   persoal-novo@example.org  STAFF de Narón, INVITED cunha invitación vixente (sen conta)
--   persoal-antigo@example.org STAFF de Narón, INVITED cunha invitación caducada
--   xestor-verin, xestor-vilardevos, xestor-rios, xestor-castrelo-do-val, xestor-cualedro,
--   xestor-laza, xestor-monterrei e xestor-oimbra  …0907 a …090e  MANAGER do seu concello, ACTIVE
--                      (invitadas pola plataforma)
--
-- admin-plataforma (…0900) invita ás primeiras persoas xestoras; o resto invítao xestor-naron.
-- Cada pertenza ten a súa invitación (usada ao aceptar). Do token só se garda o SHA-256 dun valor
-- aleatorio: ningunha destas invitacións se pode aceptar cunha ligazón.
--
-- Datas relativas ao momento de aplicar o script (a demo arrinca nunha base nova): as altas, hai
-- un mes; a baixa, hai cinco días; a invitación vixente, de onte; a caducada, de hai doce días.
-- Rexistro de actividade (audit_event) con staff.invite, staff.accept e staff.deactivate de exemplo,
-- con datas pasadas e sen datos persoais nos detalles (memberId, role).
--
-- Idempotente: ids fixos e «on conflict do nothing».

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  ferrol   constant uuid := '019a0b6e-0000-7000-8000-000000000002';
  -- sub dos usuarios do realm de demo
  admin    constant text := '019a0b6e-0000-7000-8000-000000000900';
  xn       constant text := '019a0b6e-0000-7000-8000-000000000902';
  xf       constant text := '019a0b6e-0000-7000-8000-000000000903';
  cn       constant text := '019a0b6e-0000-7000-8000-000000000904';
  excn     constant text := '019a0b6e-0000-7000-8000-000000000905';
  ahora    constant timestamptz := date_trunc('minute', now());
  t0       constant timestamptz := date_trunc('day', now()) - interval '30 days' + interval '9 hours';
  revogada constant timestamptz := ahora - interval '5 days';
  vixente  constant timestamptz := ahora - interval '1 day';
  caducada constant timestamptz := ahora - interval '12 days';
  m record;
begin
  -- Pertenzas: id, concello, slug, correo, nome, sub, rol, estado, quen invita, invitada, aceptada.
  for m in
    select *
    from (values
      ('019a0b6e-0000-7000-8000-000000000911'::uuid, naron, 'naron', 'xestor-naron@example.org',
       'Xestora Narón', xn, 'MANAGER', 'ACTIVE', admin, t0, t0 + interval '1 hour'),
      ('019a0b6e-0000-7000-8000-000000000912'::uuid, ferrol, 'ferrol', 'xestor-ferrol@example.org',
       'Xestor Ferrol', xf, 'MANAGER', 'ACTIVE', admin, t0 + interval '30 minutes',
       t0 + interval '2 hours'),
      ('019a0b6e-0000-7000-8000-000000000913'::uuid, naron, 'naron', 'recepcion-naron@example.org',
       'Recepción Narón', cn, 'STAFF', 'ACTIVE', xn, t0 + interval '1 day',
       t0 + interval '1 day 3 hours'),
      ('019a0b6e-0000-7000-8000-000000000914'::uuid, naron, 'naron',
       'ex-recepcion-naron@example.org', 'Exrecepción Narón', excn, 'STAFF', 'DEACTIVATED', xn,
       t0 + interval '1 day 10 minutes', t0 + interval '2 days'),
      ('019a0b6e-0000-7000-8000-000000000916'::uuid, naron, 'naron', 'persoal-novo@example.org',
       null, null, 'STAFF', 'INVITED', xn, vixente, null),
      ('019a0b6e-0000-7000-8000-000000000917'::uuid, naron, 'naron', 'persoal-antigo@example.org',
       null, null, 'STAFF', 'INVITED', xn, caducada, null),
      -- Comarca de Verín: unha persoa xestora por concello, invitada pola plataforma.
      ('019a0b6e-0000-7000-8000-000000000918'::uuid, '019a0b6e-0000-7000-8000-000000000003'::uuid,
       'verin', 'xestor-verin@example.org', 'Xestor Verín', '019a0b6e-0000-7000-8000-000000000907',
       'MANAGER', 'ACTIVE', admin, t0 + interval '4 days', t0 + interval '4 days 1 hour'),
      ('019a0b6e-0000-7000-8000-000000000919'::uuid, '019a0b6e-0000-7000-8000-000000000004'::uuid,
       'vilardevos', 'xestor-vilardevos@example.org', 'Xestor Vilardevós', '019a0b6e-0000-7000-8000-000000000908',
       'MANAGER', 'ACTIVE', admin, t0 + interval '5 days', t0 + interval '5 days 1 hour'),
      ('019a0b6e-0000-7000-8000-00000000091a'::uuid, '019a0b6e-0000-7000-8000-000000000005'::uuid,
       'rios', 'xestor-rios@example.org', 'Xestor Riós', '019a0b6e-0000-7000-8000-000000000909',
       'MANAGER', 'ACTIVE', admin, t0 + interval '6 days', t0 + interval '6 days 1 hour'),
      ('019a0b6e-0000-7000-8000-00000000091b'::uuid, '019a0b6e-0000-7000-8000-000000000006'::uuid,
       'castrelo-do-val', 'xestor-castrelo-do-val@example.org', 'Xestor Castrelo do Val', '019a0b6e-0000-7000-8000-00000000090a',
       'MANAGER', 'ACTIVE', admin, t0 + interval '7 days', t0 + interval '7 days 1 hour'),
      ('019a0b6e-0000-7000-8000-00000000091c'::uuid, '019a0b6e-0000-7000-8000-000000000007'::uuid,
       'cualedro', 'xestor-cualedro@example.org', 'Xestor Cualedro', '019a0b6e-0000-7000-8000-00000000090b',
       'MANAGER', 'ACTIVE', admin, t0 + interval '8 days', t0 + interval '8 days 1 hour'),
      ('019a0b6e-0000-7000-8000-00000000091d'::uuid, '019a0b6e-0000-7000-8000-000000000008'::uuid,
       'laza', 'xestor-laza@example.org', 'Xestor Laza', '019a0b6e-0000-7000-8000-00000000090c',
       'MANAGER', 'ACTIVE', admin, t0 + interval '9 days', t0 + interval '9 days 1 hour'),
      ('019a0b6e-0000-7000-8000-00000000091e'::uuid, '019a0b6e-0000-7000-8000-000000000009'::uuid,
       'monterrei', 'xestor-monterrei@example.org', 'Xestor Monterrei', '019a0b6e-0000-7000-8000-00000000090d',
       'MANAGER', 'ACTIVE', admin, t0 + interval '10 days', t0 + interval '10 days 1 hour'),
      ('019a0b6e-0000-7000-8000-00000000091f'::uuid, '019a0b6e-0000-7000-8000-00000000000a'::uuid,
       'oimbra', 'xestor-oimbra@example.org', 'Xestor Oímbra', '019a0b6e-0000-7000-8000-00000000090e',
       'MANAGER', 'ACTIVE', admin, t0 + interval '11 days', t0 + interval '11 days 1 hour')
    ) as v (id, municipality_id, slug, email, display_name, subject, role, status, invited_by,
            invited_at, accepted_at)
  loop
    insert into staff_member (id, municipality_id, email, display_name, subject, role, status,
                              language, invited_by, invited_at, accepted_at, deactivated_by,
                              deactivated_at, last_sign_in_at, previous_sign_in_at, created_at,
                              updated_at, version)
    values (m.id, m.municipality_id, m.email, m.display_name, m.subject, m.role, m.status, 'gl',
            m.invited_by, m.invited_at, m.accepted_at,
            case when m.status = 'DEACTIVATED' then xn end,
            case when m.status = 'DEACTIVATED' then revogada end,
            -- Dous accesos anteriores para que a xestora vexa «último acceso» desde o primeiro día.
            case when m.subject = xn then ahora - interval '1 day' end,
            case when m.subject = xn then ahora - interval '3 days' end,
            m.invited_at,
            coalesce(case when m.status = 'DEACTIVATED' then revogada end, m.accepted_at, m.invited_at),
            case m.status when 'INVITED' then 0 when 'ACTIVE' then 1 else 2 end)
    on conflict (id) do nothing;

    -- A invitación de cada pertenza: usada ao aceptar, ou aberta (vixente ou caducada).
    insert into staff_invitation (id, municipality_id, member_id, token_hash, requested_by,
                                  created_at, expires_at, used_at, superseded_at, delivery)
    values (overlay(m.id::text placing '92' from 34 for 2)::uuid, m.municipality_id, m.id,
            sha256(convert_to(gen_random_uuid()::text || gen_random_uuid()::text, 'UTF8')),
            m.invited_by, m.invited_at, m.invited_at + interval '168 hours', m.accepted_at, null,
            'SENT')
    on conflict (id) do nothing;

    -- Rexistro de actividade: a invitación e, se a aceptou, a aceptación (quen actúa: a persoa).
    insert into audit_event (id, occurred_at, actor_subject, action, outcome, municipality_id,
                             municipality_slug, request_id, details)
    values (overlay(m.id::text placing '93' from 34 for 2)::uuid, m.invited_at, m.invited_by,
            'staff.invite', 'SUCCESS', m.municipality_id, m.slug, null,
            jsonb_build_object('memberId', m.id, 'role', m.role))
    on conflict (id) do nothing;

    if m.accepted_at is not null then
      insert into audit_event (id, occurred_at, actor_subject, action, outcome, municipality_id,
                               municipality_slug, request_id, details)
      values (overlay(m.id::text placing '94' from 34 for 2)::uuid, m.accepted_at, m.subject,
              'staff.accept', 'SUCCESS', m.municipality_id, m.slug, null,
              jsonb_build_object('memberId', m.id, 'role', m.role))
      on conflict (id) do nothing;
    end if;

    if m.status = 'DEACTIVATED' then
      insert into audit_event (id, occurred_at, actor_subject, action, outcome, municipality_id,
                               municipality_slug, request_id, details)
      values (overlay(m.id::text placing '95' from 34 for 2)::uuid, revogada, xn, 'staff.deactivate',
              'SUCCESS', m.municipality_id, m.slug, null,
              jsonb_build_object('memberId', m.id, 'role', m.role))
      on conflict (id) do nothing;
    end if;
  end loop;
end
$$;
