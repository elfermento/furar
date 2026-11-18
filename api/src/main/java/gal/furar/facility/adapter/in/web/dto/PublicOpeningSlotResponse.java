// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.Instant;

/**
 * Un tramo aberto: minutos de reloxo no fuso do concello (0–1440, fin excluído) e os instantes
 * reais, para saber se está aberto agora sen calcular o cambio de hora no navegador.
 */
public record PublicOpeningSlotResponse(
    int startMinute, int endMinute, Instant startsAt, Instant endsAt) {}
