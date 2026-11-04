// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { EnvironmentInjector, createEnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { naronPanel } from '../../../testing/indicators';
import { BASE } from '../../../testing/manage-bookings';
import { DEFAULT_FILTERS } from './indicator-filters';
import { IndicatorsStore } from './indicators.store';

const PANEL_URL = `${BASE}/indicators`;
const year = { ...DEFAULT_FILTERS, period: 'ano' as const };

describe('IndicatorsStore', () => {
  let injector: ReturnType<typeof createEnvironmentInjector>;
  let store: IndicatorsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideTestApi()] });
    injector = createEnvironmentInjector([IndicatorsStore], TestBed.inject(EnvironmentInjector));
    store = injector.get(IndicatorsStore);
  });

  afterEach(() => backend().verify());

  it('cambiar os filtros deprisa cancela a petición anterior: só se ve a última', async () => {
    const first = store.load('naron', DEFAULT_FILTERS);
    const old = await expectRequest((r) => r.url === PANEL_URL && r.params.get('period') === 'MONTH');
    const second = store.load('naron', year);
    expect(old.cancelled).toBe(true);
    await first;
    expect(store.state()).toBe('loading');
    (await expectRequest((r) => r.url === PANEL_URL && r.params.get('period') === 'YEAR')).flush(naronPanel());
    await second;
    expect(store.state()).toBe('ready');
    expect(store.loaded()).toEqual(year);
    expect(store.panel()).not.toBeNull();
  });

  it('un erro da petición superada non pisa a nova', async () => {
    const first = store.load('naron', DEFAULT_FILTERS);
    const old = await expectRequest((r) => r.params.get('period') === 'MONTH');
    const second = store.load('naron', year);
    await first;
    expect(store.problem()).toBeNull();
    flushProblem(await expectRequest((r) => r.params.get('period') === 'YEAR'), 400, 'indicators.invalid-filter');
    await second;
    expect(old.cancelled).toBe(true);
    expect(store.state()).toBe('error');
    expect(store.problem()?.code).toBe('indicators.invalid-filter');
  });

  it('ao saír da páxina, a petición en voo cancélase', async () => {
    const pending = store.load('naron', DEFAULT_FILTERS);
    const request = await expectRequest((r) => r.url === PANEL_URL);
    injector.destroy();
    await pending;
    expect(request.cancelled).toBe(true);
    expect(store.state()).toBe('loading');
  });
});
