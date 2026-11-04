// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RequiredNoteComponent } from '../../../../shared/ui/required/required.component';
import { AddressFieldsComponent } from '../../location/address-fields.component';
import { addressErrors } from '../../location/location-form';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 2, «Ubicación»: o buscador de enderezos (enche os campos e a chincheta), os campos e o
 * mapa, os mesmos da ficha. Sen mapa no despregue, só o enderezo (publícase sen punto).
 */
@Component({
  selector: 'furar-wizard-location-step',
  imports: [AddressFieldsComponent, RequiredNoteComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-required-note />
    <furar-facility-address-fields idPrefix="wizard-location" [form]="store.location" [errors]="errors()" />
  `,
})
export class LocationStepComponent {
  protected readonly store = inject(WizardStore);
  protected readonly errors = computed(() => {
    this.store.ok();
    return addressErrors(this.store.location, this.store.tried().has(STEP.location));
  });
}
