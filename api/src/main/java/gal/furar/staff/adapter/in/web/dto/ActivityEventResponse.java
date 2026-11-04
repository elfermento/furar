// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento do rexistro de actividade (plan do dominio 4, §2.3). Sen datos persoais alén do nome e
 * o correo do persoal (ADR-0020).
 *
 * @param id o evento
 * @param at cando pasou
 * @param action o código da acción; a web ten un texto para cada un e outro de reserva
 * @param outcome o resultado
 * @param actor quen actuou
 * @param target sobre que, se a acción o di
 * @param municipality o slug do concello no momento da acción; nulo nas accións sen concello
 * @param requestId o identificador da petición (X-Request-Id), para buscala nos rexistros
 */
public record ActivityEventResponse(
    UUID id,
    Instant at,
    @Schema(example = "staff.invite") String action,
    ActivityOutcomeValue outcome,
    ActivityActorResponse actor,
    @Nullable ActivityTargetResponse target,
    @Nullable String municipality,
    @Nullable String requestId) {}
