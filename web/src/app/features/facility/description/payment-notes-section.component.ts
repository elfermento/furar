// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { AbstractControl } from '@angular/forms';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localized } from '../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../shared/ui/bilingual-field/bilingual-field.component';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { EditSectionComponent } from '../../../shared/ui/edit-section/edit-section.component';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import type { Facility } from '../detail/facility-detail.store';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { syncWhenPristine } from '../detail/section-form';
import type { DescriptionForm, DescriptionValue } from './description-form';
import { DESCRIPTION_LANGS, descriptionBody, optionalBilingualForm } from './description-form';
import type { PaymentNotesResponse } from './payment-notes.store';
import { PaymentNotesStore } from './payment-notes.store';

/** Máximo por idioma (`FacilityPaymentNotes.MAX` da API). */
export const PAYMENT_NOTE_MAX = 500;
/** O código dos erros dos textos (o mesmo ca os da API: o campo falta nun idioma). */
const NOTE_INVALID = 'validation.required';

const NOTES = ['payment', 'refund'] as const;
type NoteKey = (typeof NOTES)[number];
type NotesValue = Record<NoteKey, DescriptionValue>;

function notesValue(facility: Facility | null): NotesValue | null {
  if (!facility) return null;
  const notes = facility.paymentNotes;
  return {
    payment: { gl: notes?.payment?.gl ?? '', es: notes?.payment?.es ?? '' },
    refund: { gl: notes?.refund?.gl ?? '', es: notes?.refund?.es ?? '' },
  };
}

/**
 * Sección «Pago e devolución» da ficha (D2-11; maqueta 49): só se o concello escolleu «Distinto por
 * instalación» en Concello › Servizo. «Como se paga» e «Como se devolve» en gl e es; sen texto, a
 * persoa ve o de Furar (non o do concello: non se herda). Funciona igual ca «Normas da
 * instalación»: fóra da edición, os textos no idioma de quen mira (ou «Sen indicar»); garda co
 * `If-Match` da ficha cando a barra garda.
 */
@Component({
  selector: 'furar-facility-payment-notes',
  imports: [BilingualFieldComponent, EditSectionComponent, ReactiveFormsModule, TranslocoPipe, ValueListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (shown()) {
      <furar-edit-section
        [formGroup]="form"
        [heading]="'facility.paymentNotes.title' | transloco"
        [save]="save"
        [controlFor]="controlFor"
      >
        @if (session.editing()) {
          <div class="gap-form grid">
            @for (note of notes; track note) {
              <furar-bilingual-field
                multiline
                [rows]="2"
                [fieldId]="'facility-payment-' + note"
                [maxlength]="max"
                [label]="'facility.paymentNotes.' + note | transloco"
                [help]="'facility.paymentNotes.help' | transloco"
                [gl]="form.controls[note].controls.gl"
                [es]="form.controls[note].controls.es"
              />
            }
          </div>
        } @else {
          <furar-value-list data-payment-notes [items]="items()" />
        }
      </furar-edit-section>
    }
  `,
})
export class PaymentNotesSectionComponent implements OnInit {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(PaymentNotesStore);
  private readonly language = inject(LanguageService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());
  protected readonly session = inject(EditSession);

  protected readonly notes = NOTES;
  protected readonly max = PAYMENT_NOTE_MAX;
  protected readonly form = new FormGroup<Record<NoteKey, DescriptionForm>>({
    payment: optionalBilingualForm(PAYMENT_NOTE_MAX, NOTE_INVALID),
    refund: optionalBilingualForm(PAYMENT_NOTE_MAX, NOTE_INVALID),
  });
  private readonly municipalityNotes = signal<PaymentNotesResponse | null>(null);
  /** O concello pon os textos por instalación: só entón se ve a sección. */
  protected readonly shown = computed(() => this.municipalityNotes()?.scope === 'FACILITY');
  /** Fóra da edición: o texto da instalación ou, se non ten, o de Furar («Por defecto: …»). */
  protected readonly items = computed<ValueListItem[]>(() => {
    this.translation();
    const value = notesValue(this.detail.facility());
    const furar = this.municipalityNotes();
    const lang = this.language.lang();
    return NOTES.map((note) => {
      const own = localized(value?.[note], lang);
      const fallback = localized(note === 'payment' ? furar?.furarPayment : furar?.furarRefund, lang);
      return {
        id: note,
        label: this.transloco.translate(`facility.paymentNotes.${note}`),
        value: own || this.transloco.translate('facility.paymentNotes.default', { text: fallback }),
      };
    });
  });

  protected readonly controlFor = (field: string): AbstractControl | null => {
    const [, note, lang] = field.split('.');
    const group = NOTES.includes(note as NoteKey) ? this.form.controls[note as NoteKey] : null;
    return group && DESCRIPTION_LANGS.includes(lang as 'gl' | 'es') ? group.controls[lang as 'gl' | 'es'] : null;
  };

  protected readonly save = () =>
    this.detail.saveFacility((target) => {
      const { payment, refund } = this.form.getRawValue();
      return this.store.update(target, { payment: descriptionBody(payment), refund: descriptionBody(refund) });
    });

  constructor() {
    syncWhenPristine(this.form, () => notesValue(this.detail.facility()));
  }

  ngOnInit(): void {
    const slug = this.municipality.slug();
    if (slug) void this.store.municipalityNotes(slug).then((notes) => this.municipalityNotes.set(notes));
  }
}
