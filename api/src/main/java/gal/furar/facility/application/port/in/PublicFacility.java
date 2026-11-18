// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.Sport;
import java.util.List;
import java.util.Objects;

/**
 * Unha instalación tal como a ve a veciñanza: o agregado (só se expón o público: nome, enderezo e
 * estado), os seus deportes co nome na orde da tarxeta Datos e a imaxe de portada (a primeira da
 * súa lista ou, se non ten, o debuxo suxerido).
 */
public record PublicFacility(Facility value, List<Sport> sports, ImageView cover) {

  public PublicFacility {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(cover, "cover");
    sports = List.copyOf(sports);
  }
}
