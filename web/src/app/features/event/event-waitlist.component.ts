// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import type { WaitRow } from './event-people';

/**
 * A lista de espera dun evento (pestana «Persoas»), por orde de chegada: Posto, Persoa e Estado
 * («Avisada · só ela ata as 18:42» mentres dura a súa hora de exclusividade; «Avisada» despois).
 * No móbil, unha tarxeta por persoa. Sen ninguén esperando, non sae.
 */
@Component({
  selector: 'furar-event-waitlist',
  imports: [TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'event.people.';
    @if (rows().length) {
      <section class="f-event-people__section" data-event-waitlist [attr.aria-labelledby]="'event-waits-' + eventId()">
        <h3 class="f-event-people__title" [id]="'event-waits-' + eventId()">
          {{ t + 'waitlist' | transloco }} <span class="f-event-people__count">{{ rows().length }}</span>
        </h3>
        @if (desktop()) {
          <div class="f-card f-events__card">
            <table class="f-events__table f-event-people__table" data-waits-table>
              <caption class="sr-only">
                {{
                  t + 'waitlist' | transloco
                }}
              </caption>
              <thead>
                <tr>
                  <th scope="col" class="f-event-people__position">{{ t + 'col.position' | transloco }}</th>
                  <th scope="col">{{ t + 'col.person' | transloco }}</th>
                  <th scope="col">{{ t + 'col.status' | transloco }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track row.id) {
                  <tr [attr.data-wait]="row.id">
                    <td class="tabular-nums">{{ row.position }}</td>
                    <th scope="row">{{ row.name }}</th>
                    <td>
                      @if (row.chip) {
                        <furar-tag tone="warn" data-wait-chip>{{ row.chip }}</furar-tag>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <ol class="f-card f-event-people__cards" data-waits-cards>
            @for (row of rows(); track row.id) {
              <li class="f-event-people__card" [attr.data-wait]="row.id">
                <b>{{ row.position }}. {{ row.name }}</b>
                @if (row.chip) {
                  <furar-tag tone="warn" data-wait-chip>{{ row.chip }}</furar-tag>
                }
              </li>
            }
          </ol>
        }
      </section>
    }
  `,
})
export class EventWaitlistComponent {
  readonly rows = input.required<readonly WaitRow[]>();
  readonly desktop = input(true, { transform: booleanAttribute });
  readonly eventId = input.required<string>();
}
