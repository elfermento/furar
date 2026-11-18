// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, output, viewChild } from '@angular/core';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { ColumnFilterComponent } from '../../../shared/ui/column-filter/column-filter.component';
import type { ActivityPeriod } from './activity-period.component';

import { ActivityPeriodComponent } from './activity-period.component';
import type { ActivityColumnView } from './activity-view';

/** O período escollido, hoxe e o primeiro día que aínda se ve. */
export interface ActivityRange {
  readonly from: IsoDate | null;
  readonly to: IsoDate | null;
  readonly today: IsoDate;
  readonly min: IsoDate | null;
}

/**
 * O filtro dunha columna do rexistro (`furar-column-filter`) na cabeceira da táboa ou, no móbil,
 * coma pílula. A columna Data leva ademais «Desde» e «Ata»; escoller un atallo ou aplicar un
 * período pecha o panel (as outras columnas suman valores e o panel queda aberto).
 */
@Component({
  selector: 'furar-activity-column',
  imports: [ActivityPeriodComponent, ColumnFilterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-activity__column', '[attr.data-filter]': 'column().key' },
  template: `
    <furar-column-filter
      #filter
      [label]="column().label"
      [options]="column().options"
      [selected]="column().selected"
      [marked]="column().marked"
      [searchable]="column().searchable"
      [variant]="variant()"
      [doneLabel]="doneLabel()"
      (toggled)="onToggled($event)"
      (cleared)="cleared.emit()"
    >
      @if (column().key === 'date') {
        <furar-activity-period
          [from]="range().from"
          [to]="range().to"
          [today]="range().today"
          [min]="range().min"
          (changed)="onPeriod($event)"
        />
      }
    </furar-column-filter>
  `,
})
export class ActivityColumnComponent {
  private readonly filter = viewChild.required<ColumnFilterComponent>('filter');

  readonly column = input.required<ActivityColumnView>();
  readonly variant = input<'header' | 'chip'>('header');
  readonly doneLabel = input('');
  /** O período escollido, hoxe e o primeiro día visible (só a columna Data). */
  readonly range = input.required<ActivityRange>();

  readonly toggled = output<string>();
  readonly cleared = output();
  readonly period = output<ActivityPeriod>();

  protected onToggled(value: string): void {
    this.toggled.emit(value);
    if (this.column().key === 'date') this.filter().close();
  }

  protected onPeriod(period: ActivityPeriod): void {
    this.period.emit(period);
    this.filter().close();
  }
}
