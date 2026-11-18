// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { DialogService } from '../ui/dialog/dialog.service';
import { focusPageHeadingIfLost } from '../ui/focus-page-heading';
import type { StatusCardLine } from '../ui/status-card/status-card.component';
import { StatusCardComponent } from '../ui/status-card/status-card.component';
import { TagComponent } from '../ui/tag/tag.component';
import { ToastService } from '../ui/toast/toast.service';
import type { MyOpenMatchDetailData, MyOpenMatchDetailResult } from './my-open-match-detail.component';
import { MyOpenMatchDetailComponent } from './my-open-match-detail.component';
import { openMatchTitle, openMatchUseDetail, openMatchWhenParts } from './open-match-format';
import type { MyOpenMatchResponse } from './open-matches.store';
import { OpenMatches } from './open-matches.store';

/** Unha tarxeta: a partida e o que pinta xa calculado. */
interface Card {
  readonly match: MyOpenMatchResponse;
  readonly name: string;
  readonly lines: readonly StatusCardLine[];
  readonly pending: boolean;
}

/**
 * As partidas ás que se uniu ou pediu unirse a persoa, en «As miñas reservas › Próximas»
 * (ADR-0040): a mesma tarxeta ca as reservas (`furar-status-card`), enteira pulsable:
 * o nome, o día e a hora, a instalación, quen organiza e o chip «Apuntada» (verde) ou «Pendente»
 * (ámbar). Ao premela, o detalle con «Ver a partida» e «Saír» (ou «Retirar a solicitude»). As que
 * organiza non saen aquí: van na tarxeta da súa reserva. Sen partidas, non pinta nada; `loaded` di
 * cantas hai.
 */
@Component({
  selector: 'furar-my-open-matches',
  imports: [StatusCardComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (cards().length) {
      <ul class="f-mine__list" data-my-open-matches [attr.aria-label]="'openMatch.mine.list' | transloco">
        @for (card of cards(); track card.match.id) {
          <li>
            <furar-status-card
              [attr.data-my-open-match]="card.match.id"
              [heading]="card.name"
              [lines]="card.lines"
              [cover]="card.match.cover ?? null"
              (opened)="open(card.match)"
            >
              <furar-tag
                cardChips
                look="solid"
                [tone]="card.pending ? 'warn' : 'success'"
                [attr.data-chip]="card.match.me.role"
                >{{ 'openMatch.mine.' + card.match.me.role | transloco }}</furar-tag
              >
            </furar-status-card>
          </li>
        }
      </ul>
    }
  `,
})
export class MyOpenMatchesComponent {
  private readonly store = inject(OpenMatches);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly toasts = inject(ToastService);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);

  readonly slug = input.required<string>();
  /** Cantas hai (despois de cargar ou de saír). */
  readonly loaded = output<number>();

  protected readonly items = signal<MyOpenMatchResponse[]>([]);
  protected readonly cards = computed<Card[]>(() => {
    const lang = this.language.lang();
    return this.items().map((match) => ({
      match,
      name: openMatchTitle(match),
      pending: match.me.role === 'PENDING',
      lines: [
        { id: 'when', icon: 'calendar-month', parts: openMatchWhenParts(match, lang) },
        {
          id: 'place',
          soft: true,
          parts: [match.facilityName, openMatchUseDetail(match)].filter((part): part is string => !!part),
        },
        {
          id: 'organizer',
          icon: 'user',
          parts: [this.transloco.translate('openMatch.organizer', { name: match.organizerName })],
        },
      ],
    }));
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => void this.load(slug));
    });
  }

  /** O detalle e, se escolleu «Saír», saír (sen preguntar: pódese volver). */
  protected async open(match: MyOpenMatchResponse): Promise<void> {
    const data: MyOpenMatchDetailData = { match, slug: this.slug() };
    const ref = this.dialogs.open<MyOpenMatchDetailResult, MyOpenMatchDetailData, MyOpenMatchDetailComponent>(
      MyOpenMatchDetailComponent,
      { data, sheet: true, injector: this.injector },
    );
    if ((await firstValueFrom(ref.closed, { defaultValue: undefined })) === 'leave') {
      await this.leave(match);
    }
  }

  private async leave(match: MyOpenMatchResponse): Promise<void> {
    try {
      await this.store.leave(this.slug(), match.id);
      this.items.update((items) => items.filter((item) => item.id !== match.id));
      this.loaded.emit(this.items().length);
      this.toasts.show(match.me.role === 'PENDING' ? 'openMatch.page.withdrawnToast' : 'openMatch.page.leftToast');
      focusPageHeadingIfLost(this.injector);
    } catch (error) {
      this.toasts.error('openMatch.mine.leaveError', error);
    }
  }

  private async load(slug: string): Promise<void> {
    try {
      const all = await this.store.listMine(slug);
      this.items.set(all.filter((match) => match.me.role === 'CONFIRMED' || match.me.role === 'PENDING'));
    } catch {
      this.items.set([]);
    }
    this.loaded.emit(this.items().length);
  }
}
