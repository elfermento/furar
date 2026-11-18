// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Quen se apunta (ADR-0041): unha persoa a cargo ({@code dependentId}) ou, baleiro, a propia
 * persoa. Nun evento con límite de idade, a propia persoa dá a súa data de nacemento ({@code
 * birthDate}, sen hora): compróbase polo seu ano e só se garda nesta inscrición; a persoa a cargo
 * xa ten o ano na súa ficha.
 */
public record JoinEventRequest(@Nullable UUID dependentId, @Nullable LocalDate birthDate) {}
