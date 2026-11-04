// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl } from '@angular/forms';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { EditSectionComponent } from '../../../shared/ui/edit-section/edit-section.component';
import { MapComponent } from '../../../shared/ui/map/map.component';
import { pointOf } from '../../../shared/ui/map/map-point';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { cardAddress } from '../list/card-address';
import { syncWhenPristine } from '../detail/section-form';
import { AddressFieldsComponent } from './address-fields.component';
import { addressErrors, locationBody, locationControl, locationForm, locationValue } from './location-form';
import { LocationStore } from './location.store';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

/**
 * Sección «Localización» da ficha (D1-21b; especificación, bloque 1, «Enderezos»): fóra da
 * edición, o mapa grande de só lectura (o enderezo xa vai na cabeceira da ficha, coa icona de
 * ubicación: é a alternativa ao mapa, que non é parada de tabulador). No modo edición da ficha,
 * o buscador de enderezos, os campos e a chincheta (se o buscador non responde, todo a man). Sen
 * mapa no despregue (`FURAR_MAP=off`), fóra da edición vai o enderezo en texto e na edición quedan
 * a latitude e a lonxitude, que enche o buscador ou se escriben. Garda pola cola da ficha cando a
 * barra garda; os erros da API van ao seu campo.
 */
@Component({
  selector: 'furar-facility-location',
  imports: [
    RequiredNoteComponent,
    AddressFieldsComponent,
    EditSectionComponent,
    MapComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.location.';
    <furar-edit-section [formGroup]="form" [heading]="t + 'title' | transloco" [save]="save" [controlFor]="controlFor">
      @if (session.editing()) {
        <furar-required-note />
        <furar-facility-address-fields [form]="form" [errors]="errors()" />
      } @else if (mapEnabled() && point()) {
        <furar-map [point]="point()" [label]="t + 'pin' | transloco" />
      } @else if (address()) {
        <p data-location-address>{{ address() }}</p>
      }
    </furar-edit-section>
  `,
})
export class LocationSectionComponent {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(LocationStore);
  protected readonly session = inject(EditSession);
  protected readonly mapEnabled = inject(DeploymentSettings).mapEnabled;

  protected readonly form = locationForm();
  private readonly formEvents = toSignal(this.form.events);
  protected readonly errors = computed(() => {
    this.formEvents();
    return addressErrors(this.form, false);
  });
  protected readonly point = computed(() => {
    return pointOf(this.detail.facility()?.address);
  });
  /** Sen mapa, o enderezo en texto: «Rúa do Alto, 12 · 15570 Narón». */
  protected readonly address = computed(() => {
    const facility = this.detail.facility();
    return facility ? cardAddress(facility) : '';
  });

  protected readonly controlFor = (field: string): AbstractControl | null => locationControl(this.form, field);

  protected readonly save = () =>
    this.detail.saveFacility((target) => this.store.update(target, locationBody(this.form.getRawValue())));

  constructor() {
    syncWhenPristine(this.form, () => locationValue(this.detail.facility()));
  }
}
