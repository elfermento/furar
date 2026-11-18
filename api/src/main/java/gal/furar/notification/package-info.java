// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Avisos (docs/glosario.md; ADR-0025, punto 15; ADR-0027, punto 6): os correos das reservas
 * (confirmada, cambiada, cancelada pola persoa e cancelada polo concello, se escolleu avisar por
 * correo), en gl ou es segundo a reserva, co QR e o {@code .ics}; o de cada falta; «Quedou libre»,
 * a todas as persoas que esperaban por unha hora (ADR-0033); e o da invitación ao persoal. Sen
 * pantalla nin API HTTP, salvo «¿Es un concello?» (ADR-0042): {@code POST
 * /api/v1/municipality-join-requests}, sen concello nin sesión, envía a solicitude de alta por
 * correo a quen opera o servizo ({@code FURAR_JOIN_REQUEST_EMAIL}) e non garda nada, nin sequera o
 * envío. Os das reservas escoitan os eventos de {@code booking} despois do commit e len a API
 * pública de {@code booking} e {@code municipality}. O da invitación é síncrono: {@code staff}
 * chama a {@link gal.furar.notification.StaffInvitationMailer} despois do commit, co token en
 * memoria (un evento gardaríao en claro no rexistro de publicacións); {@code notification} non
 * depende de {@code staff}. Garda en {@code notification_delivery} que se enviou, a quen, cando e o
 * resultado, nunca o contido nin a ligazón; a fila bórrase ao ano ({@code
 * NotificationRetentionService}) ou antes, se {@code staff} esquece a invitación ({@link
 * gal.furar.notification.StaffInvitationMailer#forget}). Os das incidencias (ADR-0036) van coma o
 * da invitación: {@code incident} chama a {@link gal.furar.notification.IncidentMailer} despois do
 * commit; os dos eventos do concello (ADR-0038), igual con {@link
 * gal.furar.notification.EventMailer}. API pública: {@link
 * gal.furar.notification.StaffInvitationMailer}, {@link
 * gal.furar.notification.StaffInvitationMail}, {@link gal.furar.notification.IncidentMailer}, os
 * seus records e {@link gal.furar.notification.DeliveryResult}.
 */
@ApplicationModule(
    displayName = "Notification",
    allowedDependencies = {"shared", "booking", "facility", "municipality"})
package gal.furar.notification;

import org.springframework.modulith.ApplicationModule;
