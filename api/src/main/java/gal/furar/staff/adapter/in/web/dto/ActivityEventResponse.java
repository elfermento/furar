// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento do rexistro de actividade. Sen datos persoais alén do nome e o correo do persoal
 * (ADR-0020).
 *
 * @param id o evento
 * @param at cando pasou
 * @param action o código da acción; a web ten un texto para cada un e outro de reserva
 * @param outcome o resultado
 * @param actor quen actuou
 * @param target sobre que, se a acción o di
 * @param municipality o slug do concello no momento da acción; nulo nas accións sen concello
 * @param requestId o identificador da petición (X-Request-Id), para buscala nos rexistros
 * @param facilityId a instalación onde foi, se a acción a leva
 * @param label o nome do obxecto no momento da acción (instalación, uso, tempada…), nunca un dato
 *     persoal
 * @param changes o que cambiou, campo a campo; baleiro nos eventos anteriores ao 2026-11-10
 */
public record ActivityEventResponse(
    UUID id,
    Instant at,
    @Schema(example = "staff.invite") String action,
    ActivityOutcomeValue outcome,
    ActivityActorResponse actor,
    @Nullable ActivityTargetResponse target,
    @Nullable String municipality,
    @Nullable String requestId,
    @Nullable UUID facilityId,
    @Nullable String label,
    List<ActivityChangeResponse> changes) {}
