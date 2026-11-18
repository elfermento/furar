// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, effect, input, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import type { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import type { MunicipalityStore } from '../municipality.store';
import type { OpenMatchPolicyForm } from '../open-match-policy-form';
import { openMatchPolicyBody, openMatchPolicyChanged } from '../open-match-policy-form';

export { openMatchPolicyForm } from '../open-match-policy-form';

/**
 * «Buscar xente para xogar» en Concello › Xeral › Configuración adicional (ADR-0040):
 * o interruptor e, só aceso, «Cancelar se non hai mínimo» (horas antes, de 1 a 72), cun «?» que
 * explica que pasa. Apagado, a función non existe.
 */
@Component({
  selector: 'furar-open-match-policy',
  imports: [FieldComponent, InputDirective, ReactiveFormsModule, SwitchComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid', 'data-open-match-policy': '' },
  template: `
    @let f = form();
    <ng-container [formGroup]="f">
      <furar-switch
        switchId="settings-open-match"
        formControlName="enabled"
        [label]="'section.openMatch.label' | transloco"
        [tip]="'section.openMatch.help' | transloco"
      />
      @if (enabled()) {
        @let error = fieldError(f.controls.cancelHours, submitted());
        <furar-field
          fieldId="settings-open-match-hours"
          required
          data-open-match-hours
          [label]="'section.openMatch.cancelHours' | transloco"
          [tip]="'section.openMatch.cancelHoursTip' | transloco"
          [error]="error"
        >
          <span class="f-field-affix">
            <input
              furarInput
              id="settings-open-match-hours"
              type="text"
              inputmode="numeric"
              maxlength="2"
              autocomplete="off"
              formControlName="cancelHours"
              aria-required="true"
              [attr.aria-invalid]="error ? 'true' : null"
              [attr.aria-describedby]="describedBy('settings-open-match-hours', true, error)"
            />
            <span class="f-field-affix__unit" aria-hidden="true">{{
              'section.openMatch.hoursBefore' | transloco
            }}</span>
          </span>
        </furar-field>
      }
    </ng-container>
  `,
})
export class OpenMatchPolicyComponent {
  readonly form = input.required<OpenMatchPolicyForm>();
  readonly submitted = input(false, { transform: booleanAttribute });

  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;
  /** O interruptor (o formulario cambia ao descartar ou gardar). */
  protected readonly enabled = toSignal(
    toObservable(this.form).pipe(
      switchMap(({ controls }) => controls.enabled.valueChanges.pipe(startWith(controls.enabled.value))),
    ),
    { initialValue: false },
  );

  constructor() {
    // Apagado, as horas non se ven: o que estea a medio escribir (e non valla) volve ao gardado,
    // para que un número agochado non impida gardar.
    effect(() => {
      if (this.enabled()) return;
      const hours = untracked(this.form).controls.cancelHours;
      if (hours.invalid) hours.reset();
    });
  }
}

/**
 * O seu propio `PUT` dentro do «Gardar» da pestana, coa versión do concello que deixou o paso
 * anterior (como «Datos abertos»).
 */
export async function saveOpenMatchPolicy(
  store: Pick<MunicipalityStore, 'updateOpenMatchPolicy'>,
  current: CurrentMunicipality,
  form: OpenMatchPolicyForm,
): Promise<void> {
  const municipality = current.value();
  if (!municipality || !openMatchPolicyChanged(form, municipality.openMatchPolicy)) return;
  const saved = await store.updateOpenMatchPolicy(municipality.slug, municipality.version, openMatchPolicyBody(form));
  current.set({ ...municipality, version: saved.version, openMatchPolicy: saved.body });
}
