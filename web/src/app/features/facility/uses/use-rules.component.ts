// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { fieldError } from '../../../shared/ui/field-errors';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { LanguageService } from '../../../core/i18n/language.service';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { OptionsPipe } from '../../../shared/ui/select/options.pipe';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import type { RuleKey, RulesForm } from './use-terms';
import { CAP_PERIODS, RULE_FIELDS, RULE_KEYS, RULE_LIMITS, clearRule, durationText, ruleText } from './use-terms';

/**
 * «Condicións da reserva» do modal do uso (D2-11; maqueta 21; nome da maqueta 54): acordeón coas catro normas (cando reservar,
 * duración máxima, límite por persoa e cancelar sen custo), cada unha co seu valor en texto ou
 * «Sen establecer» e un chevron; abre unha de cada vez. Baleiras por defecto: baleira = non se
 * aplica. A duración máxima amosa o total calculado coa duración do uso.
 */
@Component({
  selector: 'furar-facility-use-rules',
  imports: [
    ButtonDirective,
    IconComponent,
    InputDirective,
    NgTemplateOutlet,
    OptionsPipe,
    ReactiveFormsModule,
    SelectComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-use-rules' },
  template: `
    @let t = 'facility.uses.rules.';
    @let f = form();
    @for (key of keys; track key) {
      @let text = texts()[key];
      @let isOpen = open() === key;
      <div class="f-use-rule" [attr.data-open]="isOpen ? '' : null" [attr.data-rule]="key">
        <button
          type="button"
          class="f-use-rule__head"
          [id]="idPrefix() + '-rule-' + key"
          [attr.aria-expanded]="isOpen"
          [attr.aria-controls]="idPrefix() + '-rule-' + key + '-body'"
          (click)="toggle(key)"
        >
          <span class="min-w-0">
            <b class="block">{{ t + key + '.label' | transloco }}</b>
            <span class="f-use-rule__value" [attr.data-empty]="text ? null : ''">{{
              text ?? (t + 'empty' | transloco)
            }}</span>
          </span>
          <furar-icon name="chevron-down" class="f-use-rule__chevron" />
        </button>
        @if (isOpen) {
          <div
            class="f-use-rule__body"
            role="group"
            [id]="idPrefix() + '-rule-' + key + '-body'"
            [attr.aria-labelledby]="idPrefix() + '-rule-' + key"
            [formGroup]="f"
          >
            <div class="f-use-rule__fields">
              @switch (key) {
                @case ('advance') {
                  <span class="f-use-rule__line">
                    <span class="f-use-rule__lead">{{ t + 'advance.from' | transloco }}</span>
                    <ng-container *ngTemplateOutlet="number; context: { field: 'minAdvanceHours' }" />
                    {{ t + 'advance.hoursBefore' | transloco }}
                  </span>
                  <span class="f-use-rule__line">
                    <span class="f-use-rule__lead">{{ t + 'advance.to' | transloco }}</span>
                    <ng-container *ngTemplateOutlet="number; context: { field: 'maxAdvanceDays' }" />
                    {{ t + 'advance.daysBefore' | transloco }}
                  </span>
                }
                @case ('duration') {
                  <span class="f-use-rule__line">
                    {{ t + 'duration.upTo' | transloco }}
                    <ng-container *ngTemplateOutlet="number; context: { field: 'maxSlots' }" />
                    {{ t + 'duration.slots' | transloco }}
                  </span>
                  @if (example(); as ex) {
                    <span class="f-use-rule__example" data-duration-example>
                      {{ t + 'duration.example' | transloco: { minutes: slotMinutes() } }}
                      <b>{{ ex }}</b>
                      {{ t + 'duration.exampleEnd' | transloco }}
                    </span>
                  }
                }
                @case ('cap') {
                  <span class="f-use-rule__line">
                    <ng-container *ngTemplateOutlet="number; context: { field: 'capCount' }" />
                    {{ t + 'cap.per' | transloco }}
                    <furar-select
                      class="f-use-rule__select"
                      formControlName="capPeriod"
                      [label]="t + 'cap.period' | transloco"
                      [options]="periods | furarOptions: t + 'cap.' : lang()"
                    />
                  </span>
                }
                @case ('cancel') {
                  <span class="f-use-rule__line">
                    {{ t + 'cancel.upTo' | transloco }}
                    <ng-container *ngTemplateOutlet="number; context: { field: 'cancelHours' }" />
                    {{ t + 'cancel.hoursBefore' | transloco }}
                  </span>
                }
              }
              @for (field of fields[key]; track field) {
                @if (errors()[field]; as e) {
                  <span class="f-field-error" [id]="idPrefix() + '-' + field + '-error'">
                    {{ e.key | transloco: e.params }}
                  </span>
                }
              }
            </div>
            @if (text) {
              <button
                type="button"
                furarButton="quiet"
                size="sm"
                class="justify-self-start"
                [attr.data-rule-clear]="key"
                (click)="clear(key)"
              >
                {{ t + 'clear' | transloco }}
              </button>
            }
          </div>
        }
      </div>
    }
    <ng-template #number let-field="field">
      <input
        furarInput
        type="number"
        inputmode="numeric"
        class="f-use-rule__input"
        placeholder="—"
        [formControl]="f.controls[asField(field)]"
        [min]="limits[asField(field)].min"
        [max]="limits[asField(field)].max"
        [attr.data-field]="field"
        [attr.aria-label]="t + 'fields.' + field | transloco: limits[asField(field)]"
        [attr.aria-invalid]="errors()[asField(field)] ? 'true' : null"
        [attr.aria-describedby]="errors()[asField(field)] ? idPrefix() + '-' + field + '-error' : null"
      />
    </ng-template>
  `,
})
export class UseRulesComponent {
  readonly form = input.required<RulesForm>();
  /** Duración de cada reserva do uso, para o total da duración máxima. */
  readonly slotMinutes = input.required<number>();
  readonly idPrefix = input.required<string>();

  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly events = toSignal(
    toObservable(this.form).pipe(switchMap((form) => form.events.pipe(startWith(null)))),
  );

  protected readonly keys = RULE_KEYS;
  protected readonly fields = RULE_FIELDS;
  protected readonly limits = RULE_LIMITS;
  protected readonly periods = CAP_PERIODS;
  protected readonly lang = inject(LanguageService).lang;
  protected readonly open = signal<RuleKey | null>(null);

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly texts = computed(() => {
    this.events();
    this.langChanges();
    const rules = this.form().getRawValue();
    const slot = this.slotMinutes();
    return Object.fromEntries(RULE_KEYS.map((key) => [key, ruleText(key, rules, slot, this.t)])) as Record<
      RuleKey,
      string | null
    >;
  });

  protected readonly example = computed(() => {
    this.events();
    this.langChanges();
    const n = this.form().controls.maxSlots.value;
    if (!n || this.form().controls.maxSlots.invalid) return null;
    const slot = this.slotMinutes();
    return this.t('facility.uses.rules.duration.total', { minutes: slot, n, total: durationText(slot * n, this.t) });
  });

  protected readonly errors = computed(() => {
    this.events();
    const c = this.form().controls;
    return Object.fromEntries(
      Object.keys(RULE_LIMITS).map((field) => [field, fieldError(c[field as keyof typeof RULE_LIMITS], false)]),
    ) as Record<keyof typeof RULE_LIMITS, ControlError | null>;
  });

  protected asField(field: string): keyof typeof RULE_LIMITS {
    return field as keyof typeof RULE_LIMITS;
  }

  protected toggle(key: RuleKey): void {
    const opening = this.open() !== key;
    this.open.set(opening ? key : null);
    if (opening) this.focusIn(key);
  }

  protected clear(key: RuleKey): void {
    clearRule(this.form(), key);
    this.host.nativeElement.querySelector<HTMLElement>(`#${this.idPrefix()}-rule-${key}`)?.focus();
  }

  /** Abre a primeira norma con erro e leva o foco ao seu campo (ao gardar ou tras a API). */
  revealErrors(): boolean {
    const c = this.form().controls;
    const key = RULE_KEYS.find((k) => RULE_FIELDS[k].some((field) => c[field].invalid));
    if (!key) return false;
    this.open.set(key);
    this.focusIn(key, '[aria-invalid="true"]');
    return true;
  }

  private focusIn(key: RuleKey, selector = 'input'): void {
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector<HTMLElement>(`[data-rule="${key}"] .f-use-rule__body ${selector}`)
          ?.focus(),
      { injector: this.injector },
    );
  }
}
