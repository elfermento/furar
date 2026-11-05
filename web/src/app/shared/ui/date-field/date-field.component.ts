// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
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
import { CalendarComponent } from '../calendar/calendar.component';
import type { IsoDate } from '../calendar/local-date';
import { formatShort, isIsoDate } from '../calendar/local-date';
import { IconComponent } from '../icon/icon.component';

let nextId = 0;

/** Atallo do panel: o texto do botón (xa traducido) e a data que escolle. */
export interface DateShortcut {
  readonly label: string;
  readonly date: IsoDate;
}

/** Debaixo do botón e, se non cabe, enriba; aliñado á esquerda en ambos casos. */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
];

/** Na barra (look="pill"): aliñado á dereita do botón, un pouco máis separado. */
const PILL_POSITIONS: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 12 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -12 },
];

/**
 * Campo de data (plan do dominio 1, D1-04): un botón coa data en texto («24/12/2026», co formato
 * do idioma) que abre en CDK Overlay un diálogo co `furar-calendar` e o foco no día escollido (ou
 * hoxe). O valor é a data ISO (`LocalDate` da API, «2026-12-24») ou `null`. Funciona con
 * `formControlName` (ControlValueAccessor) e non valida: o erro pono quen o usa con `invalid` e
 * `describedBy`. Só data simple; un rango son dous campos (P14).
 *
 * Teclado: Intro, Espazo ou frecha abaixo abren. No diálogo, o foco queda dentro (Tab entre os
 * atallos, as frechas do mes e a grella); escoller un día pecha e devolve o foco ao botón; Esc
 * pecha sen cambiar e tamén devolve o foco. O nome accesible leva o contexto e a data: «Desde,
 * 24/12/2026».
 *
 * Opcional: `shortcuts` (botóns enriba do mes, p. ex. «Hoxe» e «Mañá», con `aria-pressed`),
 * `displayWith` (texto da data no botón) e `look="pill"` (botón sen marco dentro dunha barra de
 * busca, cunha icona propia proxectada con `[furarDateFieldIcon]` e unha frecha).
 */
@Component({
  selector: 'furar-date-field',
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, CalendarComponent, IconComponent, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateFieldComponent), multi: true }],
  host: { class: 'f-date-field', '[attr.data-look]': 'look()' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      #trigger
      type="button"
      class="f-date-field__button"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      aria-haspopup="dialog"
      [id]="buttonId()"
      [attr.aria-expanded]="open()"
      [attr.aria-label]="
        'ui.dateField.name'
          | transloco
            : {
                label: required() ? label() + ' (' + ('ui.form.required' | transloco) + ')' : label(),
                date: text() || ('ui.dateField.empty' | transloco),
              }
      "
      [attr.aria-invalid]="invalid() || null"
      [attr.aria-describedby]="describedBy() || null"
      [attr.data-empty]="text() ? null : ''"
      [disabled]="isDisabled()"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      @if (look() === 'pill') {
        <ng-content select="[furarDateFieldIcon]" />
      } @else {
        <furar-icon name="calendar" />
      }
      <span class="f-date-field__text">{{ text() || ('ui.dateField.empty' | transloco) }}</span>
      @if (look() === 'pill') {
        <furar-icon name="chevron-down" class="f-date-field__chevron" />
      }
    </button>
    <ng-template
      cdkConnectedOverlay
      cdkConnectedOverlayUsePopover="inline"
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions()"
      [cdkConnectedOverlayViewportMargin]="8"
      [cdkConnectedOverlayPush]="true"
      [cdkConnectedOverlayDisableClose]="true"
      (overlayOutsideClick)="onOutsideClick($event)"
      (overlayKeydown)="onPanelKeydown($event)"
      (detach)="close(false)"
      (attach)="onAttach()"
    >
      <div
        #panel
        class="f-date-field__panel"
        [attr.data-look]="look()"
        role="dialog"
        aria-modal="true"
        (keydown.escape)="onEscape($event)"
        [attr.aria-label]="'ui.dateField.dialog' | transloco: { label: label() }"
      >
        @if (shortcuts().length) {
          <div class="f-date-field__shortcuts">
            @for (shortcut of shortcuts(); track shortcut.date) {
              <button
                type="button"
                class="f-date-field__shortcut"
                [attr.aria-pressed]="shortcut.date === value()"
                (click)="pick(shortcut.date)"
              >
                {{ shortcut.label }}
              </button>
            }
          </div>
        }
        <furar-calendar
          [value]="value()"
          [min]="min()"
          [max]="max()"
          [dateDisabled]="dateDisabled()"
          [today]="today()"
          (picked)="pick($event)"
        />
      </div>
    </ng-template>
  `,
})
export class DateFieldComponent implements ControlValueAccessor {
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly calendar = viewChild(CalendarComponent);
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  /** Contexto do nome accesible, sen a data: «Desde», «Data do bloqueo». Tamén nomea o diálogo. */
  readonly label = input.required<string>();
  /** Primeira data que se pode escoller (ISO, incluída). */
  readonly min = input<IsoDate | null>(null);
  /** Última data que se pode escoller (ISO, incluída). */
  readonly max = input<IsoDate | null>(null);
  /** Outros días que non se poden escoller. */
  readonly dateDisabled = input<(date: IsoDate) => boolean>(() => false);
  /** «Hoxe» da API (`today`); se falta, o de Madrid co reloxo do navegador (o calendario). */
  readonly today = input<IsoDate | null>(null);
  /** Marca o botón como inválido (aria-invalid e bordo de erro); o texto do erro é de quen chama. */
  /** Campo obrigatorio: o nome do botón leva «(obrigatorio)» (un botón non admite `aria-required`). */
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  /** Id do erro ou da axuda que describe o control. */
  readonly describedBy = input<string | null>(null);
  /** Id do botón, para un `<label for>` externo se fai falta. */
  readonly buttonId = input(`furar-date-field-${nextId++}`);
  /** Aspecto: campo de formulario (por defecto) ou botón sen marco dentro dunha barra. */
  readonly look = input<'field' | 'pill'>('field');
  /** Atallos enriba do mes («Hoxe», «Mañá»): texto xa traducido e data ISO. */
  readonly shortcuts = input<readonly DateShortcut[]>([]);
  /** Texto da data no botón e no seu nome; por defecto, «24/12/2026». */
  readonly displayWith = input<(date: IsoDate) => string>(formatShort);

  protected readonly positions = computed(() => (this.look() === 'pill' ? PILL_POSITIONS : POSITIONS));
  protected readonly open = signal(false);
  protected readonly value = signal<IsoDate | null>(null);
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();
  protected readonly text = computed(() => {
    const v = this.value();
    return v === null ? '' : this.displayWith()(v);
  });

  private onChange: (value: IsoDate | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: unknown): void {
    this.value.set(isIsoDate(value) ? value : null);
  }

  registerOnChange(fn: (value: IsoDate | null) => void): void {
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
    if (event.key === 'ArrowDown' && !this.open()) {
      event.preventDefault();
      this.open.set(true);
    }
  }

  /** Ao abrir, o foco vai ao día escollido (ou a hoxe) dentro da grella. */
  protected onAttach(): void {
    afterNextRender(() => this.calendar()?.focusActive(), { injector: this.injector });
  }

  /**
   * Esc no propio panel, antes de que suba: a capa vai no DOM xunto ao botón (popover «inline»),
   * dentro do formulario que a contén, e o Esc del pecharía o formulario enteiro.
   */
  protected onEscape(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.close(true);
  }

  protected onPanelKeydown(event: KeyboardEvent): void {
    if (event.key === 'Tab') {
      this.keepFocusInside(event);
    }
  }

  /**
   * Diálogo modal: Tab dá a volta entre as frechas do mes e o día itinerante. Á man e non con
   * `cdkTrapFocus`, porque as súas áncoras (tabindex=0 con aria-hidden) fan saltar axe.
   */
  private keepFocusInside(event: KeyboardEvent): void {
    const stops = [...(this.panel()?.nativeElement.querySelectorAll<HTMLElement>('button, [tabindex="0"]') ?? [])];
    if (!stops.length) {
      return;
    }
    const index = stops.indexOf(event.target as HTMLElement);
    const next = event.shiftKey ? index - 1 : index + 1;
    event.preventDefault();
    stops[(next + stops.length) % stops.length].focus();
  }

  protected onOutsideClick(event: MouseEvent): void {
    // O clic no propio botón xa o pecha toggle(); aquí só os de fóra.
    if (!this.trigger().nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }

  protected pick(date: IsoDate): void {
    if (date !== this.value()) {
      this.value.set(date);
      this.onChange(date);
    }
    this.close(true);
  }

  /** Pecha o panel; con `restoreFocus` o foco volve ao botón. */
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
