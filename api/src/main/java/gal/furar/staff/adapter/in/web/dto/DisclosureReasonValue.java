// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * Para que se exportan os eventos bloqueados (LOPDGDD art. 32.2): requirimento xudicial, Ministerio
 * Fiscal, autoridade de protección de datos ou expediente aberto polo concello.
 */
public enum DisclosureReasonValue {
  COURT_ORDER,
  PUBLIC_PROSECUTOR,
  DATA_PROTECTION_AUTHORITY,
  OPEN_PROCEEDINGS
}
