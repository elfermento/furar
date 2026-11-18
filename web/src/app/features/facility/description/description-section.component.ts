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
  DESCRIPTION_MAX,
  descriptionBody,
  descriptionForm,
  descriptionValue,
} from './description-form';
import { DescriptionStore } from './description.store';

/**
 * Sección «Descrición» da ficha: fóra da edición, o texto no idioma de quen mira (se
 * falta, o outro); no modo edición da ficha, un só campo cos dous idiomas e o conmutador GL/ES
 * (`furar-bilingual-field`). Garda polo contrato de estado da ficha (cola e `If-Match`) cando a
 * barra garda. Sen texto, fóra da edición a sección non se ve.
 */
@Component({
  selector: 'furar-facility-description',
  imports: [BilingualFieldComponent, EditSectionComponent, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Opcional: sen texto, fóra da edición non se ve a sección.
  host: { '[attr.hidden]': '!session.editing() && !text() ? "" : null' },
  template: `
    <furar-edit-section
      [formGroup]="form"
      [heading]="'facility.description.title' | transloco"
      [save]="save"
      [controlFor]="controlFor"
      [tip]="session.editing() ? ('facility.description.help' | transloco: { max: max }) : ''"
      tipId="facility-description-section-help"
    >
      @if (session.editing()) {
        <furar-bilingual-field
          fieldId="facility-description"
          multiline
          longText
          hideLabel
          [rows]="5"
          [maxlength]="max"
          [label]="'facility.description.title' | transloco"
          helpId="facility-description-section-help"
          [gl]="form.controls.gl"
          [es]="form.controls.es"
        />
      } @else if (text()) {
        <p class="max-w-[70ch] whitespace-pre-line" data-description>{{ text() }}</p>
      }
    </furar-edit-section>
  `,
})
export class DescriptionSectionComponent {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(DescriptionStore);
  private readonly language = inject(LanguageService);
  protected readonly session = inject(EditSession);

  protected readonly max = DESCRIPTION_MAX;
  protected readonly form = descriptionForm();
  /** O gardado, no idioma de quen mira. */
  protected readonly text = computed(() => localized(descriptionValue(this.detail.facility()), this.language.lang()));

  protected readonly controlFor = (field: string): AbstractControl | null => {
    const lang = field.replace(/^description\./, '') as DescriptionLang;
    return DESCRIPTION_LANGS.includes(lang) ? this.form.controls[lang] : null;
  };

  protected readonly save = () =>
    this.detail.saveFacility((target) => this.store.update(target, descriptionBody(this.form.getRawValue())));

  constructor() {
    syncWhenPristine(this.form, () => descriptionValue(this.detail.facility()));
  }
}
