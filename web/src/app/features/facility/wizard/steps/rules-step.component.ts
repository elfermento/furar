// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { localized } from '../../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../../shared/ui/bilingual-field/bilingual-field.component';
import { TabDirective, TabsComponent } from '../../../../shared/ui/tabs/tabs.component';
import { HOUSE_RULES_MAX } from '../../description/description-form';
import { PAYMENT_NOTE_MAX } from '../../description/payment-notes-section.component';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

type RulesTab = 'rules' | 'payment';

/**
 * Paso 7, «Normas» ou «Normas e pago» (opcional): as normas da instalación en gl e es
 * e, só se o concello pon o pago e a devolución por instalación, os dous textos (baleiros, vese o
 * de Furar). Con pago, «Normas» e «Pago» van en dúas pestanas (`furar-tabs` en liña, coma as
 * páxinas legais) e cada unha mostra só o seu; a que ten erros leva o «!» e, ao pulsar
 * «Seguinte», se a pestana á vista está ben e a outra non, pásase a ela para que o foco chegue ao
 * erro.
 */
@Component({
  selector: 'furar-wizard-rules-step',
  imports: [BilingualFieldComponent, NgTemplateOutlet, TabDirective, TabsComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let n = 'facility.paymentNotes.';
    @if (store.scope() === 'FACILITY') {
      <furar-tabs
        look="line"
        size="sm"
        [label]="'facility.wizard.steps.rulesAndPayment' | transloco"
        [(selected)]="tab"
      >
        @for (key of tabs; track key) {
          <button type="button" [furarTab]="key" [attr.data-rules-tab]="key">
            {{ (key === 'rules' ? 'facility.wizard.steps.rules' : 'facility.wizard.paymentTab') | transloco }}
            @if (errors()[key]) {
              <span class="f-tabs__error" aria-hidden="true"></span>
              <span class="sr-only">{{ 'ui.tabs.withErrors' | transloco }}</span>
            }
          </button>
        }
        @if (tab() === 'rules') {
          <ng-container *ngTemplateOutlet="rules; context: { hideLabel: true }" />
        } @else {
          <div class="gap-form grid" data-rules-panel="payment">
            @for (note of notes; track note) {
              <furar-bilingual-field
                multiline
                longText
                [rows]="2"
                [submitted]="tried()"
                [fieldId]="'wizard-' + note"
                [maxlength]="noteMax"
                [label]="n + note | transloco"
                [tip]="n + 'defaultTip' | transloco: { text: fallback()[note] }"
                [gl]="store.payment.controls[note].controls.gl"
                [es]="store.payment.controls[note].controls.es"
              />
            }
          </div>
        }
      </furar-tabs>
    } @else {
      <ng-container *ngTemplateOutlet="rules; context: { hideLabel: false }" />
    }
    <ng-template #rules let-hideLabel="hideLabel">
      <furar-bilingual-field
        fieldId="wizard-house-rules"
        multiline
        longText
        data-rules-panel="rules"
        [hideLabel]="hideLabel"
        [submitted]="tried()"
        [maxlength]="rulesMax"
        [label]="'facility.houseRules.title' | transloco"
        [tip]="'facility.houseRules.help' | transloco: { max: rulesMax }"
        [gl]="store.houseRules.controls.gl"
        [es]="store.houseRules.controls.es"
      />
    </ng-template>
  `,
})
export class RulesStepComponent {
  protected readonly store = inject(WizardStore);
  private readonly language = inject(LanguageService);
  protected readonly rulesMax = HOUSE_RULES_MAX;
  protected readonly noteMax = PAYMENT_NOTE_MAX;
  protected readonly notes = ['payment', 'refund'] as const;
  protected readonly tabs: readonly RulesTab[] = ['rules', 'payment'];
  protected readonly tab = signal<RulesTab>('rules');
  protected readonly tried = computed(() => this.store.tried().has(STEP.rules));
  /** Pestanas con erros, só despois de pulsar «Seguinte» (coma os erros dos campos). */
  protected readonly errors = computed<Record<RulesTab, boolean>>(() => {
    this.store.forms.changes();
    const tried = this.tried();
    return { rules: tried && this.store.houseRules.invalid, payment: tried && this.store.payment.invalid };
  });
  /** O texto de Furar que se ve se a instalación non pon o seu. */
  protected readonly fallback = computed(() => {
    const notes = this.store.notes();
    const lang = this.language.lang();
    return { payment: localized(notes?.furarPayment, lang), refund: localized(notes?.furarRefund, lang) };
  });

  constructor() {
    // Cada «Seguinte» volve marcar o paso (`tried` cambia): se o erro está só na outra pestana, a ela.
    effect(() => {
      if (!this.store.tried().has(STEP.rules)) return;
      untracked(() => {
        const errors = this.errors();
        const shown = this.tab();
        const other: RulesTab = shown === 'rules' ? 'payment' : 'rules';
        if (!errors[shown] && errors[other]) this.tab.set(other);
      });
    });
  }
}
