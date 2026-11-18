// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, provideTestApi } from '../../../testing/api';
import { DependentsStore } from './dependents.store';

const URL = '/api/v1/me/dependents';
const UXIA = { id: 'd1', name: 'Uxía Vilar', birthYear: 2015, version: 0 };
const BRAIS = { id: 'd2', name: 'Brais Vilar', birthYear: 2018, version: 0 };

describe('Persoas a cargo da conta', () => {
  afterEach(() => backend().verify());

  function setup() {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({}))] });
    return TestBed.inject(DependentsStore);
  }

  it('pide a lista unha soa vez, sen concello no URL', async () => {
    const store = setup();
    const first = store.load();
    const second = store.load();
    expect(store.state()).toBe('loading');
    (await expectRequest(URL)).flush([UXIA]);
    await Promise.all([first, second]);
    expect(store.state()).toBe('ready');
    expect(store.items()).toEqual([UXIA]);
  });

  it('se a lista non chega queda en erro e pódese volver pedir', async () => {
    const store = setup();
    const loading = store.load();
    (await expectRequest(URL)).flush(null, { status: 500, statusText: 'Error' });
    await loading;
    expect(store.state()).toBe('error');
    const again = store.load();
    (await expectRequest(URL)).flush([]);
    await again;
    expect(store.state()).toBe('ready');
  });

  it('gardar, editar (If-Match coa versión) e eliminar manteñen a lista por nome', async () => {
    const store = setup();
    const loading = store.load();
    (await expectRequest(URL)).flush([UXIA]);
    await loading;

    const creating = store.create({ name: 'Brais Vilar', birthYear: 2018, inMyCare: true });
    const post = await expectRequest(URL);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ name: 'Brais Vilar', birthYear: 2018, inMyCare: true });
    post.flush(BRAIS);
    expect((await creating).id).toBe('d2');
    expect(store.items().map((p) => p.name)).toEqual(['Brais Vilar', 'Uxía Vilar']);

    const updating = store.update(UXIA, { name: 'Antía Vilar', birthYear: 2015, inMyCare: true });
    const put = await expectRequest(`${URL}/d1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.get('If-Match')).toBe('"0"');
    put.flush({ ...UXIA, name: 'Antía Vilar', version: 1 });
    await updating;
    expect(store.items().map((p) => p.name)).toEqual(['Antía Vilar', 'Brais Vilar']);

    const removing = store.remove('d2');
    const del = await expectRequest(`${URL}/d2`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    await removing;
    expect(store.items().map((p) => p.id)).toEqual(['d1']);

    store.clear();
    expect(store.items()).toEqual([]);
    expect(store.state()).toBe('idle');
  });
});
