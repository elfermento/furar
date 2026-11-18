// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CdkListbox, CdkOption } from '@angular/cdk/listbox';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { SelectOption } from './select-option';
import { SELECT_POSITIONS } from './select-option';

let nextId = 0;

export type { SelectOption } from './select-option';
export { SELECT_POSITIONS } from './select-option';

/**
 * Despregable de Furar (o único `select` de toda a web, de unha ou de varias opcións): un botón co
 * aspecto dun campo e o texto da opción escollida que abre en CDK Overlay unha lista vertical
 * (`cdkListbox`) co foco na escollida; a escollida leva ✓ á esquerda. Dentro de `furar-field` vai
 * na cápsula do campo, co rótulo dentro (styles/components/fields.css). Funciona
 * con `formControlName`/`formControl` (ControlValueAccessor) ou co evento `changed`; os valores
 * compáranse con `===`. Non valida: o erro pono quen o usa con `invalid` e `describedBy`.
 *
 * Con `multiple` o valor é unha lista (`T[]`, na orde das opcións): a lista ten
 * `aria-multiselectable`, picar ou Espazo/Intro marcan e desmarcan e non se pecha ao escoller (pecha
 * picando fóra, con Esc ou con Tab); o botón amosa as escollidas separadas por comas.
 *
 * Teclado coma un `select` nativo: no botón, Intro, Espazo ou frechas abren. Na lista, frechas,
 * Inicio/Fin e escribir as primeiras letras moven; Intro ou Espazo escollen e pechan; Esc pecha
 * sen cambiar e Tab pecha e segue. O foco volve sempre ao botón. O nome accesible leva o rótulo
 * e o valor («Idioma por defecto, Galego»); `label` ten que ser o texto do rótulo visible (2.5.3).
 */
@Component({
  selector: 'furar-select',
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, CdkListbox, CdkOption, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SelectComponent), multi: true }],
  host: { class: 'f-select' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      #trigger
      type="button"
      class="f-select__button"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      aria-haspopup="listbox"
      [id]="selectId()"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="open() ? listId : null"
      [attr.aria-label]="
        (required() ? label() + ' (' + ('ui.form.required' | transloco) + ')' : label()) +
        ', ' +
        (text() || placeholder())
      "
      [attr.aria-invalid]="invalid() || null"
      [attr.aria-describedby]="describedBy() || null"
      [attr.data-empty]="text() ? null : ''"
      [disabled]="isDisabled()"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      <span class="f-select__text">{{ text() || placeholder() }}</span>
    </button>
    <ng-template
      cdkConnectedOverlay
      cdkConnectedOverlayUsePopover="inline"
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayMinWidth]="width()"
      [cdkConnectedOverlayDisableClose]="true"
      (overlayOutsideClick)="onOutsideClick($event)"
      (overlayKeydown)="onListKeydown($event)"
      (detach)="close(false)"
      (attach)="onAttach()"
    >
      <ul
        #list
        cdkListbox
        class="f-select__list"
        [id]="listId"
        [attr.aria-label]="label()"
        [cdkListboxMultiple]="multiple()"
        [cdkListboxValue]="selection()"
        (cdkListboxValueChange)="onListboxChange($event.value)"
      >
        @for (option of options(); track $index) {
          <li
            class="f-select__option"
            [cdkOption]="option.value"
            [cdkOptionDisabled]="!!option.disabled"
            [attr.data-index]="$index"
          >
            {{ option.label }}
          </li>
        }
      </ul>
    </ng-template>
  `,
})
export class SelectComponent<T = unknown> implements ControlValueAccessor {
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly listbox = viewChild(CdkListbox);
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  /** Texto do rótulo visible; nomea o botón (co valor) e a lista. */
  readonly label = input.required<string>();
  readonly options = input.required<readonly SelectOption<T>[]>();
  /** Texto sen nada escollido. */
  readonly placeholder = input('');
  /** Marca o campo como inválido (aria-invalid e bordo de erro); o texto do erro é de quen chama. */
  /** Campo obrigatorio: o nome do botón leva «(obrigatorio)» (un botón non admite `aria-required`). */
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  /** Id do erro ou da axuda que describe o control. */
  readonly describedBy = input<string | null>(null);
  /** Id do botón, para un `<label for>` externo. */
  readonly selectId = input(`furar-select-${nextId}`);
  /** Valor sen formulario (con `formControlName` non fai falta); con `multiple`, unha lista. */
  readonly value = input<T | readonly T[] | undefined>(undefined);
  /** Varias opcións á vez: o valor é unha lista e a lista non se pecha ao escoller. */
  readonly multiple = input(false, { transform: booleanAttribute });
  /** Valor escollido polo usuario (tamén sen formulario). */
  readonly changed = output<T>();
  /** Con `multiple`: as escollidas despois de cada cambio (tamén sen formulario). */
  readonly changedMany = output<readonly T[]>();

  protected readonly listId = `furar-select-${nextId++}-list`;
  protected readonly positions = SELECT_POSITIONS;
  protected readonly open = signal(false);
  protected readonly width = signal(0);
  /** O valor escrito: un só (tamén `null`) ou, con `multiple`, a lista. */
  protected readonly chosen = signal<readonly (T | null)[]>([]);
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();

  /** As escollidas na lista, na orde das opcións; `null` tamén pode ser unha opción («Sen límite»). */
  private readonly picked = computed(() => this.options().filter((o) => this.chosen().includes(o.value)));
  protected readonly selection = computed(() => this.picked().map((o) => o.value));
  protected readonly text = computed(() =>
    this.picked()
      .map((o) => o.label)
      .join(', '),
  );

  constructor() {
    effect(() => {
      const v = this.value();
      if (v !== undefined) {
        this.writeValue(v);
      }
    });
  }

  private onChange: (value: T | readonly T[] | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: T | readonly T[] | null): void {
    const many = this.multiple() ? (Array.isArray(value) ? (value as readonly T[]) : []) : null;
    this.chosen.set(many ?? [(value ?? null) as T | null]);
  }

  registerOnChange(fn: (value: T | readonly T[] | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabledByForm.set(disabled);
    if (disabled) {
      this.close(false);
    }
  }

  protected toggle(): void {
    if (this.open()) {
      this.close(true);
    } else {
      this.show();
    }
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && !this.open()) {
      event.preventDefault();
      this.show();
    }
  }

  private show(): void {
    this.width.set(this.trigger().nativeElement.getBoundingClientRect().width);
    this.open.set(true);
  }

  /** Ao abrir: despraza ata a escollida (ou a primeira) e leva o foco á lista. */
  protected onAttach(): void {
    afterNextRender(
      () => {
        const host = this.list()?.nativeElement;
        const current =
          host?.querySelector<HTMLElement>('[aria-selected="true"]') ??
          host?.querySelector<HTMLElement>('[role="option"]');
        current?.scrollIntoView?.({ block: 'nearest' });
        this.listbox()?.focus();
        // Esc na propia lista, antes de que suba ao formulario ou ao diálogo que a contén.
        host?.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            this.close(true);
          }
        });
        // Picar escolle e pecha tamén se xa era a escollida (o CDK só avisa se cambia).
        host?.addEventListener('click', (event) => {
          const index = (event.target as HTMLElement).closest<HTMLElement>('[data-index]')?.dataset['index'];
          if (index !== undefined) {
            this.pick(Number(index));
          }
        });
      },
      { injector: this.injector },
    );
  }

  /** Teclas da capa (o CDK xa moveu a opción activa): escoller e Tab. */
  protected onListKeydown(event: KeyboardEvent): void {
    if (event.key === 'Tab') {
      this.close(true);
    } else if (event.key === 'Enter' || event.key === ' ') {
      if (this.multiple()) {
        // O CDK marca ou desmarca a activa (cdkListboxValueChange); a lista segue aberta.
        return;
      }
      const index = (event.target as HTMLElement).dataset['index'];
      if (index !== undefined) {
        event.preventDefault();
        this.pick(Number(index));
      }
    }
  }

  protected onOutsideClick(event: MouseEvent): void {
    if (!this.trigger().nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }

  /** Con `multiple`: o CDK cambiou as marcadas (picar, Espazo ou Intro). */
  protected onListboxChange(values: readonly (T | null)[]): void {
    if (!this.multiple()) {
      return;
    }
    const next = this.options()
      .filter((o) => values.includes(o.value))
      .map((o) => o.value);
    this.chosen.set(next);
    this.onChange(next);
    this.changedMany.emit(next);
  }

  private pick(index: number): void {
    const option = this.options()[index];
    if (option?.disabled || this.multiple()) {
      return;
    }
    if (option && option.value !== this.chosen()[0]) {
      this.chosen.set([option.value]);
      this.onChange(option.value);
      this.changed.emit(option.value);
    }
    this.close(true);
  }

  /** Pecha a lista; con `restoreFocus` o foco volve ao botón. */
  close(restoreFocus: boolean): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    this.onTouched();
    if (restoreFocus) {
      this.trigger().nativeElement.focus();
    }
  }
}
