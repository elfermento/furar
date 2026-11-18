// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { WAITS_URL, waitFor } from '../reserve/reserve.fixture';
import { WaitsStore } from './waits.store';

describe('WaitsStore', () => {
  let store: WaitsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), WaitsStore] });
    store = TestBed.inject(WaitsStore);
  });
  afterEach(() => backend().verify());

  const later = waitFor({ id: 'w2', start: '2026-10-30T19:00:00Z', end: '2026-10-30T20:00:00Z' });

  it('lista unha soa vez por páxina, por inicio; con erro, queda baleira', async () => {
    const loading = store.load('naron');
    (await expectRequest(WAITS_URL)).flush([later, waitFor()]);
    await loading;
    expect(store.state()).toBe('ready');
    expect(store.items().map((wait) => wait.id)).toEqual(['w1', 'w2']);
    await store.load('naron');
    backend().expectNone(WAITS_URL);

    const again = store.load('naron', true);
    flushProblem(await expectRequest(WAITS_URL), 500, 'common.unexpected');
    await again;
    expect(store.state()).toBe('error');
    expect(store.items()).toEqual([]);
  });

  it('esperar engade a espera (ou deixa a que xa había); o erro rexeita', async () => {
    const joining = store.join('naron', 'f1', 'u1', waitFor().start);
    const request = await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST');
    expect(request.request.body).toEqual({ facilityId: 'f1', useId: 'u1', start: waitFor().start });
    request.flush(waitFor(), { status: 201, statusText: 'Created' });
    expect((await joining).id).toBe('w1');

    const repeated = store.join('naron', 'f1', 'u1', waitFor().start);
    (await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST')).flush(waitFor());
    await repeated;
    expect(store.items().map((wait) => wait.id)).toEqual(['w1']);

    const refused = store.join('naron', 'f1', 'u1', later.start);
    flushProblem(await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST'), 422, 'booking.wait-limit', {
      params: { max: 3 },
    });
    await expect(refused).rejects.toMatchObject({ code: 'booking.wait-limit' });
    expect(store.items()).toHaveLength(1);
  });

  it('deixar de esperar quita a espera; un 404 é éxito e outro erro rexeita sen quitala', async () => {
    const loading = store.load('naron');
    (await expectRequest(WAITS_URL)).flush([waitFor(), later]);
    await loading;

    const leaving = store.leave('naron', 'w1');
    flushProblem(
      await expectRequest((r) => r.url === `${WAITS_URL}/w1` && r.method === 'DELETE'),
      404,
      'booking.wait-not-found',
    );
    await leaving;
    expect(store.items().map((wait) => wait.id)).toEqual(['w2']);

    const failing = store.leave('naron', 'w2');
    flushProblem(
      await expectRequest((r) => r.url === `${WAITS_URL}/w2` && r.method === 'DELETE'),
      500,
      'common.unexpected',
    );
    await expect(failing).rejects.toMatchObject({ status: 500 });
    expect(store.items().map((wait) => wait.id)).toEqual(['w2']);
  });
});
