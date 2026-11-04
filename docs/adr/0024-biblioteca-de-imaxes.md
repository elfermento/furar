<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0024 · Imaxes das instalacións: biblioteca do concello, debuxos de Furar e debuxo suxerido

- **Estado:** aceptada · 2026-10-26 (decisión de produto; especificación, bloque 1, «Fotos»)
- **Substitúe** a galería propia de cada instalación de D1-13 (`facility_photo` con columna `cover`).

## Contexto
Ata agora cada instalación tiña as súas fotos (ata 12) e unha marcada como portada. Na práctica un concello ten a mesma foto para varias instalacións (o pavillón e as súas tres pistas) e moitas instalacións non teñen foto ningunha: a portada pública quedaba cun oco. Decídese: (1) as fotos que sube un concello son **do concello** e reutilízanse en calquera das súas instalacións; (2) Furar trae un catálogo propio de **debuxos** (CC0) que se poden usar coma fotos; (3) unha instalación **nunca queda sen imaxe**: se non ten ningunha, vese un debuxo suxerido polo nome, os deportes ou o tipo, sen IA; (4) no modal de fotos **nada se aplica ata «Gardar»**, tampouco as subidas.

## Decisión
- **Catro táboas** (migración `V202610261200__facility_image_library.sql`):
  - `drawing`: catálogo de plataforma (como `sport_catalog`, ADR-0006): `code` como PK, nome e texto alternativo gl/es, medidas. Sen `municipality_id`, en `TenancyAllowlist` e só lectura para `furar_app`. Os bytes van no classpath (`api/src/main/resources/drawings/{code}.jpg`, anotados en `REUSE.toml` ficheiro a ficheiro); a API reencódaos co mesmo procesador ca unha subida a primeira vez que se piden e gárdaos en memoria. `FacilityImageIT` comproba que táboa, ficheiros e códigos da suxestión coinciden.
  - `library_photo` (biblioteca do concello): metadato da foto e **o seu texto alternativo**; `library_photo_content` (antes `facility_photo_content`): os bytes das dúas variantes, `storage external`.
  - `facility_image`: a lista ordenada de cada instalación; cada fila é **ou** foto (`photo_id`, FK composta ao concello) **ou** debuxo (`drawing_code`, FK ao catálogo), co `CHECK` de unha das dúas, como `facility_sport`. **A primeira é a portada**: desaparece a columna `cover` e o índice parcial que a protexía. Índices únicos parciais impiden a mesma imaxe dúas veces nunha instalación. Todas co concello, FK compostas e RLS desde a propia migración.
- **Texto alternativo coa foto, non coa instalación.** O alt describe o que se ve na imaxe, que é o mesmo en calquera instalación; un só texto por foto evita traducir dúas veces o mesmo e que unha copia quede sen describir. Cambialo desde unha instalación cámbiao en todas (o modal avísao). Os debuxos levan o seu, fixo (é da plataforma, xa en gl e es).
- **Debuxo suxerido calculado, non gardado.** Unha lista baleira significa «o suxerido»: `DrawingSuggestion` (función pura do dominio, con probas en gl e es) mira por orde (1) palabras do nome sen maiúsculas nin acentos («Pavillón de Piñeiros» → pavillón polideportivo), (2) os deportes do catálogo (un só debuxo para todos; varios de pista → pavillón se é cuberta, pista exterior se non), (3) o tipo e (4) a instalación xenérica. Así «ao crear, o suxerido» e «se se quitan todas, volve o suxerido» cúmprense sen escribir nada, as instalacións que xa había non precisan unha migración de datos en Java e a suxestión segue ao nome mentres o xestor non escolle. A API devolve as imaxes que se ven (`images`, nunca baleira) e o suxerido á parte (`suggestedImage`).
- **Subidas pendentes.** Subir é `POST …/photos` (multipart, os límites e erros de sempre: 413, 415, 422), fóra da transacción, e deixa a foto **pendente** (`pending_until`, 24 h): non aparece na biblioteca ata que o `PUT …/facilities/{id}/images` dunha instalación a usa (entón leva o seu alt). Se o modal se cancela, a web descarta as súas subidas (`DELETE …/photos/{id}`, só pendentes); as que queden bórranse ao subir ou gardar outra. Así o modal non aplica nada ata «Gardar» sen mandar varios ficheiros nunha soa petición (o límite de 9 MB de Caddy e Spring segue sendo por foto).
- **Gardar nun paso.** `PUT …/facilities/{id}/images` co `If-Match` da instalación leva a lista enteira (fotos con alt, debuxos polo código): comproba que cada foto é da biblioteca do concello e cada debuxo existe, actualiza os alt, quita o pendente e substitúe a lista (borrar e `flush` antes de inserir, ADR-0022 punto 11). Máximo 12 imaxes (422 `facility.photo-limit`); repetidas ou alleas, 422 `facility.images-invalid`. Queda no rexistro de actividade.
- **Bytes públicos.** `GET …/photos/{id}/{variant}` (caché `immutable`) serve calquera foto do concello salvo as que só usan instalacións de baixa (a regra de D1-13 pasa da foto á biblioteca); `GET …/drawings/{code}/{variant}` (caché dun día). Os ids non se adiviñan; a revisión «fotos só de instalacións visibles» segue no dominio 2.
- **Portabilidade.** A fila de `facility` leva `images[]` (debuxo polo código ou foto co seu metadato, sen bytes, P32); a mesma foto pode ir en varias filas e entra unha vez. Un ficheiro anterior con `photos[]` e `cover` impórtase igual (a portada primeiro).

## Alternativas descartadas
- **Gardar o suxerido ao crear:** obriga a calculalo en Java para as instalacións que xa existen (Flyway é SQL plano) e conxélao aínda que o xestor cambie o nome antes de escoller imaxes.
- **Alt por instalación:** permite matices, pero duplica traducións e deixa copias sen describir; se algún concello o pide, unha columna opcional en `facility_image` en F2.
- **Subir todo ao «Gardar» nunha petición:** varias fotos de 8 MB non caben no límite de 9 MB por petición, e os erros dunha foto (megapíxeles, ilexible) só sairían ao final.
- **Debuxos como ficheiros da web:** a API non podería validar os códigos nin servir a portada pública coas mesmas rutas; e o catálogo quedaría en dous sitios.

## Consecuencias
- Os debuxos novos entran cunha migración (fila en `drawing`) e o ficheiro co seu REUSE; o test cruzado falla se falta un dos dous.
- Borrar fotos da biblioteca non ten pantalla (só se descartan as pendentes): queda para F2 se se pide.
- Co modo edición da ficha (2026-10-26), o modal de fotos «acepta» e é a barra fixa quen fai o `PUT …/images`; as subidas seguen pendentes ata ese gardado e descártanse se se cancela.
- Eliminar unha instalación borra a súa lista (`facility_image`); as fotos quedan na biblioteca.
- A demo xa non sobe fotos: as instalacións de Narón e Ferrol ven o seu debuxo suxerido (o Pavillón de Piñeiros, o pavillón polideportivo).
