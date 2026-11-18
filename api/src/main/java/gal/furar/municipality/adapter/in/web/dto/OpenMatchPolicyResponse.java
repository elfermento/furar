// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

/**
 * «Buscar xente para xogar» do concello (Concello › Xeral, ADR-0040): aceso, quen ten unha reserva
 * futura de pista enteira ou por partes pode abrila a outras persoas; sen o mínimo, a partida e a
 * reserva cancélanse {@code cancelHours} horas antes do inicio. Apagado, as horas quedan gardadas.
 */
public record OpenMatchPolicyResponse(boolean enabled, int cancelHours) {}
