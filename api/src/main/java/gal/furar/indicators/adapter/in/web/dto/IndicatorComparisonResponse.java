// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * Os dous tramos que compara a variación das cifras (ADR-0026, revisión do 2026-10-29), días de
 * Europe/Madrid cos dous extremos incluídos. No período en curso, «ata hoxe»: do primeiro día a
 * hoxe fronte ao mesmo tramo do anterior; se non, os dous períodos enteiros.
 *
 * @param toDate se o período está en curso e a variación compara só ata hoxe
 * @param from primeiro día do tramo actual
 * @param to último día do tramo actual (hoxe, no período en curso)
 * @param comparedFrom primeiro día do tramo co que se compara
 * @param comparedTo último día do tramo co que se compara
 */
public record IndicatorComparisonResponse(
    boolean toDate, LocalDate from, LocalDate to, LocalDate comparedFrom, LocalDate comparedTo) {}
