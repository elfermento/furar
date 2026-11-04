// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { ActivityOutcome, ActivityScope, ActorView, TargetView } from './activity';

/** Unha fila xa preparada pola páxina. */
export interface ActivityRow {
  readonly id: string;
  /** Instante ISO (para `<time datetime>`). */
  readonly at: string;
  /** «27/10/2026, 18:05» en Europe/Madrid. */
  readonly when: string;
  /** Clave do texto da acción; `null` se o código é descoñecido. */
  readonly actionKey: string | null;
  readonly action: string;
  readonly actor: ActorView;
  readonly target: TargetView;
  readonly municipality: string | null;
  readonly outcome: ActivityOutcome;
  readonly tone: TagTone;
}

/**
 * Táboa do rexistro (D4-16): `caption`, cabeceiras `th scope="col"` e a data como cabeceira de
 * fila. Por debaixo de 40rem cada fila apílase coma unha tarxeta e cada dato leva o seu rótulo
 * (`data-label`), sen scroll horizontal a 320 px; os roles ARIA explícitos manteñen a táboa para
 * os lectores de pantalla aínda que o CSS lle cambie o `display`. As filas levan `tabindex="-1"`: «Cargar máis»
 * pon nelas o foco. As accións da plataforma márcanse na vista do concello.
 */
@Component({
  selector: 'furar-activity-table',
  imports: [RouterLink, TranslocoPipe, TagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'activity.';
    <table class="f-activity__table" role="table" data-activity-table>
      <caption class="sr-only">
        {{
          caption()
        }}
      </caption>
      <thead role="rowgroup">
        <tr role="row">
          <th scope="col" role="columnheader">{{ t + 'col.date' | transloco }}</th>
          <th scope="col" role="columnheader">{{ t + 'col.person' | transloco }}</th>
          <th scope="col" role="columnheader">{{ t + 'col.action' | transloco }}</th>
          <th scope="col" role="columnheader">{{ t + 'col.target' | transloco }}</th>
          @if (scope() === 'platform') {
            <th scope="col" role="columnheader">{{ t + 'col.municipality' | transloco }}</th>
          }
          <th scope="col" role="columnheader">{{ t + 'col.outcome' | transloco }}</th>
        </tr>
      </thead>
      <tbody role="rowgroup">
        @for (row of rows(); track row.id; let i = $index) {
          <tr role="row" tabindex="-1" [attr.data-row]="i" [attr.data-outcome]="row.outcome">
            <th scope="row" role="rowheader" [attr.data-label]="t + 'col.date' | transloco">
              <time [attr.datetime]="row.at">{{ row.when }}</time>
            </th>
            <td role="cell" data-cell="person" [attr.data-label]="t + 'col.person' | transloco">
              <span class="f-activity__who">
                <span class="f-activity__name">{{ row.actor.name ?? (row.actor.nameKey | transloco) }}</span>
                @if (row.actor.email) {
                  {{ ' ' }}<span class="f-activity__soft">{{ row.actor.email }}</span>
                }
                @for (role of row.actor.roleKeys; track role) {
                  {{ ' ' }}<span class="f-activity__soft" data-role>{{ role | transloco }}</span>
                }
                @if (row.actor.platform && scope() === 'municipality') {
                  {{ ' '
                  }}<furar-tag tone="info" look="outline" data-platform>{{
                    t + 'actor.platformTag' | transloco
                  }}</furar-tag>
                }
              </span>
            </td>
            <td role="cell" data-cell="action" [attr.data-label]="t + 'col.action' | transloco">
              {{
                row.actionKey ? (row.actionKey | transloco) : (t + 'unknownAction' | transloco: { code: row.action })
              }}
            </td>
            <td role="cell" data-cell="target" [attr.data-label]="t + 'col.target' | transloco">
              <span class="f-activity__who">
                @if (row.target.link && !row.target.text) {
                  <a [routerLink]="row.target.link" [queryParams]="row.target.query" data-target-link>{{
                    row.target.kindKey | transloco
                  }}</a>
                } @else {
                  <span class="f-activity__soft">{{ row.target.kindKey | transloco }}</span>
                }
                @if (row.target.link && row.target.text) {
                  {{ ' ' }}<a [routerLink]="row.target.link" [queryParams]="row.target.query">{{ row.target.text }}</a>
                } @else if (row.target.text) {
                  {{ ' ' }}<span class="f-activity__target">{{ row.target.text }}</span>
                }
              </span>
            </td>
            @if (scope() === 'platform') {
              <td role="cell" data-cell="municipality" [attr.data-label]="t + 'col.municipality' | transloco">
                {{ row.municipality ?? (t + 'target.none' | transloco) }}
              </td>
            }
            <td role="cell" data-cell="outcome" [attr.data-label]="t + 'col.outcome' | transloco">
              <furar-tag [tone]="row.tone">{{ t + 'outcome.' + row.outcome | transloco }}</furar-tag>
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class ActivityTableComponent {
  readonly rows = input.required<readonly ActivityRow[]>();
  readonly scope = input.required<ActivityScope>();
  readonly caption = input.required<string>();
}
