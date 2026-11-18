// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.DrawingRepository;
import gal.furar.facility.domain.Drawing;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Catálogo de debuxos de Furar: táboa de plataforma, sen concello e só de lectura (ADR-0006). Herda
 * de {@link TenantScopedPersistenceAdapter} pola regra A7b do paquete, pero non o necesita.
 */
@Component
class DrawingPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements DrawingRepository {

  private final DrawingJpaRepository drawings;

  DrawingPersistenceAdapter(DrawingJpaRepository drawings) {
    this.drawings = drawings;
  }

  @Override
  public List<Drawing> findAll() {
    return drawings.findAll().stream().map(DrawingPersistenceAdapter::toDomain).toList();
  }

  @Override
  public Optional<Drawing> find(String code) {
    return drawings.findById(code).map(DrawingPersistenceAdapter::toDomain);
  }

  private static Drawing toDomain(DrawingEntity row) {
    return new Drawing(
        row.code(),
        new LocalizedText(row.name().get("gl"), row.name().get("es")),
        new LocalizedText(row.alt().get("gl"), row.alt().get("es")),
        row.width(),
        row.height());
  }
}
