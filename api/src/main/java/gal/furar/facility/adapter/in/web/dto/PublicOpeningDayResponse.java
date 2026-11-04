// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Un día do horario público cos seus tramos abertos en orde; sen tramos, pechado. */
public record PublicOpeningDayResponse(LocalDate date, List<PublicOpeningSlotResponse> slots) {}
