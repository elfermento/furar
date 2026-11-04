// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha pertenza ao persoal do concello (plan do dominio 4, §2.3).
 *
 * @param id a pertenza
 * @param email o correo invitado; nulo se a pertenza está anonimizada ou bloqueada
 * @param anonymized se a pertenza está anonimizada (desactivada hai máis de {@code
 *     FURAR_AUDIT_RETENTION}, ou invitada que nunca aceptou e xa sen invitacións): a web mostra
 *     «Persoa anonimizada», sen correo nin nome
 * @param blocked se o correo e o nome están bloqueados (desactivada hai máis de {@code
 *     FURAR_AUDIT_VISIBLE} e aínda sen anonimizar; LOPDGDD art. 32): a web mostra «Persoa
 *     desactivada», sen correo nin nome
 * @param name o nome da persoa, desde que acepta; nulo mentres está invitada («Pendente de
 *     aceptar»), anonimizada ou bloqueada
 * @param role o rol no concello
 * @param status invitada, activa ou desactivada
 * @param language o idioma do correo da invitación
 * @param invitedAt cando se invitou
 * @param invitedBy quen invitou
 * @param acceptedAt cando aceptou
 * @param deactivatedAt cando se desactivou
 * @param lastSignInAt o último acceso á xestión
 * @param invitation a última invitación, só mentres está invitada
 * @param version a versión (o ETag do detalle), para If-Match
 */
public record StaffMemberResponse(
    UUID id,
    @Nullable String email,
    boolean anonymized,
    boolean blocked,
    @Nullable String name,
    StaffRoleValue role,
    StaffStatusValue status,
    @Schema(allowableValues = {"gl", "es"}) String language,
    Instant invitedAt,
    StaffInviterResponse invitedBy,
    @Nullable Instant acceptedAt,
    @Nullable Instant deactivatedAt,
    @Nullable Instant lastSignInAt,
    @Nullable StaffInvitationResponse invitation,
    long version) {}
