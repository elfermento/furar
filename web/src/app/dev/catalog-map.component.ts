// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { CardComponent } from '../shared/ui/card/card.component';
import type { MapPoint } from '../shared/ui/map/map-point';
import { MapComponent } from '../shared/ui/map/map.component';

/** Pavillón de Piñeiros (Narón), coma na demo. */
const PINEIROS: MapPoint = { latitude: 43.5136, longitude: -8.1859 };

/**
 * Sección «Mapa» do catálogo (só desenvolvemento): un mapa de só lectura co enderezo como
 * alternativa e un editable nun formulario. As teselas son as de `/tiles` (Caddy); nas probas
 * do catálogo intercéptanse con `page.route`.
 */
@Component({
  selector: 'furar-catalog-map',
  imports: [TranslocoPipe, ReactiveFormsModule, CardComponent, MapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.map.title' | transloco" id="mapa">
      <div class="gap-form grid lg:grid-cols-2">
        <div class="gap-unit grid content-start">
          <h3 class="f-label">{{ 'catalog.map.readOnly' | transloco }}</h3>
          <furar-map [point]="pineiros" />
          <p>{{ 'catalog.map.address' | transloco }}</p>
        </div>
        <div class="gap-unit grid content-start">
          <h3 class="f-label">{{ 'catalog.map.editable' | transloco }}</h3>
          <furar-map editable idPrefix="catalog-map" [formControl]="point" [label]="'catalog.map.label' | transloco" />
        </div>
      </div>
    </furar-card>
  `,
})
export class CatalogMapComponent {
  protected readonly pineiros = PINEIROS;
  protected readonly point = new FormControl<MapPoint | null>(PINEIROS);
}
