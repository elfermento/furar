// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { ButtonDirective } from '../ui/button/button.directive';
import { inMadrid, madridToday } from '../ui/calendar/madrid-time';
import { DisclosureComponent } from '../ui/disclosure/disclosure.component';
import type { IconName } from '../ui/icon/icons';
import type { StatusBandTone } from '../ui/status-band/status-band.component';
import { StatusBandComponent } from '../ui/status-band/status-band.component';
import { TagComponent } from '../ui/tag/tag.component';
import { isScheduled, longDay, ordinal } from './event-format';
import { registrationChips } from './my-event-chips';
import type { MyEventRegistrationResponse } from './public-events.store';

/** O estado dunha inscrición na banda. */
export type BandState = 'joined' | 'pending' | 'rejected' | 'waiting' | 'covered' | 'offer';

/** O estado que pinta a banda; sen inscrición, ningún. */
export function bandState(item: Pick<MyEventRegistrationResponse, 'status' | 'seatCovered'>): BandState | null {
  switch (item.status) {
    case 'CONFIRMED':
      return 'joined';
    case 'PENDING':
      return 'pending';
    case 'REJECTED':
      return 'rejected';
    case 'WAITING':
      return item.seatCovered ? 'covered' : 'waiting';
    case 'NOTIFIED':
      return 'offer';
    default:
      return null;
  }
}

const LOOK: Record<BandState, { readonly tone: StatusBandTone; readonly icon: IconName }> = {
  joined: { tone: 'success', icon: 'success' },
  pending: { tone: 'warn', icon: 'hourglass' },
  rejected: { tone: 'info', icon: 'info' },
  waiting: { tone: 'info', icon: 'hourglass' },
  covered: { tone: 'info', icon: 'info' },
  offer: { tone: 'warn', icon: 'schedule' },
};

/**
 * A inscrición dunha persoa nun evento ou nunha actividade (ADR-0041), na banda de estado
 * (`furar-status-band`): na ficha, enriba da tarxeta de datos, e no detalle de «As miñas reservas».
 *
 * - Con praza: «Praza confirmada», a persoa a cargo, o chip do pago (nun evento de pago),
 *   «Darme de baixa» e, nunha actividade, as próximas sesións da API pregadas.
 * - Pedindo praza («Con aprobación») ou rexeitada: a frase e, pedindo, «Retirar a solicitude».
 * - Na lista de espera: «Estás na lista de espera · 3.ª» (ou, se outra persoa cubriu a praza da que
 *   se avisara, «A praza xa se cubriu» co posto), «Saír da lista» discreto e «Como funciona».
 * - Avisada: «Hai unha praza libre para ti», ata que hora só o sabe ela (pasada esa hora, que xa se
 *   avisou a máis persoas e pode confirmala mentres siga libre), «Confirmar a praza», «Non a quero»
 *   discreto e «Como funciona».
 *
 * As accións só avisan (`leave`, `confirm`): quen a usa fai a petición.
 */
@Component({
  selector: 'furar-registration-band',
  imports: [ButtonDirective, DisclosureComponent, StatusBandComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'event.band.';
    @if (state(); as s) {
      <furar-status-band
        [tone]="look().tone"
        [icon]="look().icon"
        [heading]="heading()"
        [attr.data-band]="s"
        [attr.data-dependent]="item().dependent?.id ?? null"
      >
        @if (item().dependent; as d) {
          <p data-band-person>{{ d.name }}</p>
        }
        @if (line()) {
          <p data-band-line>{{ line() }}</p>
        }
        @if (s === 'joined' && chips().payment; as p) {
          <p class="f-status-band__chips">
            <furar-tag look="solid" [tone]="p.tone" [attr.data-payment]="p.key">{{
              'event.payment.' + p.key | transloco
            }}</furar-tag>
          </p>
        }
        <ng-container ngProjectAs="[bandActions]">
          @if (s === 'offer') {
            <button type="button" furarButton="primary" data-event-confirm [busy]="busy()" (click)="confirm.emit()">
              {{ t + 'confirm' | transloco }}
            </button>
            <button type="button" furarButton="quiet" data-event-leave [disabled]="busy()" (click)="leave.emit()">
              {{ t + 'decline' | transloco }}
            </button>
          } @else if (s === 'joined') {
            <button
              type="button"
              furarButton="danger"
              data-event-leave
              [busy]="busy()"
              [attr.aria-label]="
                item().dependent ? (t + 'leaveFor' | transloco: { name: item().dependent!.name }) : null
              "
              (click)="leave.emit()"
            >
              {{ (item().dependent ? t + 'leaveOther' : t + 'leave') | transloco }}
            </button>
          } @else if (s === 'pending') {
            <button type="button" furarButton="danger" data-event-leave [busy]="busy()" (click)="leave.emit()">
              {{ t + 'withdraw' | transloco }}
            </button>
          } @else if (s !== 'rejected') {
            <button type="button" furarButton="quiet" data-event-leave [disabled]="busy()" (click)="leave.emit()">
              {{ t + 'leaveList' | transloco }}
            </button>
          }
        </ng-container>
        <ng-container ngProjectAs="[bandMore]">
          @if (s === 'joined') {
            @if (sessions().length) {
              <furar-disclosure data-band-sessions [heading]="t + 'sessions' | transloco">
                <ul class="f-status-band__list">
                  @for (day of sessions(); track day) {
                    <li>{{ day }}</li>
                  }
                </ul>
              </furar-disclosure>
            }
          } @else if (s === 'offer') {
            <furar-disclosure data-band-how [heading]="t + 'how' | transloco">
              <p>{{ t + 'howOffer' | transloco }}</p>
            </furar-disclosure>
          } @else if (s === 'waiting' || s === 'covered') {
            <furar-disclosure data-band-how [heading]="t + 'how' | transloco">
              <p>{{ t + 'howList' | transloco }}</p>
            </furar-disclosure>
          }
        </ng-container>
      </furar-status-band>
    }
  `,
})
export class RegistrationBandComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly item = input.required<MyEventRegistrationResponse>();
  /** Mentres se garda unha acción. */
  readonly busy = input(false);
  readonly leave = output();
  readonly confirm = output();

  /** Agora, para saber se xa pasou a hora só para ela; cámbiase ao chegar esa hora. */
  private readonly now = signal(Date.now());

  protected readonly state = computed(() => bandState(this.item()));
  protected readonly look = computed(() => LOOK[this.state() ?? 'rejected']);
  protected readonly chips = computed(() => registrationChips(this.item()));

  protected readonly heading = computed(() => {
    this.language.lang();
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const position = this.item().position;
    switch (this.state()) {
      case 'joined':
        return t('event.band.joined');
      case 'pending':
        return t('event.public.pendingTitle');
      case 'rejected':
        return t('event.public.rejectedTitle');
      case 'waiting':
        return position ? t('event.band.waitingAt', { position: ordinal(position) }) : t('event.band.waiting');
      case 'covered':
        return t('event.band.covered');
      case 'offer':
        return t('event.band.offer');
      default:
        return '';
    }
  });

  protected readonly line = computed(() => {
    const lang = this.language.lang();
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    const item = this.item();
    switch (this.state()) {
      case 'pending':
        return t('event.public.pendingText');
      case 'rejected':
        return t('event.public.rejectedText');
      case 'waiting':
        return t('event.band.waitingText');
      case 'covered':
        return item.position ? t('event.band.coveredAt', { position: ordinal(item.position) }) : '';
      case 'offer': {
        const until = item.exclusiveUntil;
        if (!until || new Date(until).getTime() <= this.now()) return t('event.band.offerShared');
        const { date, time } = inMadrid(until);
        return date === madridToday(new Date(this.now()))
          ? t('event.band.offerUntil', { time })
          : t('event.band.offerUntilDay', { day: longDay(date, lang), time });
      }
      default:
        return '';
    }
  });

  /** As próximas sesións dunha actividade con praza (da API): «Luns, 23 de novembro · 17:00». */
  protected readonly sessions = computed(() => {
    const lang = this.language.lang();
    if (!isScheduled(this.item().event)) return [];
    return (this.item().nextSessions ?? []).map((session) => {
      const text = longDay(session.date, lang);
      return `${text.charAt(0).toLocaleUpperCase(lang)}${text.slice(1)} · ${inMadrid(session.startsAt).time}`;
    });
  });

  constructor() {
    let timer: ReturnType<typeof setTimeout> | undefined;
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
    effect(() => {
      clearTimeout(timer);
      const until = this.item().exclusiveUntil;
      if (this.item().status !== 'NOTIFIED' || !until) return;
      const wait = new Date(until).getTime() - Date.now();
      // Ao chegar a hora, a frase pasa a «xa avisamos a máis persoas» (sen recargar).
      if (wait > 0 && wait < 2 ** 31 - 1) timer = setTimeout(() => this.now.set(Date.now()), wait + 500);
    });
  }
}
