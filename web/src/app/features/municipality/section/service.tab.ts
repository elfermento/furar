// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef, OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { LeavableTab } from '../../../core/unsaved-changes/leave-tab.guard';
import { localized } from '../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../shared/ui/bilingual-field/bilingual-field.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import type {
  MunicipalityResponse,
  PaymentNotesResponse,
  SportsContactResponse,
  Versioned,
} from '../municipality.store';
import { MunicipalityStore } from '../municipality.store';
import type { ContactForm, NotesForm } from '../sports-service-form';
import { NOTE_MAX, contactBody, contactForm, notesBody, notesForm } from '../sports-service-form';
import { tabForm } from './tab-form';
import { TabSave } from './tab-save';

export type PaymentScope = PaymentNotesResponse['scope'];

type ServiceForm = FormGroup<{
  contact: ContactForm;
  scope: FormControl<PaymentScope>;
  notes: NotesForm;
}>;

const CONTACT_FIELDS = [
  { key: 'name', autocomplete: 'organization', type: 'text' },
  { key: 'address', autocomplete: 'street-address', type: 'text' },
  { key: 'phone', autocomplete: 'tel', type: 'tel' },
  { key: 'email', autocomplete: 'email', type: 'email' },
] as const;

/**
 * «Servizo» da sección «Concello» (maqueta 49; D2-11): o contacto do servizo de deportes (o pé dos
 * correos e a ficha) e «Pago e devolución». Este último ten dous modos: «Igual para todas as
 * instalacións» (os dous textos gl/es aquí; baleiros, os de Furar) e «Distinto por instalación»
 * (só unha liña: cada ficha leva os seus, sen herdar estes, que quedan gardados). Garda cos seus
 * `PUT` e a versión do concello (`If-Match`), un detrás doutro.
 */
@Component({
  selector: 'furar-municipality-service-tab',
  imports: [
    BilingualFieldComponent,
    ButtonDirective,
    CardComponent,
    FieldComponent,
    FormErrorsSummaryComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-section__col' },
  templateUrl: './service.tab.html',
})
export class ServiceTab implements OnInit, LeavableTab {
  private readonly store = inject(MunicipalityStore);
  private readonly current = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());
  private readonly formElement = viewChild<ElementRef<HTMLElement>>('formElement');

  protected readonly tab = tabForm<ServiceForm>('service');
  protected readonly saver = new TabSave();
  protected readonly fields = CONTACT_FIELDS;
  protected readonly noteKeys = ['payment', 'refund'] as const;
  protected readonly scopes: readonly PaymentScope[] = ['ALL', 'FACILITY'];
  protected readonly noteMax = NOTE_MAX;
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  private readonly contact = signal<SportsContactResponse>({});
  private readonly notes = signal<PaymentNotesResponse | null>(null);

  /** O texto de Furar que se ve se cada nota vai baleira, no idioma de quen mira. */
  protected readonly defaults = computed(() => {
    const notes = this.notes();
    const lang = this.language.lang();
    return {
      payment: notes?.paymentDefault ? localized(notes.payment, lang) : null,
      refund: notes?.refundDefault ? localized(notes.refund, lang) : null,
    };
  });

  readonly dirty = this.tab.dirty;
  readonly tabName = computed(() => {
    this.translation();
    return this.transloco.translate('section.tab.service');
  });

  ngOnInit(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    const slug = this.current.slug();
    if (!slug) return;
    try {
      const [contact, notes] = await Promise.all([this.store.sportsContact(slug), this.store.paymentNotes(slug)]);
      this.contact.set(contact);
      this.notes.set(notes);
      this.reset();
    } catch (error: unknown) {
      this.saver.problem.set(asProblem(error));
    }
  }

  save(): Promise<boolean> {
    const form = this.tab.form();
    if (!form) return Promise.resolve(false);
    // «Distinto por instalación»: os textos de aquí non se ven; o que estea a medio escribir volve ao gardado.
    if (form.controls.scope.value === 'FACILITY' && form.controls.notes.invalid) {
      form.controls.notes.reset(notesForm(this.notes()).getRawValue());
    }
    const saved = notesForm(this.notes()).getRawValue();
    return this.saver.run(
      form,
      this.formElement(),
      async () => {
        let municipality = this.current.value();
        if (!municipality) return;
        const slug = municipality.slug;
        const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
        if (!same(form.controls.contact.getRawValue(), contactForm(this.contact()).getRawValue())) {
          const result = await this.chained(municipality, (version) =>
            this.store.updateSportsContact(slug, version, contactBody(form.controls.contact)),
          );
          this.contact.set(result.body);
          municipality = result.municipality;
        }
        const scope = form.controls.scope.value;
        if (!same(form.controls.notes.getRawValue(), saved) || scope !== (this.notes()?.scope ?? 'ALL')) {
          const result = await this.chained(municipality, (version) =>
            this.store.updatePaymentNotes(slug, version, { ...notesBody(form.controls.notes), scope }),
          );
          this.notes.set(result.body);
        }
        this.reset();
      },
      (problem) => {
        for (const error of problem.fieldErrors) {
          const path = error.field.replace(/^sportsContact\./, 'contact.').replace(/^paymentNotes\./, 'notes.');
          const control = form.get(path);
          control?.setErrors({ [error.code]: error.params });
          control?.markAsTouched();
        }
        return problem.fieldErrors.length;
      },
    );
  }

  discard(): void {
    this.reset();
    this.saver.clear();
  }

  protected async reload(): Promise<void> {
    if (await this.saver.reloadMunicipality()) await this.load();
  }

  /** Un `PUT` coa versión do concello; deixa a nova no concello para o seguinte. */
  private async chained<T>(
    municipality: MunicipalityResponse,
    put: (version: number) => Promise<Versioned<T>>,
  ): Promise<{ municipality: MunicipalityResponse; body: T }> {
    const saved = await put(municipality.version);
    const updated = { ...municipality, version: saved.version };
    this.current.set(updated);
    return { municipality: updated, body: saved.body };
  }

  private reset(): void {
    this.tab.set(
      new FormGroup({
        contact: contactForm(this.contact()),
        scope: new FormControl<PaymentScope>(this.notes()?.scope ?? 'ALL', { nonNullable: true }),
        notes: notesForm(this.notes()),
      }),
    );
  }
}
