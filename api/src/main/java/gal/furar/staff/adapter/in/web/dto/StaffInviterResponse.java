// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * Quen invitou, co mesmo criterio ca o rexistro de actividade.
 *
 * @param kind {@code MEMBER} (persoal do concello) ou {@code PLATFORM} (a web di «Administración da
 *     plataforma», sen ningún dato da conta); nunca {@code SYSTEM} nin {@code OTHER}
 * @param name o seu nome se é persoal do concello con nome; nulo se non (por exemplo, a plataforma)
 */
public record StaffInviterResponse(ActivityActorKindValue kind, @Nullable String name) {}
