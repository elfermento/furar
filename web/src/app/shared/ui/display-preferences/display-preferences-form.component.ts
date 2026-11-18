// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  effect,
  inject,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { TEXT_SIZES } from '../../../core/preferences/display-preferences';
import { DisplayPreferencesService } from '../../../core/preferences/display-preferences.service';
import { SegmentedRadioDirective, SegmentedOptionDirective } from '../segmented/segmented-radio.directive';
import { SegmentedDirective } from '../segmented/segmented.directive';
import { SwitchComponent } from '../switch/switch.component';

let nextId = 0;

/**
 * O contido do panel de accesibilidade: «Tamaño do texto» (Normal, Grande,
 * Moi grande) co segmentado en modo radio e «Alto contraste» e «Menos movemento» con
 * `furar-switch` (nome á esquerda, interruptor á dereita). Sen iconas. Cada cambio aplícase ao
 * momento (DisplayPreferencesService). Ao abrir, o foco vai ao tamaño escollido.
 */
@Component({
  selector: 'furar-display-preferences-form',
  imports: [
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedRadioDirective,
    SegmentedOptionDirective,
    SwitchComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-display-prefs__form' },
  template: `
    <p class="f-display-prefs__intro">
      {{ (prefs.savesToAccount() ? 'ui.displayPrefs.introAccount' : 'ui.displayPrefs.introDevice') | transloco }}
    </p>
    <div class="f-display-prefs__row" data-pref="text-size">
      <span class="f-display-prefs__name" [id]="sizeId">{{ 'ui.displayPrefs.textSize' | transloco }}</span>
      <div
        #sizes
        furarSegmented
        mode="radio"
        class="f-display-prefs__sizes"
        [attr.aria-labelledby]="sizeId"
        [value]="prefs.textSize()"
        (valueChange)="$event && prefs.setTextSize($event)"
      >
        @for (size of textSizes; track size) {
          <button type="button" [furarSegmentedOption]="size" [attr.data-size]="size">
            {{ 'ui.displayPrefs.size.' + size | transloco }}
          </button>
        }
      </div>
    </div>
    <furar-switch
      class="f-display-prefs__switch"
      data-pref="contrast"
      [switchId]="sizeId + '-contrast'"
      [label]="'ui.displayPrefs.contrast' | transloco"
      [formControl]="contrast"
    />
    <furar-switch
      class="f-display-prefs__switch"
      data-pref="motion"
      [switchId]="sizeId + '-motion'"
      [label]="'ui.displayPrefs.motion' | transloco"
      [formControl]="motion"
    />
  `,
})
export class DisplayPreferencesFormComponent {
  protected readonly prefs = inject(DisplayPreferencesService);
  protected readonly textSizes = TEXT_SIZES;
  protected readonly sizeId = `furar-display-prefs-${nextId++}`;
  protected readonly contrast = new FormControl(this.prefs.highContrast(), { nonNullable: true });
  protected readonly motion = new FormControl(this.prefs.reducedMotion(), { nonNullable: true });
  private readonly sizes = viewChild.required<ElementRef<HTMLElement>>('sizes');

  constructor() {
    // O sistema pode cambiar mentres o panel está aberto: os interruptores amosan o que se aplica.
    effect(() => this.contrast.setValue(this.prefs.highContrast(), { emitEvent: false }));
    effect(() => this.motion.setValue(this.prefs.reducedMotion(), { emitEvent: false }));
    const subscriptions = [
      this.contrast.valueChanges.subscribe((on) => this.prefs.setHighContrast(on)),
      this.motion.valueChanges.subscribe((on) => this.prefs.setReducedMotion(on)),
    ];
    inject(DestroyRef).onDestroy(() => subscriptions.forEach((subscription) => subscription.unsubscribe()));
    afterNextRender(() => this.sizes().nativeElement.querySelector<HTMLElement>('[tabindex="0"]')?.focus());
  }
}
