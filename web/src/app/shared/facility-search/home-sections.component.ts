// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { UpcomingEventsComponent } from '../events/upcoming-events.component';
import type { EventsMunicipality } from '../events/upcoming-events.component';
import { OpenMatchesComponent } from '../open-matches/open-matches-section.component';
import type { IsoDate } from '../ui/calendar/local-date';
import type { ListedKind } from './search-filters-dialog.component';

/**
 * Despois das instalacións dunha portada, nesta orde: «Eventos e actividades» (ADR-0038) e «Buscan
 * xente para xogar» (ADR-0040), cada un só se hai algo e só se o «Tipo» o deixa. Cun filtro, só os
 * do concello escollido (`only`), das instalacións que pasan (`facilityIds`) e dese día (`day`), co número no título (`counted`).
 * As partidas cárganse á parte, fóra da carga da portada; na común, tamén os eventos.
 */
@Component({
  selector: 'furar-home-sections',
  imports: [OpenMatchesComponent, UpcomingEventsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (show().shows('events')) {
      @if (slug(); as slug) {
        <furar-upcoming-events
          [slug]="slug"
          [only]="only()"
          [facilityIds]="facilityIds()"
          [day]="day()"
          [counted]="counted()"
          (found)="show().found('events', $event)"
        />
      } @else if (municipalities().length) {
        @defer (on idle) {
          <furar-upcoming-events
            [municipalities]="municipalities()"
            [only]="only()"
            [facilityIds]="facilityIds()"
            [day]="day()"
            [counted]="counted()"
            (found)="show().found('events', $event)"
          />
        }
      }
    }
    @if (show().shows('matches')) {
      @defer (on idle) {
        <furar-open-matches
          [slug]="slug()"
          [only]="only()"
          [only]="only()"
          [facilityIds]="facilityIds()"
          [day]="day()"
          [counted]="counted()"
          (found)="show().found('matches', $event)"
        />
      }
    }
  `,
})
export class HomeSectionsComponent {
  /** O concello; sen el, a portada común (os eventos de `municipalities`). */
  readonly slug = input<string | null>(null);
  readonly municipalities = input<readonly EventsMunicipality[]>([]);
  /** O «Tipo» da portada: que seccións van e, ao cargar, se teñen algo. */
  readonly show = input.required<ListedKind>();
  /** Só os do concello escollido no buscador (portada común). */
  readonly only = input<string | null>(null);
  readonly facilityIds = input<ReadonlySet<string> | null>(null);
  readonly day = input<IsoDate | null>(null);
  readonly counted = input(false);
}
