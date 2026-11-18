<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0036 · Incidencias: a veciñanza avisa dun problema e o persoal resólveo

- **Estado:** aceptada · 2026-11-11 (decidida polo mantedor; construída o 2026-11-11)
- **Revisada 2026-11-11 (decisión do mantedor):** sen borrado automático; a xestión elimina a incidencia ou unha foto; tope de 3 avisos por conta e día.
- **Constrúese enriba de:** [ADR-0024](0024-biblioteca-de-imaxes.md) (tratamento das fotos), [ADR-0025](0025-reservas-fase-1.md) punto 15 (correos e rexistro de envíos), [ADR-0017](0017-datos-persoais.md) e [ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9 (a plataforma non ve datos da veciñanza).

## Contexto
Quen usa unha instalación ve antes ca ninguén o que non funciona. Ata agora avisaba por teléfono ou non avisaba; o concello non tiña onde levar a conta deses avisos.

## Decisión
1. **Quen avisa e desde onde.** Calquera persoa con sesión e correo verificado (`@PersonUseCase`), nunha instalación que se ve no público: desde o bloque «Viches algo que non funciona?» ao final da ficha ou desde «Avisar dun problema» nunha reserva pasada e sen cancelar do historial. Sen sesión o botón vese igual e leva a entrar e volver co aviso aberto. A reserva ten que ser desa instalación, xa empezada e do mesmo correo ca a conta. Como moito 3 avisos por conta e día nun concello (`incident.too-many-reports`). As incidencias non son públicas e a persoa non as volve ver: só recibe un correo ao resolverse.
2. **Que leva.** Tipo (Avaría, Limpeza, Seguridade, Outro), descrición (ata 500) e ata tres fotos opcionais. As fotos pasan polo mesmo tratamento ca as das instalacións (`facility.PhotoSanitizer`: JPEG ou PNG de ata 8 MB e 40 Mpx, sen metadatos nin localización, grande e miniatura) e os bytes van nunha táboa á parte (`incident_photo_content`). O aviso é un único `multipart/form-data` (Spring admite 25 MB por petición e Caddy deixa pasar 25 MB só nas rutas dos avisos).
3. **Quen o xestiona.** Xestión e Recepción (`incident.view`, `incident.manage`): pestana «Incidencias» de «Reservas» (`/:concello/xestion/incidencias`), co número de novas na pestana, no apartado e na barra inferior. O persoal tamén anota unha («Nova incidencia», calquera instalación do concello). Eliminar a incidencia enteira ou só unha foto (por exemplo, se sae unha persoa) é só da xestión (`incident.delete`), desde o pé do detalle (eliminar á parte de «Gardar») e o botón de cada foto, con confirmación. A plataforma non as ve.
4. **Estados.** Nova, En curso, Resolta, de calquera a calquera. No detalle: nota interna e, ao pasar a Resolta un aviso da veciñanza, unha mensaxe opcional para a persoa. «Gardar» con `If-Match`.
5. **Correos.** Mesmo compositor, plantilla e rexistro (`notification_delivery`, tipos `INCIDENT_REPORTED` e `INCIDENT_RESOLVED`). Non hai unha lista de «avisos da xestión»: «Nova incidencia» vai a cada persoa con rol Xestión activa no concello (menos a quen a anotou), no idioma principal do concello, sen a descrición nin quen avisou e coa ligazón á lista. «Resolvemos o teu aviso» vai unha vez á persoa, no seu idioma, coa mensaxe se a hai. `incident` chama a `notification.IncidentMailer` despois do commit, nun fío propio.
6. **Rexistro de actividade.** `incident.create`, `incident.status` e `incident.resolve` co identificador, a instalación, o tipo, a orixe, o número de fotos e os estados; nunca o texto, a nota nin a mensaxe. Eliminar deixa `incident.delete` (co número de fotos) e `incident.photo-delete` (o identificador da foto).
7. **Persistencia.** Módulo `incident` en SQL nativo con RLS, sen JPA e fóra de `furar-export` (leva datos da persoa, coma as reservas). Sen FK a `facility` nin a `booking`: compróbanse pola súa API pública.
8. **Conservación.** Sen borrado automático: as incidencias e as súas fotos quedan ata que a xestión as elimina, porque son o historial de mantemento da instalación. Eliminar a incidencia borra tamén as fotos e os envíos de correo. A persoa que avisou desvincúlase pechando a conta: `close_account` quita a persoa das súas incidencias e borra as súas fotos. Detalle en [`datos-persoais.md`](../datos-persoais.md).

## Consecuencias
- O formulario lembra no «?» que non se poñan nomes nin fotos de persoas.
- A persoa non ten unha lista «Os meus avisos»: se a quere, é outra decisión.

## Alternativas descartadas
- **Borrado automático ao cabo dun prazo:** descartado na revisión do mantedor; as incidencias son o historial de mantemento da instalación e quedan ata que a xestión as elimina.
- **JPA e `furar-export`:** levan datos da persoa, coma as reservas; van en SQL nativo con RLS e fóra da exportación.
- **FK cara a `facility` e `booking`:** sumaría excepcións á lista pechada de FK entre módulos; a instalación e a reserva compróbanse pola súa API pública.
