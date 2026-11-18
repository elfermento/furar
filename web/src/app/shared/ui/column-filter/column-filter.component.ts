// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { FieldPopoverComponent } from '../field-popover/field-popover.component';
import { IconComponent } from '../icon/icon.component';
import { StarsComponent } from '../stars/stars.component';
import type { TagTone } from '../tag/tag.component';
import { TagComponent } from '../tag/tag.component';

/**
 * Unha opción do filtro dunha columna: o valor, o texto, un texto secundario debaixo (o nome dunha
 * persoa que se amosa polo correo), se se sabe, cantas filas a levan e, se o valor se pinta en
 * chip nas filas (un rol), o seu ton.
 */
export interface ColumnFilterOption {
  readonly value: string;
  readonly label: string;
  readonly hint?: string | null;
  readonly count?: number | null;
  /** Co ton, o texto vai nun chip (`furar-tag`) dese ton: o mesmo chip co que se pinta o valor nas filas. */
  readonly tone?: TagTone | null;
  /** Unha nota (1 a 5): vese en estrelas (`furar-stars`), coma nas filas; o texto queda para o lector. */
  readonly stars?: number | null;
}

/**
 * Filtro na cabeceira dunha columna: a cabeceira é un botón co nome da
 * columna e unha frecha que abre un panel ancorado (`furar-field-popover`, look `column`; no móbil,
 * folla desde abaixo). Con filtro posto, un punto na cabeceira e «Quitar» no panel.
 *
 * O panel leva as opcións (`options`) como botóns con `aria-pressed` e, se se sabe, cantas filas
 * hai de cada unha; con `searchable`, unha caixa que as filtra polo texto (o principal e o secundario). Contido proxectado
 * despois da lista (as datas «Desde» e «Ata» dunha columna de data). `variant="chip"` é o mesmo
 * botón coma unha pílula, para a fila de filtros do móbil, onde a táboa non ten cabeceiras.
 *
 * Non garda nada: `toggled` di a opción premida (a páxina decide se se suma ou substitúe) e
 * `cleared`, «Quitar». Pecha con Esc, co clic fóra ou, na folla, co botón `doneLabel`.
 */
@Component({
  selector: 'furar-column-filter',
  imports: [ButtonDirective, FieldPopoverComponent, IconComponent, StarsComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-column-filter', '[attr.data-variant]': 'variant()' },
  template: `
    <furar-field-popover
      #popover
      look="column"
      [label]="label()"
      [marked]="active()"
      [markedLabel]="'ui.columnFilter.active' | transloco"
      (closed)="query.set('')"
    >
      <div class="f-column-filter__panel" [attr.data-column-filter]="label()">
        <div class="f-column-filter__head">
          <span class="f-column-filter__title" aria-hidden="true">{{ label() }}</span>
          @if (active()) {
            <button type="button" furarButton="quiet" size="sm" data-column-clear (click)="cleared.emit()">
              {{ 'ui.columnFilter.clear' | transloco }}
            </button>
          }
        </div>
        @if (searchable()) {
          <label class="f-column-filter__search">
            <furar-icon name="search" />
            <span class="sr-only">{{ 'ui.columnFilter.search' | transloco: { label: label() } }}</span>
            <input
              type="search"
              data-column-search
              [placeholder]="'ui.columnFilter.searchPlaceholder' | transloco"
              [value]="query()"
              (input)="onSearch($event)"
            />
          </label>
        }
        <div class="f-column-filter__list" role="group" [attr.aria-label]="label()">
          @for (option of visible(); track option.value) {
            <button
              type="button"
              class="f-column-filter__option"
              [attr.aria-pressed]="isSelected(option.value)"
              [attr.data-option]="option.value"
              (click)="toggled.emit(option.value)"
            >
              <span class="f-column-filter__text">
                @if (option.stars) {
                  <furar-stars [value]="option.stars" aria-hidden="true" /><span class="sr-only">{{
                    option.label
                  }}</span>
                } @else if (option.tone) {
                  <furar-tag [tone]="option.tone">{{ option.label }}</furar-tag>
                } @else {
                  <span>{{ option.label }}</span>
                }
                @if (option.hint) {
                  {{ ' ' }}<span class="f-column-filter__hint">{{ option.hint }}</span>
                }
              </span>
              @if (option.count !== null && option.count !== undefined) {
                {{ ' ' }}<span class="f-column-filter__count">{{ option.count }}</span>
              }
            </button>
          } @empty {
            <p class="f-column-filter__empty">{{ 'ui.columnFilter.none' | transloco }}</p>
          }
        </div>
        <ng-content />
        @if (doneLabel()) {
          <button type="button" furarButton="primary" class="f-column-filter__done" (click)="close()">
            {{ doneLabel() }}
          </button>
        }
      </div>
    </furar-field-popover>
  `,
})
export class ColumnFilterComponent {
  private readonly popover = viewChild.required<FieldPopoverComponent>('popover');

  /** Nome da columna: o texto da cabeceira e o nome do panel. */
  readonly label = input.required<string>();
  readonly options = input<readonly ColumnFilterOption[]>([]);
  /** Os valores escollidos. */
  readonly selected = input<readonly string[]>([]);
  /** Ten filtro posto; por defecto, se hai algún valor escollido. */
  readonly marked = input<boolean | null>(null);
  /** Caixa de busca enriba das opcións (listas longas: accións, persoas). */
  readonly searchable = input(false, { transform: booleanAttribute });
  /** Cabeceira dunha táboa (por defecto) ou pílula da fila de filtros do móbil. */
  readonly variant = input<'header' | 'chip'>('header');
  /** Texto do botón que pecha a folla no móbil («Ver 12 accións»); baleiro, sen botón. */
  readonly doneLabel = input('');

  readonly toggled = output<string>();
  readonly cleared = output();

  protected readonly query = signal('');
  protected readonly active = computed(() => this.marked() ?? this.selected().length > 0);
  protected readonly visible = computed(() => {
    const query = normalize(this.query().trim());
    if (!query) return this.options();
    return this.options().filter((o) => normalize(`${o.label} ${o.hint ?? ''}`).includes(query));
  });

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  protected isSelected(value: string): boolean {
    return this.selected().includes(value);
  }

  /** Pecha o panel (tras escoller un período, por exemplo). */
  close(): void {
    this.popover().close();
  }
}

/** Sen maiúsculas nin acentos: «accion» atopa «Acción». */
function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();
}
