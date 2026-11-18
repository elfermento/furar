// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.Drawing;
import java.util.List;
import java.util.Optional;

/** Catálogo de debuxos de Furar (plataforma, só lectura). */
public interface DrawingRepository {

  List<Drawing> findAll();

  Optional<Drawing> find(String code);
}
