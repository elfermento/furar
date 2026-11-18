// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/**
 * O estado dunha partida aberta: busca xente, pechada («Deixar de buscar») ou cancelada (coa súa
 * reserva ou sen o mínimo).
 */
public enum OpenMatchStatusValue {
  OPEN,
  CLOSED,
  CANCELLED
}
