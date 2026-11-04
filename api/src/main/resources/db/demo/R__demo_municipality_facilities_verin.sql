-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Instalacións de demostración da comarca de Verín (perfil demo), con datos reais de fontes
-- públicas consultadas o 2026-10-30: webs e sedes dos concellos, ordenanzas fiscais, prensa local e
-- OpenStreetMap (© colaboradores de OpenStreetMap, ODbL) para as coordenadas. Só entran as
-- instalacións confirmadas; as pistas de parroquia sen confirmar, non.
--
-- Prezos: os das ordenanzas ou os publicados. Se hai prezo de empadroado e de non empadroado, o
-- uso leva o de non empadroado e sen luz; a descrición di os outros. Sen prezo publicado, o uso
-- queda «sen prezo» (non se mostra).
--
-- Supostos (Furar necesita un dato que non consta): horario de 9:00 a 22:00 todos os días nas
-- pistas e pavillóns sen horario publicado; de 12:00 a 20:00 en xullo e agosto nas piscinas de
-- verán sen horario; o fin da tempada das piscinas de Verín (31 de agosto); 100 prazas nas
-- piscinas sen aforo publicado; e os deportes das pistas polideportivas sen detalle (baloncesto e
-- fútbol sala). As piscinas e as pistas de uso libre non se reservan por internet.
--
-- O nome ordénase despois de R__demo_municipality_facilities.sql e antes das reservas. Sen fotos:
-- cada instalación mostra o debuxo de Furar que lle suxire o seu nome. Enderezos do buscador fixo
-- (FixedAddressSearch). Idempotente: ids fixos e «on conflict do nothing»; os tramos da semana só
-- se a tempada aínda non ten ningún.

insert into facility (id, municipality_id, name, status, type, description, online_booking,
                      show_free_slots, covered, street, street_number, postal_code, locality,
                      ine_code, latitude, longitude, created_at, updated_at, version)
values
  -- Verín
  ('019a0b6e-0000-7000-8000-000000000601', '019a0b6e-0000-7000-8000-000000000003',
   'Pavillón Municipal de Deportes', 'ACTIVE', 'SPORTS_HALL',
   '{"gl": "Pavillón cuberto con pista polideportiva. Aquí está a oficina de Deportes, aberta de luns a venres de 9:00 a 14:00. Prezo por hora de non empadroados e sen luz; con luz, 18,80 €. Empadroados ou con tarxeta Eurocidadán: 10,00 € sen luz e 15,00 € con luz.",
     "es": "Pabellón cubierto con pista polideportiva. Aquí está la oficina de Deportes, abierta de lunes a viernes de 9:00 a 14:00. Precio por hora de no empadronados y sin luz; con luz, 18,80 €. Empadronados o con tarjeta Eurocidadán: 10,00 € sin luz y 15,00 € con luz."}',
   true, false, true, 'Rúa Irmáns Moreno', '13', '32600', 'Verín', '32085', 41.938260,
   -7.434630, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000602', '019a0b6e-0000-7000-8000-000000000003',
   'Frontón municipal', 'ACTIVE', 'OTHER',
   '{"gl": "Frontón do Pavillón Municipal de Deportes. Prezo por hora de non empadroados e sen luz; con luz, 6,30 €. Empadroados ou con tarxeta Eurocidadán: 3,00 € sen luz e 5,00 € con luz.",
     "es": "Frontón del Pabellón Municipal de Deportes. Precio por hora de no empadronados y sin luz; con luz, 6,30 €. Empadronados o con tarjeta Eurocidadán: 3,00 € sin luz y 5,00 € con luz."}',
   true, false, null, 'Rúa Irmáns Moreno', '13', '32600', 'Verín', '32085', 41.938260,
   -7.434630, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000603', '019a0b6e-0000-7000-8000-000000000003',
   'Piscina Climatizada Municipal', 'ACTIVE', 'POOL',
   '{"gl": "Vaso climatizado, ximnasio, zona de relax con jacuzzi e sauna, salas de aeróbic e spinning e ludoteca, ao lado do pavillón. Baño libre de non empadroados; empadroados ou con tarxeta Eurocidadán, 3,50 € adultos e 2,90 € infantil. Tamén hai abonos mensuais, bonos de 5 sesións e cursos. Entrada directa, sen reserva.",
     "es": "Vaso climatizado, gimnasio, zona de relax con jacuzzi y sauna, salas de aeróbic y spinning y ludoteca, al lado del pabellón. Baño libre de no empadronados; empadronados o con tarjeta Eurocidadán, 3,50 € adultos y 2,90 € infantil. También hay abonos mensuales, bonos de 5 sesiones y cursos. Entrada directa, sin reserva."}',
   false, false, true, 'Rúa Irmáns Moreno', null, '32600', 'Verín', '32085', 41.938530,
   -7.434010, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000604', '019a0b6e-0000-7000-8000-000000000003',
   'Campo de fútbol de A Granxa', 'ACTIVE', 'FIELD',
   '{"gl": "Campo de fútbol 11 de herba artificial (100 × 60 m), que se divide en dous de fútbol 7, no Complexo Deportivo A Granxa. Céspede novo en 2025. Bancada cuberta para 420 persoas. Prezos por hora de non empadroados e sen luz; con luz, fútbol 11 a 62,50 €, fútbol 7 a 48,80 € e partido de 2 horas a 81,30 €. Empadroados ou con tarxeta Eurocidadán: fútbol 11 a 40,00 €, fútbol 7 a 30,00 € e partido a 50,00 € sen luz.",
     "es": "Campo de fútbol 11 de hierba artificial (100 × 60 m), que se divide en dos de fútbol 7, en el Complexo Deportivo A Granxa. Césped nuevo en 2025. Grada cubierta para 420 personas. Precios por hora de no empadronados y sin luz; con luz, fútbol 11 a 62,50 €, fútbol 7 a 48,80 € y partido de 2 horas a 81,30 €. Empadronados o con tarjeta Eurocidadán: fútbol 11 a 40,00 €, fútbol 7 a 30,00 € y partido a 50,00 € sin luz."}',
   true, false, false, 'Rúa Antonio Fernández Pérez', null, '32600', 'Verín', '32085',
   41.933100, -7.442720, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000605', '019a0b6e-0000-7000-8000-000000000003',
   'Pavillón Polideportivo Municipal de A Granxa', 'ACTIVE', 'SPORTS_HALL',
   '{"gl": "Pavillón cuberto con pista de madeira e sete vestiarios, no Complexo Deportivo A Granxa. Prezo por hora de non empadroados e sen luz; con luz, 18,80 €. Empadroados ou con tarxeta Eurocidadán: 10,00 € sen luz e 15,00 € con luz.",
     "es": "Pabellón cubierto con pista de madera y siete vestuarios, en el Complexo Deportivo A Granxa. Precio por hora de no empadronados y sin luz; con luz, 18,80 €. Empadronados o con tarjeta Eurocidadán: 10,00 € sin luz y 15,00 € con luz."}',
   true, false, true, 'Rúa Antonio Fernández Pérez', null, '32600', 'Verín', '32085',
   41.933730, -7.442720, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000606', '019a0b6e-0000-7000-8000-000000000003',
   'Pistas de pádel de A Granxa', 'ACTIVE', 'PADEL_COURT',
   '{"gl": "Dúas pistas de pádel de herba artificial con paredes de vidro, ao aire libre, no Complexo Deportivo A Granxa. Prezo por hora de non empadroados e sen luz; con luz, 6,30 €. Empadroados ou con tarxeta Eurocidadán: 3,00 € sen luz e 5,00 € con luz.",
     "es": "Dos pistas de pádel de hierba artificial con paredes de cristal, al aire libre, en el Complexo Deportivo A Granxa. Precio por hora de no empadronados y sin luz; con luz, 6,30 €. Empadronados o con tarjeta Eurocidadán: 3,00 € sin luz y 5,00 € con luz."}',
   true, false, false, 'Rúa Antonio Fernández Pérez', null, '32600', 'Verín', '32085',
   41.933430, -7.442570, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000607', '019a0b6e-0000-7000-8000-000000000003',
   'Pista de tenis de A Granxa', 'ACTIVE', 'OUTDOOR_COURT',
   '{"gl": "Tenis ao aire libre no Complexo Deportivo A Granxa. Prezo por hora de non empadroados e sen luz; con luz, 6,30 €. Empadroados ou con tarxeta Eurocidadán: 3,00 € sen luz e 5,00 € con luz.",
     "es": "Tenis al aire libre en el Complexo Deportivo A Granxa. Precio por hora de no empadronados y sin luz; con luz, 6,30 €. Empadronados o con tarjeta Eurocidadán: 3,00 € sin luz y 5,00 € con luz."}',
   true, false, false, 'Rúa Antonio Fernández Pérez', null, '32600', 'Verín', '32085',
   41.933430, -7.442570, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000608', '019a0b6e-0000-7000-8000-000000000003',
   'Pista polideportiva exterior de A Granxa', 'ACTIVE', 'OUTDOOR_COURT',
   '{"gl": "Pista polideportiva ao aire libre no Complexo Deportivo A Granxa. A ordenanza non lle pon prezo.",
     "es": "Pista polideportiva al aire libre en el Complexo Deportivo A Granxa. La ordenanza no le pone precio."}',
   false, false, false, 'Rúa Antonio Fernández Pérez', null, '32600', 'Verín', '32085',
   41.933430, -7.442570, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000609', '019a0b6e-0000-7000-8000-000000000003',
   'Piscinas descubertas municipais de A Granxa', 'ACTIVE', 'POOL',
   '{"gl": "Tres vasos de distintos tamaños, zona verde e bar. Abren no verán, todos os días de 12:30 a 20:30, festivos incluídos. Entrada de non empadroados; empadroados ou con tarxeta Eurocidadán, 1,40 € adultos e 0,75 € infantil. Gratis para menores de 2 anos e persoas dependentes. Hai bonos mensuais e de tempada. Entrada directa, sen reserva.",
     "es": "Tres vasos de distintos tamaños, zona verde y bar. Abren en verano, todos los días de 12:30 a 20:30, festivos incluidos. Entrada de no empadronados; empadronados o con tarjeta Eurocidadán, 1,40 € adultos y 0,75 € infantil. Gratis para menores de 2 años y personas dependientes. Hay bonos mensuales y de temporada. Entrada directa, sin reserva."}',
   false, false, false, 'Avenida de Portugal', null, '32600', 'Verín', '32085', 41.932770,
   -7.440470, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-00000000060a', '019a0b6e-0000-7000-8000-000000000003',
   'Campo de Fútbol José Arjiz', 'ACTIVE', 'FIELD',
   '{"gl": "Campo de herba natural con bancada cuberta e un campo anexo de terra, ao norte da vila. É o campo do club de fútbol local; a ordenanza non lle pon prezo e non se reserva por internet.",
     "es": "Campo de hierba natural con grada cubierta y un campo anexo de tierra, al norte de la villa. Es el campo del club de fútbol local; la ordenanza no le pone precio y no se reserva por internet."}',
   false, false, false, 'Avenida de Laza', null, '32600', 'Verín', '32085', 41.953140,
   -7.431650, now(), now(), 0),
  -- Vilardevós
  ('019a0b6e-0000-7000-8000-000000000611', '019a0b6e-0000-7000-8000-000000000004',
   'Piscinas municipais', 'ACTIVE', 'POOL',
   '{"gl": "Piscina de verán con vaso de adultos e vaso infantil, vestiarios e bar. Do 1 de xullo ao 31 de agosto, todos os días de 15:30 a 20:30. Entrada directa, sen reserva; o prezo non está publicado.",
     "es": "Piscina de verano con vaso de adultos y vaso infantil, vestuarios y bar. Del 1 de julio al 31 de agosto, todos los días de 15:30 a 20:30. Entrada directa, sin reserva; el precio no está publicado."}',
   false, false, false, 'Estrada OU-310', null, '32616', 'Vilardevós', '32091', 41.906220,
   -7.307210, now(), now(), 0),
  -- Riós
  ('019a0b6e-0000-7000-8000-000000000612', '019a0b6e-0000-7000-8000-000000000005',
   'Piscina municipal', 'ACTIVE', 'POOL',
   '{"gl": "Piscina de verán na Telleira, con placas fotovoltaicas no tellado do edificio. Prezos e horario publicados en 2022 (de 12:00 a 20:00). Entrada directa, sen reserva.",
     "es": "Piscina de verano en A Telleira, con placas fotovoltaicas en el tejado del edificio. Precios y horario publicados en 2022 (de 12:00 a 20:00). Entrada directa, sin reserva."}',
   false, false, null, 'Estrada de Romariz', '78', '32611', 'A Telleira', '32071', 41.976060,
   -7.286290, now(), now(), 0),
  -- Castrelo do Val
  ('019a0b6e-0000-7000-8000-000000000613', '019a0b6e-0000-7000-8000-000000000006',
   'Pista de pádel municipal', 'ACTIVE', 'PADEL_COURT',
   '{"gl": "Pista de pádel cuberta no parque do Terrón, para catro persoas como máximo. Prezo da ordenanza por hora sen luz; con luz, 4,00 €. Bono de 10 horas: 30,00 € (40,00 € con luz). Aluguer de raquetas e pelotas: 0,50 € por hora. A chave dáse tras pagar.",
     "es": "Pista de pádel cubierta en el parque do Terrón, para cuatro personas como máximo. Precio de la ordenanza por hora sin luz; con luz, 4,00 €. Bono de 10 horas: 30,00 € (40,00 € con luz). Alquiler de raquetas y pelotas: 0,50 € por hora. La llave se da tras pagar."}',
   true, false, true, 'Rúa Castrelo do Val', '10A', '32625', 'Castrelo do Val', '32021',
   41.990710, -7.427040, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000614', '019a0b6e-0000-7000-8000-000000000006',
   'Pavillón municipal (Centro Multiusos)', 'ACTIVE', 'SPORTS_HALL',
   '{"gl": "Cancha cuberta no Terrón. O uso pídese no rexistro do concello con 15 días hábiles de antelación (ordenanza de uso de edificios, locais e instalacións, 2026).",
     "es": "Cancha cubierta en O Terrón. El uso se pide en el registro del concello con 15 días hábiles de antelación (ordenanza de uso de edificios, locales e instalaciones, 2026)."}',
   false, false, true, 'O Terrón', null, '32625', 'Castrelo do Val', '32021', 41.990350,
   -7.426390, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000615', '019a0b6e-0000-7000-8000-000000000006',
   'Pista multideporte', 'ACTIVE', 'OUTDOOR_COURT',
   '{"gl": "Pista de baloncesto e fútbol no Terrón, xunto ao pavillón e á pista de pádel. Uso libre.",
     "es": "Pista de baloncesto y fútbol en O Terrón, junto al pabellón y la pista de pádel. Uso libre."}',
   false, false, null, 'O Terrón', null, '32625', 'Castrelo do Val', '32021', 41.990700,
   -7.428550, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000616', '019a0b6e-0000-7000-8000-000000000006',
   'Piscina municipal', 'ACTIVE', 'POOL',
   '{"gl": "Piscina de verán, aberta desde o 1 de xullo. Prezos da ordenanza (BOP do 11/02/2025). Págase na entrada, sen reserva.",
     "es": "Piscina de verano, abierta desde el 1 de julio. Precios de la ordenanza (BOP del 11/02/2025). Se paga en la entrada, sin reserva."}',
   false, false, false, 'Estrada de Gondulfes', null, '32625', 'Castrelo do Val', '32021',
   41.990470, -7.412910, now(), now(), 0),
  -- Cualedro
  ('019a0b6e-0000-7000-8000-000000000617', '019a0b6e-0000-7000-8000-000000000007',
   'Piscinas municipais', 'ACTIVE', 'POOL',
   '{"gl": "Vaso de adultos e vaso infantil, tres vestiarios, bar con grella e zona verde. Inauguradas en 2021. Do 1 de xullo ao 31 de agosto, de 15:00 a 21:00. Gratis para menores de 4 anos. Entrada directa, sen reserva.",
     "es": "Vaso de adultos y vaso infantil, tres vestuarios, bar con barbacoa y zona verde. Inauguradas en 2021. Del 1 de julio al 31 de agosto, de 15:00 a 21:00. Gratis para menores de 4 años. Entrada directa, sin reserva."}',
   false, false, false, 'Rúa do Toural da Feira', '3', '32689', 'Cualedro', '32028', 41.984260,
   -7.592860, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000618', '019a0b6e-0000-7000-8000-000000000007',
   'Pista multixogos', 'ACTIVE', 'OUTDOOR_COURT',
   '{"gl": "Pista multideporte no recinto das piscinas municipais. Uso libre.",
     "es": "Pista multideporte en el recinto de las piscinas municipales. Uso libre."}',
   false, false, false, 'Rúa do Toural da Feira', '3', '32689', 'Cualedro', '32028', 41.984260,
   -7.592860, now(), now(), 0),
  -- Laza
  ('019a0b6e-0000-7000-8000-000000000619', '019a0b6e-0000-7000-8000-000000000008',
   'Pista de pádel de O Toural', 'ACTIVE', 'PADEL_COURT',
   '{"gl": "Pista de pádel cuberta en O Toural, preto do concello. Prezo por hora sen luz; con luz, 5,00 €.",
     "es": "Pista de pádel cubierta en O Toural, cerca del concello. Precio por hora sin luz; con luz, 5,00 €."}',
   true, false, true, 'O Toural', null, '32620', 'Laza', '32039', 42.063200, -7.462850, now(),
   now(), 0),
  ('019a0b6e-0000-7000-8000-00000000061a', '019a0b6e-0000-7000-8000-000000000008',
   'Pista polideportiva de O Toural', 'ACTIVE', 'OUTDOOR_COURT',
   '{"gl": "Pista polideportiva ao aire libre en O Toural. Uso libre.",
     "es": "Pista polideportiva al aire libre en O Toural. Uso libre."}',
   false, false, false, 'O Toural', null, '32620', 'Laza', '32039', 42.061340, -7.459860, now(),
   now(), 0),
  ('019a0b6e-0000-7000-8000-00000000061b', '019a0b6e-0000-7000-8000-000000000008',
   'Pavillón polideportivo municipal', 'ACTIVE', 'SPORTS_HALL',
   '{"gl": "Pavillón cuberto; a cuberta reparouse en 2016. Horario e prezo non publicados.",
     "es": "Pabellón cubierto; la cubierta se reparó en 2016. Horario y precio no publicados."}',
   false, false, true, 'Rúa Canella Vales', null, '32620', 'Laza', '32039', 42.062730,
   -7.462580, now(), now(), 0),
  -- Monterrei
  ('019a0b6e-0000-7000-8000-00000000061c', '019a0b6e-0000-7000-8000-000000000009',
   'Piscinas municipais', 'ACTIVE', 'POOL',
   '{"gl": "Dous vasos de verán xunto á casa do concello, en Albarellos. Do 1 de xullo ao 31 de agosto. Entrada directa, sen reserva; o prezo actual non está publicado.",
     "es": "Dos vasos de verano junto a la casa del concello, en Albarellos. Del 1 de julio al 31 de agosto. Entrada directa, sin reserva; el precio actual no está publicado."}',
   false, false, false, 'Rúa Fontiñas', null, '32618', 'Albarellos', '32050', 41.950580,
   -7.495120, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-00000000061d', '019a0b6e-0000-7000-8000-000000000009',
   'Pistas de pádel de Vilaza', 'ACTIVE', 'PADEL_COURT',
   '{"gl": "Dúas pistas de pádel en Vilaza, abertas en decembro de 2021. O prezo actual non está publicado.",
     "es": "Dos pistas de pádel en Vilaza, abiertas en diciembre de 2021. El precio actual no está publicado."}',
   true, false, null, 'Vilaza', null, '32618', 'Vilaza', '32050', 41.932070, -7.488330, now(),
   now(), 0),
  -- Oímbra
  ('019a0b6e-0000-7000-8000-00000000061e', '019a0b6e-0000-7000-8000-00000000000a',
   'Pavillón polideportivo municipal', 'ACTIVE', 'SPORTS_HALL',
   '{"gl": "Pista de 46 × 26 m para fútbol sala, baloncesto, balonmán e tenis, con vestiarios e bancadas. Aberto en 2011. Prezo por hora sen luz para quen non está empadroado; con luz, 10,00 €. Gratis para empadroados e entidades con sede en Oímbra.",
     "es": "Pista de 46 × 26 m para fútbol sala, baloncesto, balonmano y tenis, con vestuarios y gradas. Abierto en 2011. Precio por hora sin luz para quien no está empadronado; con luz, 10,00 €. Gratis para empadronados y entidades con sede en Oímbra."}',
   true, false, true, 'Avenida do Carregal', '3', '32613', 'Oímbra', '32053', 41.885470,
   -7.476630, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-00000000061f', '019a0b6e-0000-7000-8000-00000000000a',
   'Pista de pádel', 'ACTIVE', 'PADEL_COURT',
   '{"gl": "Pista de pádel ao aire libre no antigo campo de fútbol, preto do colexio. Gratis, para catro persoas, en quendas de hora e media.",
     "es": "Pista de pádel al aire libre en el antiguo campo de fútbol, cerca del colegio. Gratis, para cuatro personas, en turnos de hora y media."}',
   true, false, false, 'Camiño da Portaxe', null, '32613', 'Oímbra', '32053', 41.885360,
   -7.466200, now(), now(), 0),
  ('019a0b6e-0000-7000-8000-000000000620', '019a0b6e-0000-7000-8000-00000000000a',
   'Piscinas municipais', 'ACTIVE', 'POOL',
   '{"gl": "Piscina de verán con vaso grande para 90 persoas e vaso de chapoteo para 10. Prezos da ordenanza de 2023; os empadroados pagan un 30 % menos. Hai bonos mensuais e quincenais. Págase na portería, sen reserva.",
     "es": "Piscina de verano con vaso grande para 90 personas y vaso de chapoteo para 10. Precios de la ordenanza de 2023; los empadronados pagan un 30 % menos. Hay bonos mensuales y quincenales. Se paga en la portería, sin reserva."}',
   false, false, false, 'Estrada de Oímbra a Videferre', null, '32613', 'Oímbra', '32053',
   41.885460, -7.475950, now(), now(), 0)
on conflict do nothing;

-- Deportes (catálogo de Furar).
insert into facility_sport (id, municipality_id, facility_id, sport_code, position)
select uuidv7(), f.municipality_id, f.id, s.code, s.position - 1
from (values ('019a0b6e-0000-7000-8000-000000000601', array['futsal', 'volleyball', 'badminton']),
             ('019a0b6e-0000-7000-8000-000000000602', array['fronton']),
             ('019a0b6e-0000-7000-8000-000000000603', array['swimming']),
             ('019a0b6e-0000-7000-8000-000000000604', array['football', 'football_7']),
             ('019a0b6e-0000-7000-8000-000000000605', array['futsal', 'basketball']),
             ('019a0b6e-0000-7000-8000-000000000606', array['padel']),
             ('019a0b6e-0000-7000-8000-000000000607', array['tennis']),
             ('019a0b6e-0000-7000-8000-000000000608', array['basketball', 'futsal']),
             ('019a0b6e-0000-7000-8000-000000000609', array['swimming']),
             ('019a0b6e-0000-7000-8000-00000000060a', array['football']),
             ('019a0b6e-0000-7000-8000-000000000611', array['swimming']),
             ('019a0b6e-0000-7000-8000-000000000612', array['swimming']),
             ('019a0b6e-0000-7000-8000-000000000613', array['padel']),
             ('019a0b6e-0000-7000-8000-000000000614', array['futsal', 'basketball']),
             ('019a0b6e-0000-7000-8000-000000000615', array['basketball', 'futsal']),
             ('019a0b6e-0000-7000-8000-000000000616', array['swimming']),
             ('019a0b6e-0000-7000-8000-000000000617', array['swimming']),
             ('019a0b6e-0000-7000-8000-000000000618', array['basketball', 'futsal']),
             ('019a0b6e-0000-7000-8000-000000000619', array['padel']),
             ('019a0b6e-0000-7000-8000-00000000061a', array['basketball', 'futsal']),
             ('019a0b6e-0000-7000-8000-00000000061b', array['futsal', 'basketball']),
             ('019a0b6e-0000-7000-8000-00000000061c', array['swimming']),
             ('019a0b6e-0000-7000-8000-00000000061d', array['padel']),
             ('019a0b6e-0000-7000-8000-00000000061e',
              array['futsal', 'basketball', 'handball', 'tennis']),
             ('019a0b6e-0000-7000-8000-00000000061f', array['padel']),
             ('019a0b6e-0000-7000-8000-000000000620', array['swimming']))
         as v(facility, codes)
     join facility f on f.id = v.facility::uuid,
     unnest(v.codes) with ordinality as s(code, position)
where not exists (select 1 from facility_sport x where x.facility_id = f.id and x.sport_code = s.code)
on conflict do nothing;

-- Servizos que constan nas fontes (vestiarios, bancada cuberta, bar e a luz das pistas con tarifa
-- con luz).
insert into facility_amenity (municipality_id, facility_id, code)
select f.municipality_id, f.id, code
from (values ('019a0b6e-0000-7000-8000-000000000601', array['LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000602', array['LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000604',
              array['CHANGING_ROOMS', 'COVERED_STANDS', 'LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000605', array['CHANGING_ROOMS', 'LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000606', array['LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000607', array['LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000609', array['CAFE']),
             ('019a0b6e-0000-7000-8000-00000000060a', array['COVERED_STANDS']),
             ('019a0b6e-0000-7000-8000-000000000611', array['CHANGING_ROOMS', 'CAFE']),
             ('019a0b6e-0000-7000-8000-000000000613', array['LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000617', array['CHANGING_ROOMS', 'CAFE']),
             ('019a0b6e-0000-7000-8000-000000000619', array['LIGHTING']),
             ('019a0b6e-0000-7000-8000-00000000061e',
              array['CHANGING_ROOMS', 'COVERED_STANDS', 'LIGHTING']))
         as v(facility, codes)
     join facility f on f.id = v.facility::uuid,
     unnest(v.codes) as code
on conflict do nothing;

-- Usos co seu prezo (en céntimos; null = sen prezo publicado, 0 = gratis). Por prazas, as
-- piscinas: o aforo publicado (A Granxa, 400; Oímbra, 90 + 10) ou 100 (suposto).
insert into facility_use (id, municipality_id, facility_id, name, rental_mode, capacity,
                          slot_minutes, availability, blocks_others, price_kind, price_cents,
                          created_at, updated_at, version)
select v.id::uuid, f.municipality_id, f.id, v.name, v.mode, v.capacity, v.minutes, 'ALWAYS', false,
       case when v.cents is null then 'NONE' when v.cents = 0 then 'FREE' else 'PAID' end,
       nullif(v.cents, 0), now(), now(), 0
from (values
  ('019a0b6e-0000-7000-8000-000000000701', '019a0b6e-0000-7000-8000-000000000601', 'Pista', 'WHOLE', null::int, 60, 1250),
  ('019a0b6e-0000-7000-8000-000000000702', '019a0b6e-0000-7000-8000-000000000602', 'Frontón', 'WHOLE', null, 60, 380),
  ('019a0b6e-0000-7000-8000-000000000703', '019a0b6e-0000-7000-8000-000000000603', 'Baño libre adultos', 'SEATS', 100, 60, 500),
  ('019a0b6e-0000-7000-8000-000000000704', '019a0b6e-0000-7000-8000-000000000603', 'Baño libre infantil', 'SEATS', 100, 60, 400),
  ('019a0b6e-0000-7000-8000-000000000705', '019a0b6e-0000-7000-8000-000000000604', 'Fútbol 11', 'WHOLE', null, 60, 5000),
  ('019a0b6e-0000-7000-8000-000000000706', '019a0b6e-0000-7000-8000-000000000604', 'Fútbol 7', 'PARTS', null, 60, 3750),
  ('019a0b6e-0000-7000-8000-000000000707', '019a0b6e-0000-7000-8000-000000000604', 'Partido de fútbol (2 horas)', 'WHOLE', null, 120, 6250),
  ('019a0b6e-0000-7000-8000-000000000708', '019a0b6e-0000-7000-8000-000000000605', 'Pista', 'WHOLE', null, 60, 1250),
  ('019a0b6e-0000-7000-8000-000000000709', '019a0b6e-0000-7000-8000-000000000606', 'Partido', 'PARTS', null, 60, 380),
  ('019a0b6e-0000-7000-8000-00000000070a', '019a0b6e-0000-7000-8000-000000000607', 'Partido', 'WHOLE', null, 60, 380),
  ('019a0b6e-0000-7000-8000-00000000070b', '019a0b6e-0000-7000-8000-000000000608', 'Pista', 'WHOLE', null, 60, null),
  ('019a0b6e-0000-7000-8000-00000000070c', '019a0b6e-0000-7000-8000-000000000609', 'Entrada adultos', 'SEATS', 400, 60, 175),
  ('019a0b6e-0000-7000-8000-00000000070d', '019a0b6e-0000-7000-8000-000000000609', 'Entrada infantil (de 2 a 14 anos)', 'SEATS', 400, 60, 90),
  ('019a0b6e-0000-7000-8000-00000000070e', '019a0b6e-0000-7000-8000-00000000060a', 'Campo enteiro', 'WHOLE', null, 120, null),
  ('019a0b6e-0000-7000-8000-000000000711', '019a0b6e-0000-7000-8000-000000000611', 'Baño libre', 'SEATS', 100, 60, null),
  ('019a0b6e-0000-7000-8000-000000000712', '019a0b6e-0000-7000-8000-000000000612', 'Entrada adultos', 'SEATS', 100, 60, 100),
  ('019a0b6e-0000-7000-8000-000000000713', '019a0b6e-0000-7000-8000-000000000612', 'Entrada infantil e pensionistas', 'SEATS', 100, 60, 50),
  ('019a0b6e-0000-7000-8000-000000000714', '019a0b6e-0000-7000-8000-000000000613', 'Partido', 'WHOLE', null, 60, 300),
  ('019a0b6e-0000-7000-8000-000000000715', '019a0b6e-0000-7000-8000-000000000614', 'Pista', 'WHOLE', null, 60, null),
  ('019a0b6e-0000-7000-8000-000000000716', '019a0b6e-0000-7000-8000-000000000615', 'Pista', 'WHOLE', null, 60, null),
  ('019a0b6e-0000-7000-8000-000000000717', '019a0b6e-0000-7000-8000-000000000616', 'Entrada desde 12 anos', 'SEATS', 100, 60, 150),
  ('019a0b6e-0000-7000-8000-000000000718', '019a0b6e-0000-7000-8000-000000000616', 'Entrada de 3 a 12 anos', 'SEATS', 100, 60, 100),
  ('019a0b6e-0000-7000-8000-000000000719', '019a0b6e-0000-7000-8000-000000000617', 'Entrada desde 14 anos', 'SEATS', 100, 60, 150),
  ('019a0b6e-0000-7000-8000-00000000071a', '019a0b6e-0000-7000-8000-000000000617', 'Entrada menores de 14 anos', 'SEATS', 100, 60, 100),
  ('019a0b6e-0000-7000-8000-00000000071b', '019a0b6e-0000-7000-8000-000000000618', 'Pista', 'WHOLE', null, 60, null),
  ('019a0b6e-0000-7000-8000-00000000071c', '019a0b6e-0000-7000-8000-000000000619', 'Partido', 'WHOLE', null, 60, 250),
  ('019a0b6e-0000-7000-8000-00000000071d', '019a0b6e-0000-7000-8000-00000000061a', 'Pista', 'WHOLE', null, 60, null),
  ('019a0b6e-0000-7000-8000-00000000071e', '019a0b6e-0000-7000-8000-00000000061b', 'Pista', 'WHOLE', null, 60, null),
  ('019a0b6e-0000-7000-8000-00000000071f', '019a0b6e-0000-7000-8000-00000000061c', 'Baño libre', 'SEATS', 100, 60, null),
  ('019a0b6e-0000-7000-8000-000000000720', '019a0b6e-0000-7000-8000-00000000061d', 'Partido', 'PARTS', null, 60, null),
  ('019a0b6e-0000-7000-8000-000000000721', '019a0b6e-0000-7000-8000-00000000061e', 'Pista', 'WHOLE', null, 60, 800),
  ('019a0b6e-0000-7000-8000-000000000722', '019a0b6e-0000-7000-8000-00000000061f', 'Quenda', 'WHOLE', null, 90, 0),
  ('019a0b6e-0000-7000-8000-000000000723', '019a0b6e-0000-7000-8000-000000000620', 'Entrada desde 14 anos', 'SEATS', 100, 60, 150),
  ('019a0b6e-0000-7000-8000-000000000724', '019a0b6e-0000-7000-8000-000000000620', 'Entrada de 3 a 14 anos', 'SEATS', 100, 60, 100))
  as v(id, facility, name, mode, capacity, minutes, cents)
  join facility f on f.id = v.facility::uuid
on conflict do nothing;

-- Partes: os dous campos de fútbol 7 de A Granxa e as dúas pistas de pádel de A Granxa e Vilaza.
insert into facility_use_part (id, municipality_id, use_id, name, position)
select v.id::uuid, u.municipality_id, u.id, v.name, v.position
from (values ('019a0b6e-0000-7000-8000-000000000781', '019a0b6e-0000-7000-8000-000000000706', 'Campo 1', 0),
             ('019a0b6e-0000-7000-8000-000000000782', '019a0b6e-0000-7000-8000-000000000706', 'Campo 2', 1),
             ('019a0b6e-0000-7000-8000-000000000783', '019a0b6e-0000-7000-8000-000000000709', 'Pista 1', 0),
             ('019a0b6e-0000-7000-8000-000000000784', '019a0b6e-0000-7000-8000-000000000709', 'Pista 2', 1),
             ('019a0b6e-0000-7000-8000-000000000785', '019a0b6e-0000-7000-8000-000000000720', 'Pista 1', 0),
             ('019a0b6e-0000-7000-8000-000000000786', '019a0b6e-0000-7000-8000-000000000720', 'Pista 2', 1))
         as v(id, use_id, name, position)
     join facility_use u on u.id = v.use_id::uuid
-- Co id: a exclusión dos nomes é diferible e non pode facer de árbitro.
on conflict (id) do nothing;

-- Normas da ordenanza do pádel de Castrelo do Val: de 1 a 2 horas e como moito con dous días de
-- antelación. Só se o uso segue sen normas (non pisa o que cambiase o xestor).
update facility_use set rule_max_advance_days = 2, rule_max_slots = 2
where id = '019a0b6e-0000-7000-8000-000000000714' and rule_max_advance_days is null;

update facility
set house_rules = '{"gl": "Catro persoas como máximo na pista; os acompañantes non entran. As persoas menores de 14 anos, só coa supervisión dunha persoa adulta. Calzado deportivo e raquetas e pelotas de pádel; só se xoga ao pádel. Non se pode comer, fumar nin entrar con vidro ou animais. Non se admiten reservas para terceiros: quen xoga ten que ser quen reservou. Perder ou estragar a chave custa 30 €.",
                    "es": "Cuatro personas como máximo en la pista; los acompañantes no entran. Los menores de 14 años, solo con la supervisión de una persona adulta. Calzado deportivo y raquetas y pelotas de pádel; solo se juega al pádel. No se puede comer, fumar ni entrar con vidrio o animales. No se admiten reservas para terceros: quien juega tiene que ser quien reservó. Perder o estropear la llave cuesta 30 €."}'
where id = '019a0b6e-0000-7000-8000-000000000613' and house_rules is null;

-- Tempadas: o curso en curso e o seguinte nas instalacións de todo o ano; o verán seguinte nas
-- piscinas de verán. Semana: 1 = luns … 7 = domingo; 8 = festivos (copia do domingo).
do $$
declare
  y constant int := extract(year from current_date - interval '8 months')::int;
  summer constant text := format('Verán %s', y + 1);
  -- Tramos (día, inicio, fin) en minutos desde a medianoite.
  -- Suposto: de 9:00 a 22:00 todos os días.
  default_week constant int[] := array[
    [1, 540, 1320], [2, 540, 1320], [3, 540, 1320], [4, 540, 1320], [5, 540, 1320],
    [6, 540, 1320], [7, 540, 1320], [8, 540, 1320]];
  -- Suposto: de 12:00 a 20:00 todos os días (piscinas de verán sen horario publicado).
  pool_week constant int[] := array[
    [1, 720, 1200], [2, 720, 1200], [3, 720, 1200], [4, 720, 1200], [5, 720, 1200],
    [6, 720, 1200], [7, 720, 1200], [8, 720, 1200]];
  -- Reais.
  granxa_pool_week constant int[] := array[
    [1, 750, 1230], [2, 750, 1230], [3, 750, 1230], [4, 750, 1230], [5, 750, 1230],
    [6, 750, 1230], [7, 750, 1230], [8, 750, 1230]];
  vilardevos_week constant int[] := array[
    [1, 930, 1230], [2, 930, 1230], [3, 930, 1230], [4, 930, 1230], [5, 930, 1230],
    [6, 930, 1230], [7, 930, 1230], [8, 930, 1230]];
  cualedro_week constant int[] := array[
    [1, 900, 1260], [2, 900, 1260], [3, 900, 1260], [4, 900, 1260], [5, 900, 1260],
    [6, 900, 1260], [7, 900, 1260], [8, 900, 1260]];
  castrelo_padel_week constant int[] := array[
    [1, 480, 1440], [2, 480, 1440], [3, 480, 1440], [4, 480, 1440], [5, 480, 1440],
    [6, 480, 1440], [7, 480, 1440], [8, 480, 1440]];
  laza_padel_week constant int[] := array[
    [1, 480, 1380], [2, 480, 1380], [3, 480, 1380], [4, 480, 1380], [5, 480, 1380],
    [6, 480, 1380], [7, 480, 1380], [8, 480, 1380]];
  oimbra_hall_week constant int[] := array[
    [1, 495, 915], [2, 495, 915], [3, 495, 915], [4, 495, 915], [5, 495, 915]];
  oimbra_padel_week constant int[] := array[
    [1, 540, 1170], [2, 540, 1170], [3, 540, 1170], [4, 540, 1170], [5, 540, 1170],
    [6, 540, 1170], [7, 540, 1170], [8, 540, 1170]];
  year_from constant date := make_date(y, 9, 1);
  year_to constant date := make_date(y + 2, 8, 31);
  s record;
  r int;
begin
  -- (id da tempada, instalación, nome, desde, ata, semana)
  for s in
    select t.id, f.municipality_id as m, f.id as facility, t.name, t.start_date, t.end_date, t.week
    from (values
      ('019a0b6e-0000-7000-8000-0000000007a1'::uuid, '019a0b6e-0000-7000-8000-000000000601'::uuid,
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a2', '019a0b6e-0000-7000-8000-000000000602',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a3', '019a0b6e-0000-7000-8000-000000000603',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a4', '019a0b6e-0000-7000-8000-000000000604',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a5', '019a0b6e-0000-7000-8000-000000000605',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a6', '019a0b6e-0000-7000-8000-000000000606',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a7', '019a0b6e-0000-7000-8000-000000000607',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a8', '019a0b6e-0000-7000-8000-000000000608',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007a9', '019a0b6e-0000-7000-8000-000000000609',
       summer, make_date(y + 1, 6, 20), make_date(y + 1, 8, 31), granxa_pool_week),
      ('019a0b6e-0000-7000-8000-0000000007aa', '019a0b6e-0000-7000-8000-00000000060a',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007ab', '019a0b6e-0000-7000-8000-000000000611',
       summer, make_date(y + 1, 7, 1), make_date(y + 1, 8, 31), vilardevos_week),
      ('019a0b6e-0000-7000-8000-0000000007ac', '019a0b6e-0000-7000-8000-000000000612',
       summer, make_date(y + 1, 7, 1), make_date(y + 1, 8, 31), pool_week),
      ('019a0b6e-0000-7000-8000-0000000007ad', '019a0b6e-0000-7000-8000-000000000613',
       'Todo o ano', year_from, year_to, castrelo_padel_week),
      ('019a0b6e-0000-7000-8000-0000000007ae', '019a0b6e-0000-7000-8000-000000000614',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007af', '019a0b6e-0000-7000-8000-000000000615',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007b0', '019a0b6e-0000-7000-8000-000000000616',
       summer, make_date(y + 1, 7, 1), make_date(y + 1, 8, 31), pool_week),
      ('019a0b6e-0000-7000-8000-0000000007b1', '019a0b6e-0000-7000-8000-000000000617',
       summer, make_date(y + 1, 7, 1), make_date(y + 1, 8, 31), cualedro_week),
      ('019a0b6e-0000-7000-8000-0000000007b2', '019a0b6e-0000-7000-8000-000000000618',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007b3', '019a0b6e-0000-7000-8000-000000000619',
       'Todo o ano', year_from, year_to, laza_padel_week),
      ('019a0b6e-0000-7000-8000-0000000007b4', '019a0b6e-0000-7000-8000-00000000061a',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007b5', '019a0b6e-0000-7000-8000-00000000061b',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007b6', '019a0b6e-0000-7000-8000-00000000061c',
       summer, make_date(y + 1, 7, 1), make_date(y + 1, 8, 31), pool_week),
      ('019a0b6e-0000-7000-8000-0000000007b7', '019a0b6e-0000-7000-8000-00000000061d',
       'Todo o ano', year_from, year_to, default_week),
      ('019a0b6e-0000-7000-8000-0000000007b8', '019a0b6e-0000-7000-8000-00000000061e',
       'Todo o ano', year_from, year_to, oimbra_hall_week),
      ('019a0b6e-0000-7000-8000-0000000007b9', '019a0b6e-0000-7000-8000-00000000061f',
       'Todo o ano', year_from, year_to, oimbra_padel_week),
      ('019a0b6e-0000-7000-8000-0000000007ba', '019a0b6e-0000-7000-8000-000000000620',
       summer, make_date(y + 1, 7, 1), make_date(y + 1, 8, 31), pool_week))
      as t(id, facility, name, start_date, end_date, week)
      join facility f on f.id = t.facility
  loop
    insert into season (id, municipality_id, facility_id, name, start_date, end_date,
                        created_at, updated_at, version)
    values (s.id, s.m, s.facility, s.name, s.start_date, s.end_date, now(), now(), 0)
    on conflict do nothing;
    if not exists (select 1 from season_hours where season_id = s.id) then
      for r in 1 .. array_length(s.week, 1) loop
        insert into season_hours (id, municipality_id, season_id, day, start_minute, end_minute)
        values (uuidv7(), s.m, s.id, s.week[r][1], s.week[r][2], s.week[r][3]);
      end loop;
    end if;
  end loop;
end
$$;
