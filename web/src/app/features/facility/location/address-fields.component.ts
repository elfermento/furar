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
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import type { ComboboxOption, ComboboxSource } from '../../../shared/ui/combobox/combobox.component';
import { ComboboxComponent } from '../../../shared/ui/combobox/combobox.component';
import { describedBy } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { LongTextDirective } from '../../../shared/ui/long-text/long-text.directive';
import { MapComponent } from '../../../shared/ui/map/map.component';
import type { MapPoint } from '../../../shared/ui/map/map-point';
import type { AddressErrors, AddressTextField, LocationForm } from './location-form';
import { applyMatch } from './location-form';
import { LocationStore } from './location.store';

/**
 * Enderezo dunha instalación (especificación, bloque 1, «Enderezos»). A busca é un atallo
 * opcional enriba dos campos: escoller unha suxestión do concello enche os campos e a chincheta;
 * se non se atopa, escríbese abaixo e ponse a chincheta. Se o buscador non responde (503), aviso
 * e todo a man: os campos e o mapa editable funcionan sempre.
 *
 * Escoller unha suxestión leva o mapa á chincheta con zoom de rúa (`furar-map`).
 *
 * Úsano «Cambiar a localización» e «Nova instalación». Os ids dos campos son
 * `<idPrefix>-<campo>`; os erros chegan xa calculados de quen ten o formulario.
 */
@Component({
  selector: 'furar-facility-address-fields',
  imports: [
    CalloutComponent,
    ComboboxComponent,
    FieldComponent,
    FieldGroupComponent,
    IconComponent,
    InputDirective,
    LongTextDirective,
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
  /** Nome do concello, para «Buscar en Narón». */
  protected readonly municipality = inject(CurrentMunicipality).name;

  readonly form = input.required<LocationForm>();
  readonly errors = input.required<AddressErrors>();
  readonly idPrefix = input('facility-location');
  /** Sen chincheta, o mapa encadra estes puntos (as instalacións do concello). */
  readonly area = input<readonly MapPoint[]>([]);

  /**
   * Vía e número nunha fila; CP e localidade noutra, nunha cápsula. `width` é o ancho mínimo de
   * cada campo (`--f-cell`) e `grow` canto medra: a vía e a localidade levan o que sobra.
   */
  protected readonly rows: readonly (readonly {
    readonly name: AddressTextField;
    readonly width: string;
    readonly grow: number;
  }[])[] = [
    [
      { name: 'street', width: '14rem', grow: 3 },
      { name: 'streetNumber', width: '7rem', grow: 1 },
    ],
    [
      { name: 'postalCode', width: '7rem', grow: 1 },
      { name: 'locality', width: '14rem', grow: 3 },
    ],
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
