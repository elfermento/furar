// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import type { ElementRef } from '@angular/core';
import type { IsoDate } from '../ui/calendar/local-date';
import { addDays } from '../ui/calendar/local-date';
import { madridToday } from '../ui/calendar/madrid-time';
import type { ComboboxSource } from '../ui/combobox/combobox.component';
import { ClearButtonComponent } from '../ui/clear-button/clear-button.component';
import { ComboboxComponent } from '../ui/combobox/combobox.component';
import type { DateShortcut } from '../ui/date-field/date-field.component';
import { DateFieldComponent } from '../ui/date-field/date-field.component';
import { IconComponent } from '../ui/icon/icon.component';
import type { SelectOption } from '../ui/select/select.component';
import type { SearchChoice, SearchCriteria, SearchFacility, SearchSport } from './facility-search';
import { choiceText, dayLabel, sportKey, suggestions } from './facility-search';

let nextId = 0;

/** Desde aquí, o día vai na barra (por debaixo, na folla «Filtros»). */
const WIDE_QUERY = '(min-width: 640px)';

function matchesWide(): boolean {
  return typeof matchMedia === 'function' ? matchMedia(WIDE_QUERY).matches : false;
}

/** Leva o foco ao botón dunha parte (o «✕» que se pulsou desaparece). */
function focusControl(segment: HTMLElement): void {
  segment.querySelector<HTMLElement>('.f-date-field__button')?.focus();
}

/**
 * Buscador de instalacións (portada e listado público): barra-cápsula co «Que
 * queres reservar?» (combobox con suxestións de deportes e instalacións), o día (calendario global
 * nunha capa con «Hoxe» e «Mañá») e o botón de buscar. Non navega: emite `searched` cos criterios
 * e quen o usa decide a onde levar. `criteria` enche os campos (o listado, desde o URL). Con
 * `disabled` vese pero non se usa (a portada sen concello).
 *
 * Escoller unha suxestión xa busca con ela (unha instalación leva á súa ficha). Buscar co texto
 * escrito sen escoller suxestión («torre») emite ese texto en `text`, para filtrar a lista por el.
 *
 * Cambiar o día avisa ao momento con `dayChange` (sen agardar a «Buscar»): os ocos das tarxetas
 * son os dese día.
 *
 * Na portada común (`catalog`, ADR-0030) busca tamén por concello. Con `filterOptions` (as dúas
 * portadas e o listado dun concello) leva ao final o botón «Filtros» (`filtersOpened`): quen usa o
 * buscador abre a folla cos filtros (`openSearchFilters`: tipo, deporte e, no móbil, o día). No
 * móbil (< 640 px) é só a icona co número de filtros postos (`filterCount`, máis o día); no
 * escritorio, unha parte máis da barra co rótulo.
 *
 * Escritorio: cada parte (o texto, o día, «Filtros») é unha cápsula. A aberta (o texto enfocado ou
 * o panel do día) márcase cun bordo azul leve (`--f-mark`); co teclado, coa cor do foco. Cada parte
 * con valor (texto, un día que non é hoxe, filtros postos) leva un «✕» que só a baleira
 * (`textCleared`, `dayChange` ou `filtersCleared`). As suxestións ábrense debaixo do texto e só se
 * hai algunha.
 */
@Component({
  selector: 'furar-facility-search',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    ClearButtonComponent,
    ComboboxComponent,
    DateFieldComponent,
    IconComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[style.--f-panel-room]': 'panelRoom()',
    '(focusin)': 'measureRoom()',
    '(document:keydown.tab)': 'keyboard.set(true)',
    '(document:keydown.shift.tab)': 'keyboard.set(true)',
    '(document:pointerdown)': 'keyboard.set(false)',
  },
  templateUrl: './facility-search.component.html',
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
  /**
   * Portada común (ADR-0030): as instalacións traen o seu concello, que tamén se suxire e se busca
   * («Instalación ou concello»).
   */
  readonly catalog = input(false, { transform: booleanAttribute });
  /**
   * Opcións do filtro «Deporte» da folla «Filtros» (os deportes e, ao final, «Cuberta»); sen elas
   * (`null`), a barra non leva o botón «Filtros».
   */
  readonly filterOptions = input<readonly SelectOption<string>[] | null>(null);
  /**
   * A chave coa que se agrupan os deportes suxeridos (e a que leva a escolla). Sen ela (`null`), o
   * código ou o id do concello: dentro dun concello non hai repetidos. A portada común pasa a do
   * seu filtro, que une os propios co mesmo nome de varios concellos.
   */
  readonly sportKeyOf = input<((sport: SearchSport) => string) | null>(null);
  /**
   * Filtros postos sen contar o día (tipo, deportes e «Cuberta»): o número do botón «Filtros». No
   * móbil, onde o día vai na folla, súmaselle se non é hoxe.
   */
  readonly filterCount = input(0);
  /** Pulsouse «Filtros» (móbil): quen usa o buscador abre a folla cos filtros. */
  readonly filtersOpened = output();
  /** Buscar: o que se escolleu (ou nada) e o día. */
  readonly searched = output<SearchCriteria>();
  /** O «✕» de «Filtros» (escritorio): quen usa o buscador quita o tipo, os deportes e «Cuberta». */
  readonly filtersCleared = output();
  /** A persoa cambiou o día (aplícase ao momento, sen «Buscar»). */
  readonly dayChange = output<IsoDate>();
  /** O «✕» do texto baleirou o campo: quen o usa quita o texto ou o concello buscados. */
  readonly textCleared = output();

  protected readonly queryId = `furar-search-query-${nextId++}`;
  protected readonly today: IsoDate = madridToday();
  protected readonly choice = new FormControl<SearchChoice | null>(null);
  protected readonly day = new FormControl<IsoDate>(this.today, { nonNullable: true });
  /** O día do campo, para o seu «✕» (só cando non é hoxe). */
  protected readonly date = signal<IsoDate>(this.today);
  protected readonly combobox = viewChild(ComboboxComponent);
  private readonly bar = viewChild.required<ElementRef<HTMLFormElement>>('bar');
  /**
   * Móbil: o alto que queda para as suxestións entre a barra e o teclado (a parte visible da
   * pantalla), para que a lista o use todo (facility-search.css).
   */
  protected readonly panelRoom = signal<string | null>(null);
  /** Escritorio (≥ 640 px): as partes ábrense debaixo de si e as suxestións só se ven se as hai. */
  protected readonly wide = signal(matchesWide());
  /**
   * A persoa vai co teclado (Tab): a parte enfocada márcase coa cor do foco (3:1) en vez do azul
   * leve, que só vale co rato.
   */
  protected readonly keyboard = signal(false);
  /** O número do botón «Filtros»: no móbil, co día se non é hoxe (alí vai na folla). */
  protected readonly shownCount = computed(
    () => this.filterCount() + (!this.wide() && this.date() !== this.today ? 1 : 0),
  );

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
      const combobox = this.combobox();
      this.facilities();
      if (criteria) {
        this.choice.setValue(criteria.choice);
        // Sen aviso: `dayChange` só sae cando o cambia a persoa.
        const date = criteria.date < this.today ? this.today : criteria.date;
        this.day.setValue(date, { emitEvent: false });
        this.date.set(date);
        if (!criteria.choice && criteria.text) combobox?.writeText(criteria.text);
      }
    });
    this.day.valueChanges.pipe(takeUntilDestroyed()).subscribe((date) => {
      this.date.set(date);
      if (!this.disabled()) this.dayChange.emit(date);
    });
    const view = inject(DOCUMENT).defaultView;
    const query = view?.matchMedia?.(WIDE_QUERY);
    const onChange = (event: MediaQueryListEvent) => this.wide.set(event.matches);
    query?.addEventListener?.('change', onChange);
    // O teclado do móbil encolle a parte visible: as suxestións collen o que queda.
    const onViewport = () => this.measureRoom();
    view?.visualViewport?.addEventListener('resize', onViewport);
    inject(DestroyRef).onDestroy(() => {
      query?.removeEventListener?.('change', onChange);
      view?.visualViewport?.removeEventListener('resize', onViewport);
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
    const key = this.sportKeyOf() ?? sportKey;
    return (query) =>
      Promise.resolve(
        suggestions(
          facilities,
          query,
          this.language.lang(),
          {
            sports: this.transloco.translate('home.search.sports'),
            facilities: this.transloco.translate('home.search.facilities'),
            ...(this.catalog() ? { municipalities: this.transloco.translate('home.search.municipalities') } : {}),
          },
          key,
        ),
      );
  });

  /** Texto do campo para unha escolla (o combobox xa o pon ao escoller; isto é para valores que cheguen de fóra). */
  protected readonly choiceLabel = (choice: SearchChoice): string =>
    choiceText(choice, this.facilities(), this.language.lang(), this.sportKeyOf() ?? sportKey);

  /** Leva o foco ao campo de texto (p. ex. ao quitar o filtro do texto buscado). */
  focus(): void {
    this.combobox()?.focus();
  }

  /** Mide o oco entre a barra e o final da parte visible da pantalla (por riba do teclado). */
  protected measureRoom(): void {
    const view = this.bar().nativeElement.ownerDocument.defaultView;
    const visible = view?.visualViewport;
    if (this.wide() || !visible) return;
    const room = visible.height + visible.offsetTop - this.bar().nativeElement.getBoundingClientRect().bottom - 20;
    this.panelRoom.set(`${Math.max(160, Math.round(room))}px`);
  }

  /** «✕» do texto: baleira o campo (e quen o usa, o filtro), co foco nel. */
  protected clearText(): void {
    this.choice.setValue(null);
    this.textCleared.emit();
    this.focus();
  }

  /** «✕» do día: volve a hoxe (avisa con `dayChange`), co foco no botón do día. */
  protected clearDay(segment: HTMLElement): void {
    this.day.setValue(this.today);
    focusControl(segment);
  }

  /** «✕» de «Filtros»: quen o usa quítaos; o foco, no botón «Filtros». */
  protected clearFilters(segment: HTMLElement): void {
    this.filtersCleared.emit();
    focusControl(segment);
  }

  /** Escolleuse unha suxestión: busca xa con ela, sen agardar ao botón. */
  protected pick(choice: SearchChoice): void {
    if (this.disabled()) return;
    this.searched.emit({ choice, date: this.day.value, text: '' });
  }

  /** Buscar: a suxestión escollida ou, sen ela, o texto escrito. */
  protected submit(event: Event): void {
    event.preventDefault();
    if (this.disabled()) return;
    const choice = this.choice.value;
    this.searched.emit({ choice, date: this.day.value, text: choice ? '' : (this.combobox()?.typedText() ?? '') });
  }
}
