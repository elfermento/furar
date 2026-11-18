// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.QrCode;

/** Xera o QR dun texto (o código da reserva; ADR-0025, punto 13). */
public interface QrEncoder {

  QrCode encode(String text);

  /**
   * O mesmo QR en PNG, para o correo (moitos clientes non mostran SVG): módulos de {@code
   * modulePixels} píxeles e unha zona tranquila de {@code quietModules} módulos brancos.
   */
  byte[] png(String text, int modulePixels, int quietModules);
}
