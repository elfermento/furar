// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { MapSettings } from '../app/core/deployment/deployment-settings';
import { CurrentMunicipality } from '../app/core/municipality/current-municipality';
import { PaymentNotesStore } from '../app/features/facility/description/payment-notes.store';
import type { Facility } from '../app/features/facility/detail/facility-detail.store';
import { FacilityDetailStore } from '../app/features/facility/detail/facility-detail.store';
import { ScheduleStore } from '../app/features/facility/schedule/schedule.store';
import type { Use } from '../app/features/facility/uses/uses.store';
import { UsesStore } from '../app/features/facility/uses/uses.store';
import { WizardForms } from '../app/features/facility/wizard/wizard-forms';
import { WizardSaver } from '../app/features/facility/wizard/wizard-saver.store';
import { WizardUseEditor } from '../app/features/facility/wizard/wizard-use-editor';
import { WizardStore } from '../app/features/facility/wizard/wizard.store';
import { expectRequest, provideTestApi } from './api';
import { IGN_MAP, provideDeploymentSettings } from './deployment';
import { municipality } from './fixtures';

export const WIZARD_BASE = '/api/v1/municipalities/naron';

/** Pago e devolución do concello (`GET …/payment-notes`), iguais para todas. */
export const WIZARD_NOTES = {
  scope: 'ALL',
  payment: { gl: 'No mostrador', es: 'En el mostrador' },
  refund: { gl: 'Polo concello', es: 'Por el ayuntamiento' },
  furarPayment: { gl: 'Furar paga', es: 'Furar paga' },
  furarRefund: { gl: 'Furar devolve', es: 'Furar devuelve' },
  paymentDefault: false,
  refundDefault: false,
};

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** Semana gardada: o mesmo tramo de luns a domingo e os festivos pechados. */
export function wizardWeek(startMinute = 480, endMinute = 1380) {
  return [
    ...WEEKDAYS.map((day) => ({ day, intervals: [{ startMinute, endMinute }] })),
    { day: 'HOLIDAYS', intervals: [] },
  ];
}

/** Tempada «Todo o ano» do borrador. */
export function wizardSeason(week: unknown[] = wizardWeek()) {
  return { id: 's1', name: 'Todo o ano', startDate: '2026-01-01', endDate: '2026-12-31', version: 1, week };
}

/** `GET …/schedule` dun borrador: sen tempadas salvo que se diga. */
export function wizardSchedule(seasons: unknown[] = []) {
  return { today: '2026-11-03', seasons, holidays: [], exceptions: [] };
}

/**
 * O asistente sen páxina: os seus stores, coma os da páxina, e o concello de Narón. Co mapa do
 * IGN salvo que se diga outro.
 */
export function setupWizard(map: MapSettings | null = IGN_MAP): WizardStore {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(),
      provideDeploymentSettings(map),
      WizardStore,
      WizardForms,
      WizardSaver,
      WizardUseEditor,
      FacilityDetailStore,
      UsesStore,
      ScheduleStore,
      PaymentNotesStore,
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality({ legal: { ineCode: '15054' } }));
  return TestBed.inject(WizardStore);
}

/** Asistente baleiro: responde aos campos propios e ao pago do concello. */
export async function startWizard(
  store: WizardStore,
  { definitions = [] as unknown[], notes = WIZARD_NOTES as object | null } = {},
): Promise<void> {
  const started = store.start();
  (await expectRequest(`${WIZARD_BASE}/custom-fields?target=FACILITY`)).flush(definitions);
  (await expectRequest(`${WIZARD_BASE}/payment-notes`)).flush(notes);
  await started;
}

/** Volve a un borrador: a instalación, os seus usos, o horario, os campos propios e o pago. */
export async function openWizard(
  store: WizardStore,
  draft: Facility,
  {
    uses = [] as Use[],
    seasons = [] as unknown[],
    definitions = [] as unknown[],
    notes = WIZARD_NOTES as object | null,
  } = {},
): Promise<void> {
  const opened = store.open(draft.id);
  (await expectRequest(`${WIZARD_BASE}/facilities/${draft.id}`)).flush(draft);
  (await expectRequest(`${WIZARD_BASE}/facilities/${draft.id}/uses`)).flush(uses);
  (await expectRequest(`${WIZARD_BASE}/facilities/${draft.id}/schedule`)).flush(wizardSchedule(seasons));
  (await expectRequest(`${WIZARD_BASE}/custom-fields?target=FACILITY`)).flush(definitions);
  (await expectRequest(`${WIZARD_BASE}/payment-notes`)).flush(notes);
  await opened;
}

/** Foto subida á biblioteca (`POST …/photos`), pendente de gardar nunha instalación. */
export function libraryPhoto(id: string, alt = { gl: '', es: '' }) {
  return {
    id,
    alt,
    thumb: `/photos/${id}/thumb`,
    large: `/photos/${id}/large`,
    width: 1200,
    height: 800,
    pending: true,
    uploadedAt: '2026-11-03T10:00:00Z',
    usedIn: [],
  };
}

/** Debuxo incluído (`GET …/drawings`). */
export function drawing(code: string, gl: string, es = gl) {
  return {
    code,
    name: { gl, es },
    alt: { gl: `Debuxo: ${gl}`, es: `Dibujo: ${es}` },
    thumb: `/drawings/${code}/thumb`,
    large: `/drawings/${code}/large`,
    width: 1200,
    height: 800,
  };
}

/** Un paso do asistente, só, cos stores de `setupWizard`. */
export async function renderWizardStep<T>(component: Type<T>) {
  const fixture = TestBed.createComponent(component);
  fixture.autoDetectChanges();
  await fixture.whenStable();
  const el = fixture.nativeElement as HTMLElement;
  const stable = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const click = async (target: Element | null) => {
    (target as HTMLElement).click();
    await stable();
  };
  /** Escribe nun campo de texto e avisa ao formulario. */
  const type = async (target: Element | null, value: string) => {
    const field = target as HTMLInputElement | HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await stable();
  };
  return { fixture, el, stable, click, type };
}
