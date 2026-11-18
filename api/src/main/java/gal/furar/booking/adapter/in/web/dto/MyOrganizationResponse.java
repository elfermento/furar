// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;

/** Unha entidade da que a persoa é responsable (ADR-0039, punto 10). */
public record MyOrganizationResponse(UUID id, String name) {}
