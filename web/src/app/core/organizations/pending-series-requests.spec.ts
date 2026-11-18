// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { PendingSeriesRequests } from './pending-series-requests';

const COUNT = '/api/v1/municipalities/naron/booking-series-requests/pending-count';

describe('O número da pestana «Entidades» (ADR-0039)', () => {
  afterEach(() => backend().verify());

  function setup(session = fakeSession()) {
    TestBed.configureTestingModule({ providers: provideTestApi(session) });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const counter = TestBed.inject(PendingSeriesRequests);
    TestBed.tick();
    return counter;
  }

  it('a xestión: as solicitudes pendentes; de novo ao navegar pola xestión e ao refrescar; se falla, sen número', async () => {
    const counter = setup();
    (await expectRequest(COUNT)).flush({ count: 2 });
    await vi.waitFor(() => expect(counter.count()).toBe(2));
    await TestBed.inject(Router)
      .navigateByUrl('/naron/xestion/entidades')
      .catch(() => false);
    const again = backend().match(COUNT);
    for (const request of again) request.flush({ count: 1 });
    counter.refresh();
    flushProblem(await expectRequest(COUNT), 500, 'common.unexpected');
    await vi.waitFor(() => expect(counter.count()).toBeNull());
  });

  it('Recepción: nin o pide nin ten número', () => {
    const counter = setup(fakeSession({ staff: 'naron' }));
    backend().expectNone(COUNT);
    expect(counter.count()).toBeNull();
  });
});
