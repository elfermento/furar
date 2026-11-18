<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0037 · Valoracións: unha por reserva á que se foi, públicas e anónimas, e o xestor oculta comentarios

- **Estado:** aceptada · 2026-11-11 (decidida polo mantedor; construída o 2026-11-11)
- **Constrúese enriba de:** [ADR-0025](0025-reservas-fase-1.md) (a persoa que reserva, sen rol), [ADR-0032](0032-non-presentados-e-validacion.md) (asistencia e control de entrada), [ADR-0026](0026-indicadores-fase-1.md) (cifras e evolución) e [ADR-0017](0017-datos-persoais.md).

## Contexto
Quen vai reservar quere saber como está unha instalación antes de ir, e o concello non tiña ningunha medida de como a valora quen a usa.

## Decidido polo mantedor (2026-11-11)
- Quen reservou valora a instalación de 1 a 5 estrelas, cun comentario opcional de 300 caracteres, despois dunha reserva pasada á que foi. Pídese só desde «As miñas reservas › Historial» (sen correos).
- As valoracións son públicas e anónimas: na ficha da instalación, só unha fila «Valoración» na tarxeta lateral que abre unha capa coa nota media e a lista, ordenable (máis recentes, máis antigas, nota máis alta e máis baixa). Nin nas tarxetas do catálogo nin no corpo da ficha.
- O xestor ve todas en Indicadores (a cifra «Valoración media» e unha táboa) e pode ocultar un comentario: deixa de verse en público e a nota segue contando. A plataforma non ve comentarios.

## Decisión
1. **Vive en `booking`, non nun módulo novo.** A valoración é dunha reserva: «unha por reserva», «só quen reservou» e «só se foi» saen da propia reserva, e a súa conservación é a da reserva. Un módulo aparte tería que ler a reserva doutro módulo, levar unha FK máis á lista pechada `CrossModuleForeignKeys` e facer que `indicators` dependese del. Táboa `booking_review` con `municipality_id`, RLS e SQL nativo (`BookingReviewSql`, `BookingReviewSqlTenantIT`), coma a lista de espera.
2. **Que reserva se pode valorar (`ReviewRule`).** Confirmada, xa rematada e sen «Non presentado». Se a instalación tiña control de entrada cando empezou (con el, sen validar pasa soa a falta), só a validada: unha sen marca pode estar á espera da tarefa das faltas. Unha reserva doutra persoa responde coma se non existise (404).
3. **Unha por reserva, sen cambios.** `unique (municipality_id, booking_id)`; gárdase con `on conflict do nothing` e a segunda dá 409 `booking.review-exists`. Non se edita nin se borra desde a web: é o máis simple e evita reescribir o que xa se publicou.
4. **Sen persoa na fila.** A valoración garda a reserva, a instalación e o uso (copiados da reserva), a nota, o comentario e cando; quen valorou só se sabe pola reserva, que se anonimiza co seu prazo. A API pública nunca devolve a reserva nin a persoa.
5. **Público.** `GET …/public/facilities/{id}/reviews/summary` (cantas e a media, tamén as de comentario oculto) e `GET …/public/facilities/{id}/reviews?sort=` (por páxinas; o comentario oculto sae baleiro), con `@PublicEndpoint` e só se a instalación se ve. Cada orde é un literal SQL propio (A8b).
6. **Xestión.** Permiso `booking.reviews` (os roles dos indicadores: só a xestión). `GET …/booking-reviews` (período, instalación, usos e nota; cos recontos por nota sen o filtro de nota) e `PUT …/booking-reviews/{id}/hidden` (`{hidden}`). Ocultar e mostrar quedan no rexistro de actividade (`booking.review-hide`, `booking.review-show`) co identificador e a instalación, nunca o texto.
7. **Indicadores.** A cifra «Valoración media» conta as valoracións polo día en que se fixeron (Europe/Madrid), cos filtros de instalación e uso da súa reserva; a súa evolución e o tramo «ata hoxe» son os das outras cifras (`BookingStatistics.reviews`, unha lectura máis por panel e dúas no período en curso ou nun rango libre). O cambio vai en décimas de nota.
8. **Baixa dunha conta.** `close_account` borra as valoracións das reservas da conta: quen pide a supresión deixa de figurar tamén na media. As demais quedan, anónimas, aínda que a reserva se anonimice.
9. **Datos abertos e portabilidade.** As valoracións non van nos conxuntos de datos abertos ([ADR-0034](0034-datos-abertos.md)) nin, de momento, en `furar-export`.

## Consecuencias
- Un texto libre público: a capa «Valorar» avisa de que se publica sen nome e de que non se poñan nomes nin datos persoais; o resto, o xestor ocultándoo.
- Con control de entrada, quen foi e non validou non pode valorar.

## Alternativas descartadas
- **Pedila por correo despois da reserva:** descartado, porque molesta a quen reservou.
- **Editar ou borrar a propia:** máis pantallas e unha media que cambia sen rastro.
- **Gardar a conta na valoración:** un dato persoal máis para manter e borrar, cando a reserva xa o di.
