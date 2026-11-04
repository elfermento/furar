// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/**
 * QR do código da reserva como camiño SVG (ADR-0025, punto 13): {@code viewBox="0 0 size size"},
 * sen zona tranquila (a marxe branca pona quen o pinta).
 *
 * @param size módulos por lado
 * @param path o atributo {@code d} dun {@code <path>} cos módulos escuros
 */
public record QrResponse(int size, String path) {}
