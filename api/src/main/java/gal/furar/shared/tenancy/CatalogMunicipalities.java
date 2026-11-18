// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.List;

/**
 * Porto para percorrer, por páxinas, os concellos que poden saír na portada común (ADR-0030) sen
 * depender do módulo {@code municipality}, que o implementa: os que teñen completos os datos legais
 * que usan os textos legais por defecto. Quen o usa traballa despois en cada un co seu {@link
 * TenantContext#runAs} e a súa RLS, nunha transacción por concello.
 */
public interface CatalogMunicipalities {

  /** Concellos por páxina, como moito. */
  int MAX_PAGE_SIZE = 50;

  /**
   * Unha páxina dos concellos da instalación, por nome (a páxina empeza en 0), da que só saen os
   * que teñen os datos legais completos.
   *
   * @throws IllegalArgumentException se {@code page} é negativa ou {@code size} non vai de 1 a
   *     {@value #MAX_PAGE_SIZE}
   */
  Page page(int page, int size);

  /**
   * Un concello: o seu id e slug (nunca nulo aquí), o nome que se mostra e se publica datos abertos
   * (ADR-0034).
   */
  record Listed(TenantRef tenant, String name, boolean openData) {

    public Listed(TenantRef tenant, String name) {
      this(tenant, name, false);
    }
  }

  /**
   * Unha páxina.
   *
   * @param items os da páxina cos datos legais completos; poden ser menos ca o tamaño pedido
   * @param hasNext se quedan concellos despois desta páxina
   */
  record Page(List<Listed> items, boolean hasNext) {

    public Page {
      items = List.copyOf(items);
    }
  }
}
