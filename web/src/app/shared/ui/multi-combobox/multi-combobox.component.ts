// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  inject,
  input,
  model,
  numberAttribute,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, from } from 'rxjs';
import type { ControlError } from '../../custom-fields/custom-field-controls';
import type { ChipItem } from '../chip-list/chip-list.component';
import { ChipListComponent } from '../chip-list/chip-list.component';
import type { ComboboxSource } from '../combobox/combobox.component';
import { ComboboxComponent } from '../combobox/combobox.component';
import { describedBy } from '../field-errors';
import { FieldComponent } from '../field.component';

/**
 * Campo de varios valores con busca (docs/deseno.md §8): dentro de `furar-field` (rótulo pequeno
 * dentro da cápsula), un `furar-combobox` con `clearOnSelect` e, debaixo, os escollidos en
 * `furar-chip-list`. É a única versión deste patrón (deportes da instalación, instalacións dos
 * filtros das reservas).
 *
 * Valor: `selected` (model), na orde en que se escolleron; `key` identifica cada valor, `display`
 * dálle o texto e `outline` marca os que levan un fino bordo. As suxestións son as da fonte sen as
 * xa escollidas e, con `limit`, como moito esas. Escoller engade e o foco queda no campo; Retroceso
 * co campo baleiro quita o último; tras quitar cun «×», o foco vai ao seguinte ou ao campo. Cada
 * alta e baixa anúnciase («Engadiuse Pádel.» e, con `countText`, «2 deportes escollidos.»).
 * Con `allowCreate`, «Crear «texto»» emite `create` co texto: a feature crea e chama a `add`.
 */
@Component({
  selector: 'furar-multi-combobox',
  imports: [ChipListComponent, ComboboxComponent, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-multi-combobox' },
  template: `
    <furar-field [fieldId]="fieldId()" [label]="label()" [tip]="tip()" [required]="required()" [error]="error()">
      <furar-combobox
        #combobox
        clearOnSelect
        [highlight]="highlight()"
        [debounce]="debounce()"
        [allowCreate]="allowCreate()"
        [createLabel]="createLabel()"
        [inputId]="fieldId()"
        [label]="label()"
        [source]="suggestions()"
        [invalid]="!!error()"
        [describedBy]="describedBy(fieldId(), !!tip(), error())"
        (selected)="add($event.value)"
        (create)="create.emit($event)"
        (keydown)="onFieldKeydown($event)"
      />
      <furar-chip-list
        [items]="chips()"
        [label]="chosenLabel()"
        [status]="status()"
        [fallbackFocus]="combobox"
        (removed)="remove($event)"
      />
    </furar-field>
  `,
})
export class MultiComboboxComponent<T = unknown> {
  private readonly transloco = inject(TranslocoService);
  private readonly combobox = viewChild.required<ComboboxComponent<T>>('combobox');

  /** Id do campo (o `for` do rótulo). */
  readonly fieldId = input.required<string>();
  /** Rótulo do campo e nome das suxestións. */
  readonly label = input.required<string>();
  /** Nome da lista dos escollidos («Deportes escollidos»). */
  readonly chosenLabel = input.required<string>();
  /** Fonte das suxestións para o texto escrito; as xa escollidas quítanse aquí. */
  readonly source = input.required<ComboboxSource<T>>();
  /** Clave estable de cada valor (para saber se xa está e para o `track`). */
  readonly key = input.required<(value: T) => string>();
  /** Texto de cada valor, xa traducido. */
  readonly display = input.required<(value: T) => string>();
  /** Valores cun fino bordo no chip (p. ex. os deportes propios do concello). */
  readonly outline = input<(value: T) => boolean>(() => false);
  /** Texto do total para o anuncio («2 deportes escollidos»); sen el, só a alta ou a baixa. */
  readonly countText = input<((count: number) => string) | null>(null);
  /** Suxestións que se ensinan como moito (0: todas). */
  readonly limit = input(0, { transform: numberAttribute });
  /** Espera sen escribir antes de buscar, en ms (0 para unha lista que xa está en memoria). */
  readonly debounce = input(300, { transform: numberAttribute });
  readonly highlight = input(false, { transform: booleanAttribute });
  readonly allowCreate = input(false, { transform: booleanAttribute });
  /** Verbo da opción de crear, xa traducido («Crear deporte»). */
  readonly createLabel = input('');
  /** Axuda nun «?» ao carón do rótulo. */
  readonly tip = input('');
  readonly required = input(false, { transform: booleanAttribute });
  readonly error = input<ControlError | null>(null);

  /** Os valores escollidos. */
  readonly selected = model<readonly T[]>([]);
  /** Escolleuse «Crear»: o texto escrito. */
  readonly create = output<string>();

  protected readonly describedBy = describedBy;
  /** Última alta ou baixa, para o `role="status"` da lista. */
  protected readonly status = signal('');
  protected readonly chips = computed(() => {
    const key = this.key();
    const display = this.display();
    const outline = this.outline();
    return this.selected().map((value): ChipItem => ({
      key: key(value),
      label: display(value),
      outline: outline(value),
    }));
  });

  /** A fonte de quen chama, sen os xa escollidos e cortada a `limit`. */
  protected readonly suggestions = computed((): ComboboxSource<T> => {
    const source = this.source();
    return async (query) => {
      const chosen = new Set(this.selected().map(this.key()));
      const options = await firstValueFrom(from(source(query)));
      const fresh = options.filter((o) => !chosen.has(this.key()(o.value)));
      return this.limit() ? fresh.slice(0, this.limit()) : fresh;
    };
  });

  /** Engade un valor (se non está xa) e anúnciao; tamén para o que crea a feature. */
  add(value: T): void {
    const key = this.key();
    if (this.selected().some((other) => key(other) === key(value))) return;
    this.selected.set([...this.selected(), value]);
    this.announce('added', value);
  }

  /** Leva o foco ao campo. */
  focus(): void {
    this.combobox().focus();
  }

  /** Quita o valor (o foco lévao `furar-chip-list` ou queda no campo, se foi con Retroceso). */
  protected remove(index: number): void {
    const value = this.selected()[index];
    if (value === undefined) return;
    this.selected.set(this.selected().filter((_, i) => i !== index));
    this.announce('removed', value);
  }

  /** Retroceso co campo baleiro quita o último escollido; o foco segue no campo. */
  protected onFieldKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Backspace' || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const count = this.selected().length;
    if ((event.target as HTMLInputElement).value !== '' || !count) return;
    event.preventDefault();
    this.remove(count - 1);
  }

  private announce(action: 'added' | 'removed', value: T): void {
    const done = this.transloco.translate('ui.multiCombobox.' + action, { name: this.display()(value) });
    const countText = this.countText();
    this.status.set(countText ? `${done} ${countText(this.selected().length)}.` : done);
  }
}
