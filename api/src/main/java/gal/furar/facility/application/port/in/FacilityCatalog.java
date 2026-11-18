// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.shared.tenancy.TenantRef;
import java.util.List;

/**
 * Unha páxina da portada común (ADR-0030): os concellos que saen nela, por nome, cada un coas
 * instalacións que ve a súa veciñanza.
 *
 * @param hasNext se quedan concellos por ler despois desta páxina
 * @param items os concellos da páxina que saen; poden ser menos ca {@code size}
 */
public record FacilityCatalog(int page, int size, boolean hasNext, List<Entry> items) {

  public FacilityCatalog {
    items = List.copyOf(items);
  }

  /**
   * Un concello e as súas instalacións, na orde alfabética do idioma de quen mira; {@code
   * openData}, se publica datos abertos (ADR-0034).
   */
  public record Entry(
      TenantRef municipality, String name, boolean openData, List<PublicFacility> facilities) {

    public Entry {
      facilities = List.copyOf(facilities);
    }
  }
}
