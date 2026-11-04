// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
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

/**
 * Pasos dun asistente (maqueta 53): en pantalla ancha, unha lista numerada de botóns co número (ou
 * ✓ se está feito, ! se falta algo), o nome e «Opcional»; o actual leva `aria-current="step"` e os
 * que aínda non se visitaron están desactivados. O estado vai tamén en texto para o lector (non só
 * coa marca, 1.4.1). No estreito, «Paso 3 de 8», o nome e unha barra de progreso. Quen o usa
 * decide que pasa ao escoller un paso (`go`).
 */
@Component({
  selector: 'furar-stepper',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-stepper' },
  template: `
    <nav class="f-stepper__nav" [attr.aria-label]="label()">
      <ol class="f-stepper__list">
        @for (step of steps(); track $index; let i = $index) {
          <li class="f-stepper__item" [attr.data-state]="step.state">
            <button
              type="button"
              class="f-stepper__step"
              [attr.data-step]="i"
              [attr.aria-current]="i === current() ? 'step' : null"
              [disabled]="i !== current() && !step.reachable"
              (click)="i !== current() && go.emit(i)"
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
    @if (currentStep(); as step) {
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
  readonly steps = input.required<readonly StepperStep[]>();
  /** Índice do paso actual. */
  readonly current = input.required<number>();
  /** Nome da navegación e da barra («Pasos de Nova instalación»). */
  readonly label = input.required<string>();
  /** Escolleu outro paso dos que se poden visitar. */
  readonly go = output<number>();

  protected readonly currentStep = computed(() => this.steps()[this.current()] ?? null);
}
