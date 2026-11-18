// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import { activityDates, inLang, scheduleWhen } from '../../shared/events/event-format';
import { conflictDates } from '../../shared/schedule/schedule';
import { ScheduleFacilitiesStore } from '../../shared/schedule/schedule-facilities.store';
import type { ScheduleValue } from '../../shared/schedule/schedule-fields.component';
import { ScheduleFieldsComponent } from '../../shared/schedule/schedule-fields.component';
import { SchedulePreviewComponent } from '../../shared/schedule/schedule-preview.component';
import { BilingualFieldComponent } from '../../shared/ui/bilingual-field/bilingual-field.component';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { madridToday } from '../../shared/ui/calendar/madrid-time';
import { DateFieldComponent } from '../../shared/ui/date-field/date-field.component';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import type { DialogService } from '../../shared/ui/dialog/dialog.service';
import { focusFirstError } from '../../shared/ui/edit-section/section-errors';
import { FieldGroupComponent } from '../../shared/ui/field-group/field-group.component';
import { FieldLineComponent } from '../../shared/ui/field-line/field-line.component';
import { FieldComponent } from '../../shared/ui/field.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { SegmentedOptionDirective, SegmentedRadioDirective } from '../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../shared/ui/segmented/segmented.directive';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { SwitchComponent } from '../../shared/ui/switch/switch.component';
import { TimePickerComponent } from '../../shared/ui/time-picker/time-picker.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import type { ValueListItem } from '../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../shared/ui/value-list/value-list.component';
import { ActivityPreview } from './activity-preview';
import { EventLimitsFieldsComponent } from './event-limits-fields.component';
import { DESCRIPTION_MAX, NAME_MAX, eventBody, eventForm, scheduleRequest } from './event-form';
import { formErrors } from './event-form-errors';
import { EventPlaceFieldComponent } from './event-place-field.component';
import { placeText } from './events';
import type { EventResponse, EventSessionsPreviewRequest } from './events.store';
import { EventsStore } from './events.store';

export interface EventDialogData {
  readonly slug: string;
  /** O evento que se edita (ou que se copia, con `copy`); sen el, un novo. */
  readonly event: EventResponse | null;
  /** «Duplicar»: unha alta co do evento copiado, sen persoas. */
  readonly copy?: boolean;
  /** As instalacións do concello, para o «Lugar». */
  readonly facilities: readonly SelectOption<string>[];
}

/** Abre a alta, a edición ou a copia dun evento; resolve co evento gardado (ou nada). */
export async function openEventDialog(
  dialogs: DialogService,
  data: EventDialogData,
): Promise<EventResponse | undefined> {
  const ref = dialogs.open<EventResponse, EventDialogData>(EventDialogComponent, { data, sheet: true });
  return firstValueFrom(ref.closed, { defaultValue: undefined });
}

let nextId = 0;

/**
 * Alta, edición e copia dun evento (ADR-0038, ADR-0041): nome en gl e es, «Cando» (Un día · Con
 * horario), a data e a hora ou Días · Horas · Datas, o lugar (a instalación, co uso e a parte nunha
 * actividade, ou outro sitio), Prazas · Prezo (· Cada), Nacidos desde · ata, a descrición e os
 * interruptores. Nunha actividade nova, a vista previa das sesións. Ao editar unha actividade, o
 * horario, as datas e o lugar lense como texto (non se cambian: remátase e duplícase).
 */
@Component({
  selector: 'furar-event-dialog',
  imports: [
    BilingualFieldComponent,
    ButtonDirective,
    DateFieldComponent,
    DialogComponent,
    EventLimitsFieldsComponent,
    EventPlaceFieldComponent,
    FieldComponent,
    FieldGroupComponent,
    FieldLineComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    ScheduleFieldsComponent,
    SchedulePreviewComponent,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SwitchComponent,
    TimePickerComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  providers: [ScheduleFacilitiesStore, ActivityPreview],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './event-dialog.component.html',
})
export class EventDialogComponent {
  protected readonly data = inject<EventDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<EventResponse>>(DialogRef);
  private readonly store = inject(EventsStore);
  private readonly facilityStore = inject(ScheduleFacilitiesStore);
  protected readonly planner = inject(ActivityPreview);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly id = `furar-event-${nextId++}`;
  protected readonly nameMax = NAME_MAX;
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly today = madridToday();
  protected readonly form = eventForm(this.data.event, this.data.copy);
  /** Editando (non unha copia): o tipo non cambia; nunha actividade, tampouco o horario e o lugar. */
  protected readonly editing = !!this.data.event && !this.data.copy;
  protected readonly submitted = signal(false);
  protected readonly saving = signal(false);
  private readonly saveProblem = signal<ApiProblem | null>(null);
  protected readonly problem = computed(() => this.saveProblem() ?? this.planner.problem());
  private readonly formEvents = signal(0);

  private readonly value = computed(() => {
    this.formEvents();
    return this.form.getRawValue();
  });
  protected readonly kind = computed(() => this.value().kind);
  protected readonly heading = computed(() => {
    this.language.lang();
    const kind = this.kind() === 'SCHEDULED' ? 'Activity' : 'Event';
    return this.transloco.translate(`event.form.${this.editing ? 'edit' : 'new'}${kind}`);
  });
  protected readonly deadlineOn = computed(() => this.value().deadlineOn);
  protected readonly placeKind = computed(() => this.value().placeKind);
  private readonly facilityId = computed(() => this.value().facilityId);
  protected readonly locked = computed(() => this.editing && this.kind() === 'SCHEDULED');
  protected readonly scheduleValue = computed<ScheduleValue>(() => {
    const { weekdays, startMinute, endMinute, firstDate, lastDate } = this.value();
    return { weekdays, startMinute, endMinute, firstDate, lastDate };
  });
  /** Os usos que valen para unha actividade: enteiros ou por partes (os por prazas non). */
  private readonly uses = computed(() =>
    (this.facilityStore.facility()?.uses ?? []).filter((use) => use.rentalMode !== 'SEATS'),
  );
  protected readonly useOptions = computed<SelectOption<string>[]>(() =>
    this.uses().map((use) => ({ value: use.id, label: use.name })),
  );
  protected readonly partOptions = computed<SelectOption<string>[]>(() => {
    const use = this.uses().find((item) => item.id === this.value().useId);
    return use?.rentalMode === 'PARTS' ? (use.parts ?? []).map((p) => ({ value: p.id, label: p.name })) : [];
  });
  /** O que non se cambia dunha actividade, como texto. */
  protected readonly fixedRows = computed<ValueListItem[]>(() => {
    const event = this.data.event;
    if (!this.locked() || !event?.schedule) return [];
    const lang = this.language.lang();
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    return [
      { id: 'when', label: t('event.detail.when'), value: scheduleWhen(event.schedule, lang, t) },
      { id: 'dates', label: t('event.detail.dates'), value: activityDates(event, lang, t) },
      { id: 'place', label: t('event.detail.place'), value: placeText(event) },
    ];
  });
  protected readonly errors = computed(() => {
    this.formEvents();
    return formErrors(this.form, this.submitted());
  });
  /** O horario completo dunha actividade nova: o que se manda á vista previa. */
  private readonly previewBody = computed<EventSessionsPreviewRequest | null>(
    () => {
      this.formEvents();
      if (this.kind() !== 'SCHEDULED' || this.locked()) return null;
      const schedule = scheduleRequest(this.form);
      if (!schedule) return null;
      const v = this.form.getRawValue();
      return { schedule, facilityId: v.placeKind === 'FACILITY' ? v.facilityId : undefined };
    },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
  );

  constructor() {
    this.form.events.subscribe(() => this.formEvents.update((n) => n + 1));
    this.planner.watch(this.data.slug, this.previewBody);
    // A instalación escollida: os seus usos e partes (cun só uso que vale, xa vai escollido).
    effect(() => {
      const facilityId = this.facilityId();
      if (this.kind() === 'SCHEDULED' && !this.locked()) untracked(() => void this.loadFacility(facilityId));
    });
    // A parte só se pide se o uso vai por partes.
    effect(() => {
      const required = this.partOptions().length > 0;
      untracked(() => {
        if (this.form.controls.partRequired.value !== required) this.form.controls.partRequired.setValue(required);
      });
    });
    // Cambiar de instalación ou de uso borra o que dependía del.
    this.form.controls.facilityId.valueChanges.subscribe(() => {
      if (!this.locked()) this.form.patchValue({ useId: '', partId: '' });
    });
    this.form.controls.useId.valueChanges.subscribe(() => {
      if (!this.locked()) this.form.patchValue({ partId: '' });
    });
  }

  protected patchSchedule(change: Partial<ScheduleValue>): void {
    this.form.patchValue(change);
    this.form.markAsDirty();
  }

  protected async submit(submitEvent: Event): Promise<void> {
    submitEvent.preventDefault();
    if (this.saving()) return;
    this.submitted.set(true);
    this.saveProblem.set(null);
    this.form.updateValueAndValidity();
    this.form.markAllAsTouched();
    this.formEvents.update((n) => n + 1);
    if (this.form.invalid) return this.focusError();
    const scheduled = this.kind() === 'SCHEDULED' && !this.locked();
    // Unha actividade nova créase co que se ve na vista previa.
    if (scheduled && this.planner.state() !== 'ready') return;
    this.saving.set(true);
    try {
      const body = eventBody(this.form, scheduled ? conflictDates(this.planner.preview()) : []);
      const current = this.editing ? this.data.event : null;
      const saved = current
        ? await this.store.update(this.data.slug, current, body)
        : await this.store.create(this.data.slug, body);
      this.toasts.show('event.form.saved', { name: inLang(saved.name, this.language.lang()) });
      this.ref.close(saved);
    } catch (error) {
      const problem = asProblem(error);
      this.saveProblem.set(problem);
      if (problem.code === 'event.sessions-changed') void this.planner.refresh();
    } finally {
      this.saving.set(false);
    }
  }

  private async loadFacility(facilityId: string): Promise<void> {
    const facility = await this.facilityStore.load(this.data.slug, facilityId || null);
    if (!facility || this.form.controls.facilityId.value !== facilityId || this.locked()) return;
    const uses = facility.uses.filter((use) => use.rentalMode !== 'SEATS');
    if (!this.form.controls.useId.value && uses.length === 1) this.form.controls.useId.setValue(uses[0].id);
  }

  private focusError(): void {
    afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
