// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl } from '@angular/forms';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { localized } from '../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../shared/ui/bilingual-field/bilingual-field.component';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { EditSectionComponent } from '../../../shared/ui/edit-section/edit-section.component';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { syncWhenPristine } from '../detail/section-form';
import type { DescriptionLang } from './description-form';
import {
  DESCRIPTION_LANGS,
  HOUSE_RULES_INVALID,
  HOUSE_RULES_MAX,
  descriptionBody,
  houseRulesValue,
  optionalBilingualForm,
} from './description-form';
import { DescriptionStore } from './description.store';

/**
 * Sección «Normas da instalación» da ficha (D2-11; maqueta 06, P9): texto libre gl/es que a ficha
 * pública amosa nun recadro en «Antes de reservar». Funciona igual ca «Descrición»: fóra da
 * edición, o texto no idioma de quen mira (ou «Sen indicar»); no modo edición, un
 * `furar-bilingual-field`. Garda co `If-Match` da ficha cando a barra garda.
 */
@Component({
  selector: 'furar-facility-house-rules',
  imports: [BilingualFieldComponent, EditSectionComponent, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-edit-section
      [formGroup]="form"
      [heading]="'facility.houseRules.title' | transloco"
      [save]="save"
      [controlFor]="controlFor"
    >
      @if (session.editing()) {
        <furar-bilingual-field
          fieldId="facility-house-rules"
          multiline
          hideLabel
          [rows]="5"
          [maxlength]="max"
          [label]="'facility.houseRules.title' | transloco"
          [help]="'facility.houseRules.help' | transloco: { max: max }"
          [gl]="form.controls.gl"
          [es]="form.controls.es"
        />
      } @else if (text()) {
        <p class="max-w-[70ch] whitespace-pre-line" data-house-rules>{{ text() }}</p>
      } @else {
        <p class="text-ink-soft" data-house-rules>{{ 'ui.editSection.empty' | transloco }}</p>
      }
    </furar-edit-section>
  `,
})
export class HouseRulesSectionComponent {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(DescriptionStore);
  private readonly language = inject(LanguageService);
  protected readonly session = inject(EditSession);

  protected readonly max = HOUSE_RULES_MAX;
  protected readonly form = optionalBilingualForm(HOUSE_RULES_MAX, HOUSE_RULES_INVALID);
  protected readonly text = computed(() => localized(houseRulesValue(this.detail.facility()), this.language.lang()));

  protected readonly controlFor = (field: string): AbstractControl | null => {
    const lang = field.replace(/^houseRules\./, '') as DescriptionLang;
    return DESCRIPTION_LANGS.includes(lang) ? this.form.controls[lang] : null;
  };

  protected readonly save = () =>
    this.detail.saveFacility((target) => this.store.updateHouseRules(target, descriptionBody(this.form.getRawValue())));

  constructor() {
    syncWhenPristine(this.form, () => houseRulesValue(this.detail.facility()));
  }
}
