// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.shared.domain.DomainException;

/**
 * «Descargar CSV» das reservas do concello (maqueta 60): as dun período de como moito {@value
 * #MAX_MONTHS} meses, por día e hora, con todos os campos propios que ve o persoal. Leva datos
 * persoais: permiso {@code booking.export} (só a xestión) e queda no rexistro de actividade antes
 * de saír a primeira fila.
 */
public interface ExportBookingsUseCase {

  /** Meses seguidos que abarca como moito unha descarga. */
  int MAX_MONTHS = 12;

  /**
   * Valida o período, rexistra a descarga e pasa as reservas a {@code sink} por tandas, sen
   * cargalas todas á vez: primeiro {@link BookingExportSink#begin} e despois cada tanda.
   *
   * @throws DomainException {@code common.invalid-parameter} se {@code from} é posterior a {@code
   *     to}; {@code booking.export-range-too-long} se o período pasa de {@value #MAX_MONTHS} meses
   */
  void export(BookingExportQuery query, BookingExportSink sink);
}
