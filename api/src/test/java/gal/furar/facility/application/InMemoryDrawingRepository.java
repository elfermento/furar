// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.DrawingRepository;
import gal.furar.facility.domain.Drawing;
import gal.furar.facility.domain.DrawingSuggestion;
import gal.furar.shared.domain.LocalizedText;
import java.util.List;
import java.util.Optional;

/** Os códigos do catálogo, cun nome e texto de proba. */
class InMemoryDrawingRepository implements DrawingRepository {

  static Drawing drawing(String code) {
    return new Drawing(
        code,
        new LocalizedText("Debuxo " + code, "Dibujo " + code),
        new LocalizedText("Alt " + code, "Alt es " + code),
        1200,
        800);
  }

  @Override
  public List<Drawing> findAll() {
    return DrawingSuggestion.CODES.stream()
        .sorted()
        .map(InMemoryDrawingRepository::drawing)
        .toList();
  }

  @Override
  public Optional<Drawing> find(String code) {
    return DrawingSuggestion.CODES.contains(code) ? Optional.of(drawing(code)) : Optional.empty();
  }
}
