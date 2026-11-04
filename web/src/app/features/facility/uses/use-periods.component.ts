// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormArray } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { fieldError } from '../../../shared/ui/field-errors';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { PeriodForm } from './use-form';
import { USE_LIMITS, periodForm } from './use-form';

const OVERLAP = 'facility.use-period-overlap';

/**
 * Períodos dun uso «só unhas datas» (D1-22; P5 e P14): cada un con dous `furar-date-field`
 * («Desde» e «Ata», extremos incluídos), ✕ para quitalo e «+ Período». O erro de cada período vai
 * debaixo da súa fila; o da lista enteira («polo menos un»), debaixo do grupo.
 */
@Component({
  selector: 'furar-facility-use-periods',
  imports: [
    DeleteButtonComponent,
    ButtonDirective,
    DateFieldComponent,
    IconComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.uses.periods.';
    <fieldset class="gap-unit grid" [attr.aria-describedby]="listError() ? baseId + '-error' : null">
      <legend class="sr-only">{{ t + 'label' | transloco }}</legend>
      @for (group of periods().controls; track group; let i = $index) {
        <div class="gap-unit grid" data-period>
          <div class="gap-unit flex flex-wrap items-center" [formGroup]="group">
            <furar-date-field
              required
              formControlName="from"
              [label]="t + 'from' | transloco: { n: i + 1 }"
              [invalid]="!!rowError(i)"
              [describedBy]="rowError(i) ? baseId + '-' + i + '-error' : null"
            />
            <span aria-hidden="true">–</span>
            <furar-date-field
              required
              formControlName="to"
              [label]="t + 'to' | transloco: { n: i + 1 }"
              [min]="group.controls.from.value"
              [invalid]="!!rowError(i)"
              [describedBy]="rowError(i) ? baseId + '-' + i + '-error' : null"
            />
            @if (periods().length > 1) {
              <furar-delete-button [label]="t + 'remove' | transloco: { n: i + 1 }" (pressed)="remove(i)" />
            }
          </div>
          @if (rowError(i); as e) {
            <span class="f-field-error" [id]="baseId + '-' + i + '-error'">{{ e.key | transloco: e.params }}</span>
          }
        </div>
      }
      @if (periods().length < max) {
        <button type="button" furarButton="quiet" size="sm" class="justify-self-start" (click)="add()">
          <furar-icon name="plus" />
          {{ t + 'add' | transloco }}
        </button>
      }
      @if (listError(); as e) {
        <span class="f-field-error" [id]="baseId + '-error'">{{ e.key | transloco: e.params }}</span>
      }
    </fieldset>
  `,
})
export class UsePeriodsComponent {
  /** Os períodos do formulario do uso. */
  readonly periods = input.required<FormArray<PeriodForm>>();
  /** Prefixo dos `id` dos erros (un por formulario de uso). */
  readonly idPrefix = input.required<string>();

  protected readonly max = USE_LIMITS.periodsMax;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly events = toSignal(
    toObservable(this.periods).pipe(switchMap((array) => array.events.pipe(startWith(null)))),
  );

  protected get baseId(): string {
    return `${this.idPrefix()}-periods`;
  }

  /** Erros por fila: o de cada data, o do período (ata antes de desde) ou o solape. */
  private readonly rowErrors = computed<(ControlError | null)[]>(() => {
    this.events();
    const array = this.periods();
    const overlapAt = (array.errors?.[OVERLAP] as { index?: number } | undefined)?.index;
    return array.controls.map((group, i) => {
      const own =
        fieldError(group.controls.from, false) ?? fieldError(group.controls.to, false) ?? fieldError(group, false);
      if (own) return own;
      const touched = group.touched || array.touched;
      return overlapAt === i && touched ? { key: `errors.${OVERLAP}`, params: {} } : null;
    });
  });

  /** Erro da lista enteira: falta un período, demasiados ou o solape con outro uso que bloquea. */
  protected readonly listError = computed<ControlError | null>(() => {
    this.events();
    const array = this.periods();
    if (!array.errors || !array.touched) return null;
    const entry = Object.entries(array.errors).find(([code]) => code !== OVERLAP);
    if (!entry) return null;
    const [code, params] = entry;
    return { key: `errors.${code}`, params: (params as Record<string, unknown>) ?? {} };
  });

  protected rowError(i: number): ControlError | null {
    return this.rowErrors()[i] ?? null;
  }

  protected add(): void {
    this.periods().push(periodForm());
    this.periods().markAsDirty();
    this.focusRow(this.periods().length - 1);
  }

  /** Quita o período e leva o foco ao anterior (ou ao primeiro) para non perdelo. */
  protected remove(index: number): void {
    this.periods().removeAt(index);
    this.periods().markAsDirty();
    this.focusRow(Math.max(0, index - 1));
  }

  private focusRow(index: number): void {
    afterNextRender(
      () => {
        const rows = this.host.nativeElement.querySelectorAll<HTMLElement>('[data-period]');
        rows.item(index)?.querySelector<HTMLButtonElement>('button')?.focus();
      },
      { injector: this.injector },
    );
  }
}
