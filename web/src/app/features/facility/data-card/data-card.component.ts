// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { AbstractControl, FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { merge } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { CustomFieldComponent } from '../../../shared/custom-fields/custom-field.component';
import {
  activeDefinitions,
  buildCustomFieldsForm,
  customFieldValuesOf,
  isEmpty,
} from '../../../shared/custom-fields/custom-field-controls';
import type { CustomFieldDefinition, CustomFieldValue } from '../../../shared/custom-fields/custom-field.types';
import { format } from '../../../shared/custom-fields/custom-fields-view.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { EditFieldComponent } from '../../../shared/ui/edit-section/edit-field.component';
import { EditSectionComponent } from '../../../shared/ui/edit-section/edit-section.component';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { OptionsPipe } from '../../../shared/ui/select/options.pipe';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { syncWhenPristine } from '../detail/section-form';
import { PhotosDraft } from '../photos/photos-draft';
import { DataCardStore } from './data-card.store';
import { EMAIL_MAX, FACILITY_TYPES, RIDEGA_MAX, dataForm, dataValue, detailsBody } from './data-form';
import { SportsFieldComponent } from './sports-field.component';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

type TextName = 'ridegaCode' | 'phone' | 'email';

/** Os tres datos de texto, coa súa axuda e o teclado que piden no móbil. */
const TEXTS: readonly {
  readonly name: TextName;
  readonly type: string;
  readonly autocomplete: string | null;
  readonly inputmode: string | null;
  readonly max: number | null;
}[] = [
  { name: 'ridegaCode', type: 'text', autocomplete: 'off', inputmode: null, max: RIDEGA_MAX },
  { name: 'phone', type: 'tel', autocomplete: 'tel', inputmode: 'tel', max: null },
  { name: 'email', type: 'email', autocomplete: 'email', inputmode: 'email', max: EMAIL_MAX },
];

/** Ruta dun erro da API (`details.phone`, `details.sports[0]`) → nome do control. */
const DETAILS_FIELD = /^details\.(\w+)/;

/**
 * Tarxeta «Datos» da ficha (D1-21d; especificación, bloque 1; maqueta `02-ficha-instalacion`):
 * en edición, o nome primeiro (maqueta 54; gárdase co `PUT …/name` antes ca o resto); deportes, tipo, código RIDEGA, teléfono, correo, campos propios do concello, «a veciñanza
 * reserva por internet» (se non, «mostrar os ocos libres») e cuberta. Cada valor edítase no sitio
 * (`furar-edit-field`) e todos gardan xuntos no `PUT …/details` pola cola da ficha. Valida igual
 * ca o servidor; o que só sabe o servidor (deporte repetido) vai ao aviso da sección. Sen campos
 * propios no concello non se pinta nada para eles. No escritorio queda fixa ao baixar (P23).
 */
@Component({
  selector: 'furar-facility-data-card',
  imports: [
    RequiredNoteComponent,
    ButtonDirective,
    CalloutComponent,
    CustomFieldComponent,
    EditFieldComponent,
    EditSectionComponent,
    FieldComponent,
    InputDirective,
    OptionsPipe,
    SelectComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SportsFieldComponent,
    StatusStateComponent,
    SwitchComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './data-card.component.html',
})
export class DataCardComponent implements OnInit {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(DataCardStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);

  protected readonly lang = inject(LanguageService).lang;
  protected readonly types = FACILITY_TYPES;
  protected readonly texts = TEXTS;
  protected readonly describedBy = describedBy;
  protected readonly form = dataForm({ withName: true });
  private readonly nameControl = this.form.get('name') as FormControl<string>;
  protected readonly definitionsState = signal<'loading' | 'ready' | 'error'>('loading');
  private readonly definitions = signal<readonly CustomFieldDefinition[]>([]);
  /** Códigos dos campos propios que ten o formulario (os activos). */
  private readonly codes = computed(() => activeDefinitions(this.definitions()).map((field) => field.code));

  private readonly formEvents = toSignal(this.form.events);
  private readonly translations = toSignal(this.transloco.langChanges$);
  protected readonly onlineBooking = computed(() => {
    this.formEvents();
    return this.form.controls.onlineBooking.value;
  });

  /** `markAllAsTouched` só avisa a cada control (onlySelf): escóitanse tamén os de texto. */
  private readonly textEvents = toSignal(
    merge(this.nameControl.events, ...TEXTS.map(({ name }) => this.form.controls[name].events)),
  );
  protected readonly errors = computed(() => {
    this.formEvents();
    this.textEvents();
    const { ridegaCode, phone, email } = this.form.controls;
    return {
      name: fieldError(this.nameControl, false),
      ridegaCode: fieldError(ridegaCode, false),
      phone: fieldError(phone, false),
      email: fieldError(email, false),
    };
  });

  /** O gardado en texto (o que se ve en repouso), non o que se está escribindo. */
  protected readonly shown = computed(() => {
    this.translations();
    const t = (key: string): string => this.transloco.translate(key);
    const yesNo = (value: boolean): string => t(value ? 'common.yes' : 'common.no');
    const details = this.detail.facility()?.details;
    const lang = this.lang();
    return {
      sports: (details?.sports ?? []).map((sport) => (lang === 'es' ? sport.label.es : sport.label.gl)).join(', '),
      type: details?.type ? t(`facility.data.type.${details.type}`) : '',
      ridegaCode: details?.ridegaCode ?? '',
      phone: details?.phone ?? '',
      email: details?.email ?? '',
      onlineBooking: yesNo(details?.onlineBooking ?? true),
      showFreeSlots: yesNo(details?.showFreeSlots ?? false),
      covered: details?.covered === undefined || details.covered === null ? '' : yesNo(details.covered),
    };
  });

  protected readonly session = inject(EditSession, { optional: true });

  /** Campos propios activos, co seu control e o valor gardado en texto. */
  protected readonly customFields = computed(() => {
    this.formEvents();
    this.translations();
    const lang = this.lang();
    const saved = this.detail.facility()?.details.customFields ?? {};
    const record = this.form.controls.customFields;
    return activeDefinitions(this.definitions())
      .filter((definition) => record.controls[definition.code])
      .map((definition) => {
        const shown = format(definition, saved[definition.code], lang);
        const text = isEmpty(saved[definition.code])
          ? ''
          : shown.translate
            ? this.transloco.translate(shown.text)
            : shown.text;
        return {
          definition,
          label: shown.label,
          value: text,
          control: record.controls[definition.code] as FormControl<CustomFieldValue>,
        };
      });
  });

  protected readonly controlFor = (field: string): AbstractControl | null => {
    if (field === 'name') return this.nameControl;
    const name = DETAILS_FIELD.exec(field)?.[1];
    return name ? (this.form.get(name) ?? null) : null;
  };

  /**
   * Primeiro o nome, se cambiou (`PUT …/name`, a súa propia versión), e despois os datos co
   * `If-Match` xa novo: se os datos fallan, o nome queda gardado e a ficha ao día.
   */
  protected readonly save = async (): Promise<unknown> => {
    const name = this.nameControl.value.trim();
    if (name && name !== this.detail.facility()?.name) {
      await this.detail.saveFacility((target) => this.store.rename(target, name));
    }
    return this.detail.saveFacility((target) =>
      this.store.update(target, detailsBody(this.form.getRawValue(), this.customFieldsBody())),
    );
  };

  constructor() {
    syncWhenPristine(this.form, () => {
      const facility = this.detail.facility();
      const value = dataValue(facility, this.codes());
      return facility && value ? { name: facility.name, ...value } : null;
    });
    // O debuxo suxerido depende de deportes, tipo e cuberta: as fotos vano vendo sen agardar a gardar.
    const photos = inject(PhotosDraft, { optional: true });
    if (photos) {
      effect(() => {
        this.formEvents();
        const editing = this.session?.editing() ?? false;
        const { sports, type, covered } = this.form.getRawValue();
        untracked(() =>
          photos.suggestFor(
            editing
              ? { sports: sports.flatMap((sport) => (sport.code ? [sport.code] : [])), type: type || null, covered }
              : null,
          ),
        );
      });
    }
  }

  ngOnInit(): void {
    void this.loadDefinitions();
  }

  protected async loadDefinitions(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    this.definitionsState.set('loading');
    try {
      const definitions = await this.store.definitions(slug);
      const values = this.detail.facility()?.details.customFields ?? {};
      this.form.setControl('customFields', buildCustomFieldsForm(definitions, values));
      this.definitions.set(definitions);
      this.definitionsState.set('ready');
    } catch {
      this.definitionsState.set('error');
    }
  }

  /**
   * Os campos propios substitúense enteiros: os do formulario co que se escribiu (baleiros fóra)
   * e os demais como están. Así, se as definicións non cargaron, non se borra ningún valor.
   */
  private customFieldsBody(): Record<string, unknown> {
    const codes = this.codes();
    const others = Object.entries(this.detail.facility()?.details.customFields ?? {}).filter(
      ([code]) => !codes.includes(code),
    );
    return {
      ...Object.fromEntries(others),
      ...customFieldValuesOf(this.definitions(), this.form.controls.customFields),
    };
  }
}
