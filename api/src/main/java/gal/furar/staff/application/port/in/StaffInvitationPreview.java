// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.domain.StaffRole;
import java.time.Instant;

/**
 * O que lle mostra a invitación a quen ten o token (plan do dominio 4, §2.3). O correo é o mesmo
 * que xa leva o correo da invitación, así que non di nada novo a quen o ten.
 *
 * @param municipalityName nome do concello
 * @param role o rol que dá
 * @param email o correo invitado (a web dío se a sesión é doutro correo)
 * @param expiresAt cando caduca
 * @param registrationProof proba firmada de que quen ten a ligazón recibiu o correo: a web pásalla
 *     a Keycloak e o rexistro dá o correo por verificado sen outro correo ({@code
 *     RegistrationProof}, ADR-0027, revisión do 2026-11-02)
 */
public record StaffInvitationPreview(
    String municipalityName,
    StaffRole role,
    String email,
    Instant expiresAt,
    String registrationProof) {}
