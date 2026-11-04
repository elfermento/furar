// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { activeDefinitions, isEmpty } from '../../../../shared/custom-fields/custom-field-controls';
import { format } from '../../../../shared/custom-fields/custom-fields-view.component';
import { localized } from '../../../../shared/custom-fields/localized';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../../shared/ui/callout/callout.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { MapComponent } from '../../../../shared/ui/map/map.component';
import { pointOf } from '../../../../shared/ui/map/map-point';
import { amenitiesBody } from '../../amenities/amenities';
import { cardAddress } from '../../list/card-address';
import { altIn } from '../../photos/photos';
import { HOLIDAYS_ROW } from '../../schedule/schedule.store';
import { intervalsText, weekdayName } from '../../schedule/week-format';
import { UseTagsComponent } from '../../uses/use-row.component';
import type { Use } from '../../uses/uses.store';
import { STEP, STEP_KEYS, priceText, stepLabelKey } from '../wizard';
import { priceMuted, pricePer } from '../../uses/use-price';
import { PriceComponent } from '../../../../shared/ui/money/price.component';
import { WizardStore } from '../wizard.store';

/** Unha fila do resumo: o valor, «Sen indicar» ou o que falta (en vermello). */
interface Row {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly problem?: string;
}

/**
 * Paso 8, «Revisar» (maqueta 53): se falta algo para publicar, o aviso coa lista (cada elemento
 * leva ao seu paso); despois, un bloque por paso co que se escribiu e «Editar». «Publicar» vai
 * na barra do asistente.
 */
@Component({
  selector: 'furar-wizard-review-step',
  imports: [
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    MapComponent,
    PriceComponent,
    TranslocoPipe,
    UseTagsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './review-step.component.html',
})
export class ReviewStepComponent implements OnInit {
  protected readonly store = inject(WizardStore);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly translations = toSignal(this.transloco.selectTranslation());
  protected readonly steps = STEP;
  protected readonly mapEnabled = this.store.mapEnabled;
  /** Ir a un paso para corrixilo. */
  readonly edit = output<number>();

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly point = computed(() => {
    this.store.ok();
    return pointOf(this.store.location.controls.point.value);
  });

  /** O debuxo que se ve sen fotos: o escollido ou o suxerido. */
  private readonly drawing = computed(
    () => this.store.drawings().find((drawing) => drawing.code === this.store.shownDrawing()) ?? null,
  );

  protected readonly thumbs = computed(() => {
    this.store.ok();
    const lang = this.language.lang();
    const images = this.store.images();
    if (images.length) return images.map((image) => ({ src: image.thumb, alt: altIn(image, lang) }));
    const drawing = this.drawing();
    return drawing ? [{ src: drawing.thumb, alt: localized(drawing.alt, lang) }] : [];
  });

  protected readonly drawingName = computed(() => {
    if (this.store.images().length) return null;
    const drawing = this.drawing();
    return drawing ? localized(drawing.name, this.language.lang()) : null;
  });

  protected readonly sections = computed(() => {
    this.translations();
    this.store.ok();
    const lang = this.language.lang();
    const t = this.t;
    const missing = t('facility.wizard.review.missingValue');
    const invalid = t('facility.wizard.review.invalid');
    const yesNo = (value: boolean | null) => (value === null ? '' : t(value ? 'common.yes' : 'common.no'));
    const data = this.store.data.getRawValue();
    const c = this.store.data.controls;
    const description = this.store.description.getRawValue();
    const rules = this.store.houseRules.getRawValue();
    const name = (step: number) => t(stepLabelKey(STEP_KEYS[step], this.store.scope()));
    const own = activeDefinitions(this.store.definitions())
      .filter((field) => c.customFields.controls[field.code])
      .map((field): Row => {
        const value = c.customFields.controls[field.code].value;
        const shown = format(field, value, lang);
        return {
          id: `cf-${field.code}`,
          label: shown.label,
          value: isEmpty(value) ? '' : shown.translate ? t(shown.text) : shown.text,
          problem: c.customFields.controls[field.code].invalid ? invalid : undefined,
        };
      });
    const week = this.store.forms.weekBody();
    const notes = this.store.notes();
    const payment = this.store.payment.getRawValue();
    const paymentRow = (note: 'payment' | 'refund'): Row => ({
      id: note,
      label: t(`facility.paymentNotes.${note}`),
      value:
        localized(payment[note], lang) ||
        t('facility.paymentNotes.default', {
          text: localized(note === 'payment' ? notes?.furarPayment : notes?.furarRefund, lang),
        }),
    });
    return [
      {
        step: STEP.data,
        title: name(STEP.data),
        rows: [
          {
            id: 'name',
            label: t('facility.wizard.data.name'),
            value: this.store.name.value.trim(),
            problem: this.store.name.valid ? undefined : missing,
          },
          {
            id: 'sports',
            label: t('facility.data.sports.label'),
            value: data.sports.map((sport) => localized(sport.label, lang)).join(', '),
            problem: data.sports.length ? undefined : missing,
          },
          {
            id: 'type',
            label: t('facility.data.type.label'),
            value: data.type ? t(`facility.data.type.${data.type}`) : '',
          },
          { id: 'covered', label: t('facility.data.covered.label'), value: yesNo(data.covered) },
          {
            id: 'description',
            label: t('facility.description.title'),
            value: localized(description, lang),
            problem: this.store.description.valid ? undefined : t('facility.wizard.review.oneLanguage'),
          },
        ] as Row[],
      },
      {
        step: STEP.location,
        title: name(STEP.location),
        rows: [
          {
            id: 'address',
            label: t('facility.wizard.review.address'),
            value: cardAddress({ address: this.store.location.getRawValue() }),
            problem: this.store.ok().location ? undefined : missing,
          },
        ] as Row[],
      },
      {
        step: STEP.contact,
        title: name(STEP.contact),
        rows: [
          {
            id: 'phone',
            label: t('facility.data.phone.label'),
            value: data.phone.trim(),
            problem: c.phone.valid ? undefined : invalid,
          },
          {
            id: 'email',
            label: t('facility.data.email.label'),
            value: data.email.trim(),
            problem: c.email.valid ? undefined : invalid,
          },
          {
            id: 'amenities',
            label: t('facility.amenities.title'),
            value: amenitiesBody(this.store.amenities.getRawValue())
              .map((item) => t(`facility.amenities.item.${item}`))
              .join(', '),
          },
          {
            id: 'ridega',
            label: t('facility.data.ridegaCode.label'),
            value: data.ridegaCode.trim(),
            problem: c.ridegaCode.valid ? undefined : invalid,
          },
          ...own,
        ] as Row[],
      },
      { step: STEP.photos, title: name(STEP.photos), rows: [] as Row[] },
      {
        step: STEP.uses,
        title: name(STEP.uses),
        rows: [
          {
            id: 'who',
            label: t('facility.wizard.uses.who'),
            value: [
              t(data.onlineBooking ? 'facility.wizard.uses.residents' : 'facility.wizard.uses.staff'),
              ...(!data.onlineBooking && data.showFreeSlots ? [t('facility.data.showFreeSlots.label')] : []),
            ].join(' · '),
          },
        ] as Row[],
      },
      {
        step: STEP.schedule,
        title: name(STEP.schedule),
        rows: this.store.ok().schedule
          ? ([
              {
                id: 'season',
                label: t('facility.wizard.review.season'),
                value: t('facility.wizard.schedule.wholeYear'),
              },
              ...week.map((row, i) => ({
                id: row.day,
                label: i === HOLIDAYS_ROW ? t('facility.schedule.day.holidays') : weekdayName(i, lang),
                value: intervalsText(row.intervals, lang) || t('facility.schedule.day.closed'),
              })),
            ] as Row[])
          : ([{ id: 'schedule', label: t('facility.wizard.review.season'), value: '', problem: missing }] as Row[]),
      },
      {
        step: STEP.rules,
        title: name(STEP.rules),
        rows: [
          {
            id: 'rules',
            label: t('facility.houseRules.title'),
            value: localized(rules, lang),
            problem: this.store.houseRules.valid ? undefined : t('facility.wizard.review.oneLanguage'),
          },
          ...(this.store.scope() === 'FACILITY' ? [paymentRow('payment'), paymentRow('refund')] : []),
        ] as Row[],
      },
    ];
  });

  ngOnInit(): void {
    this.store.loadDrawings();
  }

  protected price(use: Use): string {
    this.translations();
    return priceText(use, this.t);
  }

  protected per(use: Use): string | null {
    this.translations();
    return pricePer(use, this.t);
  }

  protected muted(use: Use): boolean {
    return priceMuted(use);
  }
}
