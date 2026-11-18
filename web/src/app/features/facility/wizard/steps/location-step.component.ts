// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrentMunicipality } from '../../../../core/municipality/current-municipality';
import type { MapPoint } from '../../../../shared/ui/map/map-point';
import { AddressFieldsComponent } from '../../location/address-fields.component';
import { addressErrors } from '../../location/location-form';
import { LocationStore } from '../../location/location.store';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 2, «Ubicación»: o buscador de enderezos (enche os campos e a chincheta), os campos e o
 * mapa, os mesmos da ficha. Sen mapa no despregue, só o enderezo (publícase sen punto). Sen
 * chincheta aínda, o mapa abre sobre as instalacións do concello (se hai algunha con punto).
 */
@Component({
  selector: 'furar-wizard-location-step',
  imports: [AddressFieldsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-facility-address-fields
      idPrefix="wizard-location"
      [form]="store.location"
      [errors]="errors()"
      [area]="area()"
    />
  `,
})
export class LocationStepComponent {
  protected readonly store = inject(WizardStore);
  protected readonly area = signal<readonly MapPoint[]>([]);
  protected readonly errors = computed(() => {
    this.store.ok();
    return addressErrors(this.store.location, this.store.tried().has(STEP.location));
  });

  constructor() {
    const slug = inject(CurrentMunicipality).slug();
    if (slug && this.store.mapEnabled() && !this.store.location.controls.point.value) {
      void inject(LocationStore)
        .facilityPoints(slug)
        .then((points) => this.area.set(points));
    }
  }
}
