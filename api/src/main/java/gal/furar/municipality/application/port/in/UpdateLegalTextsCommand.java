// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** Os textos propios das tres páxinas legais, coa versión do concello que leu o cliente. */
public record UpdateLegalTextsCommand(long expectedVersion, LegalTextsData texts) {}
