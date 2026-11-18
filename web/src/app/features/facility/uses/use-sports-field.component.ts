// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormControl } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { merge, of, switchMap } from 'rxjs';
import type { SportResponse } from '../data-card/data-form';
import { LanguageService } from '../../../core/i18n/language.service';
import { localized } from '../../../shared/custom-fields/localized';
import type { ComboboxOption, ComboboxSource } from '../../../shared/ui/combobox/combobox.component';
import { fieldError } from '../../../shared/ui/field-errors';
import { MultiComboboxComponent } from '../../../shared/ui/multi-combobox/multi-combobox.component';
import { fold } from '../data-card/sports-field.component';
import { sportKey } from './use-form';

/**
 * Os deportes dun uso (ADR-0025, revisión do 2026-11-18): o mesmo campo ca os deportes da
 * instalación (`furar-multi-combobox`), coas opcións limitadas aos dela e sen «Crear». Cun só
 * deporte na instalación non sae: o uso tena sempre. O control garda as claves.
 */
@Component({
  selector: 'furar-facility-use-sports',
  imports: [MultiComboboxComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.uses.sports.';
    @if (sports().length > 1) {
      <furar-multi-combobox
        required
        data-use-sports
        [fieldId]="idPrefix() + '-sports'"
        [label]="t + 'label' | transloco"
        [error]="error()"
        [chosenLabel]="t + 'chosen' | transloco"
        [source]="source()"
        [key]="key"
        [display]="name()"
        [countText]="countText"
        [selected]="chosen()"
        (selectedChange)="set($event)"
      />
    }
  `,
})
export class UseSportsFieldComponent {
  /** As claves dos deportes do uso. */
  readonly control = input.required<FormControl<string[]>>();
  /** Os deportes da instalación, na súa orde. */
  readonly sports = input.required<readonly SportResponse[]>();
  readonly idPrefix = input.required<string>();

  private readonly transloco = inject(TranslocoService);
  private readonly lang = inject(LanguageService).lang;
  private readonly events = toSignal(
    toObservable(this.control).pipe(switchMap((control) => merge(of(null), control.events))),
  );

  protected readonly key = sportKey;
  protected readonly name = computed(() => {
    const lang = this.lang();
    return (sport: SportResponse) => localized(sport.label, lang);
  });
  /** Os escollidos, na orde da instalación. */
  protected readonly chosen = computed(() => {
    this.events();
    const keys = this.control().value;
    return this.sports().filter((sport) => keys.includes(sportKey(sport)));
  });
  protected readonly error = computed(() => {
    this.events();
    return fieldError(this.control(), false);
  });
  protected readonly source = computed((): ComboboxSource<SportResponse> => {
    const sports = this.sports();
    const name = this.name();
    return async (query) => {
      const wanted = fold(query);
      return sports
        .map((sport): ComboboxOption<SportResponse> => ({ value: sport, label: name(sport) }))
        .filter((option) => fold(option.label).includes(wanted));
    };
  });
  protected readonly countText = (count: number): string =>
    this.transloco.translate('facility.uses.sports.' + (count === 1 ? 'countOne' : 'countOther'), { count });

  protected set(chosen: readonly SportResponse[]): void {
    const keys = new Set(chosen.map(sportKey));
    const control = this.control();
    // Sempre na orde da instalación.
    control.setValue(
      this.sports()
        .map(sportKey)
        .filter((key) => keys.has(key)),
    );
    control.markAsDirty();
    control.markAsTouched();
  }
}
