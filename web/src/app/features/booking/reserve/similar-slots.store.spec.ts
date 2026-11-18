// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { MY_SIMILAR_URL, SIMILAR_URL, busy } from './reserve.fixture';
import { SimilarSlotsStore } from './similar-slots.store';

describe('SimilarSlotsStore', () => {
  let store: SimilarSlotsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), SimilarSlotsStore] });
    store = TestBed.inject(SimilarSlotsStore);
  });
  afterEach(() => backend().verify());

  const query = { slug: 'naron', facilityId: 'f1', useId: 'u1', start: busy(1200).start, slots: 2, mine: false };
  const item = {
    reason: 'SAME_FACILITY_NEAR',
    facilityId: 'f1',
    facilityName: 'Campo',
    useId: 'u1',
    date: '2026-10-28',
    start: busy(1140).start,
    end: busy(1200).start,
    startMinute: 1140,
  };

  it('sen sesión pide a ruta pública co uso, a hora e as franxas', async () => {
    const loading = store.load(query);
    expect(store.state()).toBe('loading');
    const request = await expectRequest((r) => r.url === SIMILAR_URL);
    expect(request.request.params.get('useId')).toBe('u1');
    expect(request.request.params.get('start')).toBe(query.start);
    expect(request.request.params.get('slots')).toBe('2');
    request.flush({ items: [item] });
    await loading;
    expect(store.state()).toBe('ready');
    expect(store.items()).toHaveLength(1);
  });

  it('con sesión pide a da persoa; se falla, erro e sen filas', async () => {
    const loading = store.load({ ...query, mine: true });
    flushProblem(await expectRequest((r) => r.url === MY_SIMILAR_URL), 500, 'common.unexpected');
    await loading;
    expect(store.state()).toBe('error');
    expect(store.items()).toEqual([]);
  });

  it('só vale a última petición; ao limpar, a resposta que chega tarde descártase', async () => {
    const first = store.load(query);
    const second = store.load({ ...query, start: busy(1260).start });
    const [old, last] = backend().match((r) => r.url === SIMILAR_URL);
    last.flush({ items: [] });
    old.flush({ items: [item] });
    await Promise.all([first, second]);
    expect(store.items()).toEqual([]);

    const third = store.load(query);
    const pending = await expectRequest((r) => r.url === SIMILAR_URL);
    store.clear();
    pending.flush({ items: [item] });
    await third;
    expect(store.state()).toBe('idle');
    expect(store.items()).toEqual([]);
  });
});
