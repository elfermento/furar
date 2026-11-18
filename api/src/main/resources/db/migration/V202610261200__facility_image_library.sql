-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Biblioteca de imaxes (especificación, bloque 1, «Fotos»; decisión do 2026-10-26). Substitúe a
-- galería propia de cada instalación (V202610251058):
--   * drawing: catálogo de plataforma cos debuxos de Furar (CC0), igual para todos os concellos e
--     sen municipality_id (TenancyAllowlist). Os bytes van no classpath (drawings/{code}.jpg).
--   * library_photo: as fotos que sobe un concello. Son do concello (RLS) e reutilízanse en
--     calquera das súas instalacións. O texto alternativo vai coa foto. Unha subida queda pendente
--     (pending_until) ata que o «Gardar» dunha instalación a usa; as pendentes caducadas bórranse.
--   * library_photo_content: os bytes (antes facility_photo_content).
--   * facility_image: a lista ordenada de imaxes dunha instalación, cada unha foto da biblioteca ou
--     debuxo; a primeira é a portada (xa non hai columna cover). Lista baleira = a instalación
--     mostra o debuxo suxerido, calculado no momento (nunca queda sen imaxe).
-- As fotos que xa había pasan á biblioteca do seu concello e á lista da súa instalación, coa
-- portada primeiro e o resto na orde que tiñan.

-- 1. Debuxos de Furar (plataforma) ------------------------------------------------------------
create table drawing (
    code   text primary key,
    name   jsonb not null,
    alt    jsonb not null,
    width  int not null,
    height int not null,
    constraint drawing_code_ck check (code ~ '^[a-z][a-z0-9_]{1,39}$'),
    constraint drawing_name_ck check (name ? 'gl' and name ? 'es'),
    constraint drawing_alt_ck check (alt ? 'gl' and alt ? 'es'),
    constraint drawing_size_ck check (width between 1 and 1600 and height between 1 and 1600)
);

insert into drawing (code, name, alt, width, height) values
    ('activity_room',
     '{"gl": "Sala de actividades", "es": "Sala de actividades"}',
     '{"gl": "Debuxo dunha sala de actividades con chan de madeira, espellos, barra e colchonetas",
       "es": "Dibujo de una sala de actividades con suelo de madera, espejos, barra y colchonetas"}',
     1200, 800),
    ('athletics_track',
     '{"gl": "Pista de atletismo", "es": "Pista de atletismo"}',
     '{"gl": "Debuxo da pista de atletismo de seis rúas vista desde arriba",
       "es": "Dibujo de la pista de atletismo de seis calles vista desde arriba"}',
     1200, 800),
    ('badminton_court',
     '{"gl": "Pista de bádminton", "es": "Pista de bádminton"}',
     '{"gl": "Debuxo dunha pista de bádminton verde vista desde arriba, coa rede e un volante",
       "es": "Dibujo de una pista de bádminton verde vista desde arriba, con la red y un volante"}',
     1200, 800),
    ('basketball_court',
     '{"gl": "Pista de baloncesto exterior", "es": "Pista de baloncesto exterior"}',
     '{"gl": "Debuxo dunha pista de baloncesto ao aire libre vista desde arriba",
       "es": "Dibujo de una pista de baloncesto al aire libre vista desde arriba"}',
     1200, 800),
    ('beach_volleyball',
     '{"gl": "Voleibol praia", "es": "Vóley playa"}',
     '{"gl": "Debuxo dunha pista de voleibol na area, á beira do mar, vista desde arriba",
       "es": "Dibujo de una pista de vóley en la arena, junto al mar, vista desde arriba"}',
     1200, 800),
    ('climbing_wall',
     '{"gl": "Rocódromo", "es": "Rocódromo"}',
     '{"gl": "Debuxo dun rocódromo con presas de cores e colchón ao pé",
       "es": "Dibujo de un rocódromo con presas de colores y colchoneta al pie"}',
     1200, 800),
    ('combat_room',
     '{"gl": "Tatami e ring", "es": "Tatami y ring"}',
     '{"gl": "Debuxo dun tatami verde e vermello e un ring de boxeo vistos desde arriba",
       "es": "Dibujo de un tatami verde y rojo y un ring de boxeo vistos desde arriba"}',
     1200, 800),
    ('field_hockey',
     '{"gl": "Hóckey herba", "es": "Hockey hierba"}',
     '{"gl": "Debuxo dun campo de hóckey herba azul visto desde arriba",
       "es": "Dibujo de un campo de hockey hierba azul visto desde arriba"}',
     1200, 800),
    ('football_field',
     '{"gl": "Campo de fútbol", "es": "Campo de fútbol"}',
     '{"gl": "Debuxo do campo de fútbol visto desde arriba, coas liñas brancas sobre a herba",
       "es": "Dibujo del campo de fútbol visto desde arriba, con las líneas blancas sobre la hierba"}',
     1200, 800),
    ('fronton',
     '{"gl": "Frontón", "es": "Frontón"}',
     '{"gl": "Debuxo dun frontón verde coa súa parede e o chan da cancha",
       "es": "Dibujo de un frontón verde con su pared y el suelo de la cancha"}',
     1200, 800),
    ('futsal_court',
     '{"gl": "Pavillón (fútbol sala)", "es": "Pabellón (fútbol sala)"}',
     '{"gl": "Debuxo da pista do pavillón vista desde arriba, co chan de madeira",
       "es": "Dibujo de la pista del pabellón vista desde arriba, con el suelo de madera"}',
     1200, 800),
    ('generic',
     '{"gl": "Instalación deportiva", "es": "Instalación deportiva"}',
     '{"gl": "Debuxo dunha instalación deportiva vista desde arriba, con campo, piscina e pistas",
       "es": "Dibujo de una instalación deportiva vista desde arriba, con campo, piscina y pistas"}',
     1200, 800),
    ('gym',
     '{"gl": "Sala de musculación", "es": "Sala de musculación"}',
     '{"gl": "Debuxo dunha sala de musculación con bancos, barras e pesas",
       "es": "Dibujo de una sala de musculación con bancos, barras y pesas"}',
     1200, 800),
    ('jetty',
     '{"gl": "Embarcadoiro", "es": "Embarcadero"}',
     '{"gl": "Debuxo dun embarcadoiro de madeira na auga con piraguas, visto desde arriba",
       "es": "Dibujo de un embarcadero de madera en el agua con piraguas, visto desde arriba"}',
     1200, 800),
    ('multipurpose_room',
     '{"gl": "Sala multiusos", "es": "Sala multiusos"}',
     '{"gl": "Debuxo dunha sala multiusos con cadeiras, mesa, pantalla e xanelas",
       "es": "Dibujo de una sala multiusos con sillas, mesa, pantalla y ventanas"}',
     1200, 800),
    ('outdoor_court',
     '{"gl": "Pista polideportiva exterior", "es": "Pista polideportiva exterior"}',
     '{"gl": "Debuxo dunha pista polideportiva ao aire libre vista desde arriba",
       "es": "Dibujo de una pista polideportiva al aire libre vista desde arriba"}',
     1200, 800),
    ('padel_court',
     '{"gl": "Pista de pádel", "es": "Pista de pádel"}',
     '{"gl": "Debuxo da pista de pádel azul vista desde arriba, coa rede no medio",
       "es": "Dibujo de la pista de pádel azul vista desde arriba, con la red en el centro"}',
     1200, 800),
    ('pool',
     '{"gl": "Piscina", "es": "Piscina"}',
     '{"gl": "Debuxo dunha piscina de seis rúas vista desde arriba",
       "es": "Dibujo de una piscina de seis calles vista desde arriba"}',
     1200, 800),
    ('roller_rink',
     '{"gl": "Pista de hóckey e patinaxe", "es": "Pista de hockey y patinaje"}',
     '{"gl": "Debuxo dunha pista de hóckey sobre patíns vista desde arriba",
       "es": "Dibujo de una pista de hockey sobre patines vista desde arriba"}',
     1200, 800),
    ('rugby_field',
     '{"gl": "Campo de rugby", "es": "Campo de rugby"}',
     '{"gl": "Debuxo dun campo de rugby visto desde arriba, cos postes nos extremos",
       "es": "Dibujo de un campo de rugby visto desde arriba, con los postes en los extremos"}',
     1200, 800),
    ('sports_hall',
     '{"gl": "Pavillón polideportivo", "es": "Pabellón polideportivo"}',
     '{"gl": "Debuxo dun pavillón polideportivo visto desde arriba, coas liñas de varios deportes no chan de madeira e a bancada",
       "es": "Dibujo de un pabellón polideportivo visto desde arriba, con las líneas de varios deportes en el suelo de madera y la grada"}',
     1200, 800),
    ('squash_court',
     '{"gl": "Pista de squash", "es": "Pista de squash"}',
     '{"gl": "Debuxo dunha pista de squash vista desde dentro, co chan de madeira",
       "es": "Dibujo de una pista de squash vista desde dentro, con el suelo de madera"}',
     1200, 800),
    ('table_tennis',
     '{"gl": "Tenis de mesa", "es": "Tenis de mesa"}',
     '{"gl": "Debuxo de dúas mesas de tenis de mesa vistas desde arriba",
       "es": "Dibujo de dos mesas de tenis de mesa vistas desde arriba"}',
     1200, 800),
    ('tennis_court',
     '{"gl": "Pista de tenis", "es": "Pista de tenis"}',
     '{"gl": "Debuxo dunha pista de tenis de terra batida vista desde arriba",
       "es": "Dibujo de una pista de tenis de tierra batida vista desde arriba"}',
     1200, 800),
    ('velodrome',
     '{"gl": "Velódromo", "es": "Velódromo"}',
     '{"gl": "Debuxo dun velódromo visto desde arriba, coa pista peraltada ao redor da herba",
       "es": "Dibujo de un velódromo visto desde arriba, con la pista peraltada alrededor del césped"}',
     1200, 800),
    ('volleyball_court',
     '{"gl": "Pista de voleibol", "es": "Pista de voleibol"}',
     '{"gl": "Debuxo dunha pista de voleibol laranxa vista desde arriba, coa rede e un balón",
       "es": "Dibujo de una pista de voleibol naranja vista desde arriba, con la red y un balón"}',
     1200, 800);

-- Só lectura para a API (como sport_catalog): o rol vén do placeholder approle.
do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke insert, update, delete, truncate on drawing from %I', '${approle}');
  end if;
end
$$;

-- 2. Biblioteca do concello ----------------------------------------------------------------------
create table library_photo (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    alt             jsonb,
    width           int not null,
    height          int not null,
    content_type    text not null,
    created_at      timestamptz not null,
    pending_until   timestamptz,
    constraint library_photo_tenant_uk unique (municipality_id, id),
    constraint library_photo_alt_ck check (alt is null or (alt ? 'gl' and alt ? 'es')),
    -- Na biblioteca, sempre con texto alternativo; sen el, só mentres está pendente.
    constraint library_photo_kept_alt_ck check (pending_until is not null or alt is not null),
    constraint library_photo_size_ck check (width between 1 and 1600 and height between 1 and 1600),
    constraint library_photo_content_type_ck check (content_type in ('image/jpeg', 'image/png'))
);

create index library_photo_pending_idx on library_photo (municipality_id, pending_until)
    where pending_until is not null;

insert into library_photo (id, municipality_id, alt, width, height, content_type, created_at)
select id, municipality_id, alt, width, height, content_type, created_at
from facility_photo;

-- Os bytes cambian de dono: da foto da instalación á foto da biblioteca.
alter table facility_photo_content rename to library_photo_content;
alter table library_photo_content drop constraint facility_photo_content_photo_fk;
alter table library_photo_content rename constraint facility_photo_content_variant_ck
    to library_photo_content_variant_ck;
alter table library_photo_content rename constraint facility_photo_content_bytes_ck
    to library_photo_content_bytes_ck;
alter table library_photo_content rename constraint facility_photo_content_pkey
    to library_photo_content_pkey;
alter index facility_photo_content_tenant_idx rename to library_photo_content_tenant_idx;
alter table library_photo_content add constraint library_photo_content_photo_fk
    foreign key (municipality_id, photo_id) references library_photo (municipality_id, id)
    on delete cascade;
alter policy facility_photo_content_tenant on library_photo_content
    rename to library_photo_content_tenant;

-- 3. Imaxes de cada instalación ------------------------------------------------------------------
create table facility_image (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    position        int not null,
    photo_id        uuid,
    drawing_code    text,
    constraint facility_image_tenant_uk unique (municipality_id, id),
    constraint facility_image_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id) on delete cascade,
    constraint facility_image_photo_fk foreign key (municipality_id, photo_id)
        references library_photo (municipality_id, id) on delete cascade,
    constraint facility_image_drawing_fk foreign key (drawing_code) references drawing (code),
    constraint facility_image_one_ck check ((photo_id is null) <> (drawing_code is null)),
    constraint facility_image_position_ck check (position between 0 and 11)
);

create index facility_image_facility_idx on facility_image (municipality_id, facility_id, position);
create index facility_image_photo_idx on facility_image (municipality_id, photo_id)
    where photo_id is not null;
create unique index facility_image_photo_uk on facility_image (facility_id, photo_id)
    where photo_id is not null;
create unique index facility_image_drawing_uk on facility_image (facility_id, drawing_code)
    where drawing_code is not null;

insert into facility_image (id, municipality_id, facility_id, position, photo_id)
select id, municipality_id, facility_id,
       (row_number() over (partition by facility_id order by cover desc, position)) - 1, id
from facility_photo;

drop table facility_photo;

-- Row Level Security, como V202610231100 --------------------------------------------------------
alter table library_photo enable row level security;
create policy library_photo_tenant on library_photo
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table facility_image enable row level security;
create policy facility_image_tenant on facility_image
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
