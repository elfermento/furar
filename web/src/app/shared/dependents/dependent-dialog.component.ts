// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef as HostRef,
  Injector,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ButtonDirective } from '../ui/button/button.directive';
import { CalloutComponent } from '../ui/callout/callout.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { describedBy, fieldError, focusFirstInvalid } from '../ui/field-errors';
import { FieldGroupComponent } from '../ui/field-group/field-group.component';
import { FieldComponent } from '../ui/field.component';
import { InputDirective } from '../ui/input/input.directive';
import type { DependentResponse } from './dependents.store';
import { DependentsStore, MAX_AGE } from './dependents.store';
import { validBirthYear } from './participants';

/** A ficha que se edita; sen ela, unha nova. */
export interface DependentDialogData {
  readonly dependent: DependentResponse | null;
}

let nextId = 0;

/** O ano de nacemento: catro cifras, do ano actual menos 120 ao actual (o texto do erro da API). */
function birthYear(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  if (!value) return null;
  return validBirthYear(value, new Date().getFullYear(), MAX_AGE)
    ? null
    : { 'people.dependent-birth-year-invalid': {} };
}

/** A declaración é obrigatoria: sen marcar, o texto do erro da API. */
function declared(control: AbstractControl<boolean>): ValidationErrors | null {
  return control.value ? null : { 'people.dependent-declaration-required': {} };
}

/**
 * «Persoa ao meu cargo»: engadir ou editar unha ficha da conta. Nome e apelidos, ano
 * de nacemento e a declaración «Esta persoa está ao meu cargo», que hai que marcar cada vez (é unha
 * declaración de quen reserva, non un consentimento; ADR-0031). Sen DNI, parentesco nin datos de
 * saúde. Garda coa capa ocupada e pecha coa ficha gardada; se a API a rexeita (repetida, límite), o
 * erro queda na capa.
 */
@Component({
  selector: 'furar-dependent-dialog',
  imports: [
    ButtonDirective,
    CalloutComponent,
    DialogComponent,
    FieldComponent,
    FieldGroupComponent,
    InputDirective,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'dependents.';
    <furar-dialog size="sm" [heading]="t + 'dialog.title' | transloco" [busy]="busy()">
      <form novalidate [id]="ids + '-form'" [formGroup]="form" (submit)="$event.preventDefault(); save()">
        <furar-field-group cells [style.--f-cell]="'8rem'">
          <furar-field
            [fieldId]="ids + '-name'"
            [label]="t + 'name' | transloco"
            [required]="true"
            [error]="error('name')"
          >
            <input
              furarInput
              formControlName="name"
              autocomplete="off"
              aria-required="true"
              data-dependent-name
              [id]="ids + '-name'"
              [attr.aria-invalid]="error('name') ? 'true' : null"
              [attr.aria-describedby]="described('name')"
            />
          </furar-field>
          <furar-field
            [fieldId]="ids + '-year'"
            [label]="t + 'birthYear' | transloco"
            [required]="true"
            [error]="error('birthYear')"
          >
            <input
              furarInput
              formControlName="birthYear"
              autocomplete="off"
              inputmode="numeric"
              maxlength="4"
              aria-required="true"
              data-dependent-year
              [id]="ids + '-year'"
              [attr.aria-invalid]="error('birthYear') ? 'true' : null"
              [attr.aria-describedby]="described('year', 'birthYear')"
            />
          </furar-field>
        </furar-field-group>
        <label class="f-dependents__care">
          <input
            type="checkbox"
            formControlName="inMyCare"
            aria-required="true"
            data-dependent-care
            [attr.aria-invalid]="error('inMyCare') ? 'true' : null"
            [attr.aria-describedby]="error('inMyCare') ? ids + '-care-error' : null"
          />
          {{ t + 'inMyCare' | transloco }}
        </label>
        @if (error('inMyCare'); as e) {
          <span class="f-field-error" [id]="ids + '-care-error'">{{ e.key | transloco: e.params }}</span>
        }
        @if (problem(); as p) {
          <furar-callout tone="danger" live="alert" data-dependent-error>{{ p.translated }}</furar-callout>
        }
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton data-dependent-cancel (click)="ref.close(null)">
          {{ 'common.cancel' | transloco }}
        </button>
        <button type="submit" furarButton="primary" data-dependent-save [attr.form]="ids + '-form'" [busy]="busy()">
          {{ (data.dependent ? 'common.save' : t + 'submitAdd') | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class DependentDialogComponent {
  protected readonly data = inject<DependentDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<DependentResponse | null>>(DialogRef);
  private readonly store = inject(DependentsStore);
  private readonly host = inject<ElementRef<HTMLElement>>(HostRef);
  private readonly injector = inject(Injector);

  protected readonly ids = `furar-dependent-${nextId++}`;
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly form = new FormGroup({
    name: new FormControl(this.data.dependent?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    birthYear: new FormControl(this.data.dependent ? String(this.data.dependent.birthYear) : '', {
      nonNullable: true,
      validators: [Validators.required, birthYear],
    }),
    // Ao editar, a declaración vólvese facer: non vén marcada.
    inMyCare: new FormControl(false, { nonNullable: true, validators: [declared] }),
  });

  protected error(name: 'name' | 'birthYear' | 'inMyCare') {
    return fieldError(this.form.controls[name], this.submitted());
  }

  protected described(id: string, name: 'name' | 'birthYear' = 'name'): string | null {
    return describedBy(`${this.ids}-${id}`, false, this.error(name));
  }

  protected async save(): Promise<void> {
    if (this.busy()) return;
    this.submitted.set(true);
    this.problem.set(null);
    if (this.form.invalid) {
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    const { name, birthYear: year } = this.form.getRawValue();
    const body = { name: name.trim(), birthYear: Number(year), inMyCare: true };
    this.busy.set(true);
    try {
      const current = this.data.dependent;
      this.ref.close(current ? await this.store.update(current, body) : await this.store.create(body));
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
