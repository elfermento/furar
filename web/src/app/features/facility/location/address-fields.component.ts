// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  input,
  signal,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import type { ComboboxOption, ComboboxSource } from '../../../shared/ui/combobox/combobox.component';
import { ComboboxComponent } from '../../../shared/ui/combobox/combobox.component';
import { describedBy } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { MapComponent } from '../../../shared/ui/map/map.component';
import type { AddressErrors, AddressTextField, LocationForm } from './location-form';
import { applyMatch } from './location-form';
import { LocationStore } from './location.store';

/**
 * Enderezo dunha instalación (especificación, bloque 1, «Enderezos»): buscar entre as suxestións
 * do concello → enche os campos e a chincheta → axustar a chincheta se fai falta. Se o buscador
 * non responde (503), aviso e todo a man: os campos e o mapa editable funcionan sempre.
 *
 * Úsano «Cambiar a localización» (D1-21b) e «Nova instalación» (D1-20). Os ids dos campos son
 * `<idPrefix>-<campo>`; os erros chegan xa calculados de quen ten o formulario.
 */
@Component({
  selector: 'furar-facility-address-fields',
  imports: [
    CalloutComponent,
    ComboboxComponent,
    FieldComponent,
    InputDirective,
    MapComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './address-fields.component.html',
})
export class AddressFieldsComponent {
  private readonly store = inject(LocationStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly form = input.required<LocationForm>();
  readonly errors = input.required<AddressErrors>();
  readonly idPrefix = input('facility-location');

  /** Vía e número nunha fila; CP e localidade noutra (no móbil, un debaixo do outro). */
  protected readonly rows: readonly { readonly fields: readonly AddressTextField[]; readonly columns: string }[] = [
    { fields: ['street', 'streetNumber'], columns: 'grid-cols-1 sm:grid-cols-[minmax(0,1fr)_9rem]' },
    { fields: ['postalCode', 'locality'], columns: 'grid-cols-1 sm:grid-cols-[9rem_minmax(0,1fr)]' },
  ];
  protected readonly describedBy = describedBy;
  /** O buscador fallou: enderezo a man. */
  protected readonly manual = signal(false);

  protected readonly source: ComboboxSource<string> = async (query) =>
    (await this.store.suggest(query)).map((s): ComboboxOption<string> => ({ value: s.id, label: s.label }));

  protected async pick(option: ComboboxOption<string>): Promise<void> {
    try {
      applyMatch(this.form(), await this.store.resolve(option.value));
    } catch {
      this.goManual();
    }
  }

  /** O buscador non responde: aviso e foco no primeiro campo, que é por onde se segue. */
  protected goManual(): void {
    if (this.manual()) return;
    this.manual.set(true);
    const id = `${this.idPrefix()}-street`;
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(`#${id}`)?.focus(), {
      injector: this.injector,
    });
  }
}
