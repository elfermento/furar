// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { bookingChip } from '../mine/my-bookings';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import { peekGroups } from './bookings-calendar';
import type { PeekState } from './bookings-calendar.store';

/** Ancho da capa en px (20 rem) e aire cos bordos da xanela. */
const WIDTH = 320;
const GAP = 8;

/**
 * Vista rápida dun día (mes) ou dunha hora (semana de todas), maqueta 27: o día e «Pechar»; as
 * reservas por hora de inicio (cada hora na súa liña, sobre os nomes, con liñas finas entre
 * horas), ata catro; cada fila (persoa, instalación se son todas, uso e a etiqueta do estado) abre
 * a tarxeta da reserva. Debaixo, «+N · Ver o día» ou «Ver o día» leva á vista do día. Vai fixa
 * debaixo do botón que a abriu (ou enriba, se non cabe); péchana Esc, picar fóra e desprazar (quen
 * a usa).
 */
@Component({
  selector: 'furar-calendar-peek',
  imports: [IconComponent, StatusStateComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-bcal__peek',
    role: 'dialog',
    tabindex: '-1',
    'data-peek': '',
    '[attr.aria-label]': 'heading()',
    '[style.left.px]': 'position().left',
    '[style.top.px]': 'position().top',
  },
  template: `
    @let t = 'booking.calendar.peek.';
    <div class="f-bcal__peek-head">
      <span>{{ heading() }}</span>
      <button
        type="button"
        class="f-bcal__arr"
        data-peek-close
        [attr.aria-label]="t + 'close' | transloco"
        (click)="closed.emit()"
      >
        <furar-icon name="close" />
      </button>
    </div>
    @let current = peek();
    @if (current.state === 'ready') {
      @if (current.items.length) {
        @for (group of view().groups; track group.time) {
          <div class="f-bcal__peek-time">{{ group.time }}</div>
          @for (b of group.items; track b.id) {
            <button type="button" class="f-bcal__peek-row" [attr.data-peek-open]="b.id" (click)="opened.emit(b)">
              <span>
                <b>{{ b.holderName ?? ('booking.manage.anonymized' | transloco) }}</b
                >{{ ' ' }} <span class="f-bcal__peek-meta">{{ meta(b) }}</span> </span
              >{{ ' ' }}
              @if (chip(b); as c) {
                <furar-tag [tone]="c.tone" [look]="c.look">{{ 'booking.mine.status.' + c.key | transloco }}</furar-tag>
              }
            </button>
          }
        }
      } @else {
        <p class="f-bcal__peek-empty">{{ t + 'empty' | transloco }}</p>
      }
      <button type="button" class="f-bcal__peek-day" data-peek-day (click)="day.emit(current.date)">
        @if (view().more; as more) {
          {{ t + 'more' | transloco: { count: more } }}
        } @else {
          {{ t + 'day' | transloco }}
        }
      </button>
    } @else if (current.state === 'error') {
      <p class="f-bcal__peek-empty">{{ t + 'error' | transloco }}</p>
    } @else {
      <furar-status-state mode="skeleton" [rows]="2" [description]="t + 'loading' | transloco" />
    }
  `,
})
export class CalendarPeekComponent {
  readonly peek = input.required<PeekState>();
  /** O botón que a abriu (onde se coloca). */
  readonly anchor = input.required<HTMLElement>();
  /** «luns 26» (e a hora, na semana). */
  readonly heading = input.required<string>();
  /** Con todas as instalacións, cada fila di tamén a instalación. */
  readonly allFacilities = input(true);
  readonly opened = output<ManagedBookingResponse>();
  readonly day = output<IsoDate>();
  readonly closed = output();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly view = computed(() => peekGroups(this.peek().items));
  protected readonly position = signal({ left: 0, top: 0 });

  constructor() {
    // Debaixo do botón; se non cabe, enriba. Sempre dentro da xanela.
    afterRenderEffect(() => {
      this.peek();
      const rect = this.anchor().getBoundingClientRect();
      const view = this.host.ownerDocument.defaultView;
      const width = view?.innerWidth ?? WIDTH;
      const height = view?.innerHeight ?? 0;
      const own = this.host.offsetHeight;
      const left = Math.max(GAP, Math.min(rect.left, width - WIDTH - GAP));
      let top = rect.bottom + 6;
      if (top + own > height - GAP) top = Math.max(GAP, rect.top - own - 6);
      const current = this.position();
      if (current.left !== left || current.top !== top) this.position.set({ left, top });
    });
  }

  protected meta(b: ManagedBookingResponse): string {
    return (this.allFacilities() ? [b.facilityName, b.useName, b.partName] : [b.useName, b.partName])
      .filter(Boolean)
      .join(' · ');
  }

  protected chip(b: ManagedBookingResponse) {
    return bookingChip(b);
  }
}
