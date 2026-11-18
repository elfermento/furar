// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** A regra que propón un oco parecido (ADR-0033, punto 9). */
public enum SimilarSlotReasonValue {
  SAME_FACILITY_NEAR,
  OTHER_FACILITY_SAME_TIME,
  LATER_SAME_DAY,
  NEXT_WEEK
}
