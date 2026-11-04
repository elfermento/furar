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
  forwardRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { END_OF_DAY, formatMinutes, timeOptions } from './time-format';

let nextId = 0;

/** Debaixo do botón e, se non cabe, enriba; aliñado á esquerda en ambos casos. */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
];

/**
 * Selector de hora (plan do dominio 1, D1-03): un botón coa hora en texto que abre en CDK Overlay
 * unha lista (`cdkListbox`) cada 15 minutos co foco dentro e desprazada ata a hora escollida.
 * O valor son minutos enteiros desde a medianoite (0–1440; 1440 = «24:00», só con `allowEndOfDay`).
 * Funciona con `formControlName` (ControlValueAccessor) e non valida: o erro pono quen o usa con
 * `invalid` e `describedBy`.
 *
 * Teclado: no botón, Intro, Espazo ou frechas abren. Na lista, frechas, Inicio/Fin e escribir a
 * hora moven; Intro ou Espazo escollen e pechan; Esc pecha sen cambiar; tras pechar, o foco volve
 * ao botón. Tab pecha sen cambiar e segue ao seguinte control, coma un `select` nativo.
 * O nome accesible leva o contexto que pasa a feature en `label` e a hora: «Luns, tramo 1, desde, 8:00».
 */
@Component({
  selector: 'furar-time-picker',
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, CdkListbox, CdkOption, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimePickerComponent), multi: true }],
  host: { class: 'f-time-picker' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      #trigger
      type="button"
      class="f-time-picker__button"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      aria-haspopup="listbox"
      [id]="buttonId()"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="open() ? listId : null"
      [attr.aria-label]="
        'ui.timePicker.name'
          | transloco
            : {
                label: required() ? label() + ' (' + ('ui.form.required' | transloco) + ')' : label(),
                time: text() || ('ui.timePicker.empty' | transloco),
              }
      "
      [attr.aria-invalid]="invalid() || null"
      [attr.aria-describedby]="describedBy() || null"
      [attr.data-empty]="text() ? null : ''"
      [disabled]="isDisabled()"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      {{ text() || ('ui.timePicker.empty' | transloco) }}
    </button>
    <ng-template
      cdkConnectedOverlay
      cdkConnectedOverlayUsePopover="inline"
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayDisableClose]="true"
      (overlayOutsideClick)="onOutsideClick($event)"
      (overlayKeydown)="onListKeydown($event)"
      (detach)="close(false)"
      (attach)="onAttach()"
    >
      <ul
        #list
        cdkListbox
        class="f-time-picker__list"
        [id]="listId"
        [attr.aria-label]="label()"
        [cdkListboxValue]="selection()"
      >
        @for (option of options(); track option) {
          <li class="f-time-picker__option" [cdkOption]="option" [attr.data-minutes]="option">
            {{ format(option) }}
          </li>
        }
      </ul>
    </ng-template>
  `,
})
export class TimePickerComponent implements ControlValueAccessor {
  private readonly language = inject(LanguageService);
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly listbox = viewChild(CdkListbox);
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  /** Contexto do nome accesible, sen a hora: «Luns, tramo 1, desde». Tamén nomea a lista. */
  readonly label = input.required<string>();
  /** Primeira hora que se ofrece, en minutos (por defecto 0:00). */
  readonly min = input(0);
  /** Última hora que se ofrece, en minutos (por defecto 23:45, ou 24:00 con `allowEndOfDay`). */
  readonly max = input(END_OF_DAY);
  /** Ofrece «24:00» (fin dun tramo que pecha á medianoite, P12). */
  readonly allowEndOfDay = input(false, { transform: booleanAttribute });
  /** Marca o botón como inválido (aria-invalid e bordo de erro); o texto do erro é de quen chama. */
  /** Campo obrigatorio: o nome do botón leva «(obrigatorio)» (un botón non admite `aria-required`). */
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  /** Id do erro ou da axuda que describe o control. */
  readonly describedBy = input<string | null>(null);
  /** Id do botón, para un `<label for>` externo se fai falta. */
  readonly buttonId = input(`furar-time-picker-${nextId}`);

  protected readonly listId = `furar-time-picker-${nextId++}-list`;
  protected readonly positions = POSITIONS;
  protected readonly open = signal(false);
  protected readonly value = signal<number | null>(null);
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();

  protected readonly options = computed(() => timeOptions(this.min(), this.max(), this.allowEndOfDay()));
  protected readonly selection = computed(() => {
    const v = this.value();
    return v === null ? [] : [v];
  });
  protected readonly text = computed(() => {
    const v = this.value();
    return v === null ? '' : formatMinutes(v, this.language.lang());
  });

  private onChange: (value: number | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  protected format(minutes: number): string {
    return formatMinutes(minutes, this.language.lang());
  }

  writeValue(value: number | null): void {
    this.value.set(typeof value === 'number' && Number.isInteger(value) ? value : null);
  }

  registerOnChange(fn: (value: number | null) => void): void {
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
      this.open.set(true);
    }
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && !this.open()) {
      event.preventDefault();
      this.open.set(true);
    }
  }

  /** Ao abrir: despraza ata a hora escollida (ou a primeira) e leva o foco á lista. */
  protected onAttach(): void {
    afterNextRender(
      () => {
        const host = this.list()?.nativeElement;
        const current =
          host?.querySelector<HTMLElement>('[aria-selected="true"]') ??
          host?.querySelector<HTMLElement>('[role="option"]');
        current?.scrollIntoView({ block: 'center' });
        this.listbox()?.focus();
        // Esc na propia lista, antes de que suba ao formulario ou á fila (ver onEscape).
        host?.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            this.onEscape(event);
          }
        });
        // Picar escolle e pecha tamén se a hora xa era a escollida (o CDK só avisa se cambia).
        host?.addEventListener('click', (event) => {
          const minutes = (event.target as HTMLElement).closest<HTMLElement>('[data-minutes]')?.dataset['minutes'];
          if (minutes !== undefined) {
            this.pick(Number(minutes));
          }
        });
      },
      { injector: this.injector },
    );
  }

  /**
   * Esc na propia lista, antes de que suba: a capa vai no DOM xunto ao botón (popover «inline»),
   * dentro do formulario ou da fila que a contén, e o Esc deles pecharía o formulario enteiro. Só
   * pecha a lista e o foco volve ao botón.
   */
  protected onEscape(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.close(true);
  }

  /** Teclas da capa (o CDK xa moveu ou marcou a opción): escoller e Tab. */
  protected onListKeydown(event: KeyboardEvent): void {
    if (event.key === 'Tab') {
      // O foco volve ao botón antes da acción por defecto: Tab segue desde el, como nun select.
      this.close(true);
    } else if (event.key === 'Enter' || event.key === ' ') {
      const minutes = (event.target as HTMLElement).dataset['minutes'];
      if (minutes !== undefined) {
        event.preventDefault();
        this.pick(Number(minutes));
      }
    }
  }

  protected onOutsideClick(event: MouseEvent): void {
    // O clic no propio botón xa o pecha toggle(); aquí só os de fóra.
    if (!this.trigger().nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }

  protected pick(minutes: number): void {
    if (minutes !== this.value()) {
      this.value.set(minutes);
      this.onChange(minutes);
    }
    this.close(true);
  }

  /** Pecha a lista; con `restoreFocus` o foco volve ao botón. */
  protected close(restoreFocus: boolean): void {
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
