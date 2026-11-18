// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.Set;

/**
 * Lista pechada das FK dunha táboa dun módulo cara a unha táboa doutro (ADR-0005, ADR-0012): a
 * única excepción a «ninguén le nin bloquea as táboas doutro módulo». Cara a {@code municipality}
 * (a raíz de todos) non fai falta entrada. O resto só cara a unha táboa raíz ou publicada polo seu
 * dono, que só se arquiva e cambia como unha API. {@code SchemaTenancyIT} le o esquema real e falla
 * con calquera outra. Engadir unha entrada xustifícase no PR.
 */
public final class CrossModuleForeignKeys {

  /**
   * Unha FK permitida.
   *
   * @param from táboa que apunta
   * @param to táboa doutro módulo á que apunta
   */
  public record ForeignKey(String from, String to) {}

  public static final Set<ForeignKey> ALLOWED =
      Set.of(
          // booking → usos e partes publicados por facility (ADR-0012): o uso é da instalación,
          // a parte é do uso e o modo é o do uso. Os bloqueos van por FacilityQueries.lockFacility.
          new ForeignKey("booking", "facility_use"),
          new ForeignKey("booking", "facility_use_part"),
          new ForeignKey("booking_occupancy", "facility_use"),
          new ForeignKey("booking_occupancy", "facility_use_part"),
          new ForeignKey("use_slot", "facility_use"),
          // booking → o uso polo que se espera e o do reconto anónimo das esperas (ADR-0033).
          new ForeignKey("booking_wait", "facility_use"),
          new ForeignKey("booking_wait_tally", "facility_use"),
          // booking → o uso e a parte da tempada dunha entidade e da súa solicitude (ADR-0039).
          new ForeignKey("booking_series", "facility_use"),
          new ForeignKey("booking_series", "facility_use_part"),
          new ForeignKey("booking_series_request", "facility_use"),
          new ForeignKey("booking_series_request", "facility_use_part"),
          // booking → fichas publicadas por people (ADR-0031): a reserva queda ligada á persoa a
          // cargo para a que se fixo, e a FK impide borrar unha ficha con reservas.
          new ForeignKey("booking_participant", "account_dependent"),
          // opendata → a instalación (táboa raíz de facility) da ocupación publicada (ADR-0034):
          // ao borrar a instalación, as súas filas van con ela.
          new ForeignKey("open_data_occupancy", "facility"));

  private CrossModuleForeignKeys() {}

  public static boolean allows(String from, String to) {
    return ALLOWED.contains(new ForeignKey(from, to));
  }
}
