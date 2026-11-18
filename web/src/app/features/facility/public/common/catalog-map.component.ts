// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../../shared/ui/callout/callout.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { MapComponent } from '../../../../shared/ui/map/map.component';
import type { MapBounds, MapMarker } from '../../../../shared/ui/map/map-point';
import { NearMe } from './near-me';

/**
 * O mapa da portada común (nos resultados e no mapa a toda a pantalla): as instalacións como
 * marcas, agrupadas se están cerca, e «Preto de min» (`NearMe`) dentro, parella do zoom.
 */
@Component({
  selector: 'furar-catalog-map',
  imports: [ButtonDirective, IconComponent, MapComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    <furar-map
      cluster
      [markers]="markers()"
      [zoom]="16"
      [here]="nearMe.here()"
      [activeMarker]="active()"
      (markerSelected)="selected.emit($event)"
      (viewMoved)="moved.emit($event)"
    >
      @if (nearMe.supported) {
        <button
          #near
          mapControl
          type="button"
          size="sm"
          data-near
          [furarButton]="nearMe.here() ? 'primary' : 'secondary'"
          [attr.aria-pressed]="!!nearMe.here()"
          [busy]="nearMe.locating()"
          (click)="nearMe.toggle()"
        >
          <furar-icon name="near-me" />
          {{ 'facility.catalog.near' | transloco }}
        </button>
      }
    </furar-map>
  `,
})
export class CatalogMapComponent {
  protected readonly nearMe = inject(NearMe);

  readonly markers = input.required<readonly MapMarker[]>();
  /** A marca resaltada (a da tarxeta sinalada ou a escollida). */
  readonly active = input<string | null>(null);
  /** Picouse unha marca (o id da instalación). */
  readonly selected = output<string>();
  /** A persoa moveu ou achegou o mapa: o que se ve agora («Zona do mapa»). */
  readonly moved = output<MapBounds>();

  private readonly near = viewChild<ElementRef<HTMLButtonElement>>('near');

  constructor() {
    effect(() => {
      this.nearMe.button = this.near()?.nativeElement ?? null;
    });
    inject(DestroyRef).onDestroy(() => {
      if (this.nearMe.button === this.near()?.nativeElement) this.nearMe.button = null;
    });
  }
}

/** Aviso de «Preto de min» sen posición (sen permiso ou sen saber onde): péchase e o foco volve ao botón. */
@Component({
  selector: 'furar-near-notice',
  imports: [CalloutComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (nearMe.failure(); as failure) {
      <furar-callout live="status" dismissible data-near-notice (dismissed)="nearMe.dismiss()">
        @if (failure === 'denied') {
          <p>
            <strong>{{ 'facility.catalog.nearDenied.lead' | transloco }}</strong>
            {{ 'facility.catalog.nearDenied.text' | transloco }}
          </p>
        } @else {
          <p>{{ 'facility.catalog.nearUnavailable' | transloco }}</p>
        }
      </furar-callout>
    }
  `,
})
export class NearNoticeComponent {
  protected readonly nearMe = inject(NearMe);
}
