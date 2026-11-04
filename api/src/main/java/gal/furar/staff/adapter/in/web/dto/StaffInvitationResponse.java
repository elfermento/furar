// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A última invitación dunha pertenza invitada.
 *
 * @param sentAt cando se fixo (o último envío): a web mostra «Invitación enviada o …»
 * @param expiresAt cando caduca (despois, a web mostra «Invitación caducada» e ofrece reenviar)
 * @param delivery se o correo saíu
 * @param resendAvailableAt desde cando se pode reenviar (2 min despois do último envío); nulo se xa
 *     se pode ou se se gastaron os envíos. A web desactiva «Reenviar» ata entón; a API segue
 *     respondendo 429 se chega antes
 * @param resendsExhausted se xa se gastaron os 5 envíos da pertenza: non se pode reenviar máis
 */
public record StaffInvitationResponse(
    Instant sentAt,
    Instant expiresAt,
    InvitationDeliveryValue delivery,
    @Nullable Instant resendAvailableAt,
    boolean resendsExhausted) {}
