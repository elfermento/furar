// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { Params } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { weekdayIndex, weekdayNames } from '../../../shared/ui/calendar/local-date';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { RowListDirective } from '../../../shared/ui/row/row-list.directive';
import { RowComponent } from '../../../shared/ui/row/row.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { slotTime } from './reserve';
import { ReserveHours } from './reserve-hours';
import { ReserveStore } from './reserve.store';
import type { SimilarSlotResponse } from './similar-slots.store';
import { SimilarSlotsStore } from './similar-slots.store';

/** Unha fila: cando, onde e a ligazón que a deixa escollida. */
interface SimilarRow {
  readonly key: string;
  readonly when: string;
  readonly where: string;
  readonly link: readonly unknown[];
  readonly params: Params;
}

/**
 * «Ocos parecidos» a unha hora ocupada (ADR-0033): filas do kit co día e a hora e,
 * debaixo, onde é. Pulsar unha déixaa escollida: é unha ligazón á páxina de reservar da súa
 * instalación (esta ou outra) co uso, o día e a hora postos. Só se pide cunha ocupada escollida;
 * cargando, dúas filas de esqueleto; se falla, unha liña; sen ningún, a sección non sae.
 */
@Component({
  selector: 'furar-similar-slots',
  imports: [RowComponent, RowListDirective, StatusStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'booking.reserve.similar.';
    @if (similar.state() === 'loading' || similar.state() === 'error' || rows().length) {
      <h2 class="f-reserve__similar" id="reserve-similar">{{ t + 'title' | transloco }}</h2>
      @if (similar.state() === 'loading') {
        <furar-status-state mode="skeleton" [rows]="2" [description]="t + 'loading' | transloco" />
      } @else if (similar.state() === 'error') {
        <p class="f-reserve__hint" data-similar-error>{{ t + 'error' | transloco }}</p>
      } @else {
        <ul furarRowList aria-labelledby="reserve-similar" data-similar-slots>
          @for (row of rows(); track row.key) {
            <li>
              <furar-row [label]="row.when" [link]="row.link" [queryParams]="row.params" data-similar-slot>{{
                row.where
              }}</furar-row>
            </li>
          }
        </ul>
      }
    }
  `,
})
export class SimilarSlotsComponent {
  private readonly store = inject(ReserveStore);
  private readonly hours = inject(ReserveHours);
  protected readonly similar = inject(SimilarSlotsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  private readonly query = computed(
    () => {
      const slug = this.municipality.slug();
      const facilityId = this.store.facility()?.id;
      const use = this.store.use();
      const busy = this.hours.chosenBusy();
      if (!slug || !facilityId || !use || !busy) return null;
      return {
        slug,
        facilityId,
        useId: use.id,
        start: busy.start,
        slots: this.store.slots(),
        mine: this.store.authenticated(),
      };
    },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
  );

  protected readonly rows = computed<SimilarRow[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    const here = this.store.facility()?.id;
    const slug = this.municipality.slug() ?? '';
    const selection = this.store.selection();
    return this.similar.items().map((slot) => {
      const time = slotTime(slot.startMinute);
      const other = slot.facilityId !== here;
      return {
        key: `${slot.facilityId}|${slot.useId}|${slot.start}`,
        when: this.transloco.translate('booking.reserve.similar.when', {
          day: dayName(slot, lang),
          range: `${time}–${inMadrid(slot.end).time}`,
        }),
        where: !other
          ? this.transloco.translate('booking.reserve.similar.here')
          : slot.facilityAddress
            ? this.transloco.translate('booking.reserve.similar.other', {
                facility: slot.facilityName,
                address: slot.facilityAddress,
              })
            : slot.facilityName,
        link: ['/', slug, 'instalacions', slot.facilityId, 'reservar'],
        // Na mesma instalación, o cambio en curso (`?cambiar=`) segue; noutra non se pode cambiar.
        params: {
          uso: slot.useId,
          data: slot.date,
          hora: time,
          ...(selection.slots > 1 ? { franxas: selection.slots } : {}),
          ...(!other && selection.change ? { cambiar: selection.change } : {}),
        },
      };
    });
  });

  constructor() {
    effect(() => {
      const query = this.query();
      untracked(() => (query ? void this.similar.load(query) : this.similar.clear()));
    });
  }
}

/** «Mércores 11»: o día da semana e o número. */
function dayName(slot: SimilarSlotResponse, lang: string): string {
  const weekday = weekdayNames(lang)[weekdayIndex(slot.date)].long;
  return `${weekday.charAt(0).toLocaleUpperCase(lang)}${weekday.slice(1)} ${Number(slot.date.slice(8))}`;
}
