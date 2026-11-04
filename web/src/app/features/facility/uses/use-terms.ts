// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import { weekdayNames } from '../../../shared/ui/calendar/local-date';
import { formatShort } from '../../../shared/ui/calendar/local-date';
import { centsToInput, parseCents } from '../../../shared/ui/money/money';
import { ALL_WEEKDAYS, MONDAY_TO_FRIDAY, WEEKEND } from '../../../shared/ui/weekday-picker/weekday-picker.component';
import type { Use, UsePriceBody, UseRules, UseRulesBody } from './uses.store';

/**
 * Prezo, normas e días da semana dun uso (D2-11; plan do dominio 2, §2.1 e §2.3; maqueta 21): os
 * formularios cos mesmos rangos ca `UsePrice` e `UseRules` da API, o corpo que vai no `PUT` e os
 * textos de resumo que comparten o modal e a ficha pública.
 */
export type PriceKind = Use['price']['kind'];
export type CapPeriod = NonNullable<UseRules['capPeriod']>;
/** As normas da API ou as do formulario (baleira = `undefined` ou `null`). */
export type Rules = { [K in keyof UseRules]?: UseRules[K] | null };

export const PRICE_KINDS: readonly PriceKind[] = ['NONE', 'FREE', 'PAID'];
export const CAP_PERIODS: readonly CapPeriod[] = ['DAY', 'WEEK', 'MONTH'];

/** Os mesmos límites ca `UsePrice` (céntimos) e `UseRules` da API. */
export const PRICE_LIMITS = { min: 1, max: 100_000 } as const;
export const RULE_LIMITS = {
  minAdvanceHours: { min: 0, max: 720 },
  maxAdvanceDays: { min: 1, max: 365 },
  maxSlots: { min: 1, max: 16 },
  capCount: { min: 1, max: 50 },
  cancelHours: { min: 0, max: 720 },
} as const;
export type RuleField = keyof typeof RULE_LIMITS;

/** As catro normas da maqueta, cos seus campos (a primeira ten dous: mínimo e máximo). */
export const RULE_KEYS = ['advance', 'duration', 'cap', 'cancel'] as const;
export type RuleKey = (typeof RULE_KEYS)[number];
export const RULE_FIELDS: Record<RuleKey, readonly RuleField[]> = {
  advance: ['minAdvanceHours', 'maxAdvanceDays'],
  duration: ['maxSlots'],
  cap: ['capCount'],
  cancel: ['cancelHours'],
};

export type PriceForm = FormGroup<{ kind: FormControl<PriceKind>; amountCents: FormControl<string> }>;
export type RulesForm = FormGroup<{
  minAdvanceHours: FormControl<number | null>;
  maxAdvanceDays: FormControl<number | null>;
  maxSlots: FormControl<number | null>;
  capCount: FormControl<number | null>;
  capPeriod: FormControl<CapPeriod>;
  cancelHours: FormControl<number | null>;
}>;

export const WEEKDAYS_INVALID = 'facility.use-weekdays-invalid';
export const PRICE_INVALID = 'facility.use-price-invalid';

const blank = (value: unknown): boolean => value === null || value === undefined || value === '';

/** Baleira = non se aplica; se non, un enteiro no rango da API. */
function ruleRange({ min, max }: { min: number; max: number }): ValidatorFn {
  return (control) => {
    const value = control.value as unknown;
    if (blank(value)) return null;
    return typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max
      ? { 'validation.range': { min, max } }
      : null;
  };
}

/** Un uso de pago leva un importe de 0,01 a 1.000 € (o campo só está activo con «De pago»). */
function amount(control: AbstractControl): ValidationErrors | null {
  const cents = parseCents(control.value as string);
  return cents === null || cents < PRICE_LIMITS.min || cents > PRICE_LIMITS.max ? { [PRICE_INVALID]: {} } : null;
}

/** Polo menos un día da semana. */
export function weekdaysValidator(control: AbstractControl): ValidationErrors | null {
  const mask = control.value as number;
  return Number.isInteger(mask) && mask >= 1 && mask <= ALL_WEEKDAYS ? null : { [WEEKDAYS_INVALID]: {} };
}

export function priceForm(price?: Use['price']): PriceForm {
  const form: PriceForm = new FormGroup({
    kind: new FormControl<PriceKind>(price?.kind ?? 'NONE', { nonNullable: true }),
    amountCents: new FormControl(centsToInput(price?.amountCents), { nonNullable: true, validators: amount }),
  });
  const apply = (kind: PriceKind): void => {
    const control = form.controls.amountCents;
    if (kind === 'PAID' && control.disabled) control.enable({ emitEvent: false });
    if (kind !== 'PAID' && control.enabled) control.disable({ emitEvent: false });
  };
  apply(form.controls.kind.value);
  form.controls.kind.valueChanges.subscribe(apply);
  return form;
}

export function rulesForm(rules: Rules = {}): RulesForm {
  const rule = (field: RuleField) =>
    new FormControl<number | null>(rules[field] ?? null, ruleRange(RULE_LIMITS[field]));
  return new FormGroup({
    minAdvanceHours: rule('minAdvanceHours'),
    maxAdvanceDays: rule('maxAdvanceDays'),
    maxSlots: rule('maxSlots'),
    capCount: rule('capCount'),
    capPeriod: new FormControl<CapPeriod>(rules.capPeriod ?? 'WEEK', { nonNullable: true }),
    cancelHours: rule('cancelHours'),
  });
}

/** Baleira toda a norma (os seus campos; o período do límite volve a «semana»). */
export function clearRule(form: RulesForm, key: RuleKey): void {
  for (const field of RULE_FIELDS[key]) form.controls[field].setValue(null);
  if (key === 'cap') form.controls.capPeriod.setValue('WEEK');
  form.markAsDirty();
}

export function priceBody(form: PriceForm): UsePriceBody {
  const { kind, amountCents } = form.getRawValue();
  return kind === 'PAID' ? { kind, amountCents: parseCents(amountCents) ?? undefined } : { kind };
}

/** Só as normas con valor; o período do límite, só se hai número (igual ca a API). */
export function rulesBody(form: RulesForm): UseRulesBody {
  const value = form.getRawValue();
  const body: UseRulesBody = {};
  for (const field of Object.keys(RULE_LIMITS) as RuleField[]) {
    const n = value[field];
    if (!blank(n)) body[field] = n as number;
  }
  if (body.capCount !== undefined) body.capPeriod = value.capPeriod;
  return body;
}

export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** «30 min», «1 hora», «2 horas», «1 h 30 min». */
export function durationText(minutes: number, t: Translate): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours && rest) return t('facility.uses.duration.hoursMinutes', { hours, minutes: rest });
  if (hours) return t(hours === 1 ? 'facility.uses.duration.hour' : 'facility.uses.duration.hours', { hours });
  return t('facility.uses.duration.minutes', { minutes: rest });
}

/**
 * O valor dunha norma en texto, ou `null` se non se aplica. `public` usa as frases da ficha
 * pública («Ata 2 horas seguidas», «Gratis ata 24 horas antes»); se non, as do modal.
 */
export function ruleText(
  key: RuleKey,
  rules: Rules,
  slotMinutes: number,
  t: Translate,
  audience: 'manager' | 'public' = 'manager',
): string | null {
  const r = 'facility.uses.rules.';
  switch (key) {
    case 'advance': {
      const min = rules.minAdvanceHours;
      const max = rules.maxAdvanceDays;
      if (!blank(min) && !blank(max)) return t(`${r}advance.both`, { min, max });
      if (!blank(min)) return t(`${r}advance.min`, { min });
      if (!blank(max)) return t(`${r}advance.max`, { max });
      return null;
    }
    case 'duration':
      return blank(rules.maxSlots)
        ? null
        : t(`${r}duration.${audience}`, { duration: durationText(slotMinutes * (rules.maxSlots ?? 0), t) });
    case 'cap':
      return blank(rules.capCount)
        ? null
        : t(rules.capCount === 1 ? `${r}cap.one` : `${r}cap.many`, {
            count: rules.capCount,
            period: t(`${r}cap.${rules.capPeriod ?? 'WEEK'}`),
          });
    case 'cancel':
      return blank(rules.cancelHours) ? null : t(`${r}cancel.${audience}`, { hours: rules.cancelHours });
  }
}

/**
 * Os días en texto (maqueta 21): «Todos os días», «De luns a venres», «Fins de semana» ou a
 * lista («Luns, mércores e venres»), cos nomes de `Intl` no idioma activo.
 */
export function weekdaysText(mask: number, lang: string, t: Translate): string {
  const w = 'facility.uses.when.';
  if (mask === ALL_WEEKDAYS) return t(`${w}all`);
  if (mask === MONDAY_TO_FRIDAY) return t(`${w}workdays`);
  if (mask === WEEKEND) return t(`${w}weekend`);
  const names = weekdayNames(lang)
    .filter((_, i) => (mask & (1 << i)) !== 0)
    .map(({ long }) => (long.endsWith('s') ? long : `${long}s`));
  if (!names.length) return t(`${w}none`);
  const text = names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')}${t(`${w}and`)}${names.at(-1)}`;
  return text.charAt(0).toLocaleUpperCase(lang) + text.slice(1);
}

/** «Sempre» ou as datas («Do 20/12/2026 ao 06/01/2027», «O 31/10/2026»), e se bloquea. */
export function whenDetail(
  availability: Use['availability'],
  periods: readonly { from: string | null; to: string | null }[],
  blocksOthers: boolean,
  t: Translate,
): string {
  const w = 'facility.uses.when.';
  if (availability === 'ALWAYS') return t(`${w}always`);
  const dates = periods
    .filter((p): p is { from: string; to: string } => !!p.from && !!p.to)
    .map(({ from, to }) =>
      from === to
        ? t(`${w}day`, { day: formatShort(from) })
        : t(`${w}range`, { from: formatShort(from), to: formatShort(to) }),
    );
  const text = dates.length ? dates.join(', ') : t(`${w}noDates`);
  return blocksOthers ? `${text} · ${t(`${w}blocks`)}` : text;
}
