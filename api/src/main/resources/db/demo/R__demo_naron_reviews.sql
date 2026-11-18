-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Valoracións de exemplo de Narón (perfil demo, ADR-0037): no pavillón e na pista de pádel 1,
-- sobre reservas da historia (R__demo_naron_history.sql) dos últimos 120 días xa rematadas, á que
-- se foi (confirmadas e sen falta; ningunha das dúas instalacións ten control de entrada). Unha de
-- cada cinco reservas, notas máis altas ca baixas e un comentario nunha de cada tres, inventados e
-- sen nomes. Unha delas ten o comentario oculto polo xestor, para ensinar o chip «Oculto».
--
-- Nada aleatorio: o que toca sae de hashtext do id da reserva. A valoración faise de 1 a 30 horas
-- despois da reserva e nunca despois de agora. O nome ordénase despois de
-- R__demo_naron_people.sql (Flyway aplica as repetibles pola descrición): a historia xa está.
--
-- Idempotente: unha por reserva («on conflict do nothing» sobre booking_review_once_uk) e o id sae
-- do da reserva.

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  padel    constant uuid := '019a0b6e-0000-7000-8000-000000000202';
  pavillon constant uuid := '019a0b6e-0000-7000-8000-000000000204';
  comments constant text[] := array[
    'Pista moi ben coidada e vestiarios limpos.',
    'Boa pista. O marcador non funcionaba.',
    'Moi cómodo reservar desde o móbil.',
    'Os vestiarios tiñan a auga fría.',
    'Ben, aínda que a rede dunha canastra está rota.',
    'Chegamos á hora e a pista aínda estaba ocupada.',
    'As luces tardaron en acenderse.',
    'Todo correcto, repetiremos.',
    'Faltaría unha papeleira preto da pista.',
    'O chan esvara un pouco cando chove.',
    'Moi boa iluminación pola noite.',
    'Aparcamento cómodo e pista en bo estado.'
  ];
begin
  insert into booking_review
    (id, municipality_id, booking_id, facility_id, use_id, stars, comment, created_at)
  select md5('review' || b.id::text)::uuid,
         b.municipality_id, b.id, b.facility_id, b.use_id,
         -- Notas: 5 (40 %), 4 (30 %), 3 (15 %), 2 (10 %) e 1 (5 %).
         case
           when abs(hashtext(b.id::text || 's')) % 20 < 8 then 5
           when abs(hashtext(b.id::text || 's')) % 20 < 14 then 4
           when abs(hashtext(b.id::text || 's')) % 20 < 17 then 3
           when abs(hashtext(b.id::text || 's')) % 20 < 19 then 2
           else 1
         end,
         case
           when abs(hashtext(b.id::text || 'c')) % 3 = 0
             then comments[1 + abs(hashtext(b.id::text || 't')) % array_length(comments, 1)]
         end,
         least(upper(b.period) + (1 + abs(hashtext(b.id::text || 'h')) % 30) * interval '1 hour',
               now())
  from booking b
  where b.municipality_id = naron
    and b.facility_id in (padel, pavillon)
    and b.status = 'CONFIRMED'
    and b.attendance <> 'NO_SHOW'
    and upper(b.period) <= now()
    and upper(b.period) > now() - interval '120 days'
    and abs(hashtext(b.id::text || 'r')) % 5 = 0
  on conflict do nothing;

  -- Un comentario oculto polo xestor: o máis vello con comentario do pavillón.
  update booking_review
     set hidden_at = least(created_at + interval '1 day', now()), hidden_by = 'demo'
   where id = (select id from booking_review
                where municipality_id = naron and facility_id = pavillon
                  and comment is not null
                order by created_at, id
                limit 1)
     and hidden_at is null
     and not exists (select 1 from booking_review
                      where municipality_id = naron and hidden_at is not null);
end
$$;
