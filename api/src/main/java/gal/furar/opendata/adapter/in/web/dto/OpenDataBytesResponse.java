// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web.dto;

/** Bytes aproximados por día en cada formato. */
public record OpenDataBytesResponse(long csv, long json) {}
