// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.List;

/**
 * Unha cifra que é unha conta ou un importe en céntimos: o período, o anterior, os dous tramos da
 * variación ({@code comparison} do panel) e a evolución (da máis antiga á máis nova; o último punto
 * é o período, salvo nun rango libre).
 *
 * @param value o período enteiro, coas reservas xa feitas para os días que faltan
 * @param previous o período anterior enteiro
 * @param valueToDate o tramo actual da variación: ata hoxe no período en curso; se non, {@code
 *     value}
 * @param previousToDate o mesmo tramo do anterior; nun período pechado, {@code previous}
 */
public record IndicatorCountKpiResponse(
    long value,
    long previous,
    long valueToDate,
    long previousToDate,
    List<IndicatorCountPointResponse> evolution) {}
