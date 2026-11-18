// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import type { IsoDate } from '../calendar/local-date';
import type { DateFilterPreset } from './date-filter';
import { daysBetween } from './date-filter';
import { DateFilterComponent } from './date-filter.component';
import type { PeriodValue } from './period-filter';
import { PERIOD_PRESETS, periodOf, periodRange, presetRange, samePeriod } from './period-filter';

/** Prefixo dos textos dos atallos. */
const PRESET_KEY = 'ui.periodFilter.';

/** O período por defecto (e o que queda ao quitar o filtro): o mes en curso. */
const DEFAULT_PERIOD: PeriodValue = { period: 'mes', from: null, to: null };

/**
 * Período dun panel de cifras (indicadores do concello e da plataforma) co chip de data de toda a
 * app (`furar-date-filter`): o chip leva o período posto («Esta semana», o mes polo seu nome,
 * «Trimestre», «Ano» ou «Do 26/10 ao 31/10») e o panel, os atallos e o calendario dun rango (ata
 * `maxDays`). Un atallo ou un rango que coincide con el é ese período; outro rango, `datas`. Só
 * avisa ao rematar (aplicar, Esc ou picar fóra) e se cambiou; un rango a medias ou longo de máis
 * volve ao de antes. Quitar o filtro volve ao mes en curso.
 */
@Component({
  selector: 'furar-period-filter',
  imports: [DateFilterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-date-filter
      data-period-filter
      [(from)]="from"
      [(to)]="to"
      [today]="today()"
      [presets]="presets()"
      [format]="format()"
      [maxDays]="maxDays()"
      [label]="label()"
      (settled)="settle()"
    />
  `,
})
export class PeriodFilterComponent {
  readonly value = input.required<PeriodValue>();
  readonly today = input.required<IsoDate>();
  /** O nome do mes en curso («Novembro»), o texto do chip co mes. */
  readonly month = input.required<string>();
  /** Días como moito nun rango libre. */
  readonly maxDays = input<number | null>(null);
  /** Nome do filtro («Período»), diante do texto no nome dos botóns. */
  readonly label = input('');
  readonly changed = output<PeriodValue>();

  private readonly transloco = inject(TranslocoService);
  /** Para que os textos do chip cambien co idioma. */
  private readonly translations = toSignal(this.transloco.selectTranslation());

  /** As datas no calendario: as do período (escollendo, as do borrador). */
  protected readonly from = linkedSignal<IsoDate | null>(() => periodRange(this.value(), this.today()).from);
  protected readonly to = linkedSignal<IsoDate | null>(() => periodRange(this.value(), this.today()).to);

  protected readonly presets = computed<DateFilterPreset[]>(() =>
    PERIOD_PRESETS.map((key) => ({ id: key, label: PRESET_KEY + key, ...presetRange(key, this.today()) })),
  );

  /** O texto do chip: o do atallo (o mes, polo seu nome); un rango, o de sempre. */
  protected readonly format = computed(() => {
    this.translations();
    const today = this.today();
    const month = this.month();
    return (from: IsoDate, to: IsoDate | null): string | null => {
      if (!to) return null;
      const { period } = periodOf(from, to, today);
      if (period === 'datas') return null;
      return period === 'mes' ? month : this.transloco.translate(PRESET_KEY + period);
    };
  });

  protected settle(): void {
    const from = this.from();
    const to = this.to();
    const max = this.maxDays();
    const valid = !!from && !!to && (max === null || daysBetween(from, to) + 1 <= max);
    const next = !from && !to ? DEFAULT_PERIOD : valid ? periodOf(from, to, this.today()) : null;
    if (next && !samePeriod(next, this.value())) {
      this.changed.emit(next);
      return;
    }
    // Sen cambio (ou a medias): o calendario volve ao período posto.
    const range = periodRange(this.value(), this.today());
    this.from.set(range.from);
    this.to.set(range.to);
  }
}
