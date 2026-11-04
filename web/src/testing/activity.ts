// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import type { TestRequest } from '@angular/common/http/testing';
import { CurrentMunicipality } from '../app/core/municipality/current-municipality';
import type { ActivityEventResponse } from '../app/features/staff/activity/activity';
import { ActivityPage } from '../app/features/staff/activity/activity.page';
import { expectRequest, fakeSession, provideTestApi } from './api';
import { municipality } from './fixtures';
import type { StaffMemberResponse } from '../app/features/staff/members/staff-members';
import { MEMBERS } from './staff-members';

/** Probas do «Rexistro de actividade» (D4-16): eventos de exemplo e a páxina montada no router. */

export const ACTIVITY_URL = '/api/v1/municipalities/naron/audit-events';
export const PLATFORM_ACTIVITY_URL = '/api/v1/audit-events';
export const EXPORT_BLOCKED_URL = '/api/v1/municipalities/naron/audit-events/blocked-exports';
export const PRIVACY_URL = '/api/v1/privacy-notice';

/** Os prazos do rexistro do despregue: un ano á vista e tres gardado. */
export const NOTICE = { activityVisible: 'P1Y', activityRetention: 'P3Y' };

export function activityEvent(overrides: Partial<ActivityEventResponse> = {}): ActivityEventResponse {
  return {
    id: 'e1',
    at: '2026-10-27T17:05:00Z',
    action: 'facility.rename',
    outcome: 'SUCCESS',
    actor: { kind: 'MEMBER', name: 'Ana Rey', email: 'ana@naron.example', roles: ['MANAGER'], blocked: false },
    target: { kind: 'FACILITY', id: 'f1' },
    municipality: 'naron',
    ...overrides,
  };
}

/** Xestora, plataforma, conta sen rol (fallido), código descoñecido e unha persoa do persoal. */
export const EVENTS: ActivityEventResponse[] = [
  activityEvent(),
  activityEvent({
    id: 'e2',
    at: '2026-07-01T08:30:00Z',
    action: 'staff.invite',
    actor: { kind: 'PLATFORM', roles: ['PLATFORM_ADMIN'], blocked: false },
    target: { kind: 'STAFF_MEMBER', id: 'm2', memberEmail: 'iago@naron.example' },
  }),
  activityEvent({
    id: 'e3',
    action: 'booking.cancel',
    outcome: 'FAILURE',
    actor: { kind: 'OTHER', roles: [], blocked: false },
    target: { kind: 'BOOKING', id: 'b1' },
  }),
  activityEvent({ id: 'e4', action: 'facility.teleport', target: undefined }),
];

export const FACILITIES = {
  items: [{ id: 'f1', name: 'Pavillón de O Alto', address: {}, missing: [], sports: [], status: 'ACTIVE', version: 1 }],
  page: 0,
  size: 100,
  totalItems: 1,
};

/** Monta a páxina no URL dado; no concello, responde ao persoal e ás instalacións. */
export async function renderActivity(
  url = '/naron/xestion/rexistro',
  session = fakeSession(),
  members: StaffMemberResponse[] = MEMBERS,
) {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(session),
      provideRouter([
        { path: 'naron/xestion/rexistro', component: ActivityPage },
        { path: 'plataforma/rexistro', component: ActivityPage, data: { scope: 'platform' } },
      ]),
    ],
  });
  const platform = url.startsWith('/plataforma');
  if (!platform) TestBed.inject(CurrentMunicipality).set(municipality());
  const harness = await RouterTestingHarness.create(url);
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  (await expectRequest((r) => r.url === PRIVACY_URL)).flush(NOTICE);
  if (!platform) {
    (await expectRequest((r) => r.url === '/api/v1/municipalities/naron/staff')).flush(members);
    (await expectRequest((r) => r.url === '/api/v1/municipalities/naron/facilities')).flush(FACILITIES);
  }
  return { harness, el, settle };
}

/** A petición pendente do rexistro (do concello ou da plataforma). */
export function activityRequest(platform = false): Promise<TestRequest> {
  return expectRequest((r) => r.url === (platform ? PLATFORM_ACTIVITY_URL : ACTIVITY_URL));
}

/** Responde unha páxina e agarda a que se pinte. */
export async function flushPage(
  request: TestRequest,
  settle: () => Promise<void>,
  items: ActivityEventResponse[] = EVENTS,
  nextCursor?: string,
): Promise<void> {
  request.flush({ items, ...(nextCursor ? { nextCursor } : {}) });
  await settle();
  await settle();
}

export const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
export const row = (el: Element, index: number) => el.querySelector<HTMLElement>(`[data-row="${index}"]`)!;
export const cell = (el: Element, index: number, name: string) =>
  text(row(el, index).querySelector(`[data-cell="${name}"]`));
