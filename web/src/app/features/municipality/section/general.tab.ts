// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef, OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { LeavableTab } from '../../../core/unsaved-changes/leave-tab.guard';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { CustomFieldsFormComponent } from '../../../shared/custom-fields/custom-fields-form.component';
import type { CustomFieldsForm } from '../../../shared/custom-fields/custom-field-controls';
import {
  applyServerErrors,
  buildCustomFieldsForm,
  customFieldValuesOf,
} from '../../../shared/custom-fields/custom-field-controls';
import { localized } from '../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../shared/ui/bilingual-field/bilingual-field.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { LongTextDirective } from '../../../shared/ui/long-text/long-text.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { LANGUAGE_FIELDS } from '../languages-fieldset.component';
import type { LanguagesForm } from '../municipality-form';
import { languagesForm, languagesOf, nameControl } from '../municipality-form';
import type {
  CustomFieldDefinition,
  MunicipalityResponse,
  PaymentNotesResponse,
  SportsContactResponse,
  Versioned,
} from '../municipality.store';
import { MunicipalityStore } from '../municipality.store';
import { noShowForm, saveNoShowPolicy, settleHiddenNumbers } from '../no-show-form';
import { OpenMatchPolicyComponent, openMatchPolicyForm, saveOpenMatchPolicy } from './open-match-policy.component';
import type { ContactForm, NotesForm } from '../sports-service-form';
import { CONTACT_ROWS, NOTE_MAX, contactBody, contactForm, notesBody, notesForm } from '../sports-service-form';
import { OpeningChecklistComponent } from './opening-checklist.component';
import { tabForm } from './tab-form';
import { type SwitchControls, openDataPreview, saveSwitches, switchControls } from './extra-switches';
import { TabSave } from './tab-save';

type PaymentScope = PaymentNotesResponse['scope'];

interface GeneralControls extends SwitchControls {
  name: FormControl<string>;
  languages: LanguagesForm;
  contact: ContactForm;
  scope: FormControl<PaymentScope>;
  notes: NotesForm;
  noShow: ReturnType<typeof noShowForm>;
  openMatch: ReturnType<typeof openMatchPolicyForm>;
  customFields: CustomFieldsForm;
}
type GeneralForm = FormGroup<GeneralControls>;

/** O valor gardado de cada parte, para mandar só o que cambiou. */
const json = (value: unknown) => JSON.stringify(value);

/**
 * «Xeral» da sección «Concello»: enriba, «Para abrir as reservas», só
 * mentres falte algo; despois, nome e idiomas, o contacto do servizo de deportes (o pé dos correos
 * e a ficha), «Pago e devolución» e os datos propios (campos personalizados do concello).
 * «Pago e devolución»: «Igual para todas» (os textos gl/es aquí; baleiros, os de Furar) ou
 * «Por instalación» (cada ficha leva os seus; estes quedan gardados).
 *
 * Un formulario e un «Gardar»: manda só as partes que cambiaron, cada unha co seu `PUT` e a
 * versión do concello (`If-Match`), un detrás doutro (o concello; o contacto; as notas). Se unha
 * falla, as anteriores xa quedan gardadas e volver gardar manda só o que falta. O tema e os datos
 * legais van coma estaban (cada pestana garda o seu).
 */
@Component({
  selector: 'furar-municipality-general-tab',
  imports: [
    BilingualFieldComponent,
    ButtonDirective,
    IconComponent,
    CardComponent,
    CustomFieldsFormComponent,
    FieldComponent,
    FieldGroupComponent,
    FormErrorsSummaryComponent,
    InputDirective,
    ...LANGUAGE_FIELDS,
    LongTextDirective,
    OpenMatchPolicyComponent,
    OpeningChecklistComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    RouterLink,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    StatusStateComponent,
    SwitchComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-section__col' },
  templateUrl: './general.tab.html',
})
export class GeneralTab implements OnInit, LeavableTab {
  private readonly store = inject(MunicipalityStore);
  private readonly current = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());
  private readonly formElement = viewChild<ElementRef<HTMLElement>>('formElement');
  protected readonly language = inject(LanguageService);

  protected readonly tab = tabForm<GeneralForm>('general');
  protected readonly saver = new TabSave();
  protected readonly definitions = signal<CustomFieldDefinition[]>([]);
  protected readonly contactRows = CONTACT_ROWS;
  protected readonly noteKeys = ['payment', 'refund'] as const;
  protected readonly scopes: readonly PaymentScope[] = ['ALL', 'FACILITY'];
  protected readonly noteMax = NOTE_MAX;
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  private readonly contact = signal<SportsContactResponse>({});
  private readonly notes = signal<PaymentNotesResponse | null>(null);
  /** O nome, idiomas e datos propios gardados (o que manda o `PUT` do concello). */
  private savedMunicipality = '';

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
    return this.transloco.translate('section.tab.general');
  });

  ngOnInit(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    const municipality = this.current.value();
    if (!municipality) return;
    const slug = municipality.slug;
    try {
      const [definitions, contact, notes] = await Promise.all([
        this.store.definitions(slug),
        this.store.sportsContact(slug),
        this.store.paymentNotes(slug),
      ]);
      this.definitions.set(definitions);
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
    // «Por instalación»: os textos de aquí non se ven; o que estea a medio escribir volve ao gardado.
    if (form.controls.scope.value === 'FACILITY' && form.controls.notes.invalid) {
      form.controls.notes.reset(notesForm(this.notes()).getRawValue());
    }
    settleHiddenNumbers(form.controls.noShow, this.current.value()?.noShowPolicy);
    return this.saver.run(
      form,
      this.formElement(),
      async () => {
        let municipality = this.current.value();
        if (!municipality) return;
        const slug = municipality.slug;
        if (this.municipalityPart(form) !== this.savedMunicipality) {
          const updated = await this.store.update(municipality, {
            name: form.getRawValue().name.trim(),
            languages: languagesOf(form.controls.languages),
            defaultLanguage: form.controls.languages.controls.defaultLanguage.value,
            theme: municipality.theme,
            customFields: customFieldValuesOf(this.definitions(), form.controls.customFields),
          });
          this.current.set(updated);
          this.savedMunicipality = this.municipalityPart(form);
          municipality = updated;
        }
        if (json(form.controls.contact.getRawValue()) !== json(contactForm(this.contact()).getRawValue())) {
          const result = await this.chained(municipality, (version) =>
            this.store.updateSportsContact(slug, version, contactBody(form.controls.contact)),
          );
          this.contact.set(result.body);
          municipality = result.municipality;
        }
        const scope = form.controls.scope.value;
        if (
          json(form.controls.notes.getRawValue()) !== json(notesForm(this.notes()).getRawValue()) ||
          scope !== (this.notes()?.scope ?? 'ALL')
        ) {
          const result = await this.chained(municipality, (version) =>
            this.store.updatePaymentNotes(slug, version, { ...notesBody(form.controls.notes), scope }),
          );
          this.notes.set(result.body);
        }
        // Cada interruptor de «Configuración adicional», co seu PUT e a versión que deixou o anterior.
        await saveNoShowPolicy(this.store, this.current, form.controls.noShow);
        await saveOpenMatchPolicy(this.store, this.current, form.controls.openMatch);
        await saveSwitches(this.store, this.current, form.controls);
        this.reset();
      },
      (problem) => {
        const rest = applyServerErrors(form.controls.customFields, problem.fieldErrors);
        for (const error of rest) {
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

  protected readonly previewUrl = computed(() => openDataPreview(this.current.value()));

  private municipalityPart(form: GeneralForm): string {
    const { name, languages, customFields } = form.getRawValue();
    return json({ name, languages, customFields });
  }

  private reset(): void {
    const municipality = this.current.value();
    if (!municipality) return;
    const form: GeneralForm = new FormGroup({
      name: nameControl(municipality.name),
      languages: languagesForm(municipality.languages, municipality.defaultLanguage),
      contact: contactForm(this.contact()),
      scope: new FormControl<PaymentScope>(this.notes()?.scope ?? 'ALL', { nonNullable: true }),
      notes: notesForm(this.notes()),
      noShow: noShowForm(municipality.noShowPolicy),
      openMatch: openMatchPolicyForm(municipality.openMatchPolicy),
      ...switchControls(municipality),
      customFields: buildCustomFieldsForm(this.definitions(), municipality.customFields),
    });
    this.savedMunicipality = this.municipalityPart(form);
    this.tab.set(form);
  }
}
