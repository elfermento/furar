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
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';
import type { SelectOption } from './select.component';

let nextId = 0;

/** Xusto debaixo do botón, aliñada á súa esquerda; se non cabe, á súa dereita e, se tampouco, enriba. */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 12 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 12 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -12 },
];

/**
 * Filtro de selección múltiple dentro dunha barra (o «Deporte» do buscador): un botón sen marco,
 * coma o do día (`furar-date-field` con `look="pill"`), que abre en CDK Overlay a lista de
 * `furar-select` con varias marcadas á vez. A lista só se ve ao pulsar o botón e segue aberta
 * mentres se marca; péchase picando fóra, con Esc (o foco volve ao botón) ou con Tab.
 *
 * O botón di o rótulo sen nada marcado, o nome da opción cunha soa e «N filtros» con varias; o nome
 * accesible leva sempre o rótulo e o marcado. Teclado da lista: frechas, Inicio/Fin e as primeiras
 * letras moven; Espazo ou Intro marcan e desmarcan. A icona ponse con `[furarMultiSelectIcon]`.
 */
@Component({
  selector: 'furar-multi-select',
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, CdkListbox, CdkOption, IconComponent, TranslocoPipe],
  host: { class: 'f-date-field f-multi-select', 'data-look': 'pill' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      #trigger
      type="button"
      class="f-date-field__button"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      aria-haspopup="listbox"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="open() ? listId : null"
      [attr.aria-label]="chosen().length ? label() + ', ' + summary() : label()"
      [disabled]="disabled()"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      <ng-content select="[furarMultiSelectIcon]" />
      <span class="f-date-field__text">
        @if (chosen().length > 1) {
          {{ 'ui.multiSelect.count' | transloco: { count: chosen().length } }}
        } @else {
          {{ chosen()[0]?.label ?? label() }}
        }
      </span>
      <furar-icon name="chevron-down" class="f-date-field__chevron" />
    </button>
    <ng-template
      cdkConnectedOverlay
      cdkConnectedOverlayUsePopover="inline"
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayViewportMargin]="8"
      [cdkConnectedOverlayPush]="true"
      [cdkConnectedOverlayDisableClose]="true"
      (overlayOutsideClick)="onOutsideClick($event)"
      (overlayKeydown)="onListKeydown($event)"
      (detach)="close(false)"
      (attach)="onAttach()"
    >
      <ul
        #list
        cdkListbox
        cdkListboxMultiple
        class="f-select__list f-multi-select__list"
        [id]="listId"
        [attr.aria-label]="label()"
        [cdkListboxValue]="value()"
        (cdkListboxValueChange)="changed.emit($event.value)"
      >
        @for (option of options(); track option.value) {
          <li class="f-select__option" [cdkOption]="option.value" [cdkOptionDisabled]="!!option.disabled">
            {{ option.label }}
          </li>
        }
      </ul>
    </ng-template>
  `,
})
export class MultiSelectComponent<T = unknown> {
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly listbox = viewChild(CdkListbox);
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  /** O que filtra («Deporte»): texto do botón sen nada marcado e nome da lista. */
  readonly label = input.required<string>();
  readonly options = input.required<readonly SelectOption<T>[]>();
  /** Os valores marcados (compáranse con `===`). */
  readonly value = input<readonly T[]>([]);
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Os valores marcados despois de cada cambio. */
  readonly changed = output<readonly T[]>();

  protected readonly listId = `furar-multi-select-${nextId++}-list`;
  protected readonly positions = POSITIONS;
  protected readonly open = signal(false);
  /** As opcións marcadas, na orde da lista. */
  protected readonly chosen = computed(() => this.options().filter((o) => this.value().includes(o.value)));
  protected readonly summary = computed(() =>
    this.chosen()
      .map((o) => o.label)
      .join(', '),
  );

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

  /** Ao abrir, o foco vai á lista; Esc trátase nela, antes de que suba ao formulario que a contén. */
  protected onAttach(): void {
    afterNextRender(
      () => {
        this.listbox()?.focus();
        this.list()?.nativeElement.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            this.close(true);
          }
        });
      },
      { injector: this.injector },
    );
  }

  protected onListKeydown(event: KeyboardEvent): void {
    if (event.key === 'Tab') {
      this.close(true);
    }
  }

  protected onOutsideClick(event: MouseEvent): void {
    if (!this.trigger().nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }

  /** Pecha a lista; con `restoreFocus` o foco volve ao botón. */
  close(restoreFocus: boolean): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    if (restoreFocus) {
      this.trigger().nativeElement.focus();
    }
  }
}
