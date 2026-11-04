// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import type { IsoDate } from '../ui/calendar/local-date';
import { addDays, localToday } from '../ui/calendar/local-date';
import type { ComboboxSource } from '../ui/combobox/combobox.component';
import { ComboboxComponent } from '../ui/combobox/combobox.component';
import type { DateShortcut } from '../ui/date-field/date-field.component';
import { DateFieldComponent } from '../ui/date-field/date-field.component';
import { IconComponent } from '../ui/icon/icon.component';
import type { SearchChoice, SearchCriteria, SearchFacility } from './facility-search';
import { dayLabel, sportKey, sportName, suggestions } from './facility-search';

let nextId = 0;

/**
 * Buscador de instalacións (portada e listado público, maquetas 02 e 16): barra-cápsula co «Que
 * queres reservar?» (combobox con suxestións de deportes e instalacións), o día (calendario global
 * nunha capa con «Hoxe» e «Mañá») e o botón de buscar. Non navega: emite `searched` cos criterios
 * e quen o usa decide a onde levar. `criteria` enche os campos (o listado, desde o URL). Con
 * `disabled` vese pero non se usa (a portada sen concello, maqueta 46).
 */
@Component({
  selector: 'furar-facility-search',
  imports: [ReactiveFormsModule, TranslocoPipe, ComboboxComponent, DateFieldComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form
      #bar
      class="f-search"
      [attr.data-disabled]="disabled() || null"
      role="search"
      [attr.aria-label]="'home.search.label' | transloco"
      (submit)="submit($event)"
    >
      <label class="f-search__query" [for]="queryId">
        <furar-icon name="search" [size]="24" />
        <span class="sr-only">{{ 'home.search.what' | transloco }}</span>
      </label>
      <furar-combobox
        class="f-search__combobox"
        look="pill"
        highlight
        debounce="120"
        [inputId]="queryId"
        [panelOrigin]="bar"
        [formControl]="choice"
        [label]="'home.search.suggestions' | transloco"
        [placeholder]="'home.search.what' | transloco"
        [source]="source()"
        [displayWith]="choiceLabel"
      />
      <furar-date-field
        class="f-search__day"
        look="pill"
        [formControl]="day"
        [label]="'home.search.day' | transloco"
        [min]="today"
        [today]="today"
        [shortcuts]="shortcuts()"
        [displayWith]="dayText()"
      >
        <furar-icon furarDateFieldIcon name="calendar" />
      </furar-date-field>
      <button
        type="submit"
        class="f-search__go"
        [disabled]="disabled()"
        [attr.aria-label]="'home.search.submit' | transloco"
      >
        <furar-icon name="arrow-right" [size]="24" />
      </button>
    </form>
  `,
})
export class FacilitySearchComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** Instalacións publicadas (fonte das suxestións). */
  readonly facilities = input.required<readonly SearchFacility[]>();
  /** Valores cos que se enchen os campos (sen el, nada escollido e hoxe). */
  readonly criteria = input<SearchCriteria | null>(null);
  /** Desactivado: vese, pero nin se escribe nin se busca. */
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Buscar: o que se escolleu (ou nada) e o día. */
  readonly searched = output<SearchCriteria>();

  protected readonly queryId = `furar-search-query-${nextId++}`;
  protected readonly today: IsoDate = localToday();
  protected readonly choice = new FormControl<SearchChoice | null>(null);
  protected readonly day = new FormControl<IsoDate>(this.today, { nonNullable: true });

  constructor() {
    effect(() => {
      const options = { emitEvent: false };
      if (this.disabled()) {
        this.choice.disable(options);
        this.day.disable(options);
      } else {
        this.choice.enable(options);
        this.day.enable(options);
      }
    });
    effect(() => {
      const criteria = this.criteria();
      this.facilities();
      if (criteria) {
        this.choice.setValue(criteria.choice);
        this.day.setValue(criteria.date < this.today ? this.today : criteria.date);
      }
    });
  }

  private readonly words = computed(() => {
    this.language.lang();
    return {
      today: this.transloco.translate('home.search.today'),
      tomorrow: this.transloco.translate('home.search.tomorrow'),
    };
  });
  protected readonly shortcuts = computed((): DateShortcut[] => [
    { label: this.words().today, date: this.today },
    { label: this.words().tomorrow, date: addDays(this.today, 1) },
  ]);
  protected readonly dayText = computed(() => {
    const words = this.words();
    const lang = this.language.lang();
    return (date: IsoDate) => dayLabel(date, this.today, lang, words);
  });

  /** Fonte nova cando chegan as instalacións: o combobox repite a busca se xa había texto. */
  protected readonly source = computed((): ComboboxSource<SearchChoice> => {
    const facilities = this.facilities();
    return (query) =>
      Promise.resolve(
        suggestions(facilities, query, this.language.lang(), {
          sports: this.transloco.translate('home.search.sports'),
          facilities: this.transloco.translate('home.search.facilities'),
        }),
      );
  });

  /** Texto do campo para unha escolla (o combobox xa o pon ao escoller; isto é para valores que cheguen de fóra). */
  protected readonly choiceLabel = (choice: SearchChoice): string => {
    if (choice.kind === 'facility') return this.facilities().find((f) => f.id === choice.id)?.name ?? '';
    const sport = this.facilities()
      .flatMap((f) => f.sports)
      .find((s) => sportKey(s) === choice.key);
    return sport ? sportName(sport, this.language.lang()) : '';
  };

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.disabled()) return;
    this.searched.emit({ choice: this.choice.value, date: this.day.value });
  }
}
