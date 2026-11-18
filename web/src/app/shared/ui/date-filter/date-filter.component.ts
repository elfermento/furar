// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { CalendarComponent } from '../calendar/calendar.component';
import type { IsoDate } from '../calendar/local-date';
import { IconComponent } from '../icon/icon.component';
import type { DateFilterPreset } from './date-filter';
import { dateFilterText, daysBetween, defaultPresets, pickDay } from './date-filter';

let nextId = 0;

/** «Menos movemento» (o atributo que pon DisplayPreferencesService en <html>). */
function reducedMotion(): boolean {
  return globalThis.document?.documentElement.getAttribute('data-motion') === 'reduce';
}

interface ChipText {
  readonly text: string | null;
  readonly key: string;
  readonly params: Record<string, string>;
}

/**
 * O chip de data de toda a app (o chip `f-mdate` co aspa dentro; estilos en
 * `styles/components/date-filter.css`). Sen data, o chip «Escoller data»; con ela, un chip co texto
 * («Hoxe», «Do 26/10 ao 31/10») que volve abrir o panel e unha ✕ dentro para quitala. Pecha con
 * Esc (só el, non a capa onde vai) ou picando fóra; o foco volve ao chip.
 *
 * - Rango (por defecto; «Datas» da xestión de reservas, e datos abertos): o panel ten
 *   os atallos (Hoxe · Mañá · Esta semana, ou os de `presets`), o calendario de rango (primeiro
 *   clic, o inicio; o segundo, o fin) e «Aplicar».
 * - `single` (unha data; «Data» da folla de filtros da portada común): só o calendario; escoller un
 *   día pon `from` e `to` nese día e pecha.
 *
 * Os días fóra de `min` e `max` non se poden escoller (datos abertos, ADR-0034, punto 24). `format`
 * cambia o texto do chip; `label` vai diante del no nome dos botóns («Data, Hoxe»).
 */
@Component({
  selector: 'furar-date-filter',
  imports: [ButtonDirective, CalendarComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Esc pecha o panel tamén co foco aínda no chip (abriuse con Intro e aínda non se entrou nel).
  host: { class: 'f-mdate', '(keydown.escape)': 'escape($event)' },
  template: `
    @let t = 'booking.manage.filters.dates.';
    <div class="f-mdate__row">
      @if (chip(); as chip) {
        @let text = chip.text ?? (t + chip.key | transloco: chip.params);
        <span class="f-mdate__chip" data-date-chip>
          <button
            #trigger
            type="button"
            class="f-mdate__chip-open"
            [attr.aria-expanded]="open()"
            [attr.aria-controls]="panelId"
            [attr.aria-label]="label() ? label() + ', ' + text : null"
            (click)="toggle()"
          >
            <furar-icon name="calendar-month" />
            <span>{{ text }}</span>
          </button>
          <button
            type="button"
            class="f-mdate__chip-clear"
            data-date-clear
            [attr.aria-label]="(single() ? 'ui.dateField.clear' : t + 'clear') | transloco"
            (click)="clear()"
          >
            <furar-icon name="close" />
          </button>
        </span>
      } @else {
        <button
          #trigger
          type="button"
          class="f-mdate__add"
          data-date-add
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="panelId"
          [attr.aria-label]="label() ? label() + ', ' + ('ui.dateField.empty' | transloco) : null"
          (click)="toggle()"
        >
          <furar-icon name="calendar-month" />
          {{ 'ui.dateField.empty' | transloco }}
        </button>
      }
    </div>
    @if (open()) {
      <div
        class="f-mdate__panel"
        role="group"
        data-date-panel
        [id]="panelId"
        [attr.aria-label]="(single() ? 'ui.dateField.empty' : t + 'panel') | transloco"
      >
        @if (single()) {
          <furar-calendar
            [value]="from()"
            [today]="today()"
            [min]="min()"
            [max]="max()"
            [month]="from() ?? min()"
            (picked)="pickOne($event)"
          />
        } @else {
          <div class="f-mdate__presets" role="group" [attr.aria-label]="t + 'presets' | transloco">
            @for (preset of shownPresets(); track preset.id) {
              <button
                type="button"
                class="f-mdate__preset"
                [attr.data-preset]="preset.id"
                [attr.aria-pressed]="isPreset(preset)"
                (click)="applyPreset(preset)"
              >
                {{ preset.label | transloco }}
              </button>
            }
          </div>
          <furar-calendar
            range
            [value]="from()"
            [rangeEnd]="to()"
            [today]="today()"
            [min]="min()"
            [max]="max()"
            [month]="from() ?? max()"
            (picked)="pick($event)"
          />
          @if (tooLong()) {
            <p class="f-mdate__error" role="alert" data-date-too-long>
              {{ 'ui.dateFilter.tooLong' | transloco: { max: maxDays() } }}
            </p>
          }
          <div class="f-mdate__ok">
            <button type="button" furarButton="primary" size="sm" data-date-apply (click)="close()">
              {{ t + 'apply' | transloco }}
            </button>
          </div>
        }
      </div>
    }
  `,
})
export class DateFilterComponent {
  readonly from = model<IsoDate | null>(null);
  readonly to = model<IsoDate | null>(null);
  readonly today = input.required<IsoDate>();
  /** Os atallos; sen eles, os da xestión de reservas. */
  readonly presets = input<readonly DateFilterPreset[] | null>(null);
  /** O primeiro e o último día que se poden escoller. */
  readonly min = input<IsoDate | null>(null);
  readonly max = input<IsoDate | null>(null);
  /** Unha soa data (sen atallos nin «Aplicar»): `from` e `to` van no mesmo día. */
  readonly single = input(false, { transform: booleanAttribute });
  /** O texto do chip; sen el (ou se devolve `null`), «Hoxe», «Mañá», «O 26/10», «Do 26/10 ao 31/10»… */
  readonly format = input<((from: IsoDate, to: IsoDate | null) => string | null) | null>(null);
  /** Días como moito no rango: máis, aviso e «Aplicar» leva ao calendario (indicadores: un ano). */
  readonly maxDays = input<number | null>(null);
  /** Rematou unha escolla: pechouse o panel (aplicar, Esc ou picar fóra) ou quitouse a data. */
  readonly settled = output();
  /** Nome do grupo onde vai («Data»), diante do texto no nome dos botóns. */
  readonly label = input('');

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  private readonly calendar = viewChild(CalendarComponent);

  protected readonly shownPresets = computed(() => this.presets() ?? defaultPresets(this.today()));
  protected readonly panelId = `furar-date-filter-${nextId++}`;
  protected readonly open = signal(false);
  /** O texto do chip (o de `format` ou a clave e os valores); `null`, sen data («Escoller data»). */
  protected readonly chip = computed((): ChipText | null => {
    const from = this.from();
    if (!from) return null;
    const to = this.single() ? from : this.to();
    const format = this.format();
    const text = dateFilterText(from, to, this.today());
    return text && { text: format?.(from, to) ?? null, ...text };
  });
  /** O rango pasa de `maxDays`. */
  protected readonly tooLong = computed(() => {
    const max = this.maxDays();
    const from = this.from();
    const to = this.to();
    return max !== null && !!from && !!to && daysBetween(from, to) + 1 > max;
  });

  constructor() {
    // Picar fóra (no diálogo ou fóra del) pecha o panel, como toda capa.
    const document = inject(DOCUMENT);
    const onDown = (event: Event) => {
      if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.shut();
    };
    document.addEventListener('mousedown', onDown, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('mousedown', onDown, true));
  }

  protected toggle(): void {
    if (this.open()) {
      this.shut();
      return;
    }
    this.open.set(true);
    // Nunha folla con desprazamento, o panel enteiro á vista (sen cortar o bordo de abaixo).
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector<HTMLElement>('[data-date-panel]')
          ?.scrollIntoView?.({ block: 'nearest', behavior: reducedMotion() ? 'auto' : 'smooth' }),
      { injector: this.injector },
    );
  }

  /** Pecha o panel e avisa (`settled`). */
  private shut(): void {
    this.open.set(false);
    this.settled.emit();
  }

  protected isPreset(preset: DateFilterPreset): boolean {
    return this.from() === preset.from && this.to() === preset.to;
  }

  protected applyPreset(preset: DateFilterPreset): void {
    this.from.set(preset.from);
    this.to.set(preset.to);
  }

  /** Unha soa data: escoller o día pecha. */
  protected pickOne(day: IsoDate): void {
    this.from.set(day);
    this.to.set(day);
    this.close();
  }

  protected pick(day: IsoDate): void {
    const range = pickDay({ from: this.from(), to: this.to() }, day);
    this.from.set(range.from);
    this.to.set(range.to);
  }

  protected clear(): void {
    this.from.set(null);
    this.to.set(null);
    this.open.set(false);
    this.settled.emit();
    afterNextRender(() => this.trigger()?.nativeElement.focus(), { injector: this.injector });
  }

  /**
   * «Aplicar»: pecha e o foco volve ao campo (as datas xa están postas). Sempre activo: cun rango
   * longo de máis, o aviso xa está á vista e o foco vai ao calendario para escoller outro día.
   */
  protected close(): void {
    if (this.tooLong()) {
      this.calendar()?.focusActive();
      return;
    }
    this.closeAndFocus();
  }

  /**
   * Esc pecha só o panel, non o diálogo dos filtros (tamén cun rango longo de máis). Co panel
   * pechado, Esc segue o seu camiño (p. ex. pecha o diálogo onde está o chip).
   */
  protected escape(event: Event): void {
    if (!this.open()) return;
    event.stopPropagation();
    event.preventDefault();
    this.closeAndFocus();
  }

  private closeAndFocus(): void {
    this.shut();
    afterNextRender(() => this.trigger()?.nativeElement.focus(), { injector: this.injector });
  }
}
