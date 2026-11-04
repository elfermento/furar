// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, provideTestApi } from '../../../../testing/api';
import { BASE, LIST_URL, OVERVIEW, SUMMARY_URL, detail, row } from '../../../../testing/manage-bookings';
import { ManageBookingsStore } from './manage-bookings.store';

const fail = { status: 500, statusText: 'x' };
const PARAMS = { slug: 'naron', sort: 'START' as const };
const page = (items: unknown[], totalItems: number, number = 0) => ({ items, page: number, size: 20, totalItems });

describe('ManageBookingsStore', () => {
  let store: ManageBookingsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), ManageBookingsStore] });
    store = TestBed.inject(ManageBookingsStore);
  });
  afterEach(() => backend().verify());

  it('sen instalacións nin portada: listas baleiras e erro só se aínda non había portada', async () => {
    const facilities = store.loadFacilities('naron');
    (await expectRequest(`${BASE}/facilities?page=0&size=100`)).flush(null, fail);
    await facilities;
    expect(store.facilities()).toEqual([]);

    let overview = store.loadOverview('naron');
    expect(store.overviewState()).toBe('loading');
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(null, fail);
    await overview;
    expect(store.overviewState()).toBe('error');

    overview = store.loadOverview('naron');
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await overview;
    overview = store.loadOverview('naron');
    expect(store.overviewState()).toBe('ready');
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(null, fail);
    await overview;
    expect(store.overviewState()).toBe('ready');
    expect(store.overview()).toEqual(OVERVIEW);
  });

  it('lista por páxinas: engade sen repetir, a última petición manda e un erro non borra o que hai', async () => {
    let load = store.loadList(PARAMS);
    (await expectRequest((r) => r.url === LIST_URL)).flush(null, fail);
    await load;
    expect(store.list().state).toBe('error');

    const first = store.loadList(PARAMS);
    load = store.loadList({ ...PARAMS, q: 'ana' });
    const [old, latest] = backend().match((r) => r.url === LIST_URL);
    latest.flush(page([row()], 3));
    old.flush(page([row({ id: 'vella' })], 1));
    await Promise.all([first, load]);
    expect(store.list().items.map((b) => b.id)).toEqual(['b1']);

    let more = store.loadMore(PARAMS);
    expect(store.list().loadingMore).toBe(true);
    (await expectRequest((r) => r.url === LIST_URL && r.params.get('page') === '1')).flush(
      page([row(), row({ id: 'b2' })], 3, 1),
    );
    await more;
    expect(store.list().items.map((b) => b.id)).toEqual(['b1', 'b2']);

    more = store.loadMore(PARAMS);
    (await expectRequest((r) => r.url === LIST_URL && r.params.get('page') === '2')).flush(null, fail);
    await more;
    expect(store.list()).toMatchObject({ loadingMore: false, state: 'ready', page: 1 });

    store.clearList();
    await store.loadMore(PARAMS);
    expect(store.list().state).toBe('idle');
  });

  it('detalle: erro, peche e a resposta dunha acción pasa ao detalle e á fila', async () => {
    let open = store.open('naron', 'b1');
    (await expectRequest(`${BASE}/bookings/b1`)).flush(null, fail);
    await open;
    expect(store.detail()).toEqual({ id: 'b1', state: 'error', detail: null });
    store.close();
    expect(store.detail()).toBeNull();

    const list = store.loadList(PARAMS);
    (await expectRequest((r) => r.url === LIST_URL)).flush(page([row(), row({ id: 'b2' })], 2));
    await list;
    open = store.open('naron', 'b1');
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
    await open;

    const cancel = store.cancel('naron', detail(), ['EMAIL']);
    (await expectRequest(`${BASE}/bookings/b1/cancel`)).flush(
      detail({ status: 'CANCELLED_BY_PERSON', cancelledAt: '2026-10-26T11:00:00Z', version: 4 }),
    );
    await cancel;
    // Sen portada pedida (o calendario), non se pide; queda a reserva cambiada para quen a escoite.
    backend().expectNone((r) => r.url === SUMMARY_URL);
    expect(store.updated()?.version).toBe(4);
    expect(store.detail()?.detail?.status).toBe('CANCELLED_BY_PERSON');
    expect(store.list().items[0]).toMatchObject({
      status: 'CANCELLED_BY_PERSON',
      cancelledAt: '2026-10-26T11:00:00Z',
      version: 4,
    });
    expect(store.list().items[1].status).toBe('CONFIRMED');
  });
});
