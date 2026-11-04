// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentMunicipality } from '../app/core/municipality/current-municipality';
import type {
  ManagedBookingDetailResponse,
  ManagedBookingResponse,
} from '../app/features/booking/manage/manage-bookings';
import { ManageBookingsPage } from '../app/features/booking/manage/manage-bookings.page';
import type { BookingsOverviewResponse } from '../app/features/booking/manage/manage-bookings.store';
import { backend, expectRequest, fakeSession, provideTestApi } from './api';
import { municipality } from './fixtures';

/** Probas das reservas do concello (D2-15): datos da maqueta 26 e a páxina montada no router. */

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

export const BASE = '/api/v1/municipalities/naron';
export const LIST_URL = `${BASE}/bookings`;
export const SUMMARY_URL = `${BASE}/bookings/summary`;
export const FACILITIES_URL = `${BASE}/facilities`;

export function row(overrides: Partial<ManagedBookingResponse> = {}): ManagedBookingResponse {
  return {
    id: 'b1',
    code: 'FUR-7K2Q',
    holderName: 'Ana Rey',
    facilityId: 'f1',
    facilityName: 'Pista de pádel 1',
    useId: 'u1',
    useName: 'Pista completa',
    rentalMode: 'WHOLE',
    start: '2026-10-26T18:00:00Z',
    end: '2026-10-26T19:00:00Z',
    slots: 1,
    status: 'CONFIRMED',
    paymentStatus: 'PENDING',
    price: { kind: 'PAID', amountCents: 800 },
    done: false,
    reservedAt: '2026-10-26T09:55:00Z',
    version: 3,
    ...overrides,
  };
}

export function detail(overrides: Partial<ManagedBookingDetailResponse> = {}): ManagedBookingDetailResponse {
  return {
    ...row(),
    holderEmail: 'ana@correo.example',
    facilityAddress: 'Rúa Buxo, 16 · Narón',
    cancelUntil: '2026-10-26T16:00:00Z',
    paymentTargets: ['PAID'],
    cancellable: true,
    cancelChannels: ['EMAIL', 'WEB'],
    customFields: {},
    ...overrides,
  };
}

const PAID = row({
  id: 'b2',
  holderName: 'Iago Sixto',
  paymentStatus: 'PAID',
  start: '2026-10-28T19:30:00Z',
  end: '2026-10-28T20:30:00Z',
});
export const REFUND = row({
  id: 'b3',
  holderName: 'Sara Lema',
  status: 'CANCELLED_BY_MUNICIPALITY',
  paymentStatus: 'TO_REFUND',
});
const FREE = row({ id: 'b4', holderName: 'Escola Piñeiros', paymentStatus: 'NONE', price: { kind: 'FREE' } });

export const OVERVIEW: BookingsOverviewResponse = {
  latest: [row(), PAID, FREE],
  pendingPayment: { count: 5, items: [row()] },
  toRefund: { count: 1, items: [REFUND] },
  cancelled: { count: 2, items: [REFUND] },
};

export const FACILITIES = {
  items: [
    {
      id: 'f1',
      name: 'Pista de pádel 1',
      status: 'ACTIVE',
      version: 1,
      missing: [],
      sports: [],
      address: {},
      cover: {
        id: 'i1',
        kind: 'PHOTO',
        thumb: '/fotos/padel.jpg',
        large: '/fotos/padel-l.jpg',
        width: 320,
        height: 240,
        alt: {},
      },
    },
    { id: 'f2', name: 'Campo de fútbol 1', status: 'ACTIVE', version: 1, missing: [], sports: [], address: {} },
  ],
  page: 0,
  size: 100,
  totalItems: 2,
};

interface Req {
  url: string;
  method: string;
}
export const isList = (r: Req) => r.url === LIST_URL && r.method === 'GET';
export const page = (items: unknown[], totalItems = items.length) => ({ items, page: 0, size: 20, totalItems });

/** Hoxe é o luns 26 de outubro de 2026 (só a data: os temporizadores seguen sendo reais). */
export function useMonday(): void {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-26T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });
}

/** Monta `/naron/xestion/reservas` e responde á portada e ás instalacións. */
export async function renderManage(session = fakeSession(), url = '/naron/xestion/reservas') {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(session),
      provideRouter([
        { path: 'naron/xestion/reservas', component: ManageBookingsPage },
        { path: 'naron/xestion/calendario', component: StubPage },
      ]),
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const harness = await RouterTestingHarness.create(url);
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
  (await expectRequest((r) => r.url === FACILITIES_URL)).flush(FACILITIES);
  await vi.waitFor(async () => {
    await settle();
    expect(el.querySelector('[data-section="latest"] .f-mrow')).not.toBeNull();
  });
  return { harness, el, settle };
}

export const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
export const rowsOf = (el: Element, section: string) => [
  ...el.querySelectorAll<HTMLButtonElement>(`[data-section="${section}"] .f-mrow`),
];
export const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

/** Abre a primeira das últimas reservas e responde co seu detalle. */
export async function openDetail(el: HTMLElement, settle: () => Promise<void>, data = detail()) {
  rowsOf(el, 'latest')[0].click();
  (await expectRequest(`${BASE}/bookings/b1`)).flush(data);
  await vi.waitFor(async () => {
    await settle();
    expect(el.querySelector('[data-detail-card] furar-value-list')).not.toBeNull();
  });
  return el.querySelector<HTMLElement>('[data-detail-card]')!;
}
