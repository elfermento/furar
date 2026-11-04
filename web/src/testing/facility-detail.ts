// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import type { BlockResponse } from '../app/api/models/block-response';
import type { ScheduleResponse } from '../app/api/models/schedule-response';
import { CurrentMunicipality } from '../app/core/municipality/current-municipality';
import { BLOCKS_URL } from '../app/features/facility/blocks/blocks.fixture';
import { FacilityDetailPage } from '../app/features/facility/detail/facility-detail.page';
import type { Facility } from '../app/features/facility/detail/facility-detail.store';
import { facility } from '../app/features/facility/detail/facility.fixture';
import { SCHEDULE_URL } from '../app/features/facility/schedule/schedule.fixture';
import { FACILITY_URL, USES_URL } from '../app/features/facility/uses/uses.fixture';
import type { Use } from '../app/features/facility/uses/uses.store';
import { backend, expectRequest, provideTestApi } from './api';
import { municipality } from './fixtures';

/** O que responde a API ao abrir a ficha (por defecto, sen nada nas seccións). */
export interface DetailSetup {
  readonly facility?: Partial<Facility>;
  readonly uses?: Use[] | 'error';
  readonly schedule?: ScheduleResponse | 'error';
  readonly blocks?: BlockResponse[] | 'error';
  readonly customFields?: unknown[];
  /** Pago e devolución do concello (`scope`: por defecto, igual para todas, sen sección na ficha). */
  readonly paymentNotes?: object;
  /** Provedores extra (p. ex. o buscador de enderezos). */
  readonly providers?: unknown[];
}

const fail = { status: 500, statusText: 'x' };

export const PAYMENT_NOTES_URL = '/api/v1/municipalities/naron/payment-notes';

/** `GET …/payment-notes` co ámbito pedido e os textos de Furar. */
export function paymentNotes(scope: 'ALL' | 'FACILITY') {
  const furarPayment = { gl: 'No mostrador, ao chegar', es: 'En el mostrador, al llegar' };
  const furarRefund = { gl: 'O concello dirache como cha devolve', es: 'El ayuntamiento te dirá cómo te lo devuelve' };
  return {
    payment: furarPayment,
    paymentDefault: true,
    refund: furarRefund,
    refundDefault: true,
    scope,
    furarPayment,
    furarRefund,
  };
}

/**
 * Abre a ficha de xestión e responde as lecturas de cada sección; devolve axudas comúns e as do
 * modo edición global (`edit`, `save`, `cancel`) e dos modais (`dialog`).
 */
export async function renderDetail(setup: DetailSetup = {}) {
  TestBed.configureTestingModule({ providers: [...provideTestApi(), ...((setup.providers ?? []) as never[])] });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const fixture = TestBed.createComponent(FacilityDetailPage);
  fixture.componentRef.setInput('id', 'f1');
  const el = fixture.nativeElement as HTMLElement;
  await fixture.whenStable();
  (await expectRequest(FACILITY_URL)).flush(facility(setup.facility));
  const answer = async (url: string, body: unknown) => {
    const request = await expectRequest({ method: 'GET', url });
    if (body === 'error') request.flush({}, fail);
    else request.flush(body as object);
  };
  await answer(USES_URL, setup.uses ?? []);
  await answer(SCHEDULE_URL, setup.schedule ?? { seasons: [], holidays: [] });
  (await expectRequest((request) => request.url.endsWith('/custom-fields'))).flush(setup.customFields ?? []);
  await answer(BLOCKS_URL, setup.blocks ?? []);
  await answer(PAYMENT_NOTES_URL, setup.paymentNotes ?? paymentNotes('ALL'));

  const stable = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const click = async (target: Element) => {
    (target as HTMLElement).click();
    await stable();
  };
  const type = async (input: HTMLInputElement | HTMLTextAreaElement, value: string) => {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await stable();
  };
  const button = (text: string, root: ParentNode = el) =>
    [...root.querySelectorAll<HTMLButtonElement>('button')].find(
      (b) => b.textContent?.trim() === text,
    ) as HTMLButtonElement;
  /** A capa de enriba (o modal aberto máis recente). */
  const dialog = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1) ?? null;
  const edit = () => click(el.querySelector('[data-edit]')!);
  const save = () => click(el.querySelector('[data-edit-save]')!);
  /** «Cancelar» da barra; se hai cambios e pregunta, descártaos. */
  const cancel = async () => {
    await click(el.querySelector('[data-edit-cancel]')!);
    const discard = document.querySelector('[data-confirm-accept]');
    if (discard) await click(discard);
  };
  /** Abre o campo de data `index` dentro de `root` e pica no día `day` (orde da grella) do mes. */
  const pickDate = async (root: ParentNode, index: number, day: number): Promise<string> => {
    await click(root.querySelectorAll<HTMLButtonElement>('.f-date-field__button')[index]);
    const days = [...document.querySelectorAll<HTMLElement>('.f-date-field__panel [data-date]')].filter(
      (cell) => !(cell as HTMLButtonElement).disabled && cell.getAttribute('aria-disabled') !== 'true',
    );
    const date = days[day].dataset['date'] ?? '';
    await click(days[day]);
    return date;
  };
  /** Escolle `minutes` no selector de hora `picker` (abre a lista e pica na opción). */
  const pickTime = async (picker: Element, minutes: number) => {
    await click(picker.querySelector('button')!);
    await click(document.querySelector(`[data-minutes="${minutes}"]`)!);
  };
  await vi.waitFor(async () => {
    await stable();
    expect(el.querySelector('[data-mode="skeleton"]')).toBeNull();
  });
  return { el, fixture, stable, click, type, button, dialog, edit, save, cancel, pickDate, pickTime, backend };
}
