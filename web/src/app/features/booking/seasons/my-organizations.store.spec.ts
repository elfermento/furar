// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { SLUG_BASE, session } from '../../../../testing/seasons';
import { MyOrganizationsStore, OrganizationBookingsStore } from './my-organizations.store';

const MINE = `${SLUG_BASE}/me/organizations`;

describe('As entidades da persoa responsable e as súas reservas (ADR-0039, punto 10)', () => {
  afterEach(() => backend().verify());

  it('pídense unha vez por concello; se falla, baleiro e volven pedirse a seguinte vez', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const store = TestBed.inject(MyOrganizationsStore);
    const first = store.load('naron');
    flushProblem(await expectRequest(MINE), 500, 'common.unexpected');
    expect(await first).toEqual([]);
    const second = store.load('naron');
    (await expectRequest(MINE)).flush([{ id: 'o1', name: 'Club' }]);
    expect(await second).toEqual([{ id: 'o1', name: 'Club' }]);
    expect(await store.load('naron')).toEqual([{ id: 'o1', name: 'Club' }]);
    expect(store.items()).toHaveLength(1);
  });

  it('as reservas da entidade: unha páxina, sen repetir; «Ver máis» que falla deixa a lista; as solicitudes se fallan, baleiras', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), OrganizationBookingsStore] });
    const store = TestBed.inject(OrganizationBookingsStore);
    const load = store.load('naron', 'o1', 'UPCOMING');
    (await expectRequest((r) => r.url === `${MINE}/o1/bookings`)).flush({
      items: [session()],
      page: 0,
      size: 20,
      totalItems: 3,
    });
    await load;
    // Xa cargada: non se volve pedir sen `force`.
    await store.load('naron', 'o1', 'UPCOMING');
    const more = store.loadMore('naron', 'o1', 'UPCOMING');
    flushProblem(await expectRequest((r) => r.url === `${MINE}/o1/bookings`), 500, 'common.unexpected');
    await more;
    expect(store.lists.UPCOMING().items).toHaveLength(1);
    expect(store.lists.UPCOMING().loadingMore).toBe(false);
    const again = store.loadMore('naron', 'o1', 'UPCOMING');
    (await expectRequest((r) => r.url === `${MINE}/o1/bookings`)).flush({
      items: [session(), session({ id: 'b2' })],
      page: 1,
      size: 20,
      totalItems: 3,
    });
    await again;
    expect(store.lists.UPCOMING().items.map((b) => b.id)).toEqual(['b1', 'b2']);
    const requests = store.loadRequests('naron', 'o1');
    flushProblem(await expectRequest(`${MINE}/o1/series-requests`), 500, 'common.unexpected');
    await requests;
    expect(store.requests()).toEqual([]);
    const failed = store.load('naron', 'o1', 'HISTORY');
    flushProblem(await expectRequest((r) => r.url === `${MINE}/o1/bookings`), 500, 'common.unexpected');
    await failed;
    expect(store.lists.HISTORY().state).toBe('error');
  });
});
