// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { localized } from '../../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../../shared/ui/bilingual-field/bilingual-field.component';
import { HOUSE_RULES_MAX } from '../../description/description-form';
import { PAYMENT_NOTE_MAX } from '../../description/payment-notes-section.component';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 7, «Normas» ou «Normas e pago» (opcional; maqueta 53): as normas da instalación en gl e es
 * e, só se o concello pon o pago e a devolución por instalación, os dous textos (baleiros, vese o
 * de Furar).
 */
@Component({
  selector: 'furar-wizard-rules-step',
  imports: [BilingualFieldComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let n = 'facility.paymentNotes.';
    <div class="gap-form grid">
      <furar-bilingual-field
        fieldId="wizard-house-rules"
        multiline
        [submitted]="tried()"
        [maxlength]="rulesMax"
        [label]="'facility.houseRules.title' | transloco"
        [help]="'facility.houseRules.help' | transloco: { max: rulesMax }"
        [gl]="store.houseRules.controls.gl"
        [es]="store.houseRules.controls.es"
      />
      @if (store.scope() === 'FACILITY') {
        <section class="gap-form grid" aria-labelledby="wizard-payment-title">
          <h3 id="wizard-payment-title" class="font-bold">{{ n + 'title' | transloco }}</h3>
          @for (note of notes; track note) {
            <furar-bilingual-field
              multiline
              [rows]="2"
              [submitted]="tried()"
              [fieldId]="'wizard-' + note"
              [maxlength]="noteMax"
              [label]="n + note | transloco"
              [help]="n + 'default' | transloco: { text: fallback()[note] }"
              [gl]="store.payment.controls[note].controls.gl"
              [es]="store.payment.controls[note].controls.es"
            />
          }
          <p class="f-help">{{ n + 'help' | transloco }}</p>
        </section>
      }
    </div>
  `,
})
export class RulesStepComponent {
  protected readonly store = inject(WizardStore);
  private readonly language = inject(LanguageService);
  protected readonly rulesMax = HOUSE_RULES_MAX;
  protected readonly noteMax = PAYMENT_NOTE_MAX;
  protected readonly notes = ['payment', 'refund'] as const;
  protected readonly tried = computed(() => this.store.tried().has(STEP.rules));
  /** O texto de Furar que se ve se a instalación non pon o seu. */
  protected readonly fallback = computed(() => {
    const notes = this.store.notes();
    const lang = this.language.lang();
    return { payment: localized(notes?.furarPayment, lang), refund: localized(notes?.furarRefund, lang) };
  });
}
