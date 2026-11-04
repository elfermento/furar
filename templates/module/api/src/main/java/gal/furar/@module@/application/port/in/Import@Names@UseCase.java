// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import java.util.List;

/** Carga as filas dun ficheiro furar-export no concello novo (ADR-0018). */
public interface Import@Names@UseCase {

  void importAll(List<Restore@Name@Command> rows);
}
