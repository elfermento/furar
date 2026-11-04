// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

/**
 * A que se aplica o importe: a cada reserva dunha franxa ({@code BOOKING}) ou, nos usos por prazas,
 * a cada persoa e franxa ({@code PERSON}).
 */
public enum PricePerValue {
  BOOKING,
  PERSON
}
