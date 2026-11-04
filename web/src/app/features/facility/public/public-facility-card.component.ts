// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { weekdayIndex } from '../../../shared/ui/calendar/local-date';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatMinutes } from '../../../shared/ui/time-picker/time-format';
import { weekdayName } from '../schedule/week-format';
import type { DayGroup } from './opening';
import { groupDays, openState } from './opening';
import type { PublicFacility } from './public-facility.store';

/**
 * Tarxeta fixa da ficha pública (maqueta aprobada `06-ficha-publica`): enderezo, horario e
 * contacto, cada un coa súa icona. O horario abre cun bloque «Aberto agora · Pecha ás …» (ou
 * pechado e cando abre), e despois os días: hoxe en negriña e o resto xuntando os seguidos co
 * mesmo horario, un tramo por liña. Teléfono e correo son ligazóns na cor principal. Ao pé vai o
 * que proxecte a páxina («Reservar», só se a instalación se reserva por internet).
 */
@Component({
  selector: 'furar-public-facility-card',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-public-card' },
  template: `
    @let t = 'facility.public.';
    @let f = facility();
    <div class="f-public-card__item">
      <furar-icon name="location" [size]="24" />
      <div>
        <h2 class="f-public-card__title">{{ t + 'address' | transloco }}</h2>
        <p class="text-ink-soft" data-public-address>{{ address() }}</p>
      </div>
    </div>
    <div class="f-public-card__item">
      <furar-icon name="schedule" [size]="24" />
      <div class="min-w-0 flex-1">
        <h2 class="f-public-card__title">{{ t + 'schedule' | transloco }}</h2>
        <p class="f-public-card__now" [attr.data-open]="state().open ? '' : null" data-public-now>
          <span class="f-public-card__dot" aria-hidden="true"></span>
          <span>
            <b class="block">{{ (state().open ? t + 'openNow' : t + 'closedNow') | transloco }}</b>
            @if (nowDetail(); as detail) {
              &ngsp;<span class="block">{{ detail }}</span>
            }
          </span>
        </p>
        <dl class="f-public-card__week" data-public-week>
          @for (group of groups(); track group.from) {
            <dt [class.f-public-card__today]="group.from === 0">{{ label(group) }}</dt>
            <dd [class.f-public-card__today]="group.from === 0">
              @for (slot of group.slots; track $index) {
                <span>{{ time(slot.startMinute) }} – {{ time(slot.endMinute) }}</span>
              } @empty {
                <span>{{ t + 'closedDay' | transloco }}</span>
              }
            </dd>
          }
        </dl>
      </div>
    </div>
    @if (f.phone || f.email) {
      <div class="f-public-card__item">
        <furar-icon name="phone" [size]="24" />
        <div class="min-w-0">
          <h2 class="f-public-card__title">{{ t + 'contact' | transloco }}</h2>
          @if (f.phone) {
            <a class="f-public-card__link" [href]="'tel:' + phoneHref()">{{ f.phone }}</a>
          }
          @if (f.email) {
            <a class="f-public-card__link" [href]="'mailto:' + f.email">{{ f.email }}</a>
          }
        </div>
      </div>
    }
    <ng-content />
  `,
})
export class PublicFacilityCardComponent {
  readonly facility = input.required<PublicFacility>();
  /** Agora (para os tests); por defecto, ao cargar a páxina. */
  readonly now = input<Date>(new Date());

  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly address = computed(() => {
    const { street, streetNumber, postalCode, locality } = this.facility().address;
    return [[street, streetNumber].filter(Boolean).join(', '), `${postalCode} ${locality}`].join(' · ');
  });
  protected readonly phoneHref = computed(() => (this.facility().phone ?? '').replace(/[^\d+]/g, ''));
  protected readonly state = computed(() => {
    return openState(this.facility().openingHours, this.now());
  });
  protected readonly groups = computed(() => groupDays(this.facility().openingHours));
  protected readonly nowDetail = computed(() => {
    this.langChanges();
    const state = this.state();
    const t = (key: string, params: Record<string, unknown>) =>
      this.transloco.translate(`facility.public.${key}`, params);
    if (state.open) return t('closesAt', { time: this.time(state.closesAt) });
    if (!state.next) return '';
    const time = this.time(state.next.minute);
    if (state.next.day === 0) return t('opensToday', { time });
    if (state.next.day === 1) return t('opensTomorrow', { time });
    return t('opensOn', { day: this.dayName(state.next.day).toLocaleLowerCase(this.language.lang()), time });
  });

  protected time(minute: number): string {
    return formatMinutes(minute, this.language.lang());
  }

  /** «Hoxe, luns», «Martes a venres», «Sábado e domingo», «Xoves». */
  protected label(group: DayGroup): string {
    this.langChanges();
    const lang = this.language.lang();
    if (group.from === 0) {
      return this.transloco.translate('facility.public.today', { day: this.dayName(0).toLocaleLowerCase(lang) });
    }
    const from = this.dayName(group.from);
    if (group.from === group.to) return from;
    const to = this.dayName(group.to).toLocaleLowerCase(lang);
    const key = group.to - group.from === 1 ? 'facility.public.twoDays' : 'facility.public.dayRange';
    return this.transloco.translate(key, { from, to });
  }

  private dayName(index: number): string {
    const day = this.facility().openingHours[index];
    return day ? weekdayName(weekdayIndex(day.date), this.language.lang()) : '';
  }
}
