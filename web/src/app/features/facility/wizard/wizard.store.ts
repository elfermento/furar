// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import type { AbstractControl } from '@angular/forms';
import { LanguageService } from '../../../core/i18n/language.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { applyFieldErrors, asProblem } from '../../../shared/ui/edit-section/section-errors';
import { DataCardStore } from '../data-card/data-card.store';
import { PaymentNotesStore } from '../description/payment-notes.store';
import type { Facility } from '../detail/facility-detail.store';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { locationControl } from '../location/location-form';
import type { DraftImage } from '../photos/photos';
import { missingAlt } from '../photos/photos';
import { ScheduleStore } from '../schedule/schedule.store';
import type { Use } from '../uses/uses.store';
import { UsesStore } from '../uses/uses.store';
import type { StepKey } from './wizard';
import { REVIEW, STEP, STEP_KEYS, resumeStep } from './wizard';
import { WizardForms } from './wizard-forms';
import { WizardSaver } from './wizard-saver.store';
import { WizardUseEditor } from './wizard-use-editor';

export { NAME_MAX, SPORTS_REQUIRED } from './wizard-forms';
export type { PaymentScope } from './wizard-forms';
export type { UseEditor } from './wizard-use-editor';

const NAME_TAKEN = 'facility.name-taken';
export type LoadState = 'loading' | 'ready' | 'error';

/** O que lle falta para publicala: o texto (`facility.wizard.review.missing.*`) e o seu paso. */
export interface MissingItem {
  readonly key: string;
  readonly step: number;
}

/**
 * Estado do asistente «Nova instalación» (maqueta 53), un por páxina: o paso, ata onde se chegou,
 * se están ben e o que falta para publicar. «Seguinte» comproba e garda o paso (o primeiro crea a
 * instalación, que nace sen publicar); «Gardar como borrador» garda o que estea ben (co nome
 * abonda) e «Publicar», só se non falta nada. Os formularios están en `WizardForms` e o gardado,
 * en `WizardSaver`.
 */
@Injectable()
export class WizardStore {
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly saver = inject(WizardSaver);
  private readonly paymentNotes = inject(PaymentNotesStore);
  private readonly dataStore = inject(DataCardStore);
  readonly forms = inject(WizardForms);
  readonly detail = inject(FacilityDetailStore);
  readonly uses = inject(UsesStore);
  readonly schedule = inject(ScheduleStore);
  readonly useEditor = inject(WizardUseEditor);

  readonly step = signal(0);
  /** O paso máis lonxe que se visitou: ata el pódese ir desde o indicador. */
  readonly max = signal(0);
  /** Pasos nos que se pulsou «Seguinte»: só neles se ven os erros sen tocar o campo. */
  readonly tried = signal<ReadonlySet<number>>(new Set());
  readonly state = signal<LoadState>('ready');
  readonly busy = signal(false);
  readonly problem = signal<ApiProblem | null>(null);
  /** Hora do último gardado («18:32»). */
  readonly savedAt = signal<string | null>(null);
  readonly facility = this.detail.facility;
  readonly mapEnabled = inject(DeploymentSettings).mapEnabled;

  // Os formularios, á man dos pasos.
  readonly name = this.forms.name;
  readonly data = this.forms.data;
  readonly description = this.forms.description;
  readonly location = this.forms.location;
  readonly amenities = this.forms.amenities;
  readonly houseRules = this.forms.houseRules;
  readonly payment = this.forms.payment;
  readonly scheduleMode = this.forms.scheduleMode;
  readonly sameHours = this.forms.sameHours;
  readonly week = this.forms.week;
  readonly images = this.forms.images;
  readonly drawings = this.forms.drawings;
  readonly definitions = this.forms.definitions;
  readonly scope = this.forms.scope;
  readonly notes = this.forms.notes;

  // --- Abrir ---

  /** Asistente baleiro: os campos propios e o pago do concello, para os pasos 3 e 7. */
  async start(): Promise<void> {
    const slug = this.municipality.slug();
    if (slug) await Promise.all([this.loadDefinitions(slug, {}), this.loadNotes(slug)]);
  }

  /** Volver a un borrador: os seus datos en cada paso e, de primeiras, o paso onde quedou. */
  async open(id: string): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    this.state.set('loading');
    await this.detail.load(slug, id);
    const facility = this.detail.facility();
    try {
      if (!facility) throw new Error('Sen instalación');
      await Promise.all([
        this.uses.load(slug, id),
        this.schedule.load(slug, id),
        this.loadDefinitions(slug, facility.details.customFields),
        this.loadNotes(slug),
      ]);
      this.forms.fill(facility, this.schedule.seasons()[0] ?? null);
    } catch {
      this.state.set('error');
      return;
    }
    const step = resumeStep(facility, this.mapEnabled());
    this.step.set(step);
    this.max.set(step);
    this.state.set('ready');
  }

  private async loadDefinitions(slug: string, values: Record<string, unknown>): Promise<void> {
    this.forms.setDefinitions(await this.dataStore.definitions(slug).catch(() => []), values);
  }

  private async loadNotes(slug: string): Promise<void> {
    const notes = await this.paymentNotes.municipalityNotes(slug);
    this.forms.notes.set(notes);
    this.forms.scope.set(notes?.scope === 'FACILITY' ? 'FACILITY' : 'ALL');
  }

  // --- Comprobacións ---

  /** O paso está ben (os opcionais, se o que teñen está ben escrito). */
  readonly ok = computed<Record<StepKey, boolean>>(() => {
    this.forms.changes();
    const c = this.data.controls;
    return {
      data: this.name.valid && c.sports.valid && this.description.valid,
      location: this.location.valid,
      contact: c.phone.valid && c.email.valid && c.ridegaCode.valid && c.customFields.valid,
      photos: missingAlt(this.images()) === null,
      uses: this.uses.uses().length > 0,
      schedule: this.forms.scheduleOk(),
      rules: this.houseRules.valid && (this.scope() !== 'FACILITY' || this.payment.valid),
      review: true,
    };
  });

  /** «Por días» sen ningún día aberto. */
  readonly noOpenDay = computed(() => (this.forms.changes(), this.forms.noOpenDay()));

  /** «Para publicala falta» (paso «Revisar»), na orde dos pasos. */
  readonly missing = computed<MissingItem[]>(() => {
    const ok = this.ok();
    const checks: [boolean, string, number][] = [
      [this.name.valid, 'name', STEP.data],
      [this.data.controls.sports.valid, 'sports', STEP.data],
      [this.description.valid, 'description', STEP.data],
      [ok.location, 'address', STEP.location],
      [ok.contact, 'contact', STEP.contact],
      [ok.photos, 'photos', STEP.photos],
      [ok.uses, 'uses', STEP.uses],
      [ok.schedule, 'schedule', STEP.schedule],
      [ok.rules, this.scope() === 'FACILITY' ? 'rulesAndPayment' : 'rules', STEP.rules],
    ];
    return checks.filter(([fine]) => !fine).map(([, key, step]) => ({ key, step }));
  });

  /** Os campos propios obrigatorios piden o paso 3: deixa de ser opcional. */
  readonly contactRequired = computed(() => this.definitions().some((field) => field.required));

  /** Hai algo que gardar ou descartar ao saír. */
  readonly started = computed(() => {
    this.forms.changes();
    return !!this.facility() || this.name.value.trim() !== '';
  });

  // --- Navegación ---

  goTo(step: number): void {
    this.problem.set(null);
    this.step.set(step);
    this.max.update((max) => Math.max(max, step));
  }

  private markTried(step: number): void {
    this.tried.update((set) => new Set([...set, step]));
    const c = this.data.controls;
    const forms: Partial<Record<StepKey, AbstractControl[]>> = {
      data: [this.name, c.sports, this.description],
      location: [this.location],
      contact: [c.phone, c.email, c.ridegaCode, c.customFields],
      schedule: [this.sameHours, this.week()],
      rules: [this.houseRules, this.payment],
    };
    for (const form of forms[STEP_KEYS[step]] ?? []) form.markAllAsTouched();
    this.forms.touch();
  }

  /** «Seguinte»: comproba o paso (os erros, no seu campo) e gárdao. `false` se queda no paso. */
  async next(): Promise<boolean> {
    const step = this.step();
    if (step === STEP.uses && !(await this.useEditor.apply())) return false;
    this.markTried(step);
    if (!this.ok()[STEP_KEYS[step]] || !(await this.save([step]))) return false;
    this.goTo(step + 1);
    return true;
  }

  /** «Gardar como borrador»: co nome abonda; garda o que estea ben de cada paso. */
  async saveDraft(): Promise<boolean> {
    if (!this.name.valid) {
      this.markTried(STEP.data);
      this.goTo(STEP.data);
      return false;
    }
    if (!(await this.useEditor.apply())) return false;
    // Só os pasos visitados (o horario por defecto non se garda sen velo) e que estean ben.
    const ok = this.ok();
    const steps = STEP_KEYS.map((_, i) => i).filter((i) => i !== REVIEW && i <= this.max());
    return this.save(
      steps.filter((i) => i === STEP.data || ok[STEP_KEYS[i]]),
      true,
    );
  }

  /** «Publicar»: garda o que quede e publica. */
  async publish(): Promise<Facility | null> {
    if (this.missing().length) return null;
    if (!(await this.save(STEP_KEYS.map((_, i) => i).filter((i) => i !== REVIEW)))) return null;
    return this.run(() => this.saver.publish());
  }

  /** Descartar: se xa estaba gardada, elimínase (un borrador nunca tivo reservas). */
  async discard(): Promise<boolean> {
    if (!this.facility()) return true;
    return (await this.run(async () => (await this.detail.delete(), true))) !== null;
  }

  private async save(steps: readonly number[], partial = false): Promise<boolean> {
    const done = await this.run(async () => {
      for (const step of steps) await this.saver.save(STEP_KEYS[step], partial);
      return true;
    });
    if (done) {
      const time = new Date().toLocaleTimeString(this.language.lang(), { hour: '2-digit', minute: '2-digit' });
      this.savedAt.set(time);
    }
    return !!done;
  }

  // --- Fotos ---

  loadDrawings(): void {
    this.forms.loadDrawings();
  }

  addPhoto(image: DraftImage): void {
    this.saver.uploaded(image.id);
    this.forms.setPhotos([...this.images(), image]);
  }

  /** Quita unha foto da lista; se aínda non se gardou en ningures, descártase. */
  removePhoto(image: DraftImage): void {
    this.forms.setPhotos(this.images().filter((item) => item.key !== image.key));
    this.saver.discardUpload(image.id);
  }

  chooseDrawing(code: string): void {
    this.forms.chooseDrawing(code, this.facility()?.suggestedImage?.id ?? null);
  }

  /** O debuxo que se ve sen fotos: o escollido ou o suxerido. */
  readonly shownDrawing = computed(() => this.forms.drawing() ?? this.facility()?.suggestedImage?.id ?? null);

  // --- Usos ---

  async removeUse(use: Use): Promise<void> {
    await this.run(async () => {
      await this.uses.archive(use.id);
      await this.detail.reload();
      return true;
    });
  }

  // --- Apoio ---

  /** Fai a petición co aviso da API no paso; os erros con campo, no seu campo. `null` se falla. */
  private async run<T>(work: () => Promise<T>): Promise<T | null> {
    this.busy.set(true);
    this.problem.set(null);
    try {
      return await work();
    } catch (error) {
      this.showProblem(asProblem(error));
      return null;
    } finally {
      this.busy.set(false);
      this.forms.touch();
    }
  }

  private showProblem(problem: ApiProblem): void {
    if (problem.code === NAME_TAKEN) {
      this.name.setErrors({ [NAME_TAKEN]: problem.params });
      this.name.markAsTouched();
      this.goTo(STEP.data);
      return;
    }
    const unmatched = applyFieldErrors(this.location, problem, (field) =>
      field.startsWith('address.') ? locationControl(this.location, field) : null,
    );
    if (unmatched === problem.fieldErrors.length) {
      applyFieldErrors(this.data, problem, (field) => this.data.get(field.replace(/^details\./, '')));
    }
    this.problem.set(problem);
  }
}
