// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';

/** Como está un paso: feito, con algo que corrixir, o actual ou aínda sen visitar. */
export type StepState = 'done' | 'todo' | 'current' | 'pending';

/** Un paso do asistente: o seu nome (xa traducido), se é opcional, o estado e se se pode ir a el. */
export interface StepperStep {
  readonly label: string;
  readonly optional: boolean;
  readonly state: StepState;
  readonly reachable: boolean;
}

/** Fila de pasos enriba (`horizontal`) ou lista ao carón do paso (`vertical`). */
export type StepperLayout = 'horizontal' | 'vertical';

let nextId = 0;

/**
 * Pasos dun asistente: unha lista numerada de botóns co número (ou ✓ se está
 * feito, ! se falta algo), o nome e «Opcional»; o actual leva `aria-current="step"` e os que aínda
 * non se visitaron están desactivados. O estado vai tamén en texto para o lector (non só coa marca,
 * 1.4.1). Quen o usa decide que pasa ao escoller un paso (`go`).
 *
 * `horizontal`: en pantalla ancha, os pasos nunha fila; no estreito, «Paso 3 de 8», o nome e unha
 * barra de progreso. `vertical`: en pantalla ancha, a lista en columna (vai á esquerda do paso);
 * no estreito, un botón «Paso 3 de 8 · Nome» que abre a lista no sitio e que se pecha ao escoller,
 * ao picar fóra ou con Esc.
 */
@Component({
  selector: 'furar-stepper',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-stepper',
    '[attr.data-layout]': 'layout()',
    '(document:click)': 'onDocumentClick($event)',
    '(keydown.escape)': 'onEscape($event)',
  },
  template: `
    <nav class="f-stepper__nav" [attr.aria-label]="label()">
      @if (vertical() && currentStep(); as step) {
        <button
          #toggle
          type="button"
          class="f-stepper__toggle"
          data-stepper-toggle
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="listId"
          (click)="open.set(!open())"
        >
          <span class="f-stepper__toggle-text">
            <b>{{ 'ui.stepper.position' | transloco: { current: current() + 1, total: steps().length } }}</b>
            <span class="f-stepper__toggle-name">
              · {{ step.label }}
              @if (step.optional) {
                · {{ 'ui.stepper.optional' | transloco }}
              }
            </span>
          </span>
          <furar-icon class="f-stepper__chevron" name="chevron-down" />
        </button>
      }
      <ol class="f-stepper__list" [id]="listId" [attr.data-open]="open() ? '' : null">
        @for (step of steps(); track $index; let i = $index) {
          <li class="f-stepper__item" [attr.data-state]="step.state">
            <button
              type="button"
              class="f-stepper__step"
              [attr.data-step]="i"
              [attr.aria-current]="i === current() ? 'step' : null"
              [disabled]="i !== current() && !step.reachable"
              (click)="pick(i)"
            >
              <span class="f-stepper__mark" aria-hidden="true">
                @switch (step.state) {
                  @case ('done') {
                    <furar-icon name="check" [size]="20" />
                  }
                  @case ('todo') {
                    !
                  }
                  @default {
                    {{ i + 1 }}
                  }
                }
              </span>
              <span class="f-stepper__name">{{ step.label }}</span>
              @if (step.optional) {
                <span class="f-stepper__optional"
                  ><span class="sr-only">, </span>{{ 'ui.stepper.optional' | transloco }}</span
                >
              }
              @if (step.state !== 'pending') {
                <span class="sr-only">, {{ 'ui.stepper.state.' + step.state | transloco }}</span>
              }
            </button>
          </li>
        }
      </ol>
    </nav>
    @if (!vertical() && currentStep(); as step) {
      <div class="f-stepper__mini">
        <p class="f-stepper__mini-text">
          <b>{{ 'ui.stepper.position' | transloco: { current: current() + 1, total: steps().length } }}</b>
          <span
            >{{ step.label }}
            @if (step.optional) {
              · {{ 'ui.stepper.optional' | transloco }}
            }
          </span>
        </p>
        <div
          class="f-stepper__bar"
          role="progressbar"
          aria-valuemin="1"
          [attr.aria-label]="label()"
          [attr.aria-valuemax]="steps().length"
          [attr.aria-valuenow]="current() + 1"
          [attr.aria-valuetext]="
            ('ui.stepper.position' | transloco: { current: current() + 1, total: steps().length }) + ': ' + step.label
          "
        >
          <i [style.width.%]="((current() + 1) / steps().length) * 100"></i>
        </div>
      </div>
    }
  `,
})
export class StepperComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly toggle = viewChild<ElementRef<HTMLButtonElement>>('toggle');

  readonly steps = input.required<readonly StepperStep[]>();
  /** Índice do paso actual. */
  readonly current = input.required<number>();
  /** Nome da navegación e da barra («Pasos de Nova instalación»). */
  readonly label = input.required<string>();
  readonly layout = input<StepperLayout>('horizontal');
  /** Escolleu outro paso dos que se poden visitar. */
  readonly go = output<number>();

  protected readonly listId = `f-stepper-list-${nextId++}`;
  /** Lista aberta no estreito (só `vertical`). */
  protected readonly open = signal(false);
  protected readonly vertical = computed(() => this.layout() === 'vertical');
  protected readonly currentStep = computed(() => this.steps()[this.current()] ?? null);

  protected pick(index: number): void {
    this.open.set(false);
    if (index !== this.current()) this.go.emit(index);
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !event.composedPath().includes(this.host.nativeElement)) this.open.set(false);
  }

  protected onEscape(event: Event): void {
    if (!this.open()) return;
    event.stopPropagation();
    this.open.set(false);
    this.toggle()?.nativeElement.focus();
  }
}
