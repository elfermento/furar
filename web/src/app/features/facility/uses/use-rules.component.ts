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
import { FieldComponent } from '../../../shared/ui/field.component';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import type { RuleKey, RulesForm } from './use-terms';
import { CAP_PERIODS, RULE_FIELDS, RULE_KEYS, RULE_LIMITS, clearRule, durationText, ruleText } from './use-terms';

/**
 * «Condicións da reserva» do modal do uso: acordeón coas catro normas (cando reservar,
 * duración máxima, límite por persoa e cancelar sen custo), cada unha co seu valor en texto ou
 * «Sen establecer» e un chevron; abre unha de cada vez. Baleiras por defecto: baleira = non se
 * aplica. A duración máxima amosa o total calculado coa duración do uso.
 */
@Component({
  selector: 'furar-facility-use-rules',
  imports: [
    ButtonDirective,
    FieldComponent,
    FieldGroupComponent,
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
  templateUrl: './use-rules.component.html',
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
    // «Con reservas de 60 min: 60 min × 2 = 2 horas como máximo.», no «?» de «Como moito».
    return [
      this.t('facility.uses.rules.duration.example', { minutes: slot }),
      this.t('facility.uses.rules.duration.total', { minutes: slot, n, total: durationText(slot * n, this.t) }),
      this.t('facility.uses.rules.duration.exampleEnd'),
    ].join(' ');
  });

  protected readonly errors = computed(() => {
    this.events();
    const c = this.form().controls;
    return Object.fromEntries(
      Object.keys(RULE_LIMITS).map((field) => [field, fieldError(c[field as keyof typeof RULE_LIMITS], false)]),
    ) as Record<keyof typeof RULE_LIMITS, ControlError | null>;
  });

  /** A axuda do «?» (só a duración, co exemplo) e o erro do campo. */
  protected describedBy(field: string): string | null {
    const id = `${this.idPrefix()}-${field}`;
    const ids = [
      field === 'maxSlots' && this.example() ? `${id}-help` : null,
      this.errors()[this.asField(field)] ? `${id}-error` : null,
    ].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  }

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
