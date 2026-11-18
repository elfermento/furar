// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** O xestor edita o seu concello (nome, idiomas, tema e campos personalizados). */
public interface UpdateMunicipalityUseCase {

  MunicipalityView update(UpdateMunicipalityCommand command);
}
