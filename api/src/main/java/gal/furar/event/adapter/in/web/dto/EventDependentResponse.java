// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.util.UUID;

/** A persoa a cargo apuntada (ADR-0031, ADR-0041): a súa ficha e o nome copiado ao apuntala. */
public record EventDependentResponse(UUID id, String name) {}
