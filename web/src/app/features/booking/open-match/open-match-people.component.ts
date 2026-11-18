// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { MyOpenMatchResponse } from '../../../shared/open-matches/open-matches.store';
import { TagComponent } from '../../../shared/ui/tag/tag.component';

/**
 * Quen vai a unha partida (ADR-0040, punto 9): só o ve quen está dentro (organiza ou confirmada).
 * Primeiro quen organiza, co chip «Organiza» e «+ N» se vén con máis xente («Xa somos»); despois as
 * persoas que se uniron, coa propia marcada «Ti». O número do título é o total.
 */
@Component({
  selector: 'furar-open-match-people',
  imports: [TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'openMatch.page.';
    @if (people(); as p) {
      <section class="f-public__section" aria-labelledby="match-people" data-match-people>
        <h2 id="match-people">
          {{ t + 'people' | transloco }} <span class="f-match-count">{{ p.total }}</span>
        </h2>
        <ul class="f-match-people">
          <li data-person="organizer">
            <span>
              {{ p.organizer }}
              @if (p.more > 0) {
                <span class="text-ink-soft"> {{ t + 'more' | transloco: { count: p.more } }}</span>
              }
            </span>
            <furar-tag look="soft">{{ t + 'organizerChip' | transloco }}</furar-tag>
          </li>
          @for (player of p.players; track player.id) {
            <li [attr.data-person]="player.id">
              <span>{{ player.name }}</span>
              @if (player.you) {
                <furar-tag look="soft">{{ t + 'you' | transloco }}</furar-tag>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class OpenMatchPeopleComponent {
  /** A partida vista pola persoa con sesión (sen ela, `null`: non se ve ningún nome). */
  readonly match = input<MyOpenMatchResponse | null>(null);

  protected readonly people = computed(() => {
    const match = this.match();
    if (!match || (match.me.role !== 'ORGANIZER' && match.me.role !== 'CONFIRMED')) return null;
    return {
      total: match.total,
      organizer: match.organizerName,
      more: match.knownCount - 1,
      players: match.players.map((player) => ({ ...player, you: player.id === match.me.playerId })),
    };
  });
}
