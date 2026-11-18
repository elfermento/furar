// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, provideTestApi } from '../../../testing/api';
import { DISPLAY_PREFERENCES_REMOTE } from './display-preferences';
import { provideAccountDisplayPreferences } from './display-preferences-remote';

const URL = '/api/v1/me/display-preferences';

describe('Axustes de accesibilidade na conta', () => {
  afterEach(() => backend().verify());

  function setup() {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(fakeSession({})), provideAccountDisplayPreferences()],
    });
    const remote = TestBed.inject(DISPLAY_PREFERENCES_REMOTE);
    if (!remote) throw new Error('sen provedor');
    return remote;
  }

  it('le os gardados, sen concello no URL', async () => {
    const remote = setup();
    const loading = remote.load();
    (await expectRequest(URL)).flush({ textSize: 'large', highContrast: true, reducedMotion: null });
    expect(await loading).toEqual({ textSize: 'large', highContrast: true, reducedMotion: null });
  });

  it('204: a conta non os gardou nunca', async () => {
    const remote = setup();
    const loading = remote.load();
    (await expectRequest(URL)).flush(null, { status: 204, statusText: 'No Content' });
    expect(await loading).toBeNull();
  });

  it('garda os tres valores cun PUT', async () => {
    const remote = setup();
    const saving = remote.save({ textSize: 'x-large', highContrast: null, reducedMotion: false });
    const put = await expectRequest(URL);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ textSize: 'x-large', highContrast: null, reducedMotion: false });
    put.flush(null, { status: 204, statusText: 'No Content' });
    await saving;
  });
});
