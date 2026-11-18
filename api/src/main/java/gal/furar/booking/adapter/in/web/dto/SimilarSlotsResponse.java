// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;

/** Os ocos parecidos, como moito un por regra e na orde das regras; baleira se non hai. */
public record SimilarSlotsResponse(List<SimilarSlotResponse> items) {}
