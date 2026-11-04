// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { ApiProblem } from '../../../core/errors/api-problem';
import { DescriptionStore } from '../description/description.store';
import { facility } from './facility.fixture';
import type { Facility } from './facility-detail.store';
import { FacilityDetailStore, ifMatch } from './facility-detail.store';

const URL = '/api/v1/municipalities/naron/facilities/f1';

describe('FacilityDetailStore: contrato de estado da ficha (D1-21a)', () => {
  let store: FacilityDetailStore;
  let description: DescriptionStore;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), FacilityDetailStore] });
    store = TestBed.inject(FacilityDetailStore);
    description = TestBed.inject(DescriptionStore);
    const loading = store.load('naron', 'f1');
    expect(store.state()).toBe('loading');
    (await expectRequest(URL)).flush(facility({ version: 3 }));
    await loading;
  });

  afterEach(() => backend().verify());

  const saveDescription = (gl: string) => store.saveFacility((target) => description.update(target, { gl, es: gl }));

  it('garda a instalación e a súa versión nun signal', () => {
    expect(store.state()).toBe('ready');
    expect(store.facility()?.name).toBe('Campo de fútbol 1 de O Alto');
    expect(store.version()).toBe(3);
    expect(store.missing()).toEqual(['USES', 'SCHEDULE']);
    expect(ifMatch(3)).toBe('"3"');
  });

  it('pon en cola os gardados da instalación e cada un manda a última versión', async () => {
    const first = saveDescription('Primeira');
    const second = saveDescription('Segunda');
    const put1 = await expectRequest({ method: 'PUT', url: `${URL}/description` });
    // O segundo non sae ata que responda o primeiro.
    expect(backend().match({ method: 'PUT', url: `${URL}/description` })).toHaveLength(0);
    expect(put1.request.headers.get('If-Match')).toBe('"3"');
    put1.flush(facility({ version: 4, description: { gl: 'Primeira', es: 'Primeira' } }));
    await first;
    const put2 = await expectRequest({ method: 'PUT', url: `${URL}/description` });
    expect(put2.request.headers.get('If-Match')).toBe('"4"');
    put2.flush(facility({ version: 5, description: { gl: 'Segunda', es: 'Segunda' } }));
    expect((await second).version).toBe(5);
    expect(store.version()).toBe(5);
    expect(store.facility()?.description?.gl).toBe('Segunda');
  });

  it('un erro non para a cola nin cambia a instalación', async () => {
    const failing = saveDescription('Mala');
    const next = saveDescription('Boa');
    flushProblem(
      await expectRequest({ method: 'PUT', url: `${URL}/description` }),
      422,
      'facility.description-invalid',
    );
    await expect(failing).rejects.toBeInstanceOf(ApiProblem);
    expect(store.version()).toBe(3);
    const put = await expectRequest({ method: 'PUT', url: `${URL}/description` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    put.flush(facility({ version: 4 }));
    await next;
    expect(store.stale()).toBe(false);
  });

  it('412: marca «stale», non toca a instalación e recargar trae a versión nova', async () => {
    const saving = saveDescription('O meu texto');
    flushProblem(await expectRequest({ method: 'PUT', url: `${URL}/description` }), 412, 'common.stale-version');
    const error = await saving.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).status).toBe(412);
    expect(store.stale()).toBe(true);
    expect(store.version()).toBe(3);

    const reloading = store.reload();
    (await expectRequest(URL)).flush(facility({ version: 7, name: 'Campo 1' }));
    await reloading;
    expect(store.stale()).toBe(false);
    expect(store.facility()?.name).toBe('Campo 1');

    const retry = saveDescription('O meu texto');
    const put = await expectRequest({ method: 'PUT', url: `${URL}/description` });
    expect(put.request.headers.get('If-Match')).toBe('"7"');
    put.flush(facility({ version: 8 }));
    await retry;
  });

  it('non acepta unha resposta máis vella ca a que xa ten', async () => {
    const reloading = store.reload();
    (await expectRequest(URL)).flush(facility({ version: 9 }));
    await reloading;
    const stale: Facility = facility({ version: 2, name: 'Vello' });
    await store.saveFacility(() => Promise.resolve(stale));
    expect(store.facility()?.name).not.toBe('Vello');
  });

  it('cambia o estado con If-Match e as decisións do modal', async () => {
    const changing = store.changeStatus('TEMPORARILY_CLOSED', [{ bookingId: 'b1', action: 'KEEP', channels: [] }]);
    const put = await expectRequest({ method: 'PUT', url: `${URL}/status` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      status: 'TEMPORARILY_CLOSED',
      decisions: [{ bookingId: 'b1', action: 'KEEP', channels: [] }],
    });
    put.flush(facility({ version: 4, status: 'TEMPORARILY_CLOSED' }));
    await changing;
    expect(store.facility()?.status).toBe('TEMPORARILY_CLOSED');
  });

  it('erro de carga: estado de erro co problema', async () => {
    const loading = store.load('naron', 'nope');
    flushProblem(await expectRequest('/api/v1/municipalities/naron/facilities/nope'), 404, 'facility.not-found');
    await loading;
    expect(store.state()).toBe('error');
    expect(store.problem()?.status).toBe(404);
    expect(store.facility()).toBeNull();
  });
});
