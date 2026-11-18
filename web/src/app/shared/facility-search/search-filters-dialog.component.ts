// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { dayLabel } from './facility-search';
import { ButtonDirective } from '../ui/button/button.directive';
import type { IsoDate } from '../ui/calendar/local-date';
import { DateFilterComponent } from '../ui/date-filter/date-filter.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import type { DialogService } from '../ui/dialog/dialog.service';
import { FieldComponent } from '../ui/field.component';
import type { SelectOption } from '../ui/select/select.component';
import { SelectComponent } from '../ui/select/select.component';

/** Que lista a portada: as instalacións, as partidas abertas ou os eventos (as actividades). */
export type SearchKind = 'facilities' | 'matches' | 'events';
/** «Tipo» da folla «Filtros»: todo (as instalacións e, debaixo, o resto) ou só un deles. */
export type SearchType = 'all' | SearchKind;

/** Desde aquí o día vai na barra do buscador: a folla xa non o leva. */
const WIDE_QUERY = '(min-width: 640px)';

/** O que leva a folla: as opcións e o escollido ata agora. */
export interface SearchFiltersData {
  /** Os deportes e, ao final, «Cuberta» (as opcións do filtro «Deporte» da barra). */
  readonly options: readonly SelectOption<string>[];
  readonly value: readonly string[];
  /** A data escollida (`null`, ningunha: os ocos son os de hoxe). */
  readonly date: IsoDate | null;
  readonly today: IsoDate;
  /** Cantas instalacións se verían con estas opcións marcadas. */
  readonly count: (value: readonly string[]) => number;
  /**
   * O que pode listar a páxina, na orde do despregable «Tipo» («Todo» e as instalacións primeiro).
   * Con menos de dúas, o campo non sae (por defecto, todo).
   */
  readonly kinds?: readonly SearchType[];
  /** O que lista agora (por defecto, todo). */
  readonly kind?: SearchType;
}

/** O escollido ao pulsar «Ver N instalacións». */
export interface SearchFiltersResult {
  readonly value: readonly string[];
  readonly date: IsoDate | null;
  readonly kind: SearchType;
}

let nextId = 0;

/**
 * «Filtros» do buscador (as portadas e o listado dun concello): no móbil, folla desde abaixo; no
 * escritorio, diálogo no medio. Leva «Tipo» («Todo», instalacións, actividades ou partidas
 * abertas; só se a páxina ten algo máis ca instalacións, `kinds`), «Data» (só no móbil, con
 * calquera tipo: no escritorio o día vai na barra; o chip de data da app, `furar-date-filter`
 * cunha soa data) e «Deporte» (deportes e «Cuberta»; só con «Todo» ou as instalacións, que é o
 * que filtra). Con «Todo», «Ver N resultados». «Quitar filtros», na cabeceira, baléiraa (volve a «Todo»); «Ver N instalacións»
 * («Ver partidas», «Ver eventos») pecha co escollido. Pechar doutro xeito (✕, Esc, picar fóra) non
 * cambia nada.
 */
@Component({
  selector: 'furar-search-filters-dialog',
  imports: [ButtonDirective, DateFilterComponent, DialogComponent, FieldComponent, SelectComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.catalog.filters.';
    <furar-dialog [heading]="t + 'title' | transloco">
      <button dialogHeadAction type="button" furarButton="quiet" size="sm" data-filters-clear (click)="clear()">
        {{ t + 'clear' | transloco }}
      </button>
      <div class="f-mfilters">
        @if (kindOptions().length > 1) {
          <furar-field
            class="f-mfilters__group"
            data-filter="kind"
            [fieldId]="ids.kind"
            [label]="t + 'kind' | transloco"
          >
            <furar-select
              [selectId]="ids.kind"
              [label]="t + 'kind' | transloco"
              [options]="kindOptions()"
              [value]="kind()"
              (changed)="kind.set($event)"
            />
          </furar-field>
        }
        @if (!wide) {
          <div class="f-mfilters__group">
            <p class="f-mfilters__label" [id]="ids.date">{{ t + 'date' | transloco }}</p>
            <furar-date-filter
              single
              role="group"
              [attr.aria-labelledby]="ids.date"
              [(from)]="date"
              [today]="data.today"
              [min]="data.today"
              [format]="dayText"
            />
          </div>
        }
        @if (filtersFacilities()) {
          @if (data.options.length) {
            <furar-field
              class="f-mfilters__group"
              data-filter="sport"
              [fieldId]="ids.sport"
              [label]="t + 'sport' | transloco"
            >
              <furar-select
                multiple
                [selectId]="ids.sport"
                [label]="t + 'sport' | transloco"
                [placeholder]="t + 'anySport' | transloco"
                [options]="data.options"
                [value]="value()"
                (changedMany)="value.set($event)"
              />
            </furar-field>
          }
        }
      </div>

      <ng-container dialogActions>
        <button type="button" furarButton="primary" data-filters-apply (click)="apply()">
          @switch (kind()) {
            @case ('matches') {
              {{ t + 'showMatches' | transloco }}
            }
            @case ('events') {
              {{ t + 'showEvents' | transloco }}
            }
            @case ('all') {
              @if (kindOptions().length > 1) {
                {{ t + (count() === 1 ? 'showResultsOne' : 'showResultsOther') | transloco: { count: count() } }}
              } @else {
                {{ t + (count() === 1 ? 'showOne' : 'showOther') | transloco: { count: count() } }}
              }
            }
            @default {
              {{ t + (count() === 1 ? 'showOne' : 'showOther') | transloco: { count: count() } }}
            }
          }
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class SearchFiltersDialogComponent {
  protected readonly data = inject<SearchFiltersData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<SearchFiltersResult>>(DialogRef);

  private readonly id = nextId++;
  protected readonly ids = {
    date: `furar-search-filters-${this.id}-date`,
    sport: `furar-search-filters-${this.id}-sport`,
    kind: `furar-search-filters-${this.id}-kind`,
  };

  protected readonly value = signal<readonly string[]>(this.data.value);
  protected readonly date = signal<IsoDate | null>(this.data.date);
  protected readonly count = computed(() => this.data.count(this.value()));
  protected readonly kind = signal<SearchType>(this.data.kind ?? 'all');
  /** «Deporte» filtra as instalacións: só se ve con «Todo» ou con elas. */
  protected readonly filtersFacilities = computed(() => this.kind() === 'all' || this.kind() === 'facilities');
  /** Escritorio: o día xa vai na barra, así que a folla non o leva (nin o cambia). */
  protected readonly wide = typeof matchMedia === 'function' ? matchMedia(WIDE_QUERY).matches : false;
  /** As opcións de «Tipo»: as que ten a páxina (sen elas, todo e o campo non sae). */
  protected readonly kindOptions = computed<SelectOption<SearchType>[]>(() => {
    this.langChanges();
    return (this.data.kinds ?? []).map((kind) => ({
      value: kind,
      label: this.transloco.translate(`facility.catalog.filters.kinds.${kind}`),
    }));
  });

  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly language = inject(LanguageService);
  /** «Hoxe», «Mañá» ou «xov. 29»: o texto do chip, coma o día do buscador. */
  protected readonly dayText = (date: IsoDate): string =>
    dayLabel(date, this.data.today, this.language.lang(), {
      today: this.transloco.translate('home.search.today'),
      tomorrow: this.transloco.translate('home.search.tomorrow'),
    });

  protected clear(): void {
    this.value.set([]);
    if (!this.wide) this.date.set(null);
    this.kind.set('all');
  }

  protected apply(): void {
    this.ref.close({ value: this.value(), date: this.date(), kind: this.kind() });
  }
}

/**
 * «Tipo» dunha portada: o escollido na folla e o que ten a páxina (cada sección di ao cargar se
 * ten algo, `found`). Con «Todo», as instalacións e debaixo o resto; cun tipo, só el mentres haxa
 * algo (se non, todo).
 */
export class ListedKind {
  private readonly chosen = signal<SearchType>('all');
  private readonly present = signal<ReadonlySet<SearchKind>>(new Set());
  private readonly offered: readonly SearchKind[];
  /** O tipo que vale agora: o escollido ou, se dese non hai nada, todo. */
  readonly type = computed<SearchType>(() => {
    const chosen = this.chosen();
    return chosen === 'all' || chosen === 'facilities' || this.present().has(chosen) ? chosen : 'all';
  });
  /** O que se lista primeiro: as instalacións (con «Todo») ou o tipo escollido. */
  readonly listing = computed<SearchKind>(() => {
    const type = this.type();
    return type === 'all' ? 'facilities' : type;
  });
  /** Un tipo que non é «Todo» conta coma un filtro máis no botón «Filtros». */
  readonly count = computed(() => (this.type() === 'all' ? 0 : 1));

  /** @param offered o que pode listar a páxina ademais das instalacións, na orde das súas seccións */
  constructor(offered: readonly SearchKind[]) {
    this.offered = offered;
  }

  /** Se a sección de `kind` vai na páxina (con «Todo», todas; cun tipo, só a súa). */
  shows(kind: SearchKind): boolean {
    const type = this.type();
    return type === 'all' || type === kind;
  }

  /** A sección de `kind` cargou, con algo ou sen nada. */
  found(kind: SearchKind, some: boolean): void {
    this.present.update((present) => {
      const next = new Set(present);
      if (some) next.add(kind);
      else next.delete(kind);
      return next;
    });
  }

  /** Para a folla: as opcións de «Tipo» («Todo», as instalacións e o que hai) e o escollido. */
  sheet(): Pick<SearchFiltersData, 'kinds' | 'kind'> {
    const kinds = this.offered.filter((kind) => this.present().has(kind));
    return { kinds: kinds.length ? ['all', 'facilities', ...kinds] : [], kind: this.type() };
  }

  /** Garda o escollido na folla e devolve o resultado. */
  apply<T extends Pick<SearchFiltersResult, 'kind'>>(result: T): T {
    this.chosen.set(result.kind);
    return result;
  }

  /** Volve a «Todo» (p. ex. ao buscar ou co «✕» de «Filtros»). */
  reset(): void {
    this.chosen.set('all');
  }
}

/** Abre a folla (no móbil, desde abaixo) e resolve co escollido, ou `undefined` se se pecha sen máis. */
export function openSearchFilters(
  dialogs: DialogService,
  data: SearchFiltersData,
): Promise<SearchFiltersResult | undefined> {
  const ref = dialogs.open<SearchFiltersResult, SearchFiltersData>(SearchFiltersDialogComponent, {
    data,
    sheet: true,
  });
  return firstValueFrom(ref.closed, { defaultValue: undefined });
}
