// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ColumnResizeHandleComponent } from '../../../shared/ui/column-resize/column-resize-handle.component';
import { ColumnResizeDirective } from '../../../shared/ui/column-resize/column-resize.directive';
import type { ColumnWidths } from '../../../shared/ui/column-resize/column-widths';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { ActivityScope } from './activity';
import { OUTCOME_TONE } from './activity';
import type { ActivityRange } from './activity-column.component';
import { ActivityColumnComponent } from './activity-column.component';
import { ActivityFiltersState } from './activity-filters.state';
import { ActivityRolesComponent } from './activity-roles.component';
import type { ActivityColumnView, ActivityRow } from './activity-view';

/** Premer unha fila (co evento do clic) ou o seu resumo (sen evento, o teclado). */
export interface ActivityOpen {
  readonly row: ActivityRow;
  readonly event?: Event;
}

/**
 * A táboa do rexistro: cabeceiras co seu filtro (`furar-activity-column`) e o tirador para
 * ensanchar a columna (`furar-column-resize`; a Acción colle o que sobra) e as filas: o día, o
 * correo da persoa, os roles en chip, o resumo (un botón que abre o detalle), onde e o resultado.
 * Os filtros van ao estado da páxina (`ActivityFiltersState`).
 */
@Component({
  selector: 'furar-activity-table',
  imports: [
    ActivityColumnComponent,
    ActivityRolesComponent,
    ColumnResizeDirective,
    ColumnResizeHandleComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-activity__table-host' },
  template: `
    @let t = 'activity.';
    <table
      class="f-activity__table"
      role="table"
      data-activity-table
      [furarColumnResize]="widths()"
      [attr.data-scope]="scope()"
    >
      <caption class="sr-only">
        {{
          t + 'caption' | transloco
        }}
      </caption>
      <colgroup>
        @for (column of columns(); track column.key) {
          <col [attr.data-col]="column.key" [style.width.px]="widths().width(column.key)" />
        }
      </colgroup>
      <thead role="rowgroup">
        <tr role="row">
          @for (column of columns(); track column.key) {
            <th scope="col" role="columnheader" [attr.data-col]="column.key">
              @if (column.filter) {
                <furar-activity-column
                  [column]="column"
                  [range]="range()"
                  (toggled)="f.toggle(column.key, $event)"
                  (cleared)="f.clear(column.key)"
                  (period)="f.period($event)"
                />
              } @else {
                <span class="f-activity__th">{{ column.label }}</span>
              }
              @if (widths().width(column.key) !== null) {
                <furar-column-resize [widths]="widths()" [column]="column.key" [label]="column.label" />
              }
            </th>
          }
        </tr>
      </thead>
      <tbody role="rowgroup">
        @for (row of rows(); track row.id) {
          <tr
            role="row"
            class="f-activity__row"
            [attr.data-row]="row.id"
            [attr.data-outcome]="row.outcome"
            [attr.data-count]="row.count > 1 ? row.count : null"
            (click)="opened.emit({ row, event: $event })"
          >
            <td role="cell" data-cell="date">
              <time [attr.datetime]="row.at">{{ row.when }}</time>
            </td>
            <td role="cell" data-cell="person">{{ row.person }}</td>
            <td role="cell" data-cell="role">
              <furar-activity-roles [roles]="row.roles" />
            </td>
            <th scope="row" role="rowheader" data-cell="action">
              <button
                type="button"
                class="f-activity__open"
                aria-haspopup="dialog"
                [attr.data-open]="row.id"
                (click)="opened.emit({ row })"
              >
                <span class="f-activity__summary">{{ row.summary }}</span>
                @if (row.count > 1) {
                  <span class="f-activity__count"> · {{ t + 'changesCount' | transloco: { count: row.count } }}</span>
                }
              </button>
            </th>
            <td role="cell" data-cell="place">
              @if (row.place) {
                {{ row.place }}
              } @else {
                <span class="f-activity__soft" aria-hidden="true">—</span>
              }
            </td>
            <td role="cell" data-cell="outcome">
              <furar-tag [tone]="tone[row.outcome]">{{ t + 'outcome.' + row.outcome | transloco }}</furar-tag>
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class ActivityTableComponent {
  protected readonly f = inject(ActivityFiltersState);
  protected readonly tone = OUTCOME_TONE;

  readonly columns = input.required<readonly ActivityColumnView[]>();
  readonly rows = input.required<readonly ActivityRow[]>();
  readonly range = input.required<ActivityRange>();
  readonly widths = input.required<ColumnWidths>();
  readonly scope = input.required<ActivityScope>();

  readonly opened = output<ActivityOpen>();
}
