// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CdkListbox, CdkOption } from '@angular/cdk/listbox';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { ButtonDirective } from '../ui/button/button.directive';
import { CalendarComponent } from '../ui/calendar/calendar.component';
import type { IsoDate } from '../ui/calendar/local-date';
import { addDays } from '../ui/calendar/local-date';
import { matchParts } from '../ui/combobox/combobox-search';
import type { ComboboxOption } from '../ui/combobox/combobox.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import type { DialogService } from '../ui/dialog/dialog.service';
import { IconComponent } from '../ui/icon/icon.component';
import type { SelectOption } from '../ui/select/select.component';
import type { SearchChoice, SearchCriteria, SearchFacility, SearchSport } from './facility-search';
import { choiceText, dayLabel, sportKey, suggestions } from './facility-search';
import type { SearchType } from './search-filters-dialog.component';
import { SheetKeyboard, centerCard, dropRoom, fieldToTop } from './sheet-keyboard';

/** O que leva a folla: o buscado ata agora e as opcións (as mesmas cás da barra e de «Filtros»). */
export interface SearchSheetData {
  readonly facilities: readonly SearchFacility[];
  /** Portada común: tamén suxire concellos. */
  readonly catalog?: boolean;
  readonly sportKeyOf?: (sport: SearchSport) => string;
  readonly criteria: SearchCriteria;
  readonly today: IsoDate;
  /** Os deportes e, ao final, «Cuberta»; e os marcados. */
  readonly options: readonly SelectOption<string>[];
  readonly value: readonly string[];
  /** «Tipo»: o que pode listar a páxina («Todo» primeiro; con menos de dous, non sae) e o escollido. */
  readonly kinds?: readonly SearchType[];
  readonly kind?: SearchType;
}

/** O escollido ao pulsar «Buscar». */
export interface SearchSheetResult {
  readonly criteria: SearchCriteria;
  readonly value: readonly string[];
  readonly kind: SearchType;
}

type Section = 'where' | 'when' | 'kind' | 'sport';

let nextId = 0;

/**
 * Buscar no móbil: folla case a toda a altura, «Buscar» co ✕, con catro apartados en
 * tarxetas e un só aberto cada vez: «Onde» (o texto, coas suxestións do buscador debaixo, na
 * mesma tarxeta), «Cando» («Hoxe», «Mañá» e o calendario), «Tipo» (unha opción co ✓; só se a
 * páxina ten algo máis ca instalacións) e «Deporte» (varias; só con «Todo» ou as instalacións).
 * Pechado, cada apartado é unha fila co nome e o escollido; picala ábrea e pecha o outro. Escoller
 * unha suxestión pasa a «Cando» e escoller un día, ao seguinte. «Borrar todo» baléiraa; «Buscar»
 * pecha co escollido. Pechar doutro xeito non cambia nada.
 *
 * Fixa case a toda a altura do móbil, co campo arriba: o teclado só tapa a parte de abaixo. Mentres
 * se escribe, sen o pé (`SheetKeyboard`).
 *
 * Teclado do móbil: mentres se escribe en «Onde», empezar a desprazar as suxestións quita o foco
 * do campo (baixa o teclado e vese a lista enteira); un toque nunha suxestión só a escolle.
 */
@Component({
  selector: 'furar-search-sheet',
  imports: [ButtonDirective, CalendarComponent, CdkListbox, CdkOption, DialogComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-ssheet',
    '[attr.data-typing]': "(keyboard.writing() && open() === 'where') || null",
  },
  templateUrl: './search-sheet.component.html',
})
export class SearchSheetComponent {
  protected readonly data = inject<SearchSheetData>(DIALOG_DATA);
  private readonly injector = inject(Injector);
  private readonly ref = inject<DialogRef<SearchSheetResult>>(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly field = viewChild<ElementRef<HTMLInputElement>>('field');
  /** O teclado do móbil: mentres se escribe, sen pé. */
  protected readonly keyboard = new SheetKeyboard();
  /** Pantalla táctil (o dedo): sen foco ao abrir. */
  private readonly touch = inject(DOCUMENT).defaultView?.matchMedia?.('(pointer: coarse)').matches ?? false;

  protected readonly ids = { where: `furar-ssheet-${nextId++}`, input: `furar-ssheet-${nextId++}` };
  private readonly key = this.data.sportKeyOf ?? sportKey;
  protected readonly open = signal<Section>('where');
  protected readonly choice = signal<SearchChoice | null>(this.data.criteria.choice);
  protected readonly text = signal(
    this.data.criteria.choice
      ? choiceText(this.data.criteria.choice, this.data.facilities, this.language.lang(), this.key)
      : (this.data.criteria.text ?? ''),
  );
  protected readonly date = signal<IsoDate>(this.data.criteria.date);
  protected readonly kind = signal<SearchType>(this.data.kind ?? 'all');
  protected readonly value = signal<readonly string[]>(this.data.value);

  private readonly t = computed(() => {
    this.langChanges();
    return (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
  });
  /** As suxestións do texto, polos seus grupos (deportes, concellos, instalacións). */
  protected readonly groups = computed(() => {
    const t = this.t();
    const found = suggestions(
      this.data.facilities,
      this.text(),
      this.language.lang(),
      {
        sports: t('home.search.sports'),
        facilities: t('home.search.facilities'),
        ...(this.data.catalog ? { municipalities: t('home.search.municipalities') } : {}),
      },
      this.key,
    );
    const groups: { name: string; items: ComboboxOption<SearchChoice>[] }[] = [];
    for (const option of found) {
      const group = groups.at(-1)?.name === option.group ? groups.at(-1) : undefined;
      if (group) group.items.push(option);
      else groups.push({ name: option.group ?? '', items: [option] });
    }
    return groups;
  });
  /** Cantas suxestións hai (o anuncio do lector de pantalla). */
  protected readonly found = computed(() => this.groups().reduce((count, group) => count + group.items.length, 0));
  protected readonly kinds = computed(() =>
    (this.data.kinds ?? []).map((kind) => ({ value: kind, label: this.t()(`facility.catalog.filters.kinds.${kind}`) })),
  );
  /** «Deporte» filtra as instalacións: só con «Todo» ou con elas. */
  protected readonly sports = computed(
    () => this.data.options.length > 0 && ['all', 'facilities'].includes(this.kind()),
  );
  protected readonly shortcuts = computed(() => [
    { label: this.t()('home.search.today'), date: this.data.today },
    { label: this.t()('home.search.tomorrow'), date: addDays(this.data.today, 1) },
  ]);
  /** O escollido de cada apartado, para a súa fila pechada. */
  protected readonly summary = computed(() => {
    const t = this.t();
    const chosen = this.data.options.filter((o) => this.value().includes(o.value)).map((o) => o.label);
    return {
      where: this.text().trim() || t('home.search.anywhere'),
      when: dayLabel(this.date(), this.data.today, this.language.lang(), {
        today: t('home.search.today'),
        tomorrow: t('home.search.tomorrow'),
      }),
      kind: this.kinds().find((k) => k.value === this.kind())?.label ?? '',
      sport:
        chosen.length > 1
          ? t('ui.multiSelect.count', { count: chosen.length })
          : (chosen[0] ?? t('facility.catalog.filters.anySport')),
    };
  });

  constructor() {
    // Co rato ou o teclado, o foco xa no campo; nunha pantalla táctil, non (sacaría o teclado ou non,
    // segundo o navegador): a persoa preme o campo.
    if (!this.touch) afterNextRender(() => this.field()?.nativeElement.focus(), { injector: this.injector });
  }

  protected parts(label: string) {
    return matchParts(label, this.text());
  }

  protected show(section: Section): void {
    this.open.set(section);
    if (section !== 'where') this.keyboard.release();
    this.reveal();
    if (section === 'where' && !this.touch) {
      afterNextRender(() => this.field()?.nativeElement.focus(), { injector: this.injector });
    }
  }

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLInputElement).value);
    this.choice.set(null);
    // Teclear corta o desprazamento suave de `raise` se aínda non chegara: o campo, arriba igual.
    const field = event.target as HTMLElement;
    if (this.touch) afterNextRender(() => fieldToTop(field, true), { injector: this.injector });
  }

  /** Escolleuse unha suxestión: queda no campo e pasa a «Cando». */
  protected pick(option: ComboboxOption<SearchChoice>): void {
    this.field()?.nativeElement.blur();
    this.keyboard.release();
    this.choice.set(option.value);
    this.text.set(option.label);
    this.open.set('when');
    this.reveal();
  }

  /** Premeron «Onde»: o campo arriba, baixo a cabeceira. */
  protected raise(field: HTMLElement): void {
    this.keyboard.press();
    fieldToTop(field);
  }

  /** Tras abrir un apartado (xa pintado): fóra o aire de «Onde» e «Cando» enteiro no medio. */
  private reveal(): void {
    afterNextRender(
      () =>
        requestAnimationFrame(() => {
          const host = this.host.nativeElement;
          if (this.open() !== 'where') dropRoom(host);
          const card = host.querySelector<HTMLElement>('.f-ssheet__sec[data-open]');
          if (this.open() === 'when' && card) centerCard(card);
        }),
      { injector: this.injector },
    );
  }

  protected pickDate(date: IsoDate): void {
    this.date.set(date);
    this.open.set(this.kinds().length > 1 ? 'kind' : this.sports() ? 'sport' : 'when');
  }

  protected pickKind(kind: SearchType | undefined): void {
    if (!kind) return;
    this.kind.set(kind);
    if (this.sports()) this.open.set('sport');
  }

  /** Empezou a desprazarse a lista: fóra o teclado, para vela enteira (un toque non chega aquí). */
  protected dropKeyboard(): void {
    const field = this.field()?.nativeElement;
    if (field && field.ownerDocument.activeElement === field) field.blur();
  }

  protected clear(): void {
    this.choice.set(null);
    this.text.set('');
    this.date.set(this.data.today);
    this.kind.set('all');
    this.value.set([]);
    this.show('where');
  }

  protected apply(): void {
    const choice = this.choice();
    const criteria: SearchCriteria = { choice, date: this.date(), text: choice ? '' : this.text().trim() };
    this.ref.close({ criteria, value: this.sports() ? this.value() : [], kind: this.kind() });
  }
}

/** Abre a folla «Buscar» desde o botón do `click` (o foco volve a el ao pechar) e resolve co escollido. */
export function openSearchSheet(
  dialogs: DialogService,
  data: SearchSheetData,
  event?: Event,
): Promise<SearchSheetResult | undefined> {
  const from = event?.currentTarget as HTMLElement | null | undefined;
  const ref = dialogs.open<SearchSheetResult, SearchSheetData>(SearchSheetComponent, {
    data,
    sheet: true,
    autoFocus: false,
    ...(from ? { restoreFocus: from } : {}),
  });
  return firstValueFrom(ref.closed, { defaultValue: undefined });
}
