// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** O xestor descarga todos os datos do seu concello (formato {@code furar-export}, ADR-0018). */
public interface ExportMunicipalityUseCase {

  MunicipalityExport export();
}
