// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.time.Instant;

/**
 * O que di a invitación a quen ten o token.
 *
 * @param municipalityName nome do concello
 * @param role o rol que dá
 * @param email o correo invitado (o mesmo ao que chegou a ligazón)
 * @param expiresAt cando caduca
 * @param registrationProof proba firmada para o rexistro de Keycloak (parámetro {@code
 *     furar_invitation}): co mesmo correo, a conta nace co correo verificado. Caduca aos 30 minutos
 *     ou coa invitación; non se garda nin se rexistra
 */
public record StaffInvitationPreviewResponse(
    String municipalityName,
    StaffRoleValue role,
    String email,
    Instant expiresAt,
    String registrationProof) {}
