// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentMunicipality } from '../app/core/municipality/current-municipality';
import type { StaffMemberResponse } from '../app/features/staff/members/staff-members';
import { StaffMembersPage } from '../app/features/staff/members/staff-members.page';
import { backend, expectRequest, fakeSession, provideTestApi } from './api';
import { municipality } from './fixtures';

/** Probas de «Persoal» (D4-14): persoas de exemplo e a páxina montada no router. */

export const STAFF_URL = '/api/v1/municipalities/naron/staff';

/** Agora: o mércores 28 de outubro de 2026 ás 10:00 UTC. */
export const NOW = new Date('2026-10-28T10:00:00Z');

export function member(overrides: Partial<StaffMemberResponse> = {}): StaffMemberResponse {
  return {
    id: 'm1',
    email: 'ana@naron.example',
    anonymized: false,
    blocked: false,
    name: 'Ana Rey',
    role: 'MANAGER',
    status: 'ACTIVE',
    language: 'gl',
    invitedAt: '2026-10-01T09:00:00Z',
    invitedBy: { kind: 'MEMBER', name: 'Xestora Narón' },
    acceptedAt: '2026-10-02T09:00:00Z',
    lastSignInAt: '2026-10-27T17:05:00Z',
    version: 3,
    ...overrides,
  };
}

/** Activa, convidada, caducada, sen enviar e desactivada. */
export const MEMBERS: StaffMemberResponse[] = [
  member(),
  member({
    id: 'm2',
    email: 'iago@naron.example',
    name: undefined,
    role: 'STAFF',
    status: 'INVITED',
    acceptedAt: undefined,
    lastSignInAt: undefined,
    invitation: {
      sentAt: '2026-10-28T09:00:00Z',
      expiresAt: '2026-11-04T09:00:00Z',
      delivery: 'SENT',
      resendsExhausted: false,
    },
    version: 1,
  }),
  member({
    id: 'm3',
    email: 'eva@naron.example',
    name: undefined,
    status: 'INVITED',
    acceptedAt: undefined,
    lastSignInAt: undefined,
    invitedBy: { kind: 'PLATFORM' },
    invitation: {
      sentAt: '2026-10-14T09:00:00Z',
      expiresAt: '2026-10-21T09:00:00Z',
      delivery: 'SENT',
      resendsExhausted: false,
    },
    version: 2,
  }),
  member({
    id: 'm4',
    email: 'xan@naron.example',
    name: undefined,
    role: 'STAFF',
    invitedBy: { kind: 'MEMBER' },
    status: 'INVITED',
    acceptedAt: undefined,
    lastSignInAt: undefined,
    invitation: {
      sentAt: '2026-10-28T09:00:00Z',
      expiresAt: '2026-11-04T09:00:00Z',
      delivery: 'FAILED',
      resendsExhausted: false,
    },
    version: 1,
  }),
  member({
    id: 'm5',
    email: 'rosa@naron.example',
    name: 'Rosa Mel',
    role: 'STAFF',
    status: 'DEACTIVATED',
    lastSignInAt: undefined,
    deactivatedAt: '2026-10-20T09:00:00Z',
    version: 4,
  }),
];

/** Data falsa (só `Date`: os temporizadores seguen sendo reais) e limpeza das capas. */
export function useNow(): void {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(NOW);
  });
  afterEach(() => {
    vi.useRealTimers();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });
}

/** Ancho do móbil (`matchMedia` non coincide) ou do escritorio (sen `matchMedia`, como jsdom). */
export function useMobile(): void {
  let original: typeof window.matchMedia;
  beforeEach(() => {
    original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    })) as unknown as typeof window.matchMedia;
  });
  afterEach(() => {
    window.matchMedia = original;
  });
}

/** Monta `/naron/xestion/persoal` e responde á lista. */
export async function renderStaff(
  session = fakeSession(),
  members: StaffMemberResponse[] = MEMBERS,
  place: Parameters<typeof municipality>[0] = {},
) {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(session),
      provideRouter([{ path: 'naron/xestion/persoal', component: StaffMembersPage }]),
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality(place));
  const harness = await RouterTestingHarness.create('/naron/xestion/persoal');
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  (await expectRequest((r) => r.url === STAFF_URL && r.method === 'GET')).flush(members);
  await vi.waitFor(async () => {
    await settle();
    expect(el.querySelector('[data-invite]')).not.toBeNull();
  });
  return { harness, el, settle };
}

export const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
export const rowOf = (el: Element, id: string) => el.querySelector<HTMLElement>(`[data-member="${id}"]`)!;
export const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

/** Abre o menú de accións dunha fila e pica nunha acción. */
export async function pickAction(el: HTMLElement, settle: () => Promise<void>, id: string, action: string) {
  // Un clic de verdade enfoca o botón (jsdom non o fai).
  const trigger = el.querySelector<HTMLButtonElement>(`[data-actions="${id}"]`)!;
  trigger.focus();
  trigger.click();
  await settle();
  const item = document.querySelector<HTMLButtonElement>(`[role="menu"] [data-action="${action}"]`);
  if (!item) throw new Error(`sen a acción ${action}`);
  item.click();
  await settle();
}
