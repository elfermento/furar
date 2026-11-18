// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import type { TemplateRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, input, model, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import type { MapBounds, MapMarker } from '../../../../shared/ui/map/map-point';
import { PeekSheetComponent } from '../../../../shared/ui/peek-sheet/peek-sheet.component';
import { CatalogMapComponent, NearNoticeComponent } from './catalog-map.component';

/**
 * O botón flotante «Mapa» / «Lista» da portada común (abaixo no medio) e, no móbil, o mapa a toda
 * a pantalla que abre. No escritorio (`split`), a páxina pon o mapa ao lado da grella e aquí só
 * vai o botón.
 *
 * No móbil, debaixo do mapa asoma unha folla (`furar-peek-sheet`) co número de instalacións ou de
 * resultados; estendida, leva o mesmo listado ca a páxina (`listing`: os carruseis ou a grella) e
 * o botón pasa a «Mapa», que a volve pregar. Coa folla pregada, «Lista» (ou Esc) pecha o mapa.
 * Picar unha marca avisa con `picked` (a páxina leva á ficha).
 */
@Component({
  selector: 'furar-catalog-map-float',
  imports: [
    ButtonDirective,
    CatalogMapComponent,
    IconComponent,
    NearNoticeComponent,
    NgTemplateOutlet,
    PeekSheetComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'facility.publicList.';
    @if (open() && !split()) {
      <section
        class="f-home-map"
        tabindex="-1"
        data-home-map
        [attr.aria-label]="t + 'map' | transloco"
        (keydown.escape)="open.set(false)"
      >
        <p class="cdk-visually-hidden">{{ t + 'mapAlternative' | transloco }}</p>
        <furar-near-notice />
        <furar-catalog-map [markers]="markers()" (selected)="picked.emit($event)" (moved)="moved.emit($event)" />
        <furar-peek-sheet [label]="label()" [(expanded)]="expanded">
          <div class="f-plist"><ng-container *ngTemplateOutlet="listing()" /></div>
        </furar-peek-sheet>
      </section>
    }
    <div class="f-home-float">
      <button type="button" furarButton="primary" data-map-toggle (click)="toggle()">
        <furar-icon [name]="showsMap() ? 'location' : 'grid'" />
        {{ (showsMap() ? t + 'view.map' : t + 'view.list') | transloco }}
      </button>
    </div>
  `,
})
export class CatalogMapFloatComponent {
  /** O mapa está aberto (o botón di «Lista»). */
  readonly open = model(false);
  /** Escritorio: o mapa vai ao lado da grella, na páxina. */
  readonly split = input(false);
  readonly markers = input.required<readonly MapMarker[]>();
  /** O título da folla do móbil: «60 instalacións» ou «14 resultados». */
  readonly label = input.required<string>();
  /** O listado da páxina (carruseis ou grella), para a folla estendida. */
  readonly listing = input.required<TemplateRef<unknown>>();
  /** Picouse unha marca soa (o id da instalación). */
  readonly picked = output<string>();
  /** A persoa moveu ou achegou o mapa («Zona do mapa»). */
  readonly moved = output<MapBounds>();

  /** A folla do móbil, estendida por riba do mapa. */
  protected readonly expanded = signal(false);
  /** O botón leva ao mapa: pechado, ou coa folla estendida por riba. */
  protected readonly showsMap = computed(() => !this.open() || (!this.split() && this.expanded()));

  protected toggle(): void {
    if (this.open() && !this.split() && this.expanded()) {
      this.expanded.set(false);
      return;
    }
    this.expanded.set(false);
    this.open.update((open) => !open);
  }
}
