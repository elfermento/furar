// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Unha solicitude pendente: só a ve quen organiza («Aceptar», «Rexeitar»). */
public record OpenMatchJoinRequestResponse(UUID id, String name, Instant requestedAt) {}
