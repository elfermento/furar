// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CdkListbox, CdkOption } from '@angular/cdk/listbox';
import type { ConnectedPosition } from '@angular/cdk/overlay';
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

let nextId = 0;

/** Unha opción: o valor que se garda e o texto que se ve (xa traducido). */
export interface SelectOption<T = unknown> {
  readonly value: T;
  readonly label: string;
  /** Vese pero non se pode escoller (o motivo explícao a axuda do campo). */
  readonly disabled?: boolean;
}

/**
 * Debaixo do campo e, se non cabe, enriba; aliñada á esquerda do campo ou, se unha opción longa a
 * fai saír da pantalla, á dereita. O ancho do campo vai como mínimo do panel (`minWidth`) e a lista
 * énchea (select.css): sen iso, a lista quedaba co ancho do seu texto dentro dun panel máis ancho.
 */
export const SELECT_POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 4 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -4 },
];

/**
 * Despregable de Furar (o `select` de toda a web): un botón co aspecto dun campo e o texto da
 * opción escollida que abre en CDK Overlay unha lista (`cdkListbox`) co foco na escollida. Funciona
 * con `formControlName`/`formControl` (ControlValueAccessor) ou co evento `changed`; os valores
 * compáranse con `===`. Non valida: o erro pono quen o usa con `invalid` e `describedBy`.
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
        [cdkListboxValue]="selection()"
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
  /** Valor sen formulario (con `formControlName` non fai falta). */
  readonly value = input<T | undefined>(undefined);
  /** Valor escollido polo usuario (tamén sen formulario). */
  readonly changed = output<T>();

  protected readonly listId = `furar-select-${nextId++}-list`;
  protected readonly positions = SELECT_POSITIONS;
  protected readonly open = signal(false);
  protected readonly width = signal(0);
  protected readonly chosen = signal<T | null>(null);
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();

  /** A escollida na lista; `null` tamén pode ser unha opción («Sen límite»). */
  protected readonly selection = computed(() => {
    const v = this.chosen();
    return this.options().some((o) => o.value === v) ? [v] : [];
  });

  constructor() {
    effect(() => {
      const v = this.value();
      if (v !== undefined) {
        this.chosen.set(v);
      }
    });
  }
  protected readonly text = computed(() => this.options().find((o) => o.value === this.chosen())?.label ?? '');

  private onChange: (value: T | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: T | null): void {
    this.chosen.set(value ?? null);
  }

  registerOnChange(fn: (value: T | null) => void): void {
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

  private pick(index: number): void {
    const option = this.options()[index];
    if (option?.disabled) {
      return;
    }
    if (option && option.value !== this.chosen()) {
      this.chosen.set(option.value);
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
