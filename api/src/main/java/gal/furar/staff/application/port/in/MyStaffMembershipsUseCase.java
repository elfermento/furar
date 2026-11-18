// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import java.util.List;

/**
 * Os concellos onde a persoa da sesión é persoal activo, para cambiar de concello desde o seu menú
 * (ADR-0027, revisión do 2026-11-03). Só os da propia persoa.
 */
public interface MyStaffMembershipsUseCase {

  /** Ordenados polo nome; baleiro se non é persoal activo en ningún. */
  List<MyStaffMembership> mine();
}
