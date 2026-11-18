// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.FacilityId;

/**
 * Porto de saída: borra a instalación e todo o que depende dela no módulo (usos coas súas partes e
 * períodos, tempadas, excepcións, bloqueos e, por FK en cascada, deportes, servizos e a lista de
 * imaxes). Non toca a biblioteca de fotos nin os campos propios (son doutro porto). Dentro da
 * transacción de quen chama e do concello actual.
 */
public interface FacilityRemoval {

  void remove(FacilityId id);
}
