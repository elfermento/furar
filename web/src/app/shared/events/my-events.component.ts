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
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { DialogService } from '../ui/dialog/dialog.service';
import { focusPageHeadingIfLost } from '../ui/focus-page-heading';
import { inMadrid } from '../ui/calendar/madrid-time';
import { formatCents } from '../ui/money/money';
import type { StatusCardLine } from '../ui/status-card/status-card.component';
import { StatusCardComponent } from '../ui/status-card/status-card.component';
import { TagComponent } from '../ui/tag/tag.component';
import { ToastService } from '../ui/toast/toast.service';
import { eventWhen, inLang, isScheduled, longDay, scheduleWhen } from './event-format';
import type { MyEventDetailData, MyEventDetailResult } from './my-event-detail.component';
import { MyEventDetailComponent } from './my-event-detail.component';
import type { RegistrationChips } from './my-event-chips';
import { registrationChips } from './my-event-chips';
import type { MyEventRegistrationResponse } from './public-events.store';
import { PublicEvents } from './public-events.store';
import { SELF } from '../dependents/participants';

/** Unha tarxeta: a inscrición e o que pinta xa calculado. */
interface Card {
  /** O evento e para quen (unha conta pode ter varias inscricións no mesmo evento). */
  readonly key: string;
  readonly item: MyEventRegistrationResponse;
  readonly name: string;
  readonly amount: string | null;
  readonly lines: readonly StatusCardLine[];
  readonly chips: RegistrationChips;
}

/**
 * Os eventos e as actividades da conta (a propia persoa e as persoas a cargo), en «As miñas
 * reservas › Próximas» (ADR-0038, ADR-0041): a mesma tarxeta ca as reservas (`furar-status-card`),
 * enteira pulsable e sen botóns: o nome, o prezo arriba á dereita (con praza ou pedíndoa), o día e
 * a hora (nunha actividade, a próxima sesión ou, na lista, o horario; avisada, ata cando é só súa),
 * o lugar con para quen e os chips (inscrición e pago; «Na lista · 3.ª»; «Praza libre para ti»). As
 * avisadas van primeiro. Ao premela, o detalle (`furar-my-event-detail`) coa banda de estado. Sen
 * nada, non pinta nada; `loaded` di cantas hai (a páxina amosa o baleiro só se tampouco hai
 * reservas). Ao desaparecer unha tarxeta, o foco vai ao título da páxina.
 */
@Component({
  selector: 'furar-my-events',
  imports: [StatusCardComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (cards().length) {
      <ul class="f-mine__list" data-my-events [attr.aria-label]="'event.mine.list' | transloco">
        @for (card of cards(); track card.key) {
          <li>
            <furar-status-card
              [attr.data-my-event]="card.item.event.id"
              [attr.data-registration]="card.item.status"
              [attr.data-dependent]="card.item.dependent?.id ?? null"
              [heading]="card.name"
              [amount]="card.amount"
              [lines]="card.lines"
              [cover]="card.item.event.cover"
              (opened)="open(card.item)"
            >
              <furar-tag
                cardChips
                look="solid"
                [tone]="card.chips.status.tone"
                [attr.data-chip]="card.chips.status.status"
                >{{ card.chips.status.key | transloco: card.chips.status.params }}</furar-tag
              >
              @if (card.chips.payment; as p) {
                <furar-tag cardChips look="solid" [tone]="p.tone" [attr.data-payment]="p.key">{{
                  'event.payment.' + p.key | transloco
                }}</furar-tag>
              }
            </furar-status-card>
          </li>
        }
      </ul>
    }
  `,
})
export class MyEventsComponent {
  private readonly store = inject(PublicEvents);
  private readonly language = inject(LanguageService);
  private readonly toasts = inject(ToastService);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  readonly slug = input.required<string>();
  /** Cantas inscricións hai (despois de cargar ou de desapuntarse). */
  readonly loaded = output<number>();

  protected readonly items = signal<MyEventRegistrationResponse[]>([]);
  protected readonly cards = computed<Card[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const items = [...this.items()].sort((a, b) => Number(b.status === 'NOTIFIED') - Number(a.status === 'NOTIFIED'));
    return items.map((item) => {
      const { event } = item;
      const place = event.facility?.name ?? event.place;
      const withSeat = item.status === 'CONFIRMED' || item.status === 'PENDING';
      return {
        key: keyOf(item),
        item,
        name: inLang(event.name, lang),
        amount: withSeat && event.priceCents ? formatCents(event.priceCents) : null,
        lines: [
          whenLine(item, lang, t),
          ...(place || item.dependent
            ? [
                {
                  id: 'place',
                  soft: true,
                  parts: [place, item.dependent?.name].filter((part): part is string => !!part),
                },
              ]
            : []),
        ],
        chips: registrationChips(item),
      };
    });
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => void this.load(slug));
    });
  }

  /** O detalle e, se escolleu «Desapuntarme», desapuntarse (sen preguntar, coma antes). */
  protected async open(item: MyEventRegistrationResponse): Promise<void> {
    const data: MyEventDetailData = { item, slug: this.slug() };
    const ref = this.dialogs.open<MyEventDetailResult, MyEventDetailData, MyEventDetailComponent>(
      MyEventDetailComponent,
      { data, sheet: true, injector: this.injector },
    );
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (result === 'leave') await this.leave(item);
    if (result === 'confirm') await this.confirm(item);
  }

  /** Darse de baixa ou saír da lista (sen preguntar, coma na ficha). */
  private async leave(item: MyEventRegistrationResponse): Promise<void> {
    const waiting = item.status === 'WAITING' || item.status === 'NOTIFIED';
    try {
      await this.store.leave(this.slug(), item.event.id, item.dependent?.id);
      this.items.update((items) => items.filter((other) => keyOf(other) !== keyOf(item)));
      this.loaded.emit(this.items().length);
      this.toasts.show(waiting ? 'event.band.leftListToast' : 'event.public.leftToast');
      focusPageHeadingIfLost(this.injector);
    } catch (error) {
      this.toasts.error('event.public.leaveError', error);
    }
  }

  /** Confirmar a praza avisada: a tarxeta pasa a «Apuntada». Se xa non quedaba, volve cargar. */
  private async confirm(item: MyEventRegistrationResponse): Promise<void> {
    try {
      const saved = await this.store.confirm(this.slug(), item.event.id, item.dependent?.id);
      this.items.update((items) => items.map((other) => (keyOf(other) === keyOf(item) ? saved : other)));
      this.toasts.show('event.public.joinedToast');
    } catch (error) {
      this.toasts.error('event.band.confirmError', error);
      await this.load(this.slug());
    }
  }

  private async load(slug: string): Promise<void> {
    try {
      this.items.set(await this.store.upcomingMine(slug));
    } catch {
      this.items.set([]);
    }
    this.loaded.emit(this.items().length);
  }
}

/** O evento e para quen: unha conta pode ter varias inscricións no mesmo evento. */
function keyOf(item: MyEventRegistrationResponse): string {
  return `${item.event.id}:${item.dependent?.id ?? SELF}`;
}

/**
 * A liña de cando: avisada, ata que hora é só súa; nunha actividade con praza, a próxima sesión
 * («Próxima: mércores, 18 de novembro · 17:00») e, na lista, o horario; nun evento dun día, o día e
 * a hora.
 */
function whenLine(
  item: MyEventRegistrationResponse,
  lang: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): StatusCardLine {
  const { event } = item;
  if (item.status === 'NOTIFIED') {
    const until = item.exclusiveUntil;
    const text =
      until && new Date(until).getTime() > Date.now()
        ? t('event.mine.offerUntil', { time: inMadrid(until).time })
        : t('event.mine.offerShared');
    return { id: 'when', icon: 'schedule', parts: [text] };
  }
  if (isScheduled(event)) {
    const next = item.status === 'CONFIRMED' ? (item.nextSessions?.[0] ?? event.nextSession) : undefined;
    const parts = next
      ? [t('event.mine.next', { day: longDay(next.date, lang) }), inMadrid(next.startsAt).time]
      : scheduleWhen(event.schedule, lang, t).split(' · ');
    return { id: 'when', icon: 'event-repeat', parts };
  }
  return { id: 'when', icon: 'calendar-month', parts: eventWhen(event.startsAt, lang).split(' · ') };
}
