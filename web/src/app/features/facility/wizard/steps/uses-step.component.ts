// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../../shared/ui/callout/callout.component';
import { DeleteButtonComponent } from '../../../../shared/ui/delete-button/delete-button.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import type { RadioCardOption } from '../../../../shared/ui/radio-cards/radio-cards.component';
import { RadioCardsComponent } from '../../../../shared/ui/radio-cards/radio-cards.component';
import { SwitchComponent } from '../../../../shared/ui/switch/switch.component';
import { UseTagsComponent } from '../../uses/use-row.component';
import type { Use } from '../../uses/uses.store';
import type { UsePreset } from '../wizard';
import { STEP, presetsFor, priceText } from '../wizard';
import { priceMuted, pricePer } from '../../uses/use-price';
import { PriceComponent } from '../../../../shared/ui/money/price.component';
import { WizardUseEditor } from '../wizard-use-editor';
import { WizardStore } from '../wizard.store';
import { UseEditorComponent } from './use-editor.component';

/**
 * Paso 5, «Usos»: que é un uso, con exemplos; «Usos N» e cada uso na súa
 * tarxeta tinguida (nome e etiquetas, o prezo á dereita, «Editar» e a papeleira) e «Engadir uso» cos atallos que saen dos deportes do paso 1 («Outro», baleiro). O
 * formulario abre no sitio. Debaixo, «Quen fai as reservas?»: a veciñanza desde a web ou só o
 * persoal (entón, se se ven os ocos libres).
 */
@Component({
  selector: 'furar-wizard-uses-step',
  imports: [
    ButtonDirective,
    CalloutComponent,
    DeleteButtonComponent,
    IconComponent,
    PriceComponent,
    RadioCardsComponent,
    ReactiveFormsModule,
    SwitchComponent,
    TranslocoPipe,
    UseEditorComponent,
    UseTagsComponent,
  ],
  host: { class: 'f-wizard__step f-wizard__step--uses' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.uses.';
    @let u = 'facility.uses.';
    <furar-callout>
      <p>
        <b>{{ t + 'introTitle' | transloco }}</b>
        {{ t + 'introText' | transloco }}
      </p>
    </furar-callout>

    @if (store.uses.uses().length) {
      <div>
        <p class="f-wizard-uses__head">
          <span class="f-label">{{ 'facility.detail.sections.uses' | transloco }}</span>
          <span class="text-ink-soft">{{ store.uses.uses().length }}</span>
        </p>
        <ul class="f-wizard-uses" [attr.aria-label]="u + 'list' | transloco">
          @for (use of store.uses.uses(); track use.id) {
            @let open = editor();
            @if (open && open.use?.id === use.id) {
              <li><furar-wizard-use-editor [editor]="open" /></li>
            } @else {
              <li class="f-wizard-uses__row" [attr.data-use]="use.name">
                <span class="f-wizard-uses__name">
                  <b>{{ use.name }}</b>
                  <furar-facility-use-tags withoutWhen [use]="use" />
                </span>
                <furar-price [amount]="price(use)" [per]="per(use)" [muted]="muted(use)" />
                <span class="f-wizard-uses__acts">
                  <button
                    type="button"
                    furarButton="quiet"
                    size="sm"
                    data-use-edit
                    [attr.aria-label]="t + 'editLabel' | transloco: { name: use.name }"
                    [title]="t + 'edit' | transloco"
                    (click)="editing.open(use)"
                  >
                    <furar-icon name="pencil" />
                    <span class="f-wizard-uses__edit-text" aria-hidden="true">{{ t + 'edit' | transloco }}</span>
                  </button>
                  <furar-delete-button
                    [label]="u + 'remove.label' | transloco: { name: use.name }"
                    [tooltip]="u + 'remove.action' | transloco"
                    (pressed)="store.removeUse(use)"
                  />
                </span>
              </li>
            }
          }
        </ul>
      </div>
    }
    @if (editor(); as open) {
      @if (!open.use) {
        <furar-wizard-use-editor [editor]="open" />
      }
    } @else {
      <div class="gap-unit grid" role="group" aria-labelledby="wizard-uses-add">
        <span class="f-label" id="wizard-uses-add">{{ t + 'add' | transloco }}</span>
        <div class="gap-unit flex flex-wrap">
          @for (preset of presets(); track preset.name) {
            <button type="button" furarButton="secondary" size="sm" data-preset (click)="editing.open(null, preset)">
              <furar-icon name="plus" />
              {{ preset.name }}
            </button>
          }
          <button type="button" furarButton="quiet" size="sm" data-use-other (click)="editing.open(null)">
            <furar-icon name="plus" />
            {{ t + 'other' | transloco }}
          </button>
        </div>
        @if (noUses()) {
          <p class="f-field-error" id="wizard-uses-error">{{ 'errors.facility.uses-required' | transloco }}</p>
        }
      </div>
    }

    <hr class="f-wizard__rule" />

    <div class="gap-unit grid">
      <span class="f-label" id="wizard-who">{{ t + 'who' | transloco }}</span>
      <furar-radio-cards labelledBy="wizard-who" [formControl]="store.data.controls.onlineBooking" [options]="who()" />
      @if (!online()) {
        <p class="f-help">{{ t + 'staffHelp' | transloco }}</p>
        <furar-switch
          switchId="wizard-free-slots"
          [formControl]="store.data.controls.showFreeSlots"
          [label]="'facility.data.showFreeSlots.label' | transloco"
          [tip]="'facility.data.showFreeSlots.help' | transloco"
        />
      }
      <!-- Con control de entrada (ADR-0032): o mesmo interruptor ca na ficha. -->
      <furar-switch
        switchId="wizard-entry-control"
        [formControl]="store.data.controls.entryControl"
        [label]="'facility.data.entryControl' | transloco"
      />
    </div>
  `,
})
export class UsesStepComponent {
  protected readonly store = inject(WizardStore);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.langChanges$);
  protected readonly editing = inject(WizardUseEditor);
  protected readonly editor = this.editing.editor;

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly presets = computed<UsePreset[]>(() => {
    this.lang();
    this.store.ok();
    return presetsFor(this.store.data.controls.sports.value, this.store.uses.uses(), this.t);
  });
  protected readonly who = computed<RadioCardOption<boolean>[]>(() => {
    this.lang();
    return [
      { value: true, label: this.t('facility.wizard.uses.residents'), items: [] },
      { value: false, label: this.t('facility.wizard.uses.staff'), items: [] },
    ];
  });
  protected readonly online = computed(() => {
    this.store.ok();
    return this.store.data.controls.onlineBooking.value;
  });
  protected readonly noUses = computed(
    () => this.store.tried().has(STEP.uses) && !this.store.uses.uses().length && !this.editor(),
  );

  protected price(use: Use): string {
    this.lang();
    return priceText(use, this.t);
  }

  protected per(use: Use): string | null {
    this.lang();
    return pricePer(use, this.t);
  }

  protected muted(use: Use): boolean {
    return priceMuted(use);
  }
}
