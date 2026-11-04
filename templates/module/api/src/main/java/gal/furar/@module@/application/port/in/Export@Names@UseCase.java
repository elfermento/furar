// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import gal.furar.@module@.domain.@Name@;
import java.util.List;

/** Todos os do concello, con todos os seus valores, para o ficheiro furar-export (ADR-0018). */
public interface Export@Names@UseCase {

  List<@Name@> exportAll();
}
